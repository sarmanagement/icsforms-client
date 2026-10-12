// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies table and nested rack-card actions target the requested resource. */
class TCardPanelContextTest {
	@TempDir
	Path directory;

	@Test
	void nestedAssignmentPickerIsDocumentModalAndOwnedByInitiatingEditor() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		TCard resource = card("Robin");
		TCardPanel panel = panel(List.of(resource));
		AtomicReference<Throwable> failure = new AtomicReference<>();
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			javax.swing.JFrame root = new javax.swing.JFrame("Independent resource document");
			root.add(panel);
			root.setSize(600, 500);
			AtomicReference<javax.swing.JDialog> editor = new AtomicReference<>();
			java.util.concurrent.atomic.AtomicInteger stage = new java.util.concurrent.atomic.AtomicInteger();
			long started = System.currentTimeMillis();
			javax.swing.Timer timer = new javax.swing.Timer(100, null);
			timer.setCoalesce(false);
			timer.addActionListener(event -> {
				try {
					if (System.currentTimeMillis() - started > 10000) {
						throw new AssertionError("Nested editor did not open");
					}
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (!(window instanceof javax.swing.JDialog dialog) || !dialog.isVisible()) {
							continue;
						}
						if (stage.get() == 0 && dialog.getTitle().equals("Edit T-Card")) {
							editor.set(dialog);
							assertSame(root, dialog.getOwner());
							assertEquals(java.awt.Dialog.ModalityType.DOCUMENT_MODAL, dialog.getModalityType());
							stage.set(1);
							findButton(dialog, "Add to Assignment…").doClick();
							return;
						}
						if (stage.get() == 1 && dialog.getTitle().equals("Add to Assignment")) {
							assertSame(editor.get(), dialog.getOwner());
							assertEquals(java.awt.Dialog.ModalityType.DOCUMENT_MODAL, dialog.getModalityType());
							stage.set(2);
							dialog.dispose();
							editor.get().dispose();
							timer.stop();
							return;
						}
					}
				} catch (Throwable exception) {
					failure.set(exception);
					timer.stop();
					root.dispose();
				}
			});
			try {
				root.setVisible(true);
				timer.start();
				Method method = TCardPanel.class.getDeclaredMethod("openEditDialog", TCard.class, Component.class);
				method.setAccessible(true);
				method.invoke(panel, resource, root);
				assertEquals(2, stage.get());
			} catch (Throwable exception) {
				failure.compareAndSet(null, exception);
			} finally {
				timer.stop();
				root.dispose();
			}
		});
		if (failure.get() != null) {
			throw new AssertionError("Nested assignment picker ownership failed", failure.get());
		}
	}

	@Test
	void sortedTableLoggingTargetsSelectedModelCard() throws Exception {
		TCard zulu = card("Zulu");
		TCard alpha = card("Alpha");
		TCardPanel panel = panel(List.of(zulu, alpha));
		AtomicReference<LogEntryContext> requested = new AtomicReference<>();
		panel.setOnAddLogEntryRequest(requested::set);
		JTable table = (JTable) field(panel, "table");
		table.setAutoCreateRowSorter(true);
		table.getRowSorter().toggleSortOrder(1);
		table.setRowSelectionInterval(0, 0);
		Method selected = TCardPanel.class.getDeclaredMethod("selectedCard");
		selected.setAccessible(true);
		TCard expected = (TCard) selected.invoke(panel);
		for (Component item : table.getComponentPopupMenu().getComponents()) {
			if (item instanceof JMenuItem action && action.getText().equals("Add log entry")) {
				action.doClick();
			}
		}
		assertSame(table, requested.get().source());
		assertEquals(expected.getPersonName(), requested.get().resourceId());
	}

	/**
	 * Finds an action in a nested Swing component tree.
	 *
	 * @param component current component
	 * @param text action label
	 * @return matching button, or null when absent
	 */
	private static javax.swing.JButton findButton(Component component, String text) {
		if (component instanceof javax.swing.JButton button && text.equals(button.getText())) {
			return button;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				javax.swing.JButton button = findButton(child, text);
				if (button != null) {
					return button;
				}
			}
		}
		return null;
	}

	@Test
	void rackChildPopupKeepsItsOwnResourceInsteadOfPreviousSelection() throws Exception {
		TCard resource = card("Robin");
		TCardPanel panel = panel(List.of(resource));
		AtomicReference<LogEntryContext> requested = new AtomicReference<>();
		panel.setOnAddLogEntryRequest(requested::set);
		Method build = TCardPanel.class.getDeclaredMethod("buildRackCardWidget", TCard.class, boolean.class, boolean.class);
		build.setAccessible(true);
		JPanel rack = (JPanel) build.invoke(panel, resource, false, false);
		JComponent child = (JComponent) rack.getComponent(0);
		assertTrue(child.getInheritsPopupMenu());
		assertSame(rack.getComponentPopupMenu(), child.getComponentPopupMenu());
		for (Component item : child.getComponentPopupMenu().getComponents()) {
			if (item instanceof JMenuItem action && action.getText().equals("Add log entry")) {
				action.doClick();
			}
		}
		assertEquals("Robin", requested.get().resourceId());
		assertSame(rack, requested.get().source());
	}

	@Test
	void editDialogHasInlineAddLogEntryButton() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		TCard resource = card("Robin");
		TCardPanel panel = panel(List.of(resource));
		AtomicReference<LogEntryContext> requested = new AtomicReference<>();
		panel.setOnAddLogEntryRequest(requested::set);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicBoolean buttonFound = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			javax.swing.JFrame root = new javax.swing.JFrame("Test Frame");
			root.add(panel);
			root.setSize(600, 500);
			root.setVisible(true);
			javax.swing.Timer timer = new javax.swing.Timer(100, event -> {
				try {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()
								&& "Edit T-Card".equals(dialog.getTitle())) {
							javax.swing.JButton addLogBtn = findButton(dialog, "Add log entry…");
							if (addLogBtn != null) {
								buttonFound.set(true);
								addLogBtn.doClick();
								dialog.dispose();
								return;
							}
						}
					}
				} catch (Throwable ex) {
					failure.set(ex);
				}
			});
			timer.setRepeats(true);
			timer.start();
			try {
				Method method = TCardPanel.class.getDeclaredMethod("openEditDialog", TCard.class, Component.class);
				method.setAccessible(true);
				method.invoke(panel, resource, root);
			} catch (Throwable ex) {
				failure.compareAndSet(null, ex);
			} finally {
				timer.stop();
				root.dispose();
			}
		});
		assertNull(failure.get());
		assertTrue(buttonFound.get(), "Add log entry… button should be found");
		assertNotNull(requested.get());
		assertEquals("Robin", requested.get().resourceId());
	}

	/**
	 * Builds a resource panel fixture.
	 *
	 * @param cards incident resources
	 * @return loaded panel
	 */
	private TCardPanel panel(List<TCard> cards) {
		AppData data = new AppData();
		data.setTCards(cards);
		AppController controller = new AppController(data, new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
		TCardPanel panel = new TCardPanel(controller);
		panel.refreshFromModel();
		return panel;
	}

	/**
	 * Creates a manual personnel card.
	 *
	 * @param name resource name
	 * @return card fixture
	 */
	private static TCard card(String name) {
		TCard card = new TCard();
		card.setPersonName(name);
		return card;
	}

	/**
	 * Retrieves a private Swing fixture.
	 *
	 * @param object fixture object
	 * @param name field name
	 * @return field value
	 * @throws ReflectiveOperationException if fixture access fails
	 */
	private static Object field(Object object, String name) throws ReflectiveOperationException {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}
}
