/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers derived badge, eligibility, detail and detached-row invalidation through live notifications. */
class RoleRefreshDependenciesTest {
	@Test
	void medicalChangesUpdateBadgesAndDetailsWithoutResettingUnchangedBoards() throws Exception {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Role dependency regression");
		data.setIncidentMode(IncidentMode.GENERIC);
		data.getMedicalUnitLeaderAssignment().setName("LEADER-OLD");
		AppController controller = IncidentStoreTestFixtures.controller(data);
		MedicalUnitLeaderRolePanel[] medical = new MedicalUnitLeaderRolePanel[1];
		SafetyRolePanel[] safety = new SafetyRolePanel[1];
		OperationsRolePanel[] operations = new OperationsRolePanel[1];
		CommunicationsRolePanel[] communications = new CommunicationsRolePanel[1];
		AtomicInteger unchangedBoards = new AtomicInteger();
		AtomicInteger unchangedChannels = new AtomicInteger();
		try {
			SwingUtilities.invokeAndWait(() -> {
				controller.markDirty();
				data.getTCards().forEach(TCard::getResourceId);
				stopAutosave(controller);
				medical[0] = new MedicalUnitLeaderRolePanel(controller, destination -> { });
				safety[0] = new SafetyRolePanel(controller, destination -> { });
				operations[0] = new OperationsRolePanel(controller, () -> null, id -> { }, id -> { },
						task -> { }, () -> { }, id -> { }, () -> { }, () -> { });
				communications[0] = new CommunicationsRolePanel(controller, destination -> { }, () -> { }, () -> { });
				list(medical[0], "worklist").setSelectedValue("Personnel and aid stations", false);
				UiRefreshStateTest.watchList(list(operations[0], "taskBoard"), unchangedBoards);
				UiRefreshStateTest.watchList(list(operations[0], "availableResources"), unchangedBoards);
				((javax.swing.JTable) Ics205PanelTest.field(communications[0], "channels")).getModel()
						.addTableModelListener(event -> unchangedChannels.incrementAndGet());
				assertTrue(text(medical[0], "detail").contains("LEADER-OLD"));
			});
			String tracked = tracked(data);
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(controller.commitIfCurrent(controller.captureIncidentBinding(), () -> {
					data.getMedicalUnitLeaderAssignment().setName("LEADER-NEW");
					data.getMedicalPlan().setCall911Procedure("MEDICAL-INSTRUCTION");
				}));
				stopAutosave(controller);
				assertEquals(tracked, tracked(data), "no incidental assignments/channels/cards/log changes");
			});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(text(medical[0], "detail").contains("LEADER-NEW"));
				assertFalse(text(medical[0], "detail").contains("LEADER-OLD"));
				assertTrue(badges(safety[0]).contains("content items"), badges(safety[0]));
				assertTrue(badges(medical[0]).contains("content items"), badges(medical[0]));
				assertEquals(0, unchangedBoards.get());
				assertEquals(0, unchangedChannels.get());
				assertEquals("Personnel and aid stations", list(medical[0], "worklist").getSelectedValue());
				assertFalse(rows(list(safety[0], "worklist")).contains("Canine medical plan review"));
				data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Reviewer", java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
				controller.markDirty();
				stopAutosave(controller);
			});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(badges(safety[0]).contains("Reviewed — current"), badges(safety[0]));
				assertTrue(badges(medical[0]).contains("Reviewed — current"), badges(medical[0]));
				data.setIncidentMode(IncidentMode.SAR);
				controller.markDirty();
				stopAutosave(controller);
			});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(rows(list(safety[0], "worklist")).contains("Canine medical plan review"));
				assertTrue(rows(list(medical[0], "worklist")).contains("Canine medical plan"));
				assertEquals("Personnel and aid stations", list(medical[0], "worklist").getSelectedValue());
				assertEquals(0, unchangedBoards.get());
				assertEquals(0, unchangedChannels.get());
				((javax.swing.JCheckBox) Ics205PanelTest.field(safety[0], "showAll")).doClick();
				assertTrue(rows(list(safety[0], "worklist")).contains("Incident objectives context"));
				assertEquals("Safety planning and reported hazards", list(safety[0], "worklist").getSelectedValue());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (medical[0] != null) {
					medical[0].dispose();
				}
				if (safety[0] != null) {
					safety[0].dispose();
				}
				if (operations[0] != null) {
					operations[0].dispose();
				}
				if (communications[0] != null) {
					communications[0].dispose();
				}
				controller.close();
			});
		}
	}

	@Test
	void inPlaceResourceLabelChangeUpdatesDetachedListAndRetainsIdentity() throws Exception {
		AppData data = new AppData();
		TCard card = new TCard();
		card.setPersonName("RESOURCE-OLD");
		card.setStatus("Available");
		card.getResourceId();
		data.getTCards().add(card);
		AppController controller = IncidentStoreTestFixtures.controller(data);
		OperationsRolePanel[] panel = new OperationsRolePanel[1];
		AtomicInteger changes = new AtomicInteger();
		try {
			SwingUtilities.invokeAndWait(() -> {
				controller.markDirty();
				data.getTCards().forEach(TCard::getResourceId);
				stopAutosave(controller);
				panel[0] = new OperationsRolePanel(controller, () -> null, id -> { }, id -> { },
						task -> { }, () -> { }, id -> { }, () -> { }, () -> { });
				list(panel[0], "availableResources").setSelectedIndex(0);
				assertTrue(rows(list(panel[0], "availableResources")).contains("RESOURCE-OLD"));
				UiRefreshStateTest.watchList(list(panel[0], "availableResources"), changes);
				assertTrue(controller.commitIfCurrent(controller.captureIncidentBinding(),
						() -> card.setPersonName("RESOURCE-NEW")));
				stopAutosave(controller);
			});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(rows(list(panel[0], "availableResources")).contains("RESOURCE-NEW"));
				assertFalse(rows(list(panel[0], "availableResources")).contains("RESOURCE-OLD"));
				assertEquals(1, changes.get(), "only the changed row is notified");
				assertEquals(0, list(panel[0], "availableResources").getSelectedIndex());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (panel[0] != null) {
					panel[0].dispose();
				}
				controller.close();
			});
		}
	}

	/**
	 * Obtains a role's displayed worklist.
	 *
	 * @param panel role view.
	 * @param field list field.
	 * @return displayed list.
	 */
	private static JList<?> list(Object panel, String field) {
		return (JList<?>) Ics205PanelTest.field(panel, field);
	}

	/**
	 * Reads actual projected row labels.
	 *
	 * @param list displayed list.
	 * @return concatenated labels.
	 */
	private static String rows(JList<?> list) {
		return java.util.stream.IntStream.range(0, list.getModel().getSize())
				.mapToObj(index -> list.getModel().getElementAt(index).toString())
				.collect(java.util.stream.Collectors.joining("\n"));
	}

	/**
	 * Reads a displayed detail.
	 *
	 * @param panel role view.
	 * @param field detail field.
	 * @return visible text.
	 */
	private static String text(Object panel, String field) {
		return ((JTextArea) Ics205PanelTest.field(panel, field)).getText();
	}

	/**
	 * Reads actual role badge text.
	 *
	 * @param panel role view.
	 * @return concatenated badge text.
	 */
	private static String badges(Object panel) {
		var badges = (RolePlanStatusBadges) Ics205PanelTest.field(panel, "planBadges");
		return java.util.Arrays.stream(badges.getComponents()).map(javax.swing.JLabel.class::cast)
				.map(javax.swing.JLabel::getText).collect(java.util.stream.Collectors.joining("\n"));
	}

	/**
	 * Captures unrelated inputs to exclude incidental invalidation.
	 *
	 * @param data incident.
	 * @return detached tracked fields.
	 */
	private static String tracked(AppData data) {
		try {
			return java.util.Base64.getEncoder().encodeToString(UiRefreshState.serialize(List.of(
				data.getSarTaskAssignments(), data.getAllAssignmentResources(), data.getForm205(),
				data.getTCards(), data.getActivityLogs())));
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Stops asynchronous persistence during isolated dependency assertions.
	 *
	 * @param controller test controller.
	 */
	private static void stopAutosave(AppController controller) {
		((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
	}
}
