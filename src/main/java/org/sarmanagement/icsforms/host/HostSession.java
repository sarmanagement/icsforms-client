/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.sarmanagement.icsforms.persistence.EditLockSupport;
import org.sarmanagement.icsforms.persistence.FileRecordVersionStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentNotFoundException;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreDescriptor;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Hosts one selected incident over mutual TLS. Starting flushes editors and takes exclusive ownership of the
 * incident's files before any device is admitted; a failed start releases everything it acquired so the incident
 * remains usable and nothing is partially shared. Stopping tells reachable clients, drains in-flight requests and
 * only then closes the listeners. Standalone mode never creates a session, so no listener exists.
 */
public final class HostSession implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(HostSession.class.getName());
	private final HostDiscovery discovery = HostDiscovery.create();
	private java.io.Closeable advertisement;
	private static final Duration ADMIN_LIFETIME = Duration.ofDays(30);
	private static final String[] PROTOCOLS = {"TLSv1.3", "TLSv1.2"};
	private final IncidentStore store;
	private final IncidentId incident;
	private final HostConfig config;
	private final Runnable flushEditors;
	private final HostStorage storage;
	private final AtomicReference<HostState> state = new AtomicReference<>(HostState.STOPPED);
	private volatile Instant periodClose;
	private HostContext context;
	private HttpsServer dataServer;
	private HttpsServer enrollmentServer;
	private ExecutorService executor;
	private HostClient admin;
	private DeviceCredential adminCredential;
	private boolean lockAcquired;
	private String lastFailure;

	/**
	 * Creates a session for an incident held in a file store.
	 *
	 * @param store incident store, which must expose edit locks and a file location
	 * @param incident incident to share
	 * @param config host configuration
	 * @param flushEditors callback that saves pending editor changes; it must throw if they cannot be saved
	 */
	public HostSession(IncidentStore store, IncidentId incident, HostConfig config, Runnable flushEditors) {
		this(store, incident, config, flushEditors, null);
	}

	/**
	 * Creates a session with explicit host-owned storage.
	 *
	 * @param store incident store
	 * @param incident incident to share
	 * @param config host configuration
	 * @param flushEditors callback that saves pending editor changes
	 * @param storage host-owned storage, or null to derive it from the store location
	 */
	HostSession(IncidentStore store, IncidentId incident, HostConfig config, Runnable flushEditors,
			HostStorage storage) {
		this.store = store;
		this.incident = incident;
		this.config = config;
		this.flushEditors = flushEditors == null ? () -> { } : flushEditors;
		this.storage = storage != null ? storage : deriveStorage(store, config);
	}

	/**
	 * Derives host storage from a file store's root, or returns null for other stores.
	 *
	 * @param store incident store
	 * @param config host configuration
	 * @return storage, or null when hosting is unsupported for the store
	 */
	private static HostStorage deriveStorage(IncidentStore store, HostConfig config) {
		StoreDescriptor descriptor = store.describe();
		if (!"File".equals(descriptor.type())) {
			return null;
		}
		try {
			return new HostStorage(Path.of(descriptor.location()), config.clock());
		} catch (HostStorageException failure) {
			LOGGER.log(Level.WARNING, "Host storage unavailable: " + failure.getMessage(), failure);
			return null;
		}
	}

	/**
	 * Returns the current lifecycle state.
	 *
	 * @return state
	 */
	public HostState state() {
		return state.get();
	}

	/**
	 * Returns host-owned storage, which backup must include.
	 *
	 * @return storage, or null when unavailable
	 */
	public HostStorage storage() {
		return storage;
	}

	/**
	 * Returns the shared incident.
	 *
	 * @return incident id
	 */
	public IncidentId incident() {
		return incident;
	}

	/**
	 * Sets when the operational period closes. Credentials issued afterwards never outlive it, and issuing is
	 * refused once it has passed.
	 *
	 * @param cutoff period close time, or null for none
	 */
	public void setPeriodCloseCutoff(Instant cutoff) {
		this.periodClose = cutoff;
		HostContext current = context;
		if (current != null) {
			current.setPeriodClose(cutoff);
		}
	}

	/**
	 * Starts sharing. On any failure nothing remains started, and the file lock is released if this call took it.
	 *
	 * @throws HostStartException when sharing cannot start; the incident stays usable
	 */
	public synchronized void start() {
		if (state.get() == HostState.RUNNING || state.get() == HostState.STARTING) {
			throw new HostStartException(HostStartException.Reason.OTHER, "Hosting is already running", null);
		}
		state.set(HostState.STARTING);
		lastFailure = null;
		try {
			doStart();
			state.set(HostState.RUNNING);
		} catch (HostStartException failure) {
			abortStart(failure);
			throw failure;
		} catch (RuntimeException failure) {
			HostStartException wrapped = new HostStartException(HostStartException.Reason.OTHER,
					"Hosting could not start: " + failure.getClass().getSimpleName(), failure);
			abortStart(wrapped);
			throw wrapped;
		}
	}

	/**
	 * Performs the ordered startup steps.
	 */
	private void doStart() {
		if (storage == null) {
			throw new HostStartException(HostStartException.Reason.STORAGE_FAILURE,
					"Hosting needs a file-based incident store with writable host storage", null);
		}
		if (!isWildcardOrLoopback(config.bindAddress())) {
			throw new HostStartException(HostStartException.Reason.INVALID_CONFIGURATION,
					"Bind to all interfaces or to the loopback address", null);
		}
		try {
			flushEditors.run();
		} catch (RuntimeException failure) {
			throw new HostStartException(HostStartException.Reason.EDITOR_FLUSH_FAILED,
					"Pending editor changes could not be saved, so sharing was not started", failure);
		}
		takeOwnership();
		IncidentService service = new IncidentService(store, new FileRecordVersionStore(incidentsDirectory()));
		try {
			service.read(incident);
		} catch (IncidentNotFoundException failure) {
			throw new HostStartException(HostStartException.Reason.INCIDENT_NOT_FOUND,
					"The incident to share was not found", failure);
		} catch (RuntimeException failure) {
			LOGGER.log(java.util.logging.Level.WARNING, "The incident to share could not be read", failure);
			throw new HostStartException(HostStartException.Reason.STORAGE_FAILURE,
					"The incident could not be read: " + failure.getClass().getSimpleName(), failure);
		}
		HostAuthority authority;
		try {
			authority = storage.keys().loadOrCreate();
			storage.registry().reload();
		} catch (HostStorageException failure) {
			throw new HostStartException(HostStartException.Reason.STORAGE_FAILURE,
					"Host-owned storage failed: " + failure.getMessage(), failure);
		}
		context = new HostContext(config, authority, storage.registry(), service, incident, state);
		context.setPeriodClose(periodClose);
		executor = Executors.newFixedThreadPool(8, runnable -> {
			Thread thread = new Thread(runnable, "host-request");
			thread.setDaemon(true);
			return thread;
		});
		bind(authority);
		bootstrapAdministrator(authority);
		LOGGER.log(Level.INFO, "Hosting incident {0} on port {1}", new Object[] {incident.value(), context.port()});
	}

	/**
	 * Establishes exclusive ownership of the incident's files.
	 */
	private void takeOwnership() {
		Optional<EditLockSupport> locks = store.editLocks();
		if (locks.isEmpty()) {
			throw new HostStartException(HostStartException.Reason.NOT_EXCLUSIVE,
					"This store cannot guarantee exclusive ownership of the incident", null);
		}
		try {
			if (locks.get().isHeldByCaller(incident)) {
				lockAcquired = false;
			} else if (locks.get().lock(incident)) {
				lockAcquired = true;
			} else {
				throw new HostStartException(HostStartException.Reason.NOT_EXCLUSIVE,
						"The incident is open in another window or process", null);
			}
		} catch (HostStartException failure) {
			throw failure;
		} catch (RuntimeException failure) {
			throw new HostStartException(HostStartException.Reason.NOT_EXCLUSIVE,
					"The incident could not be locked: " + failure.getClass().getSimpleName(), failure);
		}
	}

	/**
	 * Returns the directory holding incident documents.
	 *
	 * @return incidents directory
	 */
	private Path incidentsDirectory() {
		return Path.of(store.describe().location()).toAbsolutePath().resolve("incidents");
	}

	/**
	 * Tests whether an address is a wildcard or loopback bind address.
	 *
	 * @param address bind address text
	 * @return true when administration over loopback will work
	 */
	private static boolean isWildcardOrLoopback(String address) {
		try {
			InetAddress parsed = InetAddress.getByName(address);
			return parsed.isAnyLocalAddress() || parsed.isLoopbackAddress();
		} catch (IOException failure) {
			return false;
		}
	}

	/**
	 * Binds both listeners.
	 *
	 * @param authority host authority that signs the server identity
	 */
	private void bind(HostAuthority authority) {
		try {
			char[] password = HostCrypto.sha256(Long.toHexString(System.nanoTime()) + incident.value()).toCharArray();
			Instant now = config.clock().instant();
			KeyStore serverKeys = authority.newServerKeyStore(serverNames(), now, password);
			KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyFactory.init(serverKeys, password);
			TrustManagerFactory trustFactory = TrustManagerFactory.getInstance("PKIX");
			trustFactory.init(authority.newTrustStore());
			SSLContext tls = SSLContext.getInstance("TLS");
			tls.init(keyFactory.getKeyManagers(), trustFactory.getTrustManagers(), null);
			InetSocketAddress dataAddress = new InetSocketAddress(InetAddress.getByName(config.bindAddress()),
					config.port());
			dataServer = HttpsServer.create(dataAddress, 0);
			dataServer.setHttpsConfigurator(configurator(tls, true));
			dataServer.createContext("/", new HostApiHandler(context));
			dataServer.setExecutor(executor);
			InetSocketAddress enrollmentAddress = new InetSocketAddress(InetAddress.getByName(config.bindAddress()),
					config.enrollmentPort());
			enrollmentServer = HttpsServer.create(enrollmentAddress, 0);
			enrollmentServer.setHttpsConfigurator(configurator(tls, false));
			enrollmentServer.createContext("/", new EnrollmentHandler(context));
			enrollmentServer.setExecutor(executor);
			dataServer.start();
			enrollmentServer.start();
			context.bound(dataServer.getAddress().getPort(), enrollmentServer.getAddress().getPort());
		} catch (BindException failure) {
			throw new HostStartException(HostStartException.Reason.PORT_IN_USE,
					"A port is already in use; choose another port", failure);
		} catch (IOException | GeneralSecurityException | RuntimeException failure) {
			if (failure instanceof java.net.SocketException && failure.getMessage() != null
					&& failure.getMessage().toLowerCase(java.util.Locale.ROOT).contains("in use")) {
				throw new HostStartException(HostStartException.Reason.PORT_IN_USE,
						"A port is already in use; choose another port", failure);
			}
			throw new HostStartException(HostStartException.Reason.OTHER,
					"The listeners could not start: " + failure.getClass().getSimpleName(), failure);
		}
	}

	/**
	 * Creates the HTTPS configuration for a listener.
	 *
	 * @param tls TLS context
	 * @param requireClientCertificate whether a valid device certificate is required
	 * @return configurator
	 */
	private static HttpsConfigurator configurator(SSLContext tls, boolean requireClientCertificate) {
		return new HttpsConfigurator(tls) {
			/** {@inheritDoc} */
			@Override
			public void configure(HttpsParameters params) {
				javax.net.ssl.SSLParameters parameters = tls.getDefaultSSLParameters();
				parameters.setProtocols(PROTOCOLS);
				parameters.setNeedClientAuth(requireClientCertificate);
				params.setSSLParameters(parameters);
			}
		};
	}

	/**
	 * Lists the names and addresses the server certificate must cover.
	 *
	 * @return distinct names
	 */
	private List<String> serverNames() {
		Set<String> names = new LinkedHashSet<>();
		names.add(config.advertisedHost());
		names.add("localhost");
		names.add("127.0.0.1");
		names.add("::1");
		try {
			for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				for (InetAddress address : Collections.list(network.getInetAddresses())) {
					String text = address.getHostAddress();
					int zone = text.indexOf('%');
					names.add(zone >= 0 ? text.substring(0, zone) : text);
				}
			}
		} catch (IOException failure) {
			LOGGER.log(Level.FINE, "Cannot enumerate local addresses: {0}", failure.getMessage());
		}
		return new ArrayList<>(names);
	}

	/**
	 * Creates the host's in-memory administrator credential by trusted local bootstrap and registers it.
	 *
	 * @param authority host authority
	 */
	private void bootstrapAdministrator(HostAuthority authority) {
		try {
			KeyPair keys = HostCrypto.generateKeyPair();
			Instant now = config.clock().instant();
			Instant expires = now.plus(ADMIN_LIFETIME);
			String adminId = "host-admin-" + HostCrypto.sha256(Long.toHexString(System.nanoTime())).substring(0, 12);
			X509Certificate certificate = authority.issueDevice(keys.getPublic(), adminId, now, expires);
			char[] password = HostCrypto.sha256(Long.toHexString(System.nanoTime()) + adminId).toCharArray();
			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(null, null);
			keyStore.setKeyEntry(DeviceCredential.KEY_ALIAS, keys.getPrivate(), password,
					new X509Certificate[] {certificate, authority.certificate()});
			keyStore.setCertificateEntry(DeviceCredential.CA_ALIAS, authority.certificate());
			storage.registry().replaceAdministrator(incident, HostCrypto.fingerprint(certificate), expires);
			DeviceCredential credential = new DeviceCredential(keyStore, password, authority.fingerprint(), adminId,
					expires);
			String loopback = "::".equals(config.bindAddress()) || "::1".equals(config.bindAddress()) ? "::1"
					: "127.0.0.1";
			admin = HostClient.forDevice(loopback, context.port(), context.enrollmentPort(), credential);
			adminCredential = credential;
		} catch (GeneralSecurityException | IOException | HostStorageException failure) {
			throw new HostStartException(HostStartException.Reason.STORAGE_FAILURE,
					"The host administrator credential could not be created", failure);
		}
	}

	/**
	 * Releases everything a failed start acquired.
	 *
	 * @param failure the start failure
	 */
	private void abortStart(HostStartException failure) {
		lastFailure = failure.getMessage();
		teardown(false);
		state.set(HostState.STOPPED);
	}

	/**
	 * Returns the current status, including the failure text when the service has failed.
	 *
	 * @return status snapshot
	 */
	public HostStatus status() {
		HostContext current = context;
		HostState now = state.get();
		if (current == null || now == HostState.STOPPED) {
			return new HostStatus(now, 0, 0, null, 0, lastFailure);
		}
		String failure = now == HostState.FAILED ? current.failure.get() : lastFailure;
		int connected = now == HostState.RUNNING || now == HostState.STOPPING
				? current.registry.connectedCount(config.connectedWindow())
				: 0;
		return new HostStatus(now, current.port(), current.enrollmentPort(), current.authority.fingerprint(),
				connected, failure);
	}

	/**
	 * Returns the number of devices with recent contact, for the stop warning.
	 *
	 * @return connected device count
	 */
	public int connectedDeviceCount() {
		return status().connectedDevices();
	}

	/**
	 * Issues a single-use invitation through the administrator path over loopback.
	 *
	 * @return copyable invitation text; it is a secret and must not be logged
	 * @throws HostClientException when the host rejects the request
	 */
	public String issueInvitation() {
		return requireAdmin().issueInvitation();
	}

	/**
	 * Opens the short-code pairing window through the administrator path over loopback.
	 *
	 * @return the code, its expiry and where devices connect; the code is a secret and must not be logged
	 * @throws HostClientException when the host rejects the request
	 */
	public HostClient.PairingOffer openPairing() {
		HostClient.PairingOffer offer = requireAdmin().openPairing();
		stopAdvertising();
		advertisement = discovery.advertise(offer.host(), offer.enrollmentPort());
		return offer;
	}

	/** Stops any local-network advertisement of the pairing window. */
	private synchronized void stopAdvertising() {
		if (advertisement != null) {
			try {
				advertisement.close();
			} catch (java.io.IOException failure) {
				LOGGER.log(Level.FINE, "Advertisement close failed", failure);
			}
			advertisement = null;
		}
	}

	/**
	 * Closes the in-person pairing window.
	 *
	 * @throws HostClientException when the host rejects the request
	 */
	public void closePairing() {
		stopAdvertising();
		requireAdmin().closePairing();
	}

	/**
	 * Reads the pairing window state, including any device waiting for its number to be entered.
	 *
	 * @return window state
	 * @throws HostClientException when the host rejects the request
	 */
	public HostClient.PairingState pairingState() {
		return requireAdmin().pairingState();
	}

	/**
	 * Submits the number the operator typed for the waiting device.
	 *
	 * @param code the typed number
	 * @return {@code confirmed}, {@code mismatch}, {@code burned} or {@code none}
	 * @throws HostClientException when the host rejects the request
	 */
	public String enterPairingCode(String code) {
		return requireAdmin().enterPairingCode(code);
	}

	/**
	 * Lists admitted devices through the administrator path.
	 *
	 * @return devices
	 * @throws HostClientException when the host rejects the request
	 */
	public List<DeviceInfo> devices() {
		return requireAdmin().listDevices();
	}

	/**
	 * Lists pending re-authorization requests, including their queued work for review.
	 *
	 * @return request objects
	 */
	public List<com.fasterxml.jackson.databind.JsonNode> reauthorizationRequests() {
		return requireAdmin().listReauthorizationRequests();
	}

	/**
	 * Approves a pending device re-authorization.
	 *
	 * @param requestId request to approve
	 */
	public void approveReauthorization(String requestId) {
		requireAdmin().approveReauthorization(requestId);
	}

	/**
	 * Denies a pending device re-authorization without deleting the device's local outbox.
	 *
	 * @param requestId request to deny
	 */
	public void denyReauthorization(String requestId) {
		requireAdmin().denyReauthorization(requestId);
	}

	/**
	 * Revokes a device through the administrator path.
	 *
	 * @param deviceId device to revoke
	 * @throws HostClientException when the host rejects the request
	 */
	public void revoke(String deviceId) {
		requireAdmin().revoke(deviceId);
	}

	/**
	 * Creates the loopback store the host window edits through. The host UI has no direct-write bypass: it uses
	 * the same service as connected devices, over loopback, with its local administrator credential.
	 *
	 * @return a production remote store for the shared incident
	 * @throws IllegalStateException when hosting is not running
	 */
	public RemoteIncidentStore loopbackStore() {
		requireAdmin();
		return new RemoteIncidentStore(new HostGateway(admin, adminCredential, "This workstation (host)"), incident);
	}

	/**
	 * Opens the host's durable loopback journal under the pinned authority. This performs disk I/O and must
	 * run off the event thread.
	 *
	 * @param cacheRoot private disconnected cache root
	 * @return durable remote store for the shared incident
	 * @throws IllegalStateException when hosting is not running
	 */
	public RemoteIncidentStore loopbackStore(Path cacheRoot) {
		return loopbackStore(java.util.List.of(cacheRoot));
	}

	/**
	 * Opens the host's durable loopback journal in the first candidate location that can be protected. This
	 * performs disk I/O and must run off the event thread.
	 *
	 * @param cacheRoots candidate private cache roots in order of preference
	 * @return durable remote store for the shared incident
	 * @throws IllegalStateException when hosting is not running
	 */
	public RemoteIncidentStore loopbackStore(java.util.List<Path> cacheRoots) {
		requireAdmin();
		return RemoteIncidentStore.connect(new HostGateway(admin, adminCredential, "This workstation (host)"),
				incident, cacheRoots);
	}

	/**
	 * Returns the administrator client.
	 *
	 * @return client
	 * @throws IllegalStateException when hosting is not running
	 */
	private HostClient requireAdmin() {
		HostClient current = admin;
		if (current == null || state.get() != HostState.RUNNING) {
			throw new IllegalStateException("Hosting is not running");
		}
		return current;
	}

	/**
	 * Rejects new requests and waits for admitted requests to finish before the local incident is reloaded.
	 * If requests do not drain in time, sharing resumes and the caller must not switch stores.
	 *
	 * @throws IllegalStateException when the host is not running or admitted requests do not drain
	 */
	public synchronized void prepareStop() {
		if (state.get() != HostState.RUNNING) {
			throw new IllegalStateException("Hosting is not running");
		}
		state.set(HostState.STOPPING);
		pause(config.stopGrace());
		HostContext running = context;
		long deadline = System.nanoTime() + config.drainTimeout().toNanos();
		while (running != null && running.inFlight.get() > 0 && System.nanoTime() < deadline) {
			pause(Duration.ofMillis(20));
		}
		if (running != null && running.inFlight.get() > 0) {
			state.set(HostState.RUNNING);
			throw new IllegalStateException("Host requests did not finish; sharing remains active");
		}
	}

	/**
	 * Resumes a prepared stop when the local store switch fails.
	 */
	public synchronized void cancelPreparedStop() {
		if (state.get() == HostState.STOPPING) {
			state.set(HostState.RUNNING);
		}
	}

	/**
	 * Stops sharing: reports the stopping state to reachable clients, drains in-flight requests, closes the
	 * listeners, removes the administrator credential and releases the file lock. Safe to call repeatedly.
	 */
	public synchronized void stop() {
		stopAdvertising();
		HostState current = state.get();
		if (current == HostState.STOPPED || current == HostState.STARTING) {
			return;
		}
		if (current != HostState.STOPPING) {
			state.set(HostState.STOPPING);
			pause(config.stopGrace());
			HostContext running = context;
			if (running != null) {
				long deadline = System.nanoTime() + config.drainTimeout().toNanos();
				while (running.inFlight.get() > 0 && System.nanoTime() < deadline) {
					pause(Duration.ofMillis(20));
				}
			}
		}
		teardown(true);
		state.set(HostState.STOPPED);
	}

	/**
	 * Closes listeners, removes administration, and releases the lock if this session took it.
	 *
	 * @param removeAdmin whether to remove the administrator record from the registry
	 */
	private void teardown(boolean removeAdmin) {
		if (dataServer != null) {
			dataServer.stop(0);
			dataServer = null;
		}
		if (enrollmentServer != null) {
			enrollmentServer.stop(0);
			enrollmentServer = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		admin = null;
		adminCredential = null;
		if (storage != null && (removeAdmin || context != null)) {
			try {
				storage.registry().removeAdministrator();
			} catch (RuntimeException failure) {
				LOGGER.log(Level.WARNING, "Cannot remove the administrator credential: {0}", failure.getMessage());
			}
		}
		if (lockAcquired) {
			store.editLocks().ifPresent(locks -> locks.unlock(incident));
			lockAcquired = false;
		}
		context = null;
	}

	/**
	 * Sleeps without losing an interrupt.
	 *
	 * @param duration time to wait
	 */
	private static void pause(Duration duration) {
		if (duration.isZero() || duration.isNegative()) {
			return;
		}
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		stop();
	}
}
