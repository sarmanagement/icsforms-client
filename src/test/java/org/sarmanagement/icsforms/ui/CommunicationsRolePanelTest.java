/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies stable Communications links, explicit checks, and stale-edit safety. */
class CommunicationsRolePanelTest {
	@TempDir
	Path directory;

	@Test
	void headerKeepsBannerBadgesAndClockAboveRightAlignedActions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			CommunicationsRolePanel panel = createPanel(controller(new AppData()));
			try {
				JPanel header = (JPanel) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
				BorderLayout layout = (BorderLayout) header.getLayout();
				assertTrue(layout.getLayoutComponent(BorderLayout.CENTER) instanceof JLabel);
				assertNull(layout.getLayoutComponent(BorderLayout.NORTH));
				JPanel status = (JPanel) layout.getLayoutComponent(BorderLayout.EAST);
				BorderLayout statusLayout = (BorderLayout) status.getLayout();
				assertTrue(statusLayout.getLayoutComponent(BorderLayout.CENTER) instanceof RolePlanStatusBadges);
				assertTrue(statusLayout.getLayoutComponent(BorderLayout.EAST) instanceof IncidentClockPanel);
				JPanel actions = (JPanel) layout.getLayoutComponent(BorderLayout.SOUTH);
				assertEquals(FlowLayout.TRAILING, ((FlowLayout) actions.getLayout()).getAlignment());
				for (String label : List.of("Console View", "Open full ICS 205", "Import CSV…",
						"Export CSV…", "Add ICP 214 log entry…")) {
					assertSame(actions, findButton(panel, label).getParent());
				}
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void displayingStableLinksHasNoReadSideEffectsAndRetainsSelection() throws Exception {
		AppData data = new AppData();
		RadioChannel primary = new RadioChannel();
		primary.setChannelName("Command");
		RadioChannel secondary = new RadioChannel();
		secondary.setChannelName("Tactical");
		data.getForm205().setChannels(List.of(primary, secondary));
		TCard card = new TCard();
		card.setPersonName("Responder");
		card.setRadioChannel("Legacy command");
		card.setRadioChannelId(primary.getChannelId());
		data.getTCards().add(card);
		AppController controller = controller(data);
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		JsonNode before = mapper.valueToTree(data);
		CommunicationsRolePanel[] panel = new CommunicationsRolePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = createPanel(controller));

		assertEquals(before, mapper.valueToTree(data));
		assertEquals(primary.getChannelId(), panel[0].selectedChannelId());
		assertTrue(panel[0].displayedLinks().stream().anyMatch(link -> link.contains("TCARD")
				&& link.contains("Responder")));
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertEquals(primary.getChannelId(), panel[0].selectedChannelId());
		assertEquals(RadioCheckStatus.NOT_RECORDED, primary.getRadioCheckStatus());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void radioCheckIsExplicitAndStaleFocusedSaveIsRejected() {
		AppData data = new AppData();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		channel.setRxFrequency("155.100");
		data.getForm205().getChannels().add(channel);
		AppController controller = controller(data);
		RadioChannel baseline = channel.deepCopy();
		LocalDateTime checkedAt = LocalDateTime.parse("2026-10-25T05:00");

		controller.updateRadioChannelOperations(channel.getChannelId(), baseline, "Ridge coverage",
				"Relay through Base", RadioCheckStatus.SUCCESSFUL, "COML", checkedAt, "Clear");

		assertEquals(RadioCheckStatus.SUCCESSFUL, channel.getRadioCheckStatus());
		assertEquals(checkedAt, channel.getRadioCheckedAt());
		assertEquals("Ridge coverage", channel.getCoverageGuidance());
		assertThrows(IllegalStateException.class,
				() -> controller.updateRadioChannelOperations(channel.getChannelId(), baseline, "Changed", "",
						RadioCheckStatus.NOT_RECORDED, "", null, ""));
	}

	@Test
	void recordedCheckRequiresHumanAssertionDetails() {
		AppData data = new AppData();
		RadioChannel channel = new RadioChannel();
		data.getForm205().getChannels().add(channel);
		AppController controller = controller(data);

		assertThrows(IllegalArgumentException.class,
				() -> controller.updateRadioChannelOperations(channel.getChannelId(), channel.deepCopy(), "", "",
						RadioCheckStatus.SUCCESSFUL, "", null, ""));
		assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
	}

	@Test
	void communicationsPanelExposesExplicitIcpLogEntryAction() throws Exception {
		AppController controller = controller(new AppData());
		CommunicationsRolePanel[] panel = new CommunicationsRolePanel[1];
		boolean[] requested = {false};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new CommunicationsRolePanel(controller, ignored -> { }, () -> { }, () -> { },
					() -> requested[0] = true);
			findButton(panel[0], "Add ICP 214 log entry…").doClick();
			panel[0].dispose();
		});

