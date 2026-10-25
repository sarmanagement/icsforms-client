/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;

import javax.swing.JFrame;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Opens and tracks the local Safety and Medical Unit Leader role windows.
 */
final class RolePanelWindows {
	private static final Map<AppData, JFrame> SAFETY_WINDOWS = new IdentityHashMap<>();
	private static final Map<AppData, JFrame> MEDICAL_WINDOWS = new IdentityHashMap<>();

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
		JFrame frame = createFrame("Safety Officer — " + incident.getIncidentContext().getIncidentName(), panel);
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
		JFrame frame = createFrame("Medical Unit Leader — " + incident.getIncidentContext().getIncidentName(), panel);
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
	 * Creates a standard resizable role frame.
	 *
	 * @param title frame title.
	 * @param panel frame content.
	 * @return configured frame.
	 */
	private static JFrame createFrame(String title, javax.swing.JPanel panel) {
		JFrame frame = new JFrame(title);
		ApplicationIcon.install(frame);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setContentPane(panel);
		frame.setSize(1220, 820);
		frame.setMinimumSize(new java.awt.Dimension(860, 620));
		frame.setLocationByPlatform(true);
		return frame;
	}
}
