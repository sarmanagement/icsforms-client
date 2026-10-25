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

import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies focused preparation panels remain live and responsibility-filtered. */
class PreparationRolePanelTest {
	@TempDir
	Path directory;

	@Test
	void defaultsToResponsibleWorkWithoutRejectingSimplePlans() throws Exception {
		AppController controller = controller();
		SafetyRolePanel[] safety = new SafetyRolePanel[1];
		MedicalUnitLeaderRolePanel[] medical = new MedicalUnitLeaderRolePanel[1];

		SwingUtilities.invokeAndWait(() -> {
			safety[0] = new SafetyRolePanel(controller, destination -> {
			});
			medical[0] = new MedicalUnitLeaderRolePanel(controller, destination -> {
			});
		});

		assertEquals(3, safety[0].workItemCount());
		assertEquals(5, medical[0].workItemCount());
		SwingUtilities.invokeAndWait(() -> {
			safety[0].dispose();
			medical[0].dispose();
		});
	}

	@Test
	void liveChangeRefreshesFocusedSharedValues() throws Exception {
		AppController controller = controller();
		MedicalUnitLeaderRolePanel[] panel = new MedicalUnitLeaderRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new MedicalUnitLeaderRolePanel(controller, destination -> {
		}));

		controller.getData().getMedicalPlan().setCall911Procedure("Updated from full editor");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("Updated from full editor", ((JTextArea) field(panel[0], "humanInstruction")).getText());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	private AppController controller() {
		return new AppController(new AppData(), new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
	}

	private Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
