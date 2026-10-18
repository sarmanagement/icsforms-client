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
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.concurrent.TimeUnit;
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
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			JFrame main = new JFrame("Main incident");
			JFrame log = new JFrame("Detached ICP log");
			JButton addEntry = new JButton("Add entry");
			JPanel content = new JPanel();
			content.add(new JLabel("Open debrief"));
			Timer click = new Timer(50, null);
			addEntry.addActionListener(event -> {
				Window editor = SwingUtilities.getWindowAncestor(content);
				if (editor != null && editor.isShowing()) {
					clicked.set(true);
					click.stop();
					editor.dispose();
				}
			});
			long[] nextClick = {System.nanoTime()};
			int[] attempts = {0};
			Point[] lastTarget = {null};
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			click.addActionListener(event -> {
				try {
					if (System.nanoTime() - deadline >= 0) {
						click.stop();
						main.dispose();
						return;
					}
					Window editor = SwingUtilities.getWindowAncestor(content);
					if (clicked.get() || editor == null || !editor.isShowing() || !addEntry.isShowing()
							|| !addEntry.isDisplayable() || !addEntry.isEnabled()
							|| addEntry.getWidth() <= 0 || addEntry.getHeight() <= 0) {
						return;
					}
					Point location;
					try {
						location = addEntry.getLocationOnScreen();
					} catch (IllegalComponentStateException exception) {
						return;
					}
					location.translate(addEntry.getWidth() / 2, addEntry.getHeight() / 2);
					if (!log.getGraphicsConfiguration().getBounds().contains(location)
							|| System.nanoTime() - nextClick[0] < 0) {
						return;
					}
					// A desktop may consume the first click to activate the detached window.
					log.toFront();
					log.requestFocus();
					lastTarget[0] = location;
					attempts[0]++;
					robot.mouseMove(location.x, location.y);
					try {
						robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
					} finally {
						robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
					}
					nextClick[0] = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
				} catch (Throwable exception) {
					failure.compareAndSet(null, exception);
					click.stop();
					main.dispose();
				}
			});
			try {
				Rectangle usable = new Rectangle(main.getGraphicsConfiguration().getBounds());
				Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(main.getGraphicsConfiguration());
				usable.x += insets.left;
				usable.y += insets.top;
				usable.width -= insets.left + insets.right;
				usable.height -= insets.top + insets.bottom;
				int width = Math.min(300, usable.width / 2);
				int height = Math.min(200, usable.height);
				main.setBounds(usable.x, usable.y, width, height);
				log.setBounds(usable.x + usable.width - width, usable.y, width, height);
				log.add(addEntry);
				main.setVisible(true);
				log.setVisible(true);
				click.start();
				UiSupport.showResizableConfirmDialog(main, "Debrief", content, null);
				if (failure.get() != null) {
					throw new AssertionError("Native mouse input failed while the editor was open", failure.get());
				}
				assertTrue(clicked.get(), "Detached log must receive real mouse input during a main-window editor"
						+ "; attempts=" + attempts[0] + ", last target=" + lastTarget[0]
						+ ", target bounds=" + addEntry.getBounds());
			} finally {
				click.stop();
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
