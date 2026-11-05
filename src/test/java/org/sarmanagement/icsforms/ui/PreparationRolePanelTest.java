/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;

import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

	@Test
	void unrelatedLiveChangePreservesUnsavedSafetyAndMedicalDrafts() throws Exception {
		AppController controller = controller();
		SafetyRolePanel[] safety = new SafetyRolePanel[1];
		MedicalUnitLeaderRolePanel[] medical = new MedicalUnitLeaderRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			safety[0] = new SafetyRolePanel(controller, destination -> {
			});
			medical[0] = new MedicalUnitLeaderRolePanel(controller, destination -> {
			});
			try {
				((JTextField) field(safety[0], "safetyOfficer")).setText("Draft Safety Officer");
				((JTextArea) field(safety[0], "safetyMessage")).setText("Draft safety message");
				((JTextField) field(medical[0], "leaderName")).setText("Draft Medical Leader");
				((JTextArea) field(medical[0], "humanInstruction")).setText("Draft human procedure");
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		});

		controller.getData().getIncidentContext().setIncidentName("Unrelated update");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("Draft Safety Officer", ((JTextField) field(safety[0], "safetyOfficer")).getText());
		assertEquals("Draft safety message", ((JTextArea) field(safety[0], "safetyMessage")).getText());
		assertEquals("Draft Medical Leader", ((JTextField) field(medical[0], "leaderName")).getText());
		assertEquals("Draft human procedure", ((JTextArea) field(medical[0], "humanInstruction")).getText());
		SwingUtilities.invokeAndWait(() -> {
			safety[0].dispose();
			medical[0].dispose();
		});
	}

	@Test
	void responsiblePdfsLoadAutomaticallyAndFollowLiveChangesAndSelection() throws Exception {
		AppController controller = controller();
		SafetyRolePanel[] safety = new SafetyRolePanel[1];
		MedicalUnitLeaderRolePanel[] medical = new MedicalUnitLeaderRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			safety[0] = new SafetyRolePanel(controller, destination -> {
			});
			medical[0] = new MedicalUnitLeaderRolePanel(controller, destination -> {
			});
		});
		try {
			waitForPdf(safety[0], "safetyPreview");
			waitForPdf(medical[0], "humanPreview");
			assertFalse(hasManualPdfButton(safety[0]));
			assertFalse(hasManualPdfButton(medical[0]));
			long generation = (long) field(field(safety[0], "safetyPreview"), "requestGeneration");
			SwingUtilities.invokeAndWait(() -> {
				controller.getData().getForm208().setFreeText("Live safety message");
				controller.markDirty();
			});
			SwingUtilities.invokeAndWait(() -> {
			});
			assertTrue((long) field(field(safety[0], "safetyPreview"), "requestGeneration") > generation);
			waitForPdf(safety[0], "safetyPreview");
			SwingUtilities.invokeAndWait(() -> {
				try {
					((JList<?>) field(safety[0], "worklist")).setSelectedIndex(2);
					((JList<?>) field(medical[0], "worklist")).setSelectedIndex(1);
				} catch (ReflectiveOperationException exception) {
					throw new IllegalStateException(exception);
				}

			});
			waitForPdf(safety[0], "caninePreview");
			waitForPdf(medical[0], "caninePreview");
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				safety[0].dispose();
				medical[0].dispose();
			});
		}
	}

	@Test
	void medicalResponsibleWorkPairsPlansWithTheirOwnPdfAndReviewAndShowsOperationalDetails() throws Exception {
		AppData data = new AppData();
		MedicalPlan.AidStation station = new MedicalPlan.AidStation();
		station.setName("North Aid");
		station.setLocation("Base camp");
		station.setContactNumbers("555-0100");
		data.getMedicalPlan().getAidStations().add(station);
		MedicalPlan.TransportService transport = new MedicalPlan.TransportService();
		transport.setService("Ground ambulance");
		transport.setLocation("East road");
		transport.setContactNumbers("555-0101");
		data.getMedicalPlan().getTransportServices().add(transport);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Ridge Hospital");
		hospital.setAddress("1 Ridge Road");
		data.getMedicalPlan().getHospitals().add(hospital);
		LocalDateTime reviewTime = LocalDateTime.of(2026, 11, 2, 2, 0);
		data.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Reviewer", reviewTime);
		data.recordPlanReview(PlanReviewArea.CANINE_MEDICAL, "Reviewer", reviewTime);
		data.getCanineMedicalPlan().setEmergencyProcedures("Changed after review");
		AppController controller = controller(data);
		AtomicReference<WorkspaceDestination> destination = new AtomicReference<>();
		MedicalUnitLeaderRolePanel[] panel = new MedicalUnitLeaderRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new MedicalUnitLeaderRolePanel(controller, destination::set);
			JList<?> worklist = (JList<?>) fieldUnchecked(panel[0], "worklist");
			JTextArea detail = (JTextArea) fieldUnchecked(panel[0], "detail");
			AsyncFormPreviewPanel humanPreview = (AsyncFormPreviewPanel) fieldUnchecked(panel[0], "humanPreview");
			AsyncFormPreviewPanel caninePreview = (AsyncFormPreviewPanel) fieldUnchecked(panel[0], "caninePreview");
			PlanReviewStrip humanReview = (PlanReviewStrip) fieldUnchecked(panel[0], "humanReview");
			PlanReviewStrip canineReview = (PlanReviewStrip) fieldUnchecked(panel[0], "canineReview");
			worklist.setSelectedValue("Human medical plan preparation", true);
			assertTrue(humanPreview.isVisible());
			assertFalse(caninePreview.isVisible());
			assertTrue(humanReview.isVisible());
			assertFalse(canineReview.isVisible());
			worklist.setSelectedValue("Canine medical plan preparation", true);
			assertFalse(humanPreview.isVisible());
			assertTrue(caninePreview.isVisible());
			assertFalse(humanReview.isVisible());
			assertTrue(canineReview.isVisible());
			worklist.setSelectedValue("Personnel and aid stations", true);
			assertTrue(detail.getText().contains("North Aid"));
			assertTrue(detail.getText().contains("555-0100"));
			worklist.setSelectedValue("Transport and facilities", true);
			assertTrue(detail.getText().contains("Ground ambulance"));
			assertTrue(detail.getText().contains("Ridge Hospital"));
			worklist.setSelectedValue("Review handoff", true);
			assertTrue(humanReview.isVisible());
			assertTrue(canineReview.isVisible());
			assertTrue(detail.getText().contains("Reviewed-approved"));
			assertTrue(detail.getText().contains("Changed since review"));
			worklist.setSelectedValue("Canine medical plan preparation", true);
			try {
				var method = MedicalUnitLeaderRolePanel.class.getDeclaredMethod("openSelectedSource");
				method.setAccessible(true);
				method.invoke(panel[0]);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
			assertEquals(WorkspaceDestination.canineMedicalPlan(), destination.get());
		});
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void incidentReplacementCancelsPdfWorkAndRejectsFurtherRefreshes() throws Exception {
		AppController controller = controller();
		SafetyRolePanel[] safety = new SafetyRolePanel[1];
		MedicalUnitLeaderRolePanel[] medical = new MedicalUnitLeaderRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			safety[0] = new SafetyRolePanel(controller, destination -> {
			});
			medical[0] = new MedicalUnitLeaderRolePanel(controller, destination -> {
			});
			controller.setData(new AppData());
		});
		SwingUtilities.invokeAndWait(() -> {
			safety[0].refreshFromModel();
			medical[0].refreshFromModel();
		});
		try {
			assertNull(field(field(safety[0], "safetyPreview"), "worker"));
			assertNull(field(field(medical[0], "humanPreview"), "worker"));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				safety[0].dispose();
				medical[0].dispose();
			});
		}
	}

	/**
	 * Waits for automatically requested role PDF output.
	 *
	 * @param owner role panel.
	 * @param name PDF field name.
	 * @throws Exception when reflection, the wait, or EDT synchronization fails.
	 */
	private void waitForPdf(Object owner, String name) throws Exception {
		AsyncFormPreviewPanel preview = (AsyncFormPreviewPanel) field(owner, name);
		AtomicBoolean ready = new AtomicBoolean();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!ready.get() && System.nanoTime() < deadline) {
			SwingUtilities.invokeAndWait(() -> ready.set(preview.previewStateText().contains("Current PDF")
					&& preview.displayedPageCount() > 0));
			if (!ready.get()) {
				Thread.sleep(25);
			}
		}
		assertTrue(ready.get(), preview.previewStateText());
	}

	/**
	 * Finds obsolete manual preview or refresh actions in a component tree.
	 *
	 * @param container role panel tree.
	 * @return whether a manual PDF action is present.
	 */
	private boolean hasManualPdfButton(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof JButton button
					&& (button.getText().contains("Preview") || button.getText().contains("Refresh"))) {
				return true;
			}
			if (component instanceof Container child && hasManualPdfButton(child)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a controller with repository-local test storage.
	 *
	 * @return isolated incident controller.
	 */
	private AppController controller() {
		return controller(new AppData());
	}

	/**
	 * Creates a controller using the supplied incident fixture.
	 *
	 * @param data incident data.
	 * @return isolated incident controller.
	 */
	private AppController controller(AppData data) {
		return IncidentStoreTestFixtures.controller(data);
	}

	/**
	 * Reads a panel field from an assertion running on the Swing event thread.
	 *
	 * @param target role panel.
	 * @param name field name.
	 * @return field value.
	 */
	private Object fieldUnchecked(Object target, String name) {
		try {
			return field(target, name);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * Reads a role panel field for focused behavioral assertions.
	 *
	 * @param target owning object.
	 * @param name field name.
	 * @return current field value.
	 * @throws ReflectiveOperationException when the field cannot be accessed.
	 */
	private Object field(Object target, String name) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
