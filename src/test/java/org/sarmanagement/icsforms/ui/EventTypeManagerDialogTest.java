/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityEventType;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Covers event-type action placement, selection restrictions, and custom quick-menu creation. */
class EventTypeManagerDialogTest {

	@Test
	void managementActionsRemainBelowScrollableTableAndRemovalNeedsCustomSelection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			ActivityEventType builtIn = new ActivityEventType("BUILT_IN", "Built in", true);
			ActivityEventType custom = new ActivityEventType("CUSTOM", "Custom", false);
			Ics214Panel.EventTypeManagerDialog manager =
					new Ics214Panel.EventTypeManagerDialog(List.of(builtIn, custom));
			JPanel content = manager.contentPanel();
			BorderLayout layout = (BorderLayout) content.getLayout();
			assertTrue(layout.getLayoutComponent(BorderLayout.CENTER) instanceof JScrollPane);
			assertSame(manager.actionPanel, layout.getLayoutComponent(BorderLayout.SOUTH));
			assertFalse(manager.panel.isAncestorOf(manager.actionPanel));

			JTable table = descendants(manager.panel, JTable.class).get(0);
			JButton remove = (JButton) manager.actionPanel.getComponent(1);
			assertFalse(remove.isEnabled());
			table.setRowSelectionInterval(0, 0);
			assertFalse(remove.isEnabled());
			table.setRowSelectionInterval(1, 1);
			assertTrue(remove.isEnabled());
		});
	}

	@Test
	void customTypeDialogExplainsFormatAndPersistsQuickMenuChoice() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Custom event dialog requires Xvfb");
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicReference<Ics214Panel.EventTypeManagerDialog> managerRef = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			Ics214Panel.EventTypeManagerDialog manager =
					new Ics214Panel.EventTypeManagerDialog(ActivityEventType.defaultTypes());
			managerRef.set(manager);
			Timer timer = new Timer(40, event -> {
				for (Window window : Window.getWindows()) {
					if (!(window instanceof JDialog dialog) || !dialog.isShowing()
							|| !"New Event Type".equals(dialog.getTitle())) {
						continue;
					}
					try {
						List<JTextField> fields = descendants(dialog, JTextField.class);
						fields.get(0).setText("FIELD_UPDATE");
						fields.get(1).setText("Field Update");
						JCheckBox quickMenu = descendants(dialog, JCheckBox.class).stream()
								.filter(box -> "Show in quick menu".equals(box.getText())).findFirst().orElseThrow();
						assertFalse(quickMenu.isSelected());
						quickMenu.setSelected(true);
						assertTrue(descendants(dialog, JTextArea.class).stream()
								.anyMatch(area -> area.getText().contains("FIELD_UPDATE")
										&& area.getText().contains("quick-log")));
						JButton confirm = descendants(dialog, JButton.class).stream()
								.filter(button -> "OK".equals(button.getText())).findFirst().orElseThrow();
						confirm.doClick();
					} catch (Throwable exception) {
						failure.set(exception);
						dialog.dispose();
					}
					return;
				}
			});
			timer.setRepeats(true);
			timer.start();
			try {
				((JButton) manager.actionPanel.getComponent(0)).doClick();
			} finally {
				timer.stop();
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog dialog && "New Event Type".equals(dialog.getTitle())) {
						dialog.dispose();
					}
				}
			}
		});
		if (failure.get() != null) {
			throw new AssertionError("Custom event type dialog interaction failed", failure.get());
		}
		ActivityEventType created = managerRef.get().getEventTypes().stream()
				.filter(type -> "FIELD_UPDATE".equals(type.getId())).findFirst().orElse(null);
		assertNotNull(created);
		assertFalse(created.isBuiltIn());
		assertTrue(created.isShowInQuickMenu());
	}

	/**
	 * Collects descendants of a Swing type.
	 *
	 * @param root component tree root.
	 * @param type requested component type.
	 * @param <T> component type.
	 * @return matching descendants.
	 */
	private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
		java.util.ArrayList<T> matches = new java.util.ArrayList<>();
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				matches.add(type.cast(child));
			}
			if (child instanceof Container container) {
				matches.addAll(descendants(container, type));
			}
		}
		return matches;
	}
}
