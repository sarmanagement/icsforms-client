/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises visible role-window wiring and incident-switch registration under Xvfb. */
class RolePanelWindowsTest {
	@TempDir
	Path directory;

	@Test
	void opensFocusedRoleWindowsAndRegistersThemAsIncidentEditors() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Window exercise");
		AppController controller = new AppController(data,
				new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
		List<JFrame> frames = new ArrayList<>();

		SwingUtilities.invokeAndWait(() -> {
			frames.add(RolePanelWindows.openIncidentCommand(null, controller, ignored -> {
			}));
			frames.add(RolePanelWindows.openSafety(null, controller, ignored -> {
			}));
			frames.add(RolePanelWindows.openMedicalUnitLeader(null, controller, ignored -> {
			}));
			frames.add(RolePanelWindows.openOperations(null, controller, () -> null, ignored -> {
			}, ignored -> {
			}, ignored -> {
			}, () -> {
			}, ignored -> {
			}, () -> {
			}, () -> {
			}));
			frames.add(RolePanelWindows.openCommunications(null, controller, ignored -> {
			}, () -> {
			}, () -> {
			}));
		});

		try {
			assertEquals(5, frames.size());
			assertTrue(frames.stream().allMatch(JFrame::isDisplayable));
			assertEquals(5, UiSupport.outstandingIncidentWindows(null, data).size());
		} finally {
			SwingUtilities.invokeAndWait(() -> frames.forEach(JFrame::dispose));
		}
	}
}
