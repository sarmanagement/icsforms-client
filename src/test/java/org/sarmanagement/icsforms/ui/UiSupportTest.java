package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JComboBox;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSupportTest {

	@Test
	void formRowsStayTopAlignedWhenPanelIsTallerThanContent() {
		JPanel panel = UiSupport.formPanel();
		UiSupport.addRow(panel, 0, "Field one", UiSupport.textField());
		UiSupport.addRow(panel, 1, "Field two", UiSupport.textField());

		panel.setSize(700, 500);
		panel.doLayout();

		JLabel firstLabel = findLabel(panel, "Field one");
		assertNotNull(firstLabel);
		assertTrue(firstLabel.getY() < 20);
	}

	// -----------------------------------------------------------------------
	// looksLikePhoneNumber
	// -----------------------------------------------------------------------

	@Test
	void phoneNumber10DigitsDashFormatRecognised() {
		assertTrue(AppController.looksLikePhoneNumber("415-555-1234"));
	}

	@Test
	void phoneNumber10DigitsParenFormatRecognised() {
		assertTrue(AppController.looksLikePhoneNumber("(415) 555-1234"));
	}

	@Test
	void phoneNumberInternationalPrefixRecognised() {
		assertTrue(AppController.looksLikePhoneNumber("+1 415 555 1234"));
	}

	@Test
	void phoneNumber7DigitsMinimumRecognised() {
		assertTrue(AppController.looksLikePhoneNumber("555-1234"));
	}

	@Test
	void radioChannelNameRejected() {
		assertFalse(AppController.looksLikePhoneNumber("Tac-1"));
	}

	@Test
	void radioChannelWithLettersRejected() {
		assertFalse(AppController.looksLikePhoneNumber("Ch 5"));
	}

	@Test
	void vhfFrequencyRejected() {
		assertFalse(AppController.looksLikePhoneNumber("155.340"));
	}

	@Test
	void uhfTalkgroupRejected() {
		assertFalse(AppController.looksLikePhoneNumber("UHF-3"));
	}

	@Test
	void blankValueRejected() {
		assertFalse(AppController.looksLikePhoneNumber(""));
		assertFalse(AppController.looksLikePhoneNumber(null));
		assertFalse(AppController.looksLikePhoneNumber("   "));
	}

	@Test
	void sixDigitStringTooShortRejected() {
		assertFalse(AppController.looksLikePhoneNumber("123456"));
	}

	@Test
	void taskLabelIncludesAssignmentNumberResourceLeaderAndAssignmentPreview() {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentTeamNumber("A-12");
		task.setResourceIdentifier("K9-3");
		task.setLeader("Sam Lee");
		task.setAssignment("Search creek drainage from bridge to bend");

		String label = UiSupport.taskLabel(task);

		assertTrue(label.contains("A-12"));
		assertTrue(label.contains("K9-3"));
		assertTrue(label.contains("Sam Lee"));
		assertTrue(label.contains("Search creek drainage"));
	}

	@Test
	void detectingTaskLabelAppendsSpecificResourceName() {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentTeamNumber("T2");
		task.setResourceIdentifier("Ground-1");
		task.setLeader("Alex");
		task.setAssignment("Sweep north flank");

		assertTrue(UiSupport.detectingTaskLabel(task, "Ranger Kim").contains("Ranger Kim"));
	}

	@Test
	void lifecycleToCardStatusTreatsAllAssignedStatesAsAssigned() {
		assertEquals("Assigned", AppController.lifecycleToCardStatus("assigned - enroute to assignment"));
		assertEquals("Assigned", AppController.lifecycleToCardStatus("assigned - on task"));
		assertEquals("Assigned", AppController.lifecycleToCardStatus("assigned - returning from assignment"));
		assertEquals("", AppController.lifecycleToCardStatus("planned"));
		assertEquals(null, AppController.lifecycleToCardStatus("returned"));
	}

	@Test
	void exportFormDialogLabelsIncludeFormNamesAndPluralSarTaskForms() {
		assertEquals("ICS 201 – Incident Briefing", MainFrame.exportFormDisplayLabel("ICS 201"));
		assertEquals("ICS 205A Communications List", MainFrame.exportFormDisplayLabel("ICS 205A"));
		assertEquals("ICS 214 – Activity Log", MainFrame.exportFormDisplayLabel("ICS 214"));
		assertEquals("SAR Task Assignment Forms", MainFrame.exportFormDisplayLabel("SAR Task Assignment"));
	}

	@Test
	void exportFormDialogDefaultsToCombinedIapOutput() {
		MainFrame.ExportModeControls controls = MainFrame.createExportModeControls();

		assertTrue(controls.combined().isSelected());
		assertFalse(controls.individual().isSelected());
		assertEquals(2, controls.panel().getComponentCount());
	}

	@Test
	void pdfLayoutSettingsApplyMarginInchesAndPaperSize() {
		AppData data = new AppData();

		MainFrame.applyPdfLayoutSettings(data, PdfLayoutSettings.PaperSize.A4, 0.5d);

		assertEquals(PdfLayoutSettings.PaperSize.A4, data.getPdfLayoutSettings().getPaperSize());
		assertEquals(36f, data.getPdfLayoutSettings().getPageMarginPoints(), 0.01f);
		assertEquals(0.5d, MainFrame.pointsToInches(data.getPdfLayoutSettings().getPageMarginPoints()));
	}

	@Test
	void directoryViewUsesLargerFontForSmallFilteredResultSets() {
		assertTrue(TCardPanel.useLargeDirectoryFont(1));
		assertTrue(TCardPanel.useLargeDirectoryFont(8));
		assertFalse(TCardPanel.useLargeDirectoryFont(9));
		assertFalse(TCardPanel.useLargeDirectoryFont(0));
	}

	@Test
	void scrollTableToLastRowRequestsLastRowRectangle() throws Exception {
		class TrackingTable extends JTable {
			private Rectangle lastScrolledRect;

			@Override
			public void scrollRectToVisible(Rectangle rectangle) {
				lastScrolledRect = rectangle;
			}
		}

		TrackingTable table = new TrackingTable();
		table.setModel(new javax.swing.table.DefaultTableModel(20, 2));

		UiSupport.scrollTableToLastRow(table);
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(table.getCellRect(table.getRowCount() - 1, 0, true), table.lastScrolledRect);
	}

	@Test
	void humanReadableTimeZoneIncludesIdOffsetAndAbbreviation() {
		String label = MainFrame.humanReadableTimeZone(java.time.ZoneId.of("America/Denver"));

		assertTrue(label.startsWith("America/Denver (UTC-"));
		assertTrue(label.endsWith(")"));
		assertTrue(label.contains("MST") || label.contains("MDT"));
	}

	@Test
	void configureDialogComboBoxAddsKeyboardPopupShortcuts() {
		JComboBox<String> comboBox = new JComboBox<>(new String[] {"One", "Two"});
		comboBox.setEditable(true);

		UiSupport.configureDialogComboBox(comboBox, 220);

		assertNotNull(comboBox.getActionForKeyStroke(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK)));
		assertNotNull(((javax.swing.JComponent) comboBox.getEditor().getEditorComponent()).getActionForKeyStroke(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0)));
	}

	private static JLabel findLabel(Component component, String text) {
		if (component instanceof JLabel label && text.equals(label.getText())) {
			return label;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				JLabel label = findLabel(child, text);
				if (label != null) {
					return label;
				}
			}
		}
		return null;
	}
}
