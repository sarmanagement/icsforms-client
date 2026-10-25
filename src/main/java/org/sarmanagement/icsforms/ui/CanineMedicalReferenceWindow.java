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
 * Dedicated non-modal canine medical reference window.
 */
public final class CanineMedicalReferenceWindow {
	private static final Map<AppData, JFrame> WINDOWS = new IdentityHashMap<>();

	/** Prevents utility construction. */
	private CanineMedicalReferenceWindow() {
	}

	/**
	 * Opens or focuses the canine-only reference window.
	 *
	 * @param owner parent window.
	 * @param controller incident controller.
	 * @param navigator typed workspace navigation callback.
	 * @return opened or focused frame.
	 */
	public static JFrame open(Window owner, AppController controller, Consumer<WorkspaceDestination> navigator) {
		AppData incident = controller.getData();
		JFrame existing = WINDOWS.get(incident);
		if (existing != null && existing.isDisplayable()) {
			existing.toFront();
			return existing;
		}
		MedicalReferencePanel panel = new MedicalReferencePanel(controller, true, navigator);
		JFrame frame = new JFrame(panel.referenceTitle());
		ApplicationIcon.install(frame);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setContentPane(panel);
		frame.setSize(1260, 820);
		frame.setLocationByPlatform(true);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosed(java.awt.event.WindowEvent event) {
				panel.dispose();
				WINDOWS.remove(incident);
			}
		});
		UiSupport.registerIncidentWindow(frame, incident, false);
		WINDOWS.put(incident, frame);
		frame.setVisible(true);
		return frame;
	}
}
