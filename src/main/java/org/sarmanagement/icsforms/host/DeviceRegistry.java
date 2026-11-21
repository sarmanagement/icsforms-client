/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Durable registry of pending invitations and admitted devices, including revocations. It lives in host-owned
 * storage and is backed up together with the incident data. Invitation secrets are stored only as SHA-256
 * hashes. A corrupt registry fails closed rather than being reset, because a reset could re-admit revoked devices.
 */
public final class DeviceRegistry {
	/** Registry file format version. */
	static final int FORMAT_VERSION = 1;
	private static final String ROLE_CEILING_PILOT = "PILOT";
	private static final Duration PURGE_AFTER = Duration.ofDays(1);
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private final Path file;
	private final Clock clock;
	private final Object lock = new Object();
	private final Map<String, Instant> contacts = new ConcurrentHashMap<>();
	private RegistryState state;

	/**
	 * One admitted device credential.
	 *
	 * @param deviceId stable device identifier
	 * @param name operator-visible device name
	 * @param incidentId incident the credential is scoped to
	 * @param certificateFingerprint SHA-256 fingerprint of the device certificate
	 * @param issuedAt issue time
	 * @param expiresAt credential expiry
	 * @param revokedAt revocation time, or null while not revoked
	 * @param admin true for the host's own local administrator credential
	 * @param permissions granted permissions
	 * @param roleCeiling role ceiling reserved for later stages
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DeviceRecord(String deviceId, String name, String incidentId, String certificateFingerprint,
			Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean admin, Set<HostPermission> permissions,
			String roleCeiling) {
		/**
		 * Detaches the permission set.
		 */
		public DeviceRecord {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}

		/**
		 * Reports whether the credential was revoked.
		 *
		 * @return true after revocation
		 */
		public boolean revoked() {
			return revokedAt != null;
		}

