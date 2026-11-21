/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Test harness that hosts one incident from a temporary directory on ephemeral loopback ports.
 */
final class HostFixture implements AutoCloseable {
	final Path directory;
	final MutableClock clock = new MutableClock();
	final FileIncidentStore store;
	final IncidentId incident;
	HostSession session;

	/**
	 * Creates a fixture with a new incident.
	 *
	 * @param directory empty temporary directory
	 */
	HostFixture(Path directory) {
		this.directory = directory;
		this.store = new FileIncidentStore(directory);
		this.incident = store.create(new AppData());
		this.session = new HostSession(store, incident, config(), null);
	}

	/**
	 * Creates a fixture over an existing directory and incident.
	 *
	 * @param directory store directory
	 * @param incident incident to host
	 */
	HostFixture(Path directory, IncidentId incident) {
		this.directory = directory;
		this.store = new FileIncidentStore(directory);
		this.incident = incident;
		this.session = new HostSession(store, incident, config(), null);
	}

	/**
	 * Builds a loopback-only configuration with fast stop timing.
	 *
	 * @return configuration
	 */
	HostConfig config() {
		return HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1").withClock(clock)
				.withStopTiming(Duration.ZERO, Duration.ofSeconds(2));
	}

	/**
	 * Starts the session and returns it.
	 *
	 * @return the started session
	 */
	HostSession start() {
		session.start();
		return session;
	}

	/**
	 * Issues an invitation and pairs a new device.
	 *
	 * @param name device name
	 * @return the device credential
	 */
	DeviceCredential pair(String name) {
		return HostClient.pair(HostInvitation.parse(session.issueInvitation()), name);
	}

	/**
	 * Creates a client for a device.
	 *
	 * @param credential device credential
	 * @return client
	 */
	HostClient client(DeviceCredential credential) {
		HostStatus status = session.status();
		return HostClient.forDevice("127.0.0.1", status.port(), status.enrollmentPort(), credential);
	}

	/**
	 * Builds an update batch for the incident context record.
	 *
	 * @param client client to read with
	 * @param operationId idempotency key
	 * @param name new incident name
	 * @return batch JSON
	 */
	String renameBatch(HostClient client, String operationId, String name) {
		JsonNode read = client.readIncident(incident.value());
		for (JsonNode record : read.path("records")) {
			if (record.path("key").path("type").asText().endsWith("IncidentContext")) {
				ObjectNode content = ((ObjectNode) record.path("content")).deepCopy();
				content.put("incidentName", name);
				ObjectNode submission = HostHttp.MAPPER.createObjectNode();
				submission.set("key", record.path("key"));
				submission.put("baseVersion", record.path("version").asLong());
				submission.put("operation", "UPDATE");
				submission.set("content", content);
				ObjectNode batch = HostHttp.MAPPER.createObjectNode();
				batch.put("operationId", operationId);
				ArrayNode records = batch.putArray("records");
				records.add(submission);
				return batch.toString();
			}
		}
		throw new IllegalStateException("No context record");
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		session.stop();
		store.close();
	}
}
