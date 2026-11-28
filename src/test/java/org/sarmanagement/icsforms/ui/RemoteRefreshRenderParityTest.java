/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/**
 * Oracle for "every change that reaches the database reaches every screen": a window that was open while another
 * device saved a full incident must display exactly what a window opened afterwards displays.
 */
class RemoteRefreshRenderParityTest {
	@TempDir
	Path directory;
	private final List<MainFrame> frames = new ArrayList<>();

	/**
	 * Stops autosave and disposes every window.
	 *
	 * @throws Exception when the event thread cannot be reached
	 */
	@AfterEach
	void tearDown() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (Window window : Window.getWindows()) {
				if (window instanceof MainFrame frame && frame.isDisplayable()) {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
				}
				window.dispose();
			}
		});
	}

	@Test
	void anOpenWindowShowsWhatAFreshWindowShowsAfterAnotherDeviceSavesAFullIncident() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData maple;
		try (FileIncidentStore scratch = new FileIncidentStore(directory.resolve("scratch"))) {
			var id = scratch.importFile(Path.of("docs/examples/maple-ridge-dementia-op1.json")).orElseThrow();
			maple = scratch.load(id);
		}
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore hostStore = host.pair();
			MainFrame open = frame(host, hostStore);
			List<JFrame> openRoles = roles(open);

			RemoteIncidentStore clientStore = host.pair();
			StoredIncident snapshot = clientStore.loadSnapshot(host.incident());
			ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
					.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
			ObjectNode content = (ObjectNode) mapper.valueToTree(maple);
			content.remove("incidentId");
			for (String name : java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(content.fieldNames(), 0), false).toList()) {
				ObjectNode one = mapper.createObjectNode();
				one.set(name, content.get(name));
				StoredIncident current = clientStore.loadSnapshot(host.incident());
				mapper.readerForUpdating(current.data()).readValue(one);
				try {
					clientStore.save(host.incident(), current.data(), current.revision());
				} catch (RuntimeException failure) {
					throw new AssertionError("saving " + name, failure);
				}
			}

			AppController controller = (AppController) field(open, "controller");
			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
			SwingUtilities.invokeAndWait(() -> { });

			RemoteIncidentStore freshStore = host.pair();
			MainFrame fresh = frame(host, freshStore);
			List<JFrame> freshRoles = roles(fresh);

			Map<String, String> expected = new LinkedHashMap<>();
			Map<String, String> actual = new LinkedHashMap<>();
			SwingUtilities.invokeAndWait(() -> {
				capture("main", fresh, expected);
				capture("main", open, actual);
				for (int i = 0; i < freshRoles.size(); i++) {
					capture("role" + i, freshRoles.get(i), expected);
					capture("role" + i, openRoles.get(i), actual);
				}
			});
			List<String> differences = new ArrayList<>();
			for (Map.Entry<String, String> entry : expected.entrySet()) {
				String shown = actual.get(entry.getKey());
				if (!entry.getValue().equals(shown)) {
					differences.add(entry.getKey() + "\n    fresh: " + abbreviate(entry.getValue()) + "\n    open : "
							+ abbreviate(shown));
				}
			}
			assertTrue(differences.isEmpty(), differences.size() + " stale components:\n"
					+ String.join("\n", differences.subList(0, Math.min(60, differences.size()))));
		}
	}

	/**
	 * Opens a connected main window the way the connect flow does.
	 *
	 * @param host fixture sharing the incident
	 * @param store paired store
	 * @return the window
	 * @throws Exception when creation fails
	 */
	private MainFrame frame(HostedIncidentFixture host, RemoteIncidentStore store) throws Exception {
		StoredIncident first = store.loadSnapshot(host.incident());
		AtomicReference<MainFrame> holder = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(first.data(), store, host.incident(), new PdfExportService(),
					new IncidentValidator(), directory);
			holder.set(frame);
		});
		frames.add(holder.get());
		return holder.get();
	}

	/**
	 * Opens every role window of a main window.
	 *
	 * @param frame owning window
	 * @return the role windows
	 * @throws Exception when the event thread cannot be reached
	 */
	private static List<JFrame> roles(MainFrame frame) throws Exception {
		AppController controller = (AppController) field(frame, "controller");
		List<JFrame> result = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			result.add(RolePanelWindows.openIncidentCommand(frame, controller, ignored -> { }));
			result.add(RolePanelWindows.openSafety(frame, controller, ignored -> { }));
			result.add(RolePanelWindows.openMedicalUnitLeader(frame, controller, ignored -> { }));
			result.add(RolePanelWindows.openCommunications(frame, controller, ignored -> { }, () -> { }, () -> { }));
			result.add(RolePanelWindows.openOperations(frame, controller, () -> null, ignored -> { },
					ignored -> { }, ignored -> { }, () -> { }, ignored -> { }, () -> { }, () -> { }));
		});
		return result;
	}

	/**
	 * Records the visible state of every component beneath a window.
	 *
	 * @param prefix label for the window
	 * @param window window to read
	 * @param into destination map
	 */
	private static void capture(String prefix, Window window, Map<String, String> into) {
		walk(prefix, window, into);
	}

	/**
	 * Walks a component tree, recording each component under its structural path.
	 *
	 * @param path path of the component
	 * @param component component to record
	 * @param into destination map
	 */
	private static void walk(String path, Component component, Map<String, String> into) {
		if (component instanceof IncidentClockPanel) {
			return;
		}
		String state = state(component);
		if (state != null) {
			into.put(path + "/" + component.getClass().getSimpleName(), state);
		}
		if (component instanceof Container container) {
			Component[] children = container.getComponents();
			for (int i = 0; i < children.length; i++) {
				walk(path + "/" + component.getClass().getSimpleName() + i, children[i], into);
			}
		}
	}

	/**
	 * Describes the displayed value of a component.
	 *
	 * @param c component to describe
	 * @return displayed state, or null when the component displays nothing of interest
	 */
	private static String state(Component c) {
		if (c instanceof JLabel label) {
			return label.getText() + "|" + label.isVisible();
		}
		if (c instanceof JTextComponent text) {
			return text.getText() + "|" + text.isEnabled() + "|" + text.isEditable();
		}
		if (c instanceof AbstractButton button) {
			return button.getText() + "|" + button.isSelected() + "|" + button.isEnabled() + "|" + button.isVisible();
		}
		if (c instanceof JComboBox<?> combo) {
			return String.valueOf(combo.getSelectedItem()) + "|" + combo.getItemCount();
		}
		if (c instanceof JSpinner spinner) {
			return String.valueOf(spinner.getValue());
		}
		if (c instanceof JTabbedPane tabs) {
			StringBuilder titles = new StringBuilder();
			for (int i = 0; i < tabs.getTabCount(); i++) {
				titles.append(tabs.getTitleAt(i)).append(';');
			}
			return titles.toString();
		}
		if (c instanceof JTable table) {
			StringBuilder cells = new StringBuilder();
			for (int row = 0; row < table.getRowCount(); row++) {
				for (int col = 0; col < table.getColumnCount(); col++) {
					cells.append(table.getValueAt(row, col)).append('\t');
				}
				cells.append('\n');
			}
			return table.getRowCount() + "x" + table.getColumnCount() + "\n" + cells;
		}
		if (c instanceof JList<?> list) {
			StringBuilder items = new StringBuilder();
			for (int i = 0; i < list.getModel().getSize(); i++) {
				items.append(list.getModel().getElementAt(i)).append(';');
			}
			return items.toString();
		}
		return null;
	}

	/**
	 * Shortens long values for failure messages.
	 *
	 * @param value text to shorten
	 * @return at most 160 characters
	 */
	private static String abbreviate(String value) {
		if (value == null) {
			return null;
		}
		String single = value.replace('\n', '↵');
		return single.length() > 160 ? single.substring(0, 160) + "…" : single;
	}

	/**
	 * Reads a declared field reflectively.
	 *
	 * @param target object to read
	 * @param name field name
	 * @return field value
	 */
	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}
}