		assertTrue(requested[0]);
	}

	@Test
	void switchingPlanAndConsoleKeepsSameWorkbenchSelectionFilterAndUnsavedInput() throws Exception {
		AppData data = new AppData();
		RadioChannel first = new RadioChannel();
		first.setChannelName("Command");
		RadioChannel second = new RadioChannel();
		second.setChannelName("Tactical");
		data.getForm205().getChannels().addAll(List.of(first, second));
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			CommunicationsRolePanel panel = createPanel(controller);
			JTable table = findAccessible(panel, JTable.class, "Sortable communications channels");
			table.setRowSelectionInterval(1, 1);
			JTextArea coverage = findAccessible(panel, JTextArea.class, "Coverage guidance");
			coverage.setText("Unsaved workbench guidance");
			JTextField filter = findAccessible(panel, JTextField.class, "Filter communications channels");
			filter.setText("Tactical");
			findButton(panel, "Console View").doClick();
			assertEquals(second.getChannelId(), panel.selectedChannelId());
			controller.markDirty();
			panel.refreshFromModel();
			findButton(panel, "Plan View").doClick();
			assertTrue(coverage == findAccessible(panel, JTextArea.class, "Coverage guidance"));
			assertTrue(table == findAccessible(panel, JTable.class, "Sortable communications channels"));
			assertEquals("Unsaved workbench guidance", coverage.getText());
			assertEquals("Tactical", filter.getText());
			assertEquals(second.getChannelId(), panel.selectedChannelId());
			assertEquals("", second.getCoverageGuidance());
			panel.dispose();
		});
	}

	/**
	 * Finds a component by its accessible name across both retained cards.
	 *
	 * @param parent component tree.
	 * @param type expected component type.
	 * @param name accessible name.
	 * @param <T> component type.
	 * @return matching component.
	 */
	private static <T extends Component> T findAccessible(Container parent, Class<T> type, String name) {
		for (Component child : parent.getComponents()) {
			if (type.isInstance(child) && child.getAccessibleContext() != null
					&& name.equals(child.getAccessibleContext().getAccessibleName())) {
				return type.cast(child);
			}
			if (child instanceof Container nested) {
				try {
					return findAccessible(nested, type, name);
				} catch (IllegalArgumentException ignored) {
					// Continue searching sibling containers.
				}
			}
		}
		throw new IllegalArgumentException("Accessible component not found: " + name);
	}

	/**
	 * Creates a communications workbench with inert workspace callbacks.
	 *
	 * @param controller test incident controller.
	 * @return communications panel.
	 */
	private CommunicationsRolePanel createPanel(AppController controller) {
		return new CommunicationsRolePanel(controller, ignored -> {
		}, () -> {
		}, () -> {
		});
	}

	/**
	 * Finds a button in both retained communications views.
	 *
	 * @param container component tree.
	 * @param text visible button label.
	 * @return matching button.
	 */
	private static JButton findButton(Container container, String text) {
		for (Component child : container.getComponents()) {
			if (child instanceof JButton button && text.equals(button.getText())) {
				return button;
			}
			if (child instanceof Container nested) {
				try {
					return findButton(nested, text);
				} catch (IllegalArgumentException ignored) {
					// Continue searching sibling containers.
				}
			}
		}
		throw new IllegalArgumentException("Button not found: " + text);
	}

	/**
	 * Creates a controller with project-local test persistence.
	 *
	 * @param data test incident.
	 * @return test controller.
	 */
	private AppController controller(AppData data) {
		return IncidentStoreTestFixtures.controller(data);
	}
}