		/**
		 * Returns the state at a point in time; revocation takes precedence over expiry.
		 *
		 * @param now evaluation time
		 * @return credential state
		 */
		public CredentialState stateAt(Instant now) {
			if (revoked()) {
				return CredentialState.REVOKED;
			}
			return now.isBefore(expiresAt) ? CredentialState.ACTIVE : CredentialState.EXPIRED;
		}
	}

	/**
	 * One pending invitation.
	 *
	 * @param invitationId public invitation identifier
	 * @param secretHash SHA-256 of the invitation secret
	 * @param incidentId incident the invitation is scoped to
	 * @param issuedAt issue time
	 * @param expiresAt invitation expiry
	 * @param maxUses allowed redemptions, one in the pilot
	 * @param uses redemptions so far
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record InvitationRecord(String invitationId, String secretHash, String incidentId, Instant issuedAt,
			Instant expiresAt, int maxUses, int uses) {
	}

	/**
	 * Complete persisted registry state.
	 *
	 * @param formatVersion file format version
	 * @param invitations pending and recently used invitations
	 * @param devices admitted devices
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RegistryState(int formatVersion, List<InvitationRecord> invitations, List<DeviceRecord> devices) {
		/**
		 * Detaches the lists.
		 */
		RegistryState {
			invitations = invitations == null ? List.of() : List.copyOf(invitations);
			devices = devices == null ? List.of() : List.copyOf(devices);
		}
	}

	/**
	 * A newly issued invitation. The secret is available only here and is never persisted or logged.
	 *
	 * @param invitationId public invitation identifier
	 * @param secret single-use secret
	 * @param incident scoped incident
	 * @param expiresAt invitation expiry
	 */
	public record IssuedInvitation(String invitationId, String secret, IncidentId incident, Instant expiresAt) {
		/**
		 * Redacts the secret from diagnostic output.
		 *
		 * @return text that omits the secret
		 */
		@Override
		public String toString() {
			return "IssuedInvitation[" + invitationId + "]";
		}
	}

	/**
	 * Result of a successful redemption.
	 *
	 * @param device registry record of the new device
	 * @param certificate signed device certificate
	 */
	public record Admission(DeviceRecord device, X509Certificate certificate) {
	}

	/**
	 * Signs the certificate for a newly admitted device while the registry lock is held.
	 */
	@FunctionalInterface
	public interface CertificateSigner {
		/**
		 * Signs a device certificate.
		 *
		 * @param deviceId identifier assigned to the device
		 * @param now issue time
		 * @param notAfter credential expiry
		 * @return signed certificate
		 */
		X509Certificate sign(String deviceId, Instant now, Instant notAfter);
	}

	/**
	 * Opens the registry, loading the persisted state when present.
	 *
	 * @param file registry file in host-owned storage
	 * @param clock time source
	 * @throws HostStorageException when an existing file cannot be read
	 */
	public DeviceRegistry(Path file, Clock clock) {
		this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
		this.clock = Objects.requireNonNull(clock, "clock");
		this.state = read();
	}

	/**
	 * Reloads state from disk, for example after a restore. Transient last-contact data is discarded.
	 *
	 * @throws HostStorageException when the file cannot be read
	 */
	public void reload() {
		synchronized (lock) {
			state = read();
			contacts.clear();
		}
	}

	/**
	 * Reads the registry file, or returns an empty state when none exists.
	 *
	 * @return persisted state
	 */
	private RegistryState read() {
		if (!Files.exists(file)) {
			return new RegistryState(FORMAT_VERSION, List.of(), List.of());
		}
		try {
			RegistryState loaded = MAPPER.readValue(file.toFile(), RegistryState.class);
			if (loaded.formatVersion() > FORMAT_VERSION) {
				throw new HostStorageException("Device registry was written by a newer version");
			}
			return loaded;
		} catch (IOException exception) {
			throw new HostStorageException("Cannot read the device registry", exception);
		}
	}

	/**
	 * Replaces the registry with content from a backup, validating it first. Pending invitations in the backup are
	 * dropped so a restore never revives an unredeemed invitation secret; devices and revocations are kept.
	 *
	 * @param content registry file content produced by {@link #snapshot()}
	 * @throws HostStorageException when the content is invalid or cannot be persisted
	 */
	public void restore(byte[] content) {
		RegistryState loaded = parseBackup(content);
		synchronized (lock) {
			commit(new RegistryState(FORMAT_VERSION, List.of(), loaded.devices()));
			contacts.clear();
		}
	}

	/**
	 * Validates registry backup content without changing anything.
	 *
	 * @param content registry file content produced by {@link #snapshot()}
	 * @throws HostStorageException when the content is invalid
	 */
	static void validateBackup(byte[] content) {
		parseBackup(content);
	}

	/**
	 * Parses and checks registry backup content.
	 *
	 * @param content registry file content
	 * @return parsed state
	 * @throws HostStorageException when the content is invalid or unsupported
	 */
	private static RegistryState parseBackup(byte[] content) {
		RegistryState loaded;
		try {
			loaded = MAPPER.readValue(content, RegistryState.class);
		} catch (IOException exception) {
			throw new HostStorageException("The backup device registry is not valid", exception);
		}
		if (loaded.formatVersion() > FORMAT_VERSION || loaded.devices() == null) {
			throw new HostStorageException("The backup device registry is not supported");
		}
		return loaded;
	}

	/**
	 * Runs an operation while holding the registry lock, so a backup sees one consistent registry version.
	 *
	 * @param operation operation to run
	 * @param <T> result type
	 * @return the operation's result
	 */
	public <T> T withLock(Supplier<T> operation) {
		synchronized (lock) {
			return operation.get();
		}
	}

	/**
	 * Serializes the registry for backup. The host's local administrator credential is excluded because it is
	 * recreated each time hosting starts.
	 *
	 * @return registry file content
	 */
	public byte[] snapshot() {
		synchronized (lock) {
			List<DeviceRecord> devices = state.devices().stream().filter(device -> !device.admin()).toList();
			try {
				return MAPPER.writerWithDefaultPrettyPrinter()
						.writeValueAsBytes(new RegistryState(FORMAT_VERSION, state.invitations(), devices));
			} catch (IOException exception) {
				throw new HostStorageException("Cannot serialize the device registry", exception);
			}
		}
	}

	/**
	 * Issues a short-lived, single-use invitation scoped to one incident.
	 *
	 * @param incident incident to share
	 * @param lifetime invitation lifetime
	 * @return the invitation, including its one-time secret
	 * @throws HostStorageException when the registry cannot be persisted
	 */
	public IssuedInvitation issueInvitation(IncidentId incident, Duration lifetime) {
		synchronized (lock) {
			Instant now = clock.instant();
			String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(HostCrypto.randomBytes(32));
			InvitationRecord record = new InvitationRecord(UUID.randomUUID().toString(), HostCrypto.sha256(secret),
					incident.value(), now, now.plus(lifetime), 1, 0);
			List<InvitationRecord> invitations = new ArrayList<>(state.invitations());
			invitations.add(record);
			commit(new RegistryState(FORMAT_VERSION, invitations, state.devices()));
			return new IssuedInvitation(record.invitationId(), secret, incident, record.expiresAt());
		}
	}

	/**
	 * Redeems an invitation for a device credential. Validation, use-count increment, device registration and
	 * persistence are one atomic step, so concurrent redemptions of a single-use invitation succeed only once.
	 *
	 * @param secret invitation secret
	 * @param requested incident the caller asked for
	 * @param shared incident currently shared by the host
	 * @param deviceName operator-visible name
	 * @param lifetime credential lifetime
	 * @param cutoff period close time that caps the credential, or null
	 * @param signer certificate signer called while the lock is held
	 * @return admitted device and certificate
	 * @throws InvitationRejectedException when the invitation is unknown, expired, used or for another incident
	 * @throws HostStorageException when the admission cannot be persisted; nothing is admitted in that case
	 */
	public Admission redeem(String secret, IncidentId requested, IncidentId shared, String deviceName,
			Duration lifetime, Instant cutoff, CertificateSigner signer) {
		synchronized (lock) {
			Instant now = clock.instant();
			String hash = HostCrypto.sha256(secret == null ? "" : secret);
			InvitationRecord invitation = state.invitations().stream()
					.filter(candidate -> candidate.secretHash().equals(hash)).findFirst()
					.orElseThrow(() -> new InvitationRejectedException("unknown invitation"));
			if (invitation.uses() >= invitation.maxUses()) {
				throw new InvitationRejectedException("invitation already used");
			}
			if (!now.isBefore(invitation.expiresAt())) {
				throw new InvitationRejectedException("invitation expired");
			}
			if (!invitation.incidentId().equals(requested.value()) || !invitation.incidentId().equals(shared.value())) {
				throw new InvitationRejectedException("invitation is for a different incident");
			}
			Instant notAfter = now.plus(lifetime);
			if (cutoff != null && cutoff.isBefore(notAfter)) {
				notAfter = cutoff;
			}
			if (!now.isBefore(notAfter)) {
				throw new InvitationRejectedException("operational period is closed");
			}
			String deviceId = UUID.randomUUID().toString();
			X509Certificate certificate = signer.sign(deviceId, now, notAfter);
			DeviceRecord device = new DeviceRecord(deviceId, deviceName, shared.value(),
					HostCrypto.fingerprint(certificate), now, notAfter, null, false,
					EnumSet.allOf(HostPermission.class), ROLE_CEILING_PILOT);
			List<InvitationRecord> invitations = new ArrayList<>(state.invitations());
			invitations.replaceAll(candidate -> candidate == invitation ? new InvitationRecord(invitation.invitationId(),
					invitation.secretHash(), invitation.incidentId(), invitation.issuedAt(), invitation.expiresAt(),
					invitation.maxUses(), invitation.uses() + 1) : candidate);
			List<DeviceRecord> devices = new ArrayList<>(state.devices());
			devices.add(device);
			commit(new RegistryState(FORMAT_VERSION, invitations, devices));
			return new Admission(device, certificate);
		}
	}

	/**
	 * Replaces any previous local administrator credential with a new one, obtained by trusted local bootstrap.
	 *
	 * @param incident incident the administrator manages
	 * @param fingerprint fingerprint of the in-memory administrator certificate
	 * @param expiresAt credential expiry
	 * @return administrator record
	 * @throws HostStorageException when the registry cannot be persisted
	 */
	public DeviceRecord replaceAdministrator(IncidentId incident, String fingerprint, Instant expiresAt) {
		synchronized (lock) {
			Instant now = clock.instant();
			DeviceRecord admin = new DeviceRecord(UUID.randomUUID().toString(), "Host administrator", incident.value(),
					fingerprint, now, expiresAt, null, true, EnumSet.allOf(HostPermission.class), ROLE_CEILING_PILOT);
			List<DeviceRecord> devices = new ArrayList<>(state.devices().stream().filter(device -> !device.admin()).toList());
			devices.add(admin);
			commit(new RegistryState(FORMAT_VERSION, state.invitations(), devices));
			return admin;
		}
	}

	/**
	 * Removes the local administrator credential, for example when hosting stops.
	 *
	 * @throws HostStorageException when the registry cannot be persisted
	 */
	public void removeAdministrator() {
		synchronized (lock) {
			if (state.devices().stream().anyMatch(DeviceRecord::admin)) {
				commit(new RegistryState(FORMAT_VERSION, state.invitations(),
						state.devices().stream().filter(device -> !device.admin()).toList()));
			}
		}
	}

	/**
	 * Revokes a device. The revocation is effective in memory first and then persisted, so a storage failure
	 * never leaves a device that the operator revoked still admitted.
	 *
	 * @param deviceId device to revoke
	 * @return true when a non-administrator device was found and revoked
	 * @throws HostStorageException when the revocation cannot be persisted
	 */
	public boolean revoke(String deviceId) {
		synchronized (lock) {
			Optional<DeviceRecord> target = state.devices().stream()
					.filter(device -> device.deviceId().equals(deviceId) && !device.admin()).findFirst();
			if (target.isEmpty()) {
				return false;
			}
			if (target.get().revoked()) {
				return true;
			}
			DeviceRecord current = target.get();
			DeviceRecord revoked = new DeviceRecord(current.deviceId(), current.name(), current.incidentId(),
					current.certificateFingerprint(), current.issuedAt(), current.expiresAt(), clock.instant(), false,
					current.permissions(), current.roleCeiling());
			List<DeviceRecord> devices = new ArrayList<>(state.devices());
			devices.replaceAll(device -> device == current ? revoked : device);
			RegistryState next = new RegistryState(FORMAT_VERSION, state.invitations(), devices);
			state = next;
			write(next);
			return true;
		}
	}

	/**
	 * Lists admitted devices, excluding the local administrator.
	 *
	 * @return device records
	 */
	public List<DeviceRecord> devices() {
		synchronized (lock) {
			return state.devices().stream().filter(device -> !device.admin()).toList();
		}
	}

	/**
	 * Finds a device by certificate fingerprint.
	 *
	 * @param fingerprint SHA-256 certificate fingerprint
	 * @return matching record, if any
	 */
	public Optional<DeviceRecord> byFingerprint(String fingerprint) {
		synchronized (lock) {
			return state.devices().stream().filter(device -> device.certificateFingerprint().equals(fingerprint))
					.findFirst();
		}
	}

	/**
	 * Reports the state of a credential for the pre-authentication status signal.
	 *
	 * @param fingerprint SHA-256 certificate fingerprint
	 * @return credential state
	 */
	public CredentialState stateOf(String fingerprint) {
		Instant now = clock.instant();
		return byFingerprint(fingerprint).map(device -> device.stateAt(now)).orElse(CredentialState.UNKNOWN);
	}

	/**
	 * Notes that a device just made an authenticated request. This data is transient and not persisted.
	 *
	 * @param deviceId device identifier
	 */
	public void recordContact(String deviceId) {
		contacts.put(deviceId, clock.instant());
	}

	/**
	 * Returns the last authenticated contact since the host started.
	 *
	 * @param deviceId device identifier
	 * @return last contact time, if any
	 */
	public Optional<Instant> lastContact(String deviceId) {
		return Optional.ofNullable(contacts.get(deviceId));
	}

	/**
	 * Counts active, non-administrator devices that made contact within a window.
	 *
	 * @param window recency window
	 * @return connected device count
	 */
	public int connectedCount(Duration window) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(window);
		return (int) devices().stream().filter(device -> device.stateAt(now) == CredentialState.ACTIVE)
				.filter(device -> lastContact(device.deviceId()).map(contact -> contact.isAfter(cutoff)).orElse(false))
				.count();
	}

	/**
	 * Persists a new state and, only on success, makes it current.
	 *
	 * @param next state to persist
	 */
	private void commit(RegistryState next) {
		Instant cutoff = clock.instant().minus(PURGE_AFTER);
		List<InvitationRecord> retained = next.invitations().stream()
				.filter(invitation -> invitation.expiresAt().isAfter(cutoff)).toList();
		RegistryState pruned = new RegistryState(next.formatVersion(), retained, next.devices());
		write(pruned);
		state = pruned;
	}

	/**
	 * Writes the registry file atomically with owner-only permissions.
	 *
	 * @param value state to write
	 */
	private void write(RegistryState value) {
		try {
			OwnerOnlyFiles.write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
		} catch (IOException exception) {
			throw new HostStorageException("Cannot persist the device registry", exception);
		}
	}
}
