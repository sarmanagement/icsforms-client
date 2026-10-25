/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies Incident Command facts use explicit persisted state only. */
class IncidentCommandRolePanelTest {
	@TempDir
	Path directory;

	@Test
	void resourceOverviewDoesNotInferAvailabilityFromPresence() throws Exception {
		AppData data = new AppData();
		TCard unspecified = new TCard();
		unspecified.setPersonName("Unspecified Responder");
		TCard available = new TCard();
		available.setPersonName("Available Responder");
		available.setStatus("Available");
		data.getTCards().addAll(List.of(unspecified, available));
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller(data), ignored -> {
		}));

		assertEquals(1, panel[0].displayedResourceCount("Unspecified"));
		assertEquals(1, panel[0].displayedResourceCount("Available"));
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void liveRefreshShowsExplicitPlanningStageWithoutDerivingItFromContent() throws Exception {
		AppData data = new AppData();
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Search", 0)));
		AppController controller = controller(data);
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
		}));

		assertEquals(IapPlanningStage.NOT_RECORDED, planningStage(panel[0]).getSelectedItem());
		controller.setIapPlanningStage(IapPlanningStage.PLANNING_MEETING);
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(IapPlanningStage.PLANNING_MEETING, planningStage(panel[0]).getSelectedItem());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void unrelatedLiveChangePreservesApprovalDraft() throws Exception {
		AppController controller = controller(new AppData());
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
			});
			try {
				textField(panel[0], "approver").setText("");
				textField(panel[0], "signature").setText("Draft signature");
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		});

		controller.getData().getIncidentContext().setIncidentName("Unrelated update");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("", textField(panel[0], "approver").getText());
		assertEquals("Draft signature", textField(panel[0], "signature").getText());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}

	@SuppressWarnings("unchecked")
	private JComboBox<IapPlanningStage> planningStage(IncidentCommandRolePanel panel) throws Exception {
		Field field = IncidentCommandRolePanel.class.getDeclaredField("planningStage");
		field.setAccessible(true);
		return (JComboBox<IapPlanningStage>) field.get(panel);
	}

	private JTextField textField(IncidentCommandRolePanel panel, String name) throws ReflectiveOperationException {
		Field field = IncidentCommandRolePanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return (JTextField) field.get(panel);
	}
}
