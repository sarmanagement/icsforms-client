/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;

import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Desktop regressions for document-scoped editors and independent log roots. */
class UiSupportModalityTest {

	@Test
	void operationalDialogBelongsOnlyToItsInitiatingDocument() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AtomicReference<Dialog.ModalityType> modality = new AtomicReference<>();
		AtomicReference<Window> root = new AtomicReference<>();
		AtomicBoolean outstandingDialog = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			JFrame main = new JFrame("Main incident");
			JFrame log = new JFrame("Detached ICP log");
			main.setSize(300, 200);
			log.setSize(300, 200);
			main.setVisible(true);
			log.setVisible(true);
			JPanel content = new JPanel();
			content.add(new JLabel("Pending assignment edits"));
			Timer dismiss = new Timer(100, event -> {
				Window window = SwingUtilities.getWindowAncestor(content);
				if (window instanceof JDialog dialog) {
					modality.set(dialog.getModalityType());
					root.set(UiSupport.documentRoot(dialog));
					outstandingDialog.set(UiSupport.outstandingIncidentWindows(main, new AppData()).contains(dialog));
					((JOptionPane) SwingUtilities.getAncestorOfClass(JOptionPane.class, content))
							.setValue(JOptionPane.CANCEL_OPTION);
				}
			});
			dismiss.setRepeats(false);
			dismiss.start();
			try {
				assertFalse(UiSupport.showResizableConfirmDialog(main, "Assignment editor", content, null));
				assertEquals(Dialog.ModalityType.DOCUMENT_MODAL, modality.get());
				assertSame(main, root.get());
				assertTrue(outstandingDialog.get());
				assertSame(log, UiSupport.documentRoot(log));
			} finally {
				dismiss.stop();
				main.dispose();
				log.dispose();
			}
		});
	}

	@Test
	void detachedLogReceivesMouseInputWhileMainEditorIsOpen() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		java.awt.Robot robot = new java.awt.Robot();
		AtomicBoolean clicked = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			JFrame main = new JFrame("Main incident");
			JFrame log = new JFrame("Detached ICP log");
			main.setBounds(0, 0, 300, 200);
			log.setBounds(500, 0, 300, 200);
			JButton addEntry = new JButton("Add entry");
			addEntry.addActionListener(event -> clicked.set(true));
			log.add(addEntry);
			main.setVisible(true);
			log.setVisible(true);
			JPanel content = new JPanel();
			content.add(new JLabel("Open debrief"));
			Timer click = new Timer(250, event -> {
				java.awt.Point location = addEntry.getLocationOnScreen();
				robot.mouseMove(location.x + addEntry.getWidth() / 2, location.y + addEntry.getHeight() / 2);
				robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
				robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
			});
			click.setRepeats(false);
			Timer dismiss = new Timer(1000, event -> {
				((JOptionPane) SwingUtilities.getAncestorOfClass(JOptionPane.class, content))
						.setValue(JOptionPane.CANCEL_OPTION);
			});
			dismiss.setRepeats(false);
			click.start();
			dismiss.start();
			try {
				UiSupport.showResizableConfirmDialog(main, "Debrief", content, null);
				assertTrue(clicked.get(), "Detached log must receive real mouse input during a main-window editor");
			} finally {
				click.stop();
				dismiss.stop();
				main.dispose();
				log.dispose();
			}
		});
	}

	@Test
	void incidentSwitchTracksAndDisposesIndependentLogRoots() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			JFrame main = new JFrame();
			JFrame log = new JFrame();
			AppData active = new AppData();
			AppData other = new AppData();
			main.setSize(300, 200);
			log.setSize(300, 200);
			main.setVisible(true);
			log.setVisible(true);
			try {
				UiSupport.registerIncidentWindow(log, active);
				assertTrue(UiSupport.outstandingIncidentWindows(main, active).contains(log));
				assertTrue(UiSupport.outstandingIncidentWindows(main, other).isEmpty());
				UiSupport.disposeIncidentWindows(other);
				assertTrue(log.isShowing());
				UiSupport.disposeIncidentWindows(active);
				assertFalse(log.isDisplayable());
			} finally {
				main.dispose();
				log.dispose();
			}
		});
	}
}
