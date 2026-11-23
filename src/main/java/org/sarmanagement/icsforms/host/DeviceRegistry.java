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
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.security.PublicKey;

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
	private static final int MAX_PENDING_REQUESTS = 64;
	private static final int MAX_AUDIT_RECORDS = 500;
	private static final int MAX_OUTBOX_CHARS = 900_000;
	/** Most queued operation identifiers retained for attribution after approval. */
	private static final int MAX_RECOVERED_OPERATIONS = 20_000;
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
	 * @param originalDeviceId revoked identity whose approved replacement this device is
	 * @param recoveredOperationIds operation identifiers the administrator saw queued on the revoked identity;
	 *        only submissions with these identifiers are attributed to that identity
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DeviceRecord(String deviceId, String name, String incidentId, String certificateFingerprint,
			Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean admin, Set<HostPermission> permissions,
			String roleCeiling, String originalDeviceId, Set<String> recoveredOperationIds) {
		/**
		 * Creates a device record without recovered operation identifiers.
		 *
		 * @param deviceId stable device identifier
		 * @param name operator-visible name
		 * @param incidentId incident the credential is scoped to
		 * @param certificateFingerprint SHA-256 certificate fingerprint
		 * @param issuedAt issue time
		 * @param expiresAt credential expiry
		 * @param revokedAt revocation time, or null when active
		 * @param admin true for the host administrator
		 * @param permissions granted permissions
		 * @param roleCeiling role ceiling
		 * @param originalDeviceId revoked identity this device replaces, or null
		 */
		public DeviceRecord(String deviceId, String name, String incidentId, String certificateFingerprint,
				Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean admin, Set<HostPermission> permissions,
				String roleCeiling, String originalDeviceId) {
			this(deviceId, name, incidentId, certificateFingerprint, issuedAt, expiresAt, revokedAt, admin,
					permissions, roleCeiling, originalDeviceId, Set.of());
		}

		/**
		 * Creates a device record without an earlier device identity.
		 *
		 * @param deviceId stable device identifier
		 * @param name operator-visible name
		 * @param incidentId incident the credential is scoped to
		 * @param certificateFingerprint SHA-256 certificate fingerprint
		 * @param issuedAt issue time
		 * @param expiresAt credential expiry
		 * @param revokedAt revocation time, or null when active
		 * @param admin true for the host administrator
		 * @param permissions granted permissions
		 * @param roleCeiling role ceiling
		 */
		public DeviceRecord(String deviceId, String name, String incidentId, String certificateFingerprint,
				Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean admin, Set<HostPermission> permissions,
				String roleCeiling) {
			this(deviceId, name, incidentId, certificateFingerprint, issuedAt, expiresAt, revokedAt, admin, permissions,
					roleCeiling, null);
		}

		/**
		 * Detaches the permission set.
		 */
		public DeviceRecord {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
			recoveredOperationIds = recoveredOperationIds == null ? Set.of() : Set.copyOf(recoveredOperationIds);
		}

		/**
		 * Reports whether a submission is recovered work queued on the revoked identity.
		 *
		 * @param operationId client operation identifier
		 * @return true when this device replaced another and the administrator saw the operation queued
		 */
		public boolean recoversOperation(String operationId) {
			return originalDeviceId != null && recoveredOperationIds.contains(operationId);
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

	/** Re-authorization request type. */
	public enum ReauthorizationKind {
		/** A same-key renewal for an expired, non-revoked device. */
		RENEWAL,
		/** An invitation-backed new identity replacing a revoked device. */
		REPAIR
	}

	/** Approval decision state. */
	public enum ApprovalState {
		/** Waiting for a host administrator. */
		PENDING,
		/** Approved by a host administrator. */
		APPROVED,
		/** Denied by a host administrator. */
		DENIED
	}

	/**
	 * Re-authorization request retained for operator review and audit.
	 *
	 * @param requestId request identifier
	 * @param tokenHash SHA-256 of the one-time polling token
	 * @param kind renewal or repair
	 * @param originalDeviceId identity which queued the work
	 * @param deviceName proposed operator-visible device name
	 * @param publicKey encoded key proven by the signed certificate request
	 * @param outboxJson pending work for administrator review
	 * @param createdAt request time
	 * @param state decision state
	 * @param deviceId admitted identity, once approved
	 * @param certificate issued certificate, available only to the token holder
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ReauthorizationRequest(String requestId, String tokenHash, ReauthorizationKind kind,
			String originalDeviceId, String deviceName, String publicKey, String outboxJson, Instant createdAt,
			ApprovalState state, String deviceId, String certificate) {
		/**
		 * Redacts queued incident contents, key material and tokens from diagnostics.
		 *
		 * @return text containing only the public request identifier and state
		 */
		@Override
		public String toString() {
			return "ReauthorizationRequest[" + requestId + ", " + state + "]";
		}
	}

	/**
	 * Audit record for an administrator decision.
	 *
	 * @param requestId request decided
	 * @param decision approval or denial
	 * @param decidedAt decision time
	 * @param originalDeviceId identity that queued the work
	 * @param deviceId identity admitted by approval, or null after denial
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ApprovalAudit(String requestId, ApprovalState decision, Instant decidedAt, String originalDeviceId,
			String deviceId) {
	}

	/**
	 * New request and the only copy of its polling token.
	 *
	 * @param request request safe to display to the administrator
	 * @param token one-time random bearer token for polling
	 */
	public record SubmittedRequest(ReauthorizationRequest request, String token) {
		/**
		 * Redacts the polling token from diagnostic output.
		 *
		 * @return text that omits the token
		 */
		@Override
		public String toString() {
			return "SubmittedRequest[" + request.requestId() + "]";
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
	 * @param requests pending re-authorization requests
	 * @param audit approval decisions
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RegistryState(int formatVersion, List<InvitationRecord> invitations, List<DeviceRecord> devices,
			List<ReauthorizationRequest> requests, List<ApprovalAudit> audit) {
		/**
		 * Detaches the lists.
		 */
		RegistryState {
			invitations = invitations == null ? List.of() : List.copyOf(invitations);
			devices = devices == null ? List.of() : List.copyOf(devices);
			requests = requests == null ? List.of() : List.copyOf(requests);
			audit = audit == null ? List.of() : List.copyOf(audit);
		}

		/**
		 * Creates state in the original registry format.
		 *
		 * @param formatVersion file format version
		 * @param invitations invitations
		 * @param devices admitted devices
		 */
		RegistryState(int formatVersion, List<InvitationRecord> invitations, List<DeviceRecord> devices) {
			this(formatVersion, invitations, devices, List.of(), List.of());
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
	 * Signs a certificate for a re-authorization request's proven key.
	 */
	@FunctionalInterface
	public interface ReauthorizationSigner {
		/**
		 * Signs a device certificate.
		 *
		 * @param publicKey device key proven by the request
		 * @param deviceId identity assigned to the credential
		 * @param now issue time
		 * @param notAfter credential expiry
		 * @return signed certificate
		 */
		X509Certificate sign(PublicKey publicKey, String deviceId, Instant now, Instant notAfter);
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
			commit(new RegistryState(FORMAT_VERSION, List.of(), loaded.devices(), loaded.requests(), loaded.audit()));
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
				return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(new RegistryState(FORMAT_VERSION,
						state.invitations(), devices, state.requests(), state.audit()));
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
			commit(new RegistryState(FORMAT_VERSION, invitations, state.devices(), state.requests(), state.audit()));
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
			commit(new RegistryState(FORMAT_VERSION, invitations, devices, state.requests(), state.audit()));
			return new Admission(device, certificate);
		}
	}

	/**
	 * Queues same-key renewal of an expired device for administrator approval.
	 *
	 * @param deviceId existing identity
	 * @param fingerprint existing certificate fingerprint
	 * @param certificate existing certificate proving the key identity
	 * @param requestedKey public key from a signed certificate request
	 * @param deviceName operator-visible name
	 * @param outboxJson serialized pending outbox work
	 * @param cutoff optional issuance cutoff
	 * @return request and one-time polling token
	 * @throws InvitationRejectedException when the device is unknown, revoked, not expired, or proof differs
	 * @throws HostStorageException when the request cannot be persisted
	 */
	public SubmittedRequest requestRenewal(String deviceId, String fingerprint, X509Certificate certificate,
			PublicKey requestedKey, String deviceName, String outboxJson, Instant cutoff) {
		synchronized (lock) {
			DeviceRecord device = state.devices().stream()
					.filter(candidate -> candidate.deviceId().equals(deviceId)
							&& candidate.certificateFingerprint().equals(fingerprint) && !candidate.admin())
					.findFirst().orElseThrow(() -> new InvitationRejectedException("unknown device"));
			if (device.revoked() || (device.stateAt(clock.instant()) != CredentialState.EXPIRED
					&& (cutoff == null || clock.instant().isBefore(cutoff)))
					|| !fingerprint.equals(HostCrypto.fingerprint(certificate))
					|| !Arrays.equals(certificate.getPublicKey().getEncoded(), requestedKey.getEncoded())) {
				throw new InvitationRejectedException("renewal proof is not valid");
			}
			return queueRequest(ReauthorizationKind.RENEWAL, deviceId, deviceName, requestedKey, outboxJson,
					state.invitations());
		}
	}

	/**
	 * Queues an invitation-backed replacement for a revoked device identity.
	 *
	 * @param invitationSecret one-time invitation secret
	 * @param requested incident scoped by the invitation
	 * @param shared incident currently hosted
	 * @param originalDeviceId revoked identity whose outbox is being recovered
	 * @param deviceName operator-visible name
	 * @param requestedKey public key from a signed certificate request
	 * @param outboxJson serialized pending outbox work
	 * @return request and one-time polling token
	 * @throws InvitationRejectedException when the invitation or original identity is not valid
	 * @throws HostStorageException when the request cannot be persisted
	 */
	public SubmittedRequest requestRepair(String invitationSecret, IncidentId requested, IncidentId shared,
			String originalDeviceId, String deviceName, PublicKey requestedKey, String outboxJson) {
		synchronized (lock) {
			DeviceRecord original = state.devices().stream()
					.filter(candidate -> candidate.deviceId().equals(originalDeviceId) && !candidate.admin())
					.findFirst().orElseThrow(() -> new InvitationRejectedException("unknown device"));
			if (!original.revoked()) {
				throw new InvitationRejectedException("original device is not revoked");
			}
			InvitationRecord invitation = validInvitation(invitationSecret, requested, shared);
			List<InvitationRecord> invitations = new ArrayList<>(state.invitations());
			invitations.replaceAll(candidate -> candidate == invitation ? withUse(candidate) : candidate);
			return queueRequest(ReauthorizationKind.REPAIR, originalDeviceId, deviceName, requestedKey, outboxJson,
					invitations);
		}
	}

	/**
	 * Lists requests awaiting a host administrator.
	 *
	 * @return immutable pending requests
	 */
	public List<ReauthorizationRequest> pendingRequests() {
		synchronized (lock) {
			return state.requests().stream().filter(request -> request.state() == ApprovalState.PENDING).toList();
		}
	}

	/**
	 * Returns the audit history of administrator decisions.
	 *
	 * @return immutable audit records
	 */
	public List<ApprovalAudit> approvalAudit() {
		synchronized (lock) {
			return state.audit();
		}
	}

	/**
	 * Resolves a polling token without exposing other requests.
	 *
	 * @param token bearer token returned when the request was queued
	 * @return matching request, if any
	 */
	public Optional<ReauthorizationRequest> requestForToken(String token) {
		String hash = HostCrypto.sha256(token == null ? "" : token);
		synchronized (lock) {
			return state.requests().stream()
					.filter(request -> java.security.MessageDigest.isEqual(
							request.tokenHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
							hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
					.findFirst();
		}
	}

	/**
	 * Approves a pending request, issuing a certificate only after the registry update is durable.
	 *
	 * @param requestId request to approve
	 * @param lifetime credential lifetime
	 * @param cutoff operational period close, or null
	 * @param signer certificate signer
	 * @return approved request
	 * @throws InvitationRejectedException when the request is no longer approvable
	 * @throws HostStorageException when the decision cannot be persisted
	 */
	public ReauthorizationRequest approveRequest(String requestId, Duration lifetime, Instant cutoff,
			ReauthorizationSigner signer) {
		synchronized (lock) {
			ReauthorizationRequest request = pendingRequest(requestId);
			Instant now = clock.instant();
			Instant notAfter = now.plus(lifetime);
			if (cutoff != null && cutoff.isBefore(notAfter)) {
				notAfter = cutoff;
			}
			if (!now.isBefore(notAfter)) {
				throw new InvitationRejectedException("operational period is closed");
			}
			PublicKey key = decodePublicKey(request.publicKey());
			DeviceRecord original = state.devices().stream()
					.filter(device -> device.deviceId().equals(request.originalDeviceId())).findFirst()
					.orElseThrow(() -> new InvitationRejectedException("original device is unavailable"));
			String deviceId = original.deviceId();
			if (request.kind() == ReauthorizationKind.RENEWAL) {
				if (original.revoked() || (original.stateAt(now) != CredentialState.EXPIRED
						&& (cutoff == null || now.isBefore(cutoff)))) {
					throw new InvitationRejectedException("device is no longer eligible for renewal");
				}
			} else if (!original.revoked()) {
				throw new InvitationRejectedException("original device is no longer revoked");
			} else {
				deviceId = UUID.randomUUID().toString();
			}
			X509Certificate certificate = signer.sign(key, deviceId, now, notAfter);
			String fingerprint = HostCrypto.fingerprint(certificate);
			List<DeviceRecord> devices = new ArrayList<>(state.devices());
			if (request.kind() == ReauthorizationKind.RENEWAL) {
				DeviceRecord renewed = new DeviceRecord(original.deviceId(), original.name(), original.incidentId(),
						fingerprint, now, notAfter, null, false, original.permissions(), original.roleCeiling(),
						original.originalDeviceId(), original.recoveredOperationIds());
				devices.replaceAll(device -> device == original ? renewed : device);
			} else {
				devices.add(new DeviceRecord(deviceId, request.deviceName(), original.incidentId(), fingerprint, now,
						notAfter, null, false, original.permissions(), original.roleCeiling(),
						request.originalDeviceId(), queuedOperationIds(request.outboxJson())));
			}
			ReauthorizationRequest approved = new ReauthorizationRequest(request.requestId(), request.tokenHash(),
					request.kind(), request.originalDeviceId(), request.deviceName(), request.publicKey(),
					request.outboxJson(), request.createdAt(), ApprovalState.APPROVED, deviceId,
					HostCrypto.pem(certificate));
			List<ReauthorizationRequest> requests = replaceRequest(request, approved);
			List<ApprovalAudit> audit = appendAudit(new ApprovalAudit(requestId, ApprovalState.APPROVED, now,
					request.originalDeviceId(), deviceId));
			commit(new RegistryState(FORMAT_VERSION, state.invitations(), devices, requests, audit));
			return approved;
		}
	}

	/**
	 * Denies a pending request without changing its original device or outbox.
	 *
	 * @param requestId request to deny
	 * @return denied request
	 * @throws InvitationRejectedException when the request is not pending
	 * @throws HostStorageException when the decision cannot be persisted
	 */
	public ReauthorizationRequest denyRequest(String requestId) {
		synchronized (lock) {
			ReauthorizationRequest request = pendingRequest(requestId);
			ReauthorizationRequest denied = new ReauthorizationRequest(request.requestId(), request.tokenHash(),
					request.kind(), request.originalDeviceId(), request.deviceName(), request.publicKey(),
					request.outboxJson(), request.createdAt(), ApprovalState.DENIED, null, null);
			List<ReauthorizationRequest> requests = replaceRequest(request, denied);
			List<ApprovalAudit> audit = appendAudit(new ApprovalAudit(requestId, ApprovalState.DENIED,
					clock.instant(), request.originalDeviceId(), null));
			commit(new RegistryState(FORMAT_VERSION, state.invitations(), state.devices(), requests, audit));
			return denied;
		}
	}

	/**
	 * Reads the operation identifiers a device reported as queued.
	 *
	 * @param outboxJson serialized pending work array
	 * @return bounded set of reported operation identifiers
	 */
	private static Set<String> queuedOperationIds(String outboxJson) {
		java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
		try {
			for (com.fasterxml.jackson.databind.JsonNode entry : MAPPER.readTree(outboxJson)) {
				String id = entry.path("operationId").asText("");
				if (!id.isBlank() && ids.size() < MAX_RECOVERED_OPERATIONS) {
					ids.add(id);
				}
			}
		} catch (IOException exception) {
			return Set.of();
		}
		return ids;
	}

	/**
	 * Creates and persists a bounded pending request.
	 *
	 * @param kind request type
	 * @param originalDeviceId original identity
	 * @param deviceName operator-visible name
	 * @param requestedKey proof-of-possession public key
	 * @param outboxJson pending work for administrator review
	 * @param invitations updated invitation state
	 * @return request and one-time token
	 */
	private SubmittedRequest queueRequest(ReauthorizationKind kind, String originalDeviceId, String deviceName,
			PublicKey requestedKey, String outboxJson, List<InvitationRecord> invitations) {
		if (state.requests().stream().filter(request -> request.state() == ApprovalState.PENDING).count()
				>= MAX_PENDING_REQUESTS) {
			throw new InvitationRejectedException("approval queue is full");
		}
		if (state.requests().stream().anyMatch(request -> request.state() == ApprovalState.PENDING
				&& request.originalDeviceId().equals(originalDeviceId))) {
			throw new InvitationRejectedException("a request for this device is already pending");
		}
		if (outboxJson == null || outboxJson.length() > MAX_OUTBOX_CHARS) {
			throw new InvitationRejectedException("pending outbox is too large");
		}
		try {
			if (!MAPPER.readTree(outboxJson).isArray()) {
				throw new InvitationRejectedException("pending outbox is invalid");
			}
		} catch (IOException exception) {
			throw new InvitationRejectedException("pending outbox is invalid");
		}
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(HostCrypto.randomBytes(32));
		ReauthorizationRequest request = new ReauthorizationRequest(UUID.randomUUID().toString(),
				HostCrypto.sha256(token), kind, originalDeviceId, deviceName,
				Base64.getEncoder().encodeToString(requestedKey.getEncoded()), outboxJson, clock.instant(),
				ApprovalState.PENDING, null, null);
		List<ReauthorizationRequest> requests = new ArrayList<>(state.requests());
		requests.add(request);
		commit(new RegistryState(FORMAT_VERSION, invitations, state.devices(), requests, state.audit()));
		return new SubmittedRequest(request, token);
	}

	/**
	 * Finds a valid unexpired invitation for the requested incident.
	 *
	 * @param secret invitation secret
	 * @param requested requested incident
	 * @param shared currently hosted incident
	 * @return invitation to reserve
	 */
	private InvitationRecord validInvitation(String secret, IncidentId requested, IncidentId shared) {
		String hash = HostCrypto.sha256(secret == null ? "" : secret);
		InvitationRecord invitation = state.invitations().stream()
				.filter(candidate -> candidate.secretHash().equals(hash)).findFirst()
				.orElseThrow(() -> new InvitationRejectedException("unknown invitation"));
		if (invitation.uses() >= invitation.maxUses() || !clock.instant().isBefore(invitation.expiresAt())
				|| !invitation.incidentId().equals(requested.value())
				|| !invitation.incidentId().equals(shared.value())) {
			throw new InvitationRejectedException("invitation is not valid");
		}
		return invitation;
	}

	/**
	 * Advances an invitation's use count.
	 *
	 * @param invitation invitation to consume
	 * @return consumed invitation
	 */
	private static InvitationRecord withUse(InvitationRecord invitation) {
		return new InvitationRecord(invitation.invitationId(), invitation.secretHash(), invitation.incidentId(),
				invitation.issuedAt(), invitation.expiresAt(), invitation.maxUses(), invitation.uses() + 1);
	}

	/**
	 * Resolves a pending request.
	 *
	 * @param requestId request identifier
	 * @return pending request
	 */
	private ReauthorizationRequest pendingRequest(String requestId) {
		return state.requests().stream()
				.filter(request -> request.requestId().equals(requestId) && request.state() == ApprovalState.PENDING)
				.findFirst().orElseThrow(() -> new InvitationRejectedException("request is not pending"));
	}

	/**
	 * Replaces a request in a detached request list.
	 *
	 * @param oldRequest request being resolved
	 * @param newRequest decided request
	 * @return updated request list
	 */
	private List<ReauthorizationRequest> replaceRequest(ReauthorizationRequest oldRequest,
			ReauthorizationRequest newRequest) {
		List<ReauthorizationRequest> requests = new ArrayList<>(state.requests());
		requests.replaceAll(request -> request == oldRequest ? newRequest : request);
		return requests;
	}

	/**
	 * Appends an audit record while retaining a bounded history.
	 *
	 * @param entry decision record
	 * @return updated audit list
	 */
	private List<ApprovalAudit> appendAudit(ApprovalAudit entry) {
		List<ApprovalAudit> audit = new ArrayList<>(state.audit());
		audit.add(entry);
		return audit.size() > MAX_AUDIT_RECORDS ? audit.subList(audit.size() - MAX_AUDIT_RECORDS, audit.size())
				: audit;
	}

	/**
	 * Decodes the verified device public key held by a pending request.
	 *
	 * @param encoded base64 key bytes
	 * @return public key
	 */
	private static PublicKey decodePublicKey(String encoded) {
		try {
			return java.security.KeyFactory.getInstance("EC").generatePublic(
					new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
		} catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
			throw new HostStorageException("The pending device key is invalid", exception);
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
			commit(new RegistryState(FORMAT_VERSION, state.invitations(), devices, state.requests(), state.audit()));
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
						state.devices().stream().filter(device -> !device.admin()).toList(), state.requests(),
						state.audit()));
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
					current.permissions(), current.roleCeiling(), current.originalDeviceId(),
					current.recoveredOperationIds());
			List<DeviceRecord> devices = new ArrayList<>(state.devices());
			devices.replaceAll(device -> device == current ? revoked : device);
			RegistryState next = new RegistryState(FORMAT_VERSION, state.invitations(), devices, state.requests(),
					state.audit());
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
		List<ReauthorizationRequest> requests = next.requests().size() > MAX_PENDING_REQUESTS * 4
				? next.requests().stream().filter(request -> request.state() == ApprovalState.PENDING).toList()
				: next.requests();
		RegistryState pruned = new RegistryState(next.formatVersion(), retained, next.devices(), requests, next.audit());
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
