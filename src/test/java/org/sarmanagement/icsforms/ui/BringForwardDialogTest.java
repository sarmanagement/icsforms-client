/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.LocalRepository;
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
			MainFrame frame = new MainFrame(data,
					new LocalRepository(directory.resolve("incident.json")),
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
			MainFrame frame = new MainFrame(data, new LocalRepository(directory.resolve("availability.json")),
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
			store.create(source);
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
					assertFalse(((JCheckBox) choices.get(PlanReviewArea.COMMUNICATIONS)).isEnabled());
					JButton apply = (JButton) field(dialog, "apply");
					assertFalse(apply.isEnabled());
					human.doClick();
					assertTrue(apply.isEnabled());
					assertSame(target, controller.getData());
					assertEquals(id, controller.getActiveIncidentId());
					assertEquals("", target.getMedicalPlan().getEmergencyProcedures());
				} finally {
					dialog.dispose();
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
					JCheckBox promote = (JCheckBox) field(startup, "promotePlannedIcs215");
					assertEquals("Promote planned ICS 215 target period when advancing", promote.getText());
					assertFalse(promote.isSelected());
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
