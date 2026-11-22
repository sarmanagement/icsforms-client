/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real host session (ephemeral loopback port, temporary directory) sharing one seeded incident, plus helpers
 * that pair remote stores and wrap their transport in a deliberately slow gateway.
 */
final class HostedIncidentFixture implements AutoCloseable {
	private final FileIncidentStore backing;
	private final HostSession session;
	private final IncidentId incident;
	private int devices;

	/**
	 * Seeds an incident named {@code name} and starts a host session for it.
	 *
	 * @param directory empty temporary directory for the host's store
	 * @param name seeded incident name
	 */
	HostedIncidentFixture(Path directory, String name) {
		backing = new FileIncidentStore(directory);
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		incident = backing.create(data);
		session = new HostSession(backing, incident, HostConfig.defaults("127.0.0.1", 0, 0)
				.withBindAddress("127.0.0.1").withStopTiming(Duration.ofMillis(1500), Duration.ofSeconds(2)), null);
		session.start();
	}

	/**
	 * Returns the shared incident.
	 *
	 * @return incident identity
	 */
	IncidentId incident() {
		return incident;
	}

	/**
	 * Returns the host session.
	 *
	 * @return running session
	 */
	HostSession session() {
		return session;
	}

	/**
	 * Returns the host's backing store, for assertions on what the host persisted.
	 *
	 * @return backing file store
	 */
	FileIncidentStore backing() {
		return backing;
	}

	/**
	 * Pairs a new device and returns its remote store.
	 *
	 * @return remote store for the shared incident
	 */
	RemoteIncidentStore pair() {
		return HostGateway.pair(session.issueInvitation(), "device-" + (++devices));
	}

	/**
	 * Pairs a new device whose transport is wrapped by a slow gateway.
	 *
	 * @param slow wrapper factory receiving the real gateway
	 * @return remote store over the slow gateway
	 */
	RemoteIncidentStore pairThrough(java.util.function.Function<RemoteIncidentGateway, RemoteIncidentGateway> slow) {
		RemoteIncidentStore paired = pair();
		return new RemoteIncidentStore(slow.apply(paired.gateway()), incident);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		session.stop();
		backing.close();
	}

	/**
	 * Gateway that blocks every call on a latch while armed and records calls made on the event thread.
	 */
	static final class SlowGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private volatile CountDownLatch gate = new CountDownLatch(0);
		private final AtomicInteger calls = new AtomicInteger();
		private final AtomicInteger onEventThread = new AtomicInteger();

		/**
		 * Creates the gateway.
		 *
		 * @param delegate real transport
		 */
		SlowGateway(RemoteIncidentGateway delegate) {
			this.delegate = delegate;
		}

		/**
		 * Makes later calls block until {@link #release()}.
		 */
		void arm() {
			gate = new CountDownLatch(1);
		}

		/**
		 * Lets blocked and later calls proceed.
		 */
		void release() {
			gate.countDown();
		}

		/**
		 * Returns how many calls started so far.
		 *
		 * @return call count
		 */
		int calls() {
			return calls.get();
		}

		/**
		 * Returns how many calls ran on the event-dispatch thread.
		 *
		 * @return event-thread call count
		 */
		int callsOnEventThread() {
			return onEventThread.get();
		}

		/**
		 * Records and delays a call.
		 */
		private void enter() {
			calls.incrementAndGet();
			if (javax.swing.SwingUtilities.isEventDispatchThread()) {
				onEventThread.incrementAndGet();
			}
			try {
				if (!gate.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
					throw new IllegalStateException("Slow gateway was never released");
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(interrupted);
			}
		}

		/** {@inheritDoc} */
		@Override
		public String location() {
			return delegate.location();
		}

		/** {@inheritDoc} */
		@Override
		public java.util.Optional<String> authorityFingerprint() {
			return delegate.authorityFingerprint();
		}

		/** {@inheritDoc} */
		@Override
		public org.sarmanagement.icsforms.persistence.IncidentService.ReadResult read(IncidentId id) {
			enter();
			return delegate.read(id);
		}

		/** {@inheritDoc} */
		@Override
		public org.sarmanagement.icsforms.persistence.IncidentService.ChangeResult changesSince(IncidentId id,
				long after) {
			enter();
			return delegate.changesSince(id, after);
		}

		/** {@inheritDoc} */
		@Override
		public org.sarmanagement.icsforms.persistence.IncidentService.SubmissionResult submit(
				org.sarmanagement.icsforms.persistence.IncidentService.RecordBatch batch) {
			enter();
			return delegate.submit(batch);
		}

		/** {@inheritDoc} */
		@Override
		public HostProbe probe() {
			enter();
			return delegate.probe();
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
			delegate.close();
		}
	}
}
