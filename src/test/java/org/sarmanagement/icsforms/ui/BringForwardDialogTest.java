/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RecordIdentityAssertions;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies source availability and explicit selection in the actual modal editor. */
class BringForwardDialogTest {
	@TempDir
	Path directory;

	@Test
	void everyEmbeddedEditorUsesTheSharedFrameOperationWithoutLocalFlushes() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			AtomicInteger requests = new AtomicInteger();
			AppData data = new AppData();
			data.getForm205().setSpecialInstructions("Available previous communications");
			data.advanceToNewOperationalPeriod();
			IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
			MainFrame frame = new MainFrame(data, fixture.store(), fixture.incidentId(),
					new PdfExportService(), new IncidentValidator(), directory) {
				/** {@inheritDoc} */
				@Override
				void openBringForwardPlans() {
					requests.incrementAndGet();
				}
			};
			try {
				for (String name : new String[]{"ics205Panel", "medicalPlanPanel", "canineMedicalPanel", "safetyPanel"}) {
					JButton launch = button((Component) field(frame, name), "Bring Forward…");
					assertNotNull(launch);
					launch.doClick();
				}
				assertEquals(4, requests.get());
			} finally {
				AppController controller = (AppController) field(frame, "controller");
				((Timer) field(controller, "autosaveTimer")).stop();
				frame.dispose();
			}
		});
	}

	@Test
	void editorButtonsTrackSourceAvailabilityOnRefresh() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			AppData data = new AppData();
			MainFrame frame = IncidentStoreTestFixtures.frame(data, directory,
					new PdfExportService(), new IncidentValidator(), directory);
			AppController controller = (AppController) field(frame, "controller");
			try {
				String[] names = {"ics205Panel", "medicalPlanPanel", "canineMedicalPanel", "safetyPanel"};
				for (String name : names) {
					assertFalse(button((Component) field(frame, name), "Bring Forward…").isEnabled());
				}
				data.getForm205().setSpecialInstructions("Prior plan");
				controller.newOperationalPeriod();
				((Ics205Panel) field(frame, "ics205Panel")).refreshFromModel();
				((Ics206Panel) field(frame, "medicalPlanPanel")).refreshFromModel();
				((CanineMedicalPanel) field(frame, "canineMedicalPanel")).refreshFromModel();
				((SafetyPanel) field(frame, "safetyPanel")).refreshFromModel();
				for (String name : names) {
					assertTrue(button((Component) field(frame, name), "Bring Forward…").isEnabled());
				}
			} finally {
				((Timer) field(controller, "autosaveTimer")).stop();
				frame.dispose();
			}
		});
	}

	@Test
	void periodChangeDuringConfirmationCannotImportIntoTheNewPeriod() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData target = new AppData();
			target.getForm205().setSpecialInstructions("Previous communications");
			target.advanceToNewOperationalPeriod();
			var id = store.create(target);
			AppController controller = new AppController(target, store, id,
					new PdfExportService(), new IncidentValidator());
			SwingUtilities.invokeAndWait(() -> {
				BringForwardDialog dialog = new BringForwardDialog(null, controller);
				boolean[] advanced = {false};
				Timer confirmation = new Timer(20, event -> {
					for (Window window : Window.getWindows()) {
						if (!window.isShowing()) {
							continue;
						}
						JOptionPane pane = optionPane(window);
						if (pane != null) {
							if (!advanced[0]) {
								controller.newOperationalPeriod();
								advanced[0] = true;
							}
							pane.setValue(JOptionPane.OK_OPTION);
						}
					}
				});
				try {
					Map<?, ?> choices = (Map<?, ?>) field(dialog, "choices");
					((JCheckBox) choices.get(PlanReviewArea.COMMUNICATIONS)).doClick();
					confirmation.start();
					((JButton) field(dialog, "apply")).doClick();
					assertTrue(advanced[0]);
					assertEquals("", target.getForm205().getSpecialInstructions());
					assertEquals(2, target.getOperationalPeriodHistory().size());
				} finally {
					confirmation.stop();
					((Timer) field(controller, "autosaveTimer")).stop();
					dialog.dispose();
				}
			});
		}
	}

	@Test
	void otherIncidentImportsRemainAvailableWithoutAnyPreviousPeriod() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData source = new AppData();
			source.getMedicalPlan().setEmergencyProcedures("Other incident procedure");
			RadioChannel sourceChannel = new RadioChannel();
			sourceChannel.setChannelName("Other incident channel");
			source.getForm205().getChannels().add(sourceChannel);
			SafetyMessageBlock safetyBlock = new SafetyMessageBlock();
			safetyBlock.setHazard("Other incident hazard");
			source.getForm208().getHazardBlocks().add(safetyBlock);
			MedicalPlan.Hospital sourceHospital = new MedicalPlan.Hospital();
			sourceHospital.setName("Other incident hospital");
			source.getMedicalPlan().getHospitals().add(sourceHospital);
			VetFacility sourceFacility = new VetFacility();
			sourceFacility.setName("Other incident veterinary hospital");
			source.getCanineMedicalPlan().getSelectedFacilities().add(sourceFacility);
			Ics215Form.ResourceHeading sourceHeading = new Ics215Form.ResourceHeading();
			sourceHeading.category = "Medical";
			source.getNextPeriodForm215().getResourceHeadings().add(sourceHeading);
			Ics215Form.WorkRow sourceRow = new Ics215Form.WorkRow();
			sourceRow.resourceAllocations.put(sourceHeading.id, new Ics215Form.ResourceAllocation());
			sourceRow.ics204DraftFormId = "source-draft-form";
			source.getNextPeriodForm215().getWorkRows().add(sourceRow);
			Map<Class<?>, Set<String>> sourceIds = RecordIdentityAssertions.collect(source);
			var sourceId = store.create(source);
			AppData target = new AppData();
			var id = store.create(target);
			AppController controller = new AppController(target, store, id,
					new PdfExportService(), new IncidentValidator());
			SwingUtilities.invokeAndWait(() -> {
				BringForwardDialog dialog = new BringForwardDialog(null, controller);
				try {
					assertFalse(((JRadioButton) field(dialog, "previous")).isEnabled());
					JRadioButton other = (JRadioButton) field(dialog, "other");
					assertTrue(other.isEnabled());
					assertTrue(other.isSelected());
					Map<?, ?> choices = (Map<?, ?>) field(dialog, "choices");
					JCheckBox human = (JCheckBox) choices.get(PlanReviewArea.HUMAN_MEDICAL);
					assertTrue(human.isEnabled());
					assertFalse(human.isSelected());
					for (PlanReviewArea area : PlanReviewArea.values()) {
						JCheckBox choice = (JCheckBox) choices.get(area);
						assertTrue(choice.isEnabled(), area.name());
						choice.doClick();
					}
					JButton apply = (JButton) field(dialog, "apply");
					assertTrue(apply.isEnabled());
					JCheckBox ics215 = (JCheckBox) field(dialog, "ics215Choice");
					assertTrue(ics215.isEnabled());
					ics215.doClick();
					assertTrue(apply.isEnabled());
					assertSame(target, controller.getData());
					assertEquals(id, controller.getActiveIncidentId());
					assertEquals("", target.getMedicalPlan().getEmergencyProcedures());
					var imported = controller.getImportPlanSources(sourceId).get(0);
					Map<Class<?>, Set<String>> importedIds =
							assertDoesNotThrow(() -> RecordIdentityAssertions.collect(imported));
					String sourceChannelId = imported.getForm205().getChannels().get(0).getId();
					String sourceSafetyId = imported.getForm208().getHazardBlocks().get(0).getId();
					String sourcePlanId = imported.getMedicalPlan().getId();
					String sourceHospitalId = imported.getMedicalPlan().getHospitals().get(0).getId();
					String sourceFacilityId = imported.getCanineMedicalPlan().getSelectedFacilities().get(0).getId();
					String sourceHeadingId = imported.getNextPeriodForm215().getResourceHeadings().get(0).id;
					String sourceRowId = imported.getNextPeriodForm215().getWorkRows().get(0).id;
					controller.bringForwardPlans(imported, Set.of(PlanReviewArea.values()), true, true, false);
					Map<Class<?>, Set<String>> targetIds =
							assertDoesNotThrow(() -> RecordIdentityAssertions.collect(target));
					for (Map.Entry<Class<?>, Set<String>> entry : importedIds.entrySet()) {
						assertTrue(java.util.Collections.disjoint(entry.getValue(),
								targetIds.getOrDefault(entry.getKey(), Set.of())));
					}
					assertNotEquals(sourceChannelId, target.getForm205().getChannels().get(0).getId());
					assertNotEquals(sourceSafetyId, target.getForm208().getHazardBlocks().get(0).getId());
					assertNotEquals(sourcePlanId, target.getMedicalPlan().getId());
					assertNotEquals(sourceHospitalId, target.getMedicalPlan().getHospitals().get(0).getId());
					assertNotEquals(sourceFacilityId,
							target.getCanineMedicalPlan().getSelectedFacilities().get(0).getId());
					assertNotEquals(sourceHeadingId, target.getNextPeriodForm215().getResourceHeadings().get(0).id);
					assertNotEquals(sourceRowId, target.getNextPeriodForm215().getWorkRows().get(0).id);
					assertTrue(target.getNextPeriodForm215().getWorkRows().get(0).resourceAllocations
							.containsKey(target.getNextPeriodForm215().getResourceHeadings().get(0).id));
					assertFalse(target.getNextPeriodForm215().getWorkRows().get(0).resourceAllocations
							.containsKey(sourceHeadingId));
					assertEquals("", target.getNextPeriodForm215().getWorkRows().get(0).ics204DraftFormId);
					assertEquals(sourceHospitalId, imported.getMedicalPlan().getHospitals().get(0).getId());
					assertEquals(importedIds, assertDoesNotThrow(() -> RecordIdentityAssertions.collect(imported)));
					assertDoesNotThrow(() -> RecordIdentityAssertions.collect(target));
					assertEquals(sourceIds, assertDoesNotThrow(() -> RecordIdentityAssertions.collect(source)));
					assertEquals("Other incident hazard", source.getForm208().getHazardBlocks().get(0).getHazard());
					assertEquals("Other incident hospital", source.getMedicalPlan().getHospitals().get(0).getName());
				} finally {
					dialog.dispose();
					controller.close();
				}
			});
		}
	}

	@Test
	void previousPeriodAvailabilityRequiresContentAndStartupOffersExplicitIcs215Promotion() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData target = new AppData();
			target.getForm205().setSpecialInstructions("Archived communications");
			target.advanceToNewOperationalPeriod();
			var id = store.create(target);
			AppController controller = new AppController(target, store, id,
					new PdfExportService(), new IncidentValidator());
			SwingUtilities.invokeAndWait(() -> {
				BringForwardDialog dialog = new BringForwardDialog(null, controller);
				StartupDialog startup = new StartupDialog(null, store, false);
				try {
					assertTrue(((JRadioButton) field(dialog, "previous")).isEnabled());
					assertFalse(((JRadioButton) field(dialog, "other")).isEnabled());
					Map<?, ?> choices = (Map<?, ?>) field(dialog, "choices");
					assertTrue(((JCheckBox) choices.get(PlanReviewArea.COMMUNICATIONS)).isEnabled());
					assertFalse(((JCheckBox) choices.get(PlanReviewArea.HUMAN_MEDICAL)).isEnabled());
					assertFalse(startup.isBringForwardCommunicationsPlan());
					assertFalse(startup.isPromotePlannedIcs215());
				} finally {
					dialog.dispose();
					startup.dispose();
				}
			});
		}
	}

	/**
	 * Reads a private Swing control without introducing production test-only APIs.
	 *
	 * @param owner dialog holding the control.
	 * @param name field name.
	 * @return control value.
	 * @throws IllegalStateException when the expected control is absent.
	 */
	private static Object field(Object owner, String name) {
		try {
			for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
				try {
					var field = type.getDeclaredField(name);
					field.setAccessible(true);
					return field.get(owner);
				} catch (NoSuchFieldException exception) {
					// Controls may be declared on a frame superclass used by a test double.
				}
			}
			throw new NoSuchFieldException(name);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Missing dialog control: " + name, exception);
		}
	}

	/**
	 * Finds a named editor action through nested toolbars and tab content.
	 *
	 * @param component editor root or descendant.
	 * @param text action label.
	 * @return matching button, or null when absent.
	 */
	private static JButton button(Component component, String text) {
		if (component instanceof JButton candidate && text.equals(candidate.getText())) {
			return candidate;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton candidate = button(child, text);
				if (candidate != null) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the standard option pane inside a showing confirmation or warning.
	 *
	 * @param component window or descendant to inspect.
	 * @return embedded option pane, or null when absent.
	 */
	private static JOptionPane optionPane(Component component) {
		if (component instanceof JOptionPane pane) {
			return pane;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JOptionPane pane = optionPane(child);
				if (pane != null) {
					return pane;
				}
			}
		}
		return null;
	}
}
