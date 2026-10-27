/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies startup opens saved incidents through the combined manager. */
class StartupDialogTest {
	@TempDir
	Path directory;

	@Test
	void openingAtStartupUsesIncidentManager() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve("store"))) {
			AppData data = new AppData();
			data.getIncidentContext().setIncidentName("Startup incident");
			var id = store.create(data);
			StartupDialog[] startup = new StartupDialog[1];
			SwingUtilities.invokeAndWait(() -> {
				startup[0] = new StartupDialog(null, store, true);
				Timer open = new Timer(120, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof StartupDialog dialog && dialog.isVisible()) {
							((Timer) event.getSource()).stop();
							button(dialog, "Open existing incident…").doClick();
						}
					}
				});
				Timer select = new Timer(120, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof IncidentManagerDialog manager && manager.isVisible()) {
							JTable table = descendants(manager).stream().filter(JTable.class::isInstance)
									.map(JTable.class::cast).findFirst().orElseThrow();
							table.setRowSelectionInterval(0, 0);
							button(manager, "Open").doClick();
							((Timer) event.getSource()).stop();
						}
					}
				});
				Timer failSafe = new Timer(10000, event -> {
					for (Window window : Window.getWindows()) {
						if (window.isVisible()) {
							window.dispose();
						}
					}
				});
				open.start();
				select.start();
				failSafe.setRepeats(false);
				failSafe.start();
				startup[0].setVisible(true);
				open.stop();
				select.stop();
				failSafe.stop();
			});
			assertEquals(StartupDialog.StartupAction.OPEN_EXISTING, startup[0].getChosenAction());
			assertEquals(id, startup[0].getChosenIncidentId());
		}
	}

	/**
	 * Finds a button in a nested window.
	 *
	 * @param parent parent component.
	 * @param title button title.
	 * @return matching button.
	 */
	private static JButton button(Container parent, String title) {
		return descendants(parent).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
				.filter(candidate -> title.equals(candidate.getText())).findFirst().orElseThrow();
	}

	/**
	 * Traverses nested Swing components.
	 *
	 * @param parent component container.
	 * @return all descendants.
	 */
	private static java.util.List<Component> descendants(Container parent) {
		java.util.List<Component> result = new java.util.ArrayList<>();
		for (Component child : parent.getComponents()) {
			result.add(child);
			if (child instanceof Container container) {
				result.addAll(descendants(container));
			}
		}
		return result;
	}
}
