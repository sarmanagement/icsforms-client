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
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies stable Communications links, explicit checks, and stale-edit safety. */
class CommunicationsRolePanelTest {
	@TempDir
	Path directory;

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

	private CommunicationsRolePanel createPanel(AppController controller) {
		return new CommunicationsRolePanel(controller, ignored -> {
		}, () -> {
		}, () -> {
		});
	}

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

	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}
}
