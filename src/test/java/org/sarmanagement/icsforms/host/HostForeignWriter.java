/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.nio.file.Path;

/**
 * Runs in a separate JVM to attempt to take the edit lock for, and save, an incident another process may be
 * hosting. It prints {@code LOCK=true|false} and {@code SAVE=OK|REFUSED} for the parent test to check.
 */
public final class HostForeignWriter {
	/**
	 * Prevents instantiation.
	 */
	private HostForeignWriter() {
	}

	/**
	 * Entry point.
	 *
	 * @param args store directory and incident id
	 */
	public static void main(String[] args) {
		System.setProperty("icsforms.testMode", "true");
		IncidentId incident = new IncidentId(args[1]);
		try (FileIncidentStore store = new FileIncidentStore(Path.of(args[0]))) {
			boolean locked = store.lock(incident);
			System.out.println("LOCK=" + locked);
			String save;
			try {
				AppData data = store.load(incident);
				data.getIncidentContext().setIncidentName("written by another process");
				store.save(incident, data);
				save = "OK";
			} catch (RuntimeException failure) {
				save = "REFUSED";
			}
			System.out.println("SAVE=" + save);
			if (locked) {
				store.unlock(incident);
			}
		}
	}
}
