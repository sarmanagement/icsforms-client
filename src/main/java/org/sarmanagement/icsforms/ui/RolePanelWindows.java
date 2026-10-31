/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.JFrame;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Opens and tracks local responsibility-focused role windows.
 */
final class RolePanelWindows {
	private static final Map<AppData, JFrame> SAFETY_WINDOWS = new IdentityHashMap<>();
	private static final Map<AppData, JFrame> MEDICAL_WINDOWS = new IdentityHashMap<>();
	private static final Map<AppData, JFrame> OPERATIONS_WINDOWS = new IdentityHashMap<>();
	private static final Map<AppData, JFrame> INCIDENT_COMMAND_WINDOWS = new IdentityHashMap<>();
	private static final Map<AppData, JFrame> COMMUNICATIONS_WINDOWS = new IdentityHashMap<>();

	/** Prevents utility construction. */
	private RolePanelWindows() {
	}

	/**
	 * Opens or focuses the Safety Officer panel.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param navigator typed workspace navigator.
	 * @return role window.
	 */
	static JFrame openSafety(Window owner, AppController controller, Consumer<WorkspaceDestination> navigator) {
		AppData incident = controller.getData();
		JFrame existing = SAFETY_WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		SafetyRolePanel panel = new SafetyRolePanel(controller, navigator);
		JFrame frame = createFrame("Safety Officer — " + incident.getIncidentContext().getIncidentName(), panel,
				ApplicationIcon.Variant.SAFETY);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				SAFETY_WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident);
		SAFETY_WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * Opens or focuses the Medical Unit Leader panel.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param navigator typed workspace navigator.
	 * @return role window.
	 */
	static JFrame openMedicalUnitLeader(Window owner, AppController controller,
			Consumer<WorkspaceDestination> navigator) {
		AppData incident = controller.getData();
		JFrame existing = MEDICAL_WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		MedicalUnitLeaderRolePanel panel = new MedicalUnitLeaderRolePanel(controller, navigator);
		JFrame frame = createFrame("Medical Unit Leader — " + incident.getIncidentContext().getIncidentName(), panel,
				ApplicationIcon.Variant.MEDICAL);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				MEDICAL_WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident);
		MEDICAL_WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * Opens or focuses the Operations panel.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param newAssignment established assignment workflow.
	 * @param editTask established task edit workflow.
	 * @param changeStatus established lifecycle workflow.
	 * @param addLog established activity-entry workflow.
	 * @param recordClue established clue workflow.
	 * @param debrief established debrief workflow.
	 * @param humanReference human reference action.
	 * @param canineReference canine reference action.
	 * @return role window.
	 */
	static JFrame openOperations(Window owner, AppController controller,
			java.util.function.Supplier<ResourceAssignment> newAssignment, Consumer<String> editTask,
			Consumer<String> changeStatus, Consumer<SarTaskAssignment> addLog, Runnable recordClue,
			Consumer<String> debrief, Runnable humanReference, Runnable canineReference) {
		return openOperations(owner, controller, newAssignment, editTask, changeStatus, addLog, recordClue,
				debrief, humanReference, canineReference, () -> {
				});
	}

	/**
	 * Opens or focuses the Operations panel and delegates planning to the shared ICS 215 editor.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param newAssignment established assignment workflow.
	 * @param editTask established task edit workflow.
	 * @param changeStatus established lifecycle workflow.
	 * @param addLog established activity-entry workflow.
	 * @param recordClue established clue workflow.
	 * @param debrief established debrief workflow.
	 * @param humanReference human reference action.
	 * @param canineReference canine reference action.
	 * @param openOperationalPlanning opens the shared ICS 215 editor.
	 * @return role window.
	 */
	static JFrame openOperations(Window owner, AppController controller,
			java.util.function.Supplier<ResourceAssignment> newAssignment, Consumer<String> editTask,
			Consumer<String> changeStatus, Consumer<SarTaskAssignment> addLog, Runnable recordClue,
			Consumer<String> debrief, Runnable humanReference, Runnable canineReference,
			Runnable openOperationalPlanning) {
		AppData incident = controller.getData();
		JFrame existing = OPERATIONS_WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		OperationsRolePanel panel = new OperationsRolePanel(controller, newAssignment, editTask, changeStatus, addLog,
				recordClue, debrief, humanReference, canineReference, openOperationalPlanning);
		JFrame frame = createFrame("Operations — " + incident.getIncidentContext().getIncidentName(), panel,
				ApplicationIcon.Variant.OPERATIONS);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				OPERATIONS_WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident);
		OPERATIONS_WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * Opens or focuses the Incident Command panel.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param navigator typed workspace navigator.
	 * @return role window.
	 */
	static JFrame openIncidentCommand(Window owner, AppController controller,
			Consumer<WorkspaceDestination> navigator) {
		AppData incident = controller.getData();
		JFrame existing = INCIDENT_COMMAND_WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		IncidentCommandRolePanel panel = new IncidentCommandRolePanel(controller, navigator);
		JFrame frame = createFrame("Incident Command — " + incident.getIncidentContext().getIncidentName(), panel,
				ApplicationIcon.Variant.INCIDENT_COMMAND);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				INCIDENT_COMMAND_WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident);
		INCIDENT_COMMAND_WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * Opens or focuses the Communications Unit panel.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param navigator typed workspace navigator.
	 * @param importCsv established communications import workflow.
	 * @param exportCsv established communications export workflow.
	 * @return role window.
	 */
	static JFrame openCommunications(Window owner, AppController controller,
			Consumer<WorkspaceDestination> navigator, Runnable importCsv, Runnable exportCsv) {
		AppData incident = controller.getData();
		JFrame existing = COMMUNICATIONS_WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		CommunicationsRolePanel panel = new CommunicationsRolePanel(controller, navigator, importCsv, exportCsv);
		JFrame frame = createFrame("Communications Unit — " + incident.getIncidentContext().getIncidentName(), panel,
				ApplicationIcon.Variant.COMMUNICATIONS);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				COMMUNICATIONS_WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident);
		COMMUNICATIONS_WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * Creates a standard resizable role frame.
	 *
	 * @param title frame title.
	 * @param panel frame content.
	 * @param variant responsibility-specific icon theme.
	 * @return configured frame.
	 */
	private static JFrame createFrame(String title, javax.swing.JPanel panel, ApplicationIcon.Variant variant) {
		JFrame frame = new JFrame(title);
		ApplicationIcon.install(frame, variant);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setContentPane(panel);
		frame.setSize(1220, 820);
		frame.setMinimumSize(new java.awt.Dimension(860, 620));
		frame.setLocationByPlatform(true);
		return frame;
	}
}
