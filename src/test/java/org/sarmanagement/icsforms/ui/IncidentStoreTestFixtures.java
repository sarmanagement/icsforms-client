/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Creates store-backed controller fixtures and registers their resources for teardown.
 */
public final class IncidentStoreTestFixtures {
	private static final Map<AppController, ControllableIncidentStore> CONTROLLER_STORES =
			Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * Prevents fixture utility instantiation.
	 */
	private IncidentStoreTestFixtures() {
	}

	/**
	 * Creates a controller and store under an automatically isolated directory.
	 *
	 * @param data incident model to edit.
	 * @return initialized fixture.
	 */
	public static Fixture create(AppData data) {
		try {
			return create(Files.createTempDirectory("icsforms-store-test"), data);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create isolated incident test directory", exception);
		}
	}

	/**
	 * Creates a controller and store with caller-supplied application services.
	 *
	 * @param data incident model to edit.
	 * @param exportService PDF export service.
	 * @param validator incident validator.
	 * @return initialized fixture.
	 */
	public static Fixture create(AppData data, PdfExportService exportService, IncidentValidator validator) {
		try {
			return create(Files.createTempDirectory("icsforms-store-test"), data, exportService, validator);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create isolated incident test directory", exception);
		}
	}

	/**
	 * Creates a controller and store under the supplied isolated directory.
	 *
	 * @param directory isolated store directory.
	 * @param data incident model to edit.
	 * @return initialized fixture.
	 */
	public static Fixture create(Path directory, AppData data) {
		return create(directory, data, new PdfExportService(), new IncidentValidator());
	}

	/**
	 * Creates a controller and store with caller-supplied application services.
	 *
	 * @param directory isolated store directory.
	 * @param data incident model to edit.
	 * @param exportService PDF export service.
	 * @param validator incident validator.
	 * @return initialized fixture.
	 */
	public static Fixture create(Path directory, AppData data, PdfExportService exportService,
			IncidentValidator validator) {
		ControllableIncidentStore store = store(directory);
		IncidentId id = store.create(data);
		AppController controller = SwingTestLifecycleExtension.track(
				new AppController(data, store, id, exportService, validator));
		CONTROLLER_STORES.put(controller, store);
		return new Fixture(store, id, controller);
	}

	/**
	 * Opens and tracks a controllable store without creating an incident or controller.
	 *
	 * @param directory isolated store directory.
	 * @return open test store.
	 */
	public static ControllableIncidentStore store(Path directory) {
		return SwingTestLifecycleExtension.trackStore(new ControllableIncidentStore(directory));
	}

	/**
	 * Creates a tracked store-backed controller in an automatically isolated directory.
	 *
	 * @param data incident model to edit.
	 * @return initialized controller.
	 */
	public static AppController controller(AppData data) {
		return create(data).controller();
	}

	/**
	 * Creates a tracked store-backed controller using caller-supplied services.
	 *
	 * @param data incident model to edit.
	 * @param exportService PDF export service.
	 * @param validator incident validator.
	 * @return initialized controller.
	 */
	public static AppController controller(AppData data, PdfExportService exportService,
			IncidentValidator validator) {
		return create(data, exportService, validator).controller();
	}

	/**
	 * Returns the test store backing a fixture-created controller.
	 *
	 * @param controller fixture-created controller.
	 * @return test store.
	 */
	public static ControllableIncidentStore store(AppController controller) {
		ControllableIncidentStore store = CONTROLLER_STORES.get(controller);
		if (store == null) {
			throw new IllegalArgumentException("Controller was not created by this fixture");
		}
		return store;
	}

	/**
	 * Creates a main frame backed by a tracked store fixture.
	 *
	 * @param data incident model to edit.
	 * @param storeDirectory directory for fixture persistence.
	 * @param exportService PDF export service.
	 * @param validator incident validator.
	 * @param applicationDirectory application data directory.
	 * @return initialized main frame.
	 */
	public static MainFrame frame(AppData data, Path storeDirectory, PdfExportService exportService,
			IncidentValidator validator, Path applicationDirectory) {
		try {
			Fixture fixture = create(Files.createTempDirectory(storeDirectory, "incident-store-"), data,
					exportService, validator);
			return new MainFrame(data, fixture.store(), fixture.incidentId(), exportService, validator,
					applicationDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create isolated incident test directory", exception);
		}
	}

	/**
	 * Holds the store, incident identity, and controller owned by a test.
	 */
	public static final class Fixture {
		private final ControllableIncidentStore store;
		private final IncidentId incidentId;
		private final AppController controller;

		/**
		 * Creates a holder for a tracked store-backed controller.
		 *
		 * @param store backing test store.
		 * @param incidentId tracked incident identity.
		 * @param controller tracked controller.
		 */
		private Fixture(ControllableIncidentStore store, IncidentId incidentId, AppController controller) {
			this.store = store;
			this.incidentId = incidentId;
			this.controller = controller;
		}

		/**
		 * Returns the controllable store backing this fixture.
		 *
		 * @return test store.
		 */
		public ControllableIncidentStore store() {
			return store;
		}

		/**
		 * Returns the identity created for this fixture.
		 *
		 * @return incident identity.
		 */
		public IncidentId incidentId() {
			return incidentId;
		}

		/**
		 * Returns the controller using the fixture's active incident.
		 *
		 * @return store-backed controller.
		 */
		public AppController controller() {
			return controller;
		}
	}
}
