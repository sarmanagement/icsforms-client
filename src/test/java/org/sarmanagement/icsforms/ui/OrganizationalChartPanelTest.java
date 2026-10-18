/*
 * Copyright (C) 2026 SAR Management
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/>.
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Workflow coverage for main-tab representatives and supplemental organization assignments. */
class OrganizationalChartPanelTest {
	@Test
	void medicalUnitLeaderIsAvailableBesideFoodAndSynchronizesMedicalPlans() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				AppController controller = sampleController();
				OrganizationalChartPanel panel = new OrganizationalChartPanel(controller);
				JTextField medicalLeader = (JTextField) field(panel, "medicalUnitLeaderNameField");
				medicalLeader.setText("Fictional Medical Unit Leader");
				panel.pushToModel();

				assertTrue(medicalLeader.getClientProperty("uiSupport.namePicker.open")
						instanceof java.util.function.Consumer<?>);
				assertEquals("Fictional Medical Unit Leader",
						controller.getData().getOrganizationalChart().getMedicalUnitLeaderName());
				assertEquals("Fictional Medical Unit Leader",
						controller.getData().getMedicalPlan().getDesignatedMedicalUnitName());
				assertEquals("Fictional Medical Unit Leader",
						controller.getData().getCanineMedicalPlan().getPreparedByMedicalUnitLeader());
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void mainPanelRepresentativesAndContactsSurviveFileStoreSaveAndReload() throws Exception {
		Path directory = Files.createDirectories(Path.of("target"));
		Path workspace = Files.createTempDirectory(directory, "org-chart-store-");
		try (FileIncidentStore store = new FileIncidentStore(workspace.resolve("store"))) {
			IncidentId id = store.create(new AppData());
			AppController controller = new AppController(store.load(id), store, id,
					new PdfExportService(), new IncidentValidator());
			SwingUtilities.invokeAndWait(() -> {
				try {
					var panel = new OrganizationalChartPanel(controller);
					panel.refreshFromModel();
					var agencies = (OrganizationalChartPanel.EntryTable) field(panel, "agencyRepresentatives");
					JTable table = (JTable) field(agencies, "table");
					button(agencies, "Add").doClick();
					table.setValueAt("Free-text Partner Agency", 0, 0);
					table.setValueAt("Free-text Representative", 0, 1);
					table.setValueAt("Partner Radio 4", 0, 2);
					assertTrue(table.editCellAt(0, 3));
					((JTextField) table.getEditorComponent()).setText("555-0134");
					panel.pushToModel();
					store.save(id, controller.getData());
					AppData reloaded = store.load(id);
					AppController reloadedController = new AppController(reloaded, store, id,
							new PdfExportService(), new IncidentValidator());
					var reloadedPanel = new OrganizationalChartPanel(reloadedController);
					reloadedPanel.refreshFromModel();
					var reloadedAgencies = (OrganizationalChartPanel.EntryTable) field(
							reloadedPanel, "agencyRepresentatives");
					JTable reloadedTable = (JTable) field(reloadedAgencies, "table");
					assertEquals(1, reloadedTable.getRowCount());
					assertEquals("Free-text Partner Agency", reloadedTable.getValueAt(0, 0));
					assertEquals("Free-text Representative", reloadedTable.getValueAt(0, 1));
					assertEquals("Partner Radio 4", reloadedTable.getValueAt(0, 2));
					assertEquals("555-0134", reloadedTable.getValueAt(0, 3));
					assertEquals(AppData.CURRENT_SCHEMA_VERSION, reloaded.getSchemaVersion());
					assertTrue(reloaded.getTCards().isEmpty());
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			});
		} finally {
			try (var paths = Files.walk(workspace)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Test
	void mainPanelAddsEditsRefreshesAndRemovesFreeTextRepresentativesWithoutTCards() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				AppController controller = sampleController();
				var panel = new OrganizationalChartPanel(controller);
				panel.refreshFromModel();
				var agencies = (OrganizationalChartPanel.EntryTable) field(panel, "agencyRepresentatives");
				JTable table = (JTable) field(agencies, "table");
				assertEquals("Agency/Organization Representatives",
						table.getAccessibleContext().getAccessibleName());
				assertTrue(SwingUtilities.isDescendingFrom(table, panel));
				Container sections = agencies.getParent();
				int agencyIndex = sections.getComponentZOrder(agencies);
				assertEquals("Additional Positions",
						((TitledBorder) ((JPanel) sections.getComponent(agencyIndex + 2)).getBorder()).getTitle());
				assertEquals(0, table.getRowCount());
				assertEquals(4, table.getColumnCount());
				button(agencies, "Add").doClick();
				table.setValueAt("County Volunteer Organization", 0, 0);
				table.setValueAt("Alex Free Text", 0, 1);
				table.setValueAt("County Channel", 0, 2);
				assertTrue(table.editCellAt(0, 3));
				((JTextField) table.getEditorComponent()).setText("555-0123");
				panel.pushToModel();
				Ics203Form form = controller.getData().getOrganizationalChart().getAssignmentList();
				assertEquals(1, form.getAgencyRepresentatives().size());
				OrgChartEntry saved = form.getAgencyRepresentatives().get(0);
				assertEquals("County Volunteer Organization", saved.getTitle());
				assertEquals("Alex Free Text", saved.getName());
				assertEquals("County Channel", saved.getRadio());
				assertEquals("555-0123", saved.getPhone());
				assertTrue(controller.getData().getTCards().isEmpty());

				panel.refreshFromModel();
				assertEquals("555-0123", table.getValueAt(0, 3));
				assertTrue(table.editCellAt(0, 1));
				((JTextField) table.getEditorComponent()).setText("Updated Representative");
				panel.pushToModel();
				assertEquals("Updated Representative", form.getAgencyRepresentatives().get(0).getName());
				assertTrue(table.editCellAt(0, 1));
				((JTextField) table.getEditorComponent()).setText("Stale unsaved name");
				form.getAgencyRepresentatives().get(0).setName("Reloaded Representative");
				panel.refreshFromModel();
				panel.pushToModel();
				assertEquals("Reloaded Representative", table.getValueAt(0, 1));
				assertEquals("Reloaded Representative", form.getAgencyRepresentatives().get(0).getName());

				button(agencies, "Add").doClick();
				table.setValueAt("Agency without assigned person", 1, 0);
				panel.pushToModel();
				assertEquals("", form.getAgencyRepresentatives().get(1).getName());
				table.setRowSelectionInterval(0, 0);
				button(agencies, "Remove").doClick();
				panel.pushToModel();
				assertEquals("Agency without assigned person", form.getAgencyRepresentatives().get(0).getTitle());
				table.setRowSelectionInterval(0, 0);
				button(agencies, "Remove").doClick();
				panel.pushToModel();
				panel.refreshFromModel();
				assertTrue(form.getAgencyRepresentatives().isEmpty());
				assertEquals(0, table.getRowCount());
				assertTrue(controller.getData().getTCards().isEmpty());
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void supplementalEditorSynchronizesMainRepresentativesOnOpenAndAcceptanceButNotCancellation() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				AppController controller = sampleController();
				var panel = new OrganizationalChartPanel(controller);
				panel.refreshFromModel();
				var mainAgencies = (OrganizationalChartPanel.EntryTable) field(panel, "agencyRepresentatives");
				JTable mainTable = (JTable) field(mainAgencies, "table");
				button(mainAgencies, "Add").doClick();
				mainTable.setValueAt("Main agency", 0, 0);
				mainTable.setValueAt("Main radio", 0, 2);
				mainTable.setValueAt("Main phone", 0, 3);
				assertTrue(mainTable.editCellAt(0, 1));
				((JTextField) mainTable.getEditorComponent()).setText("Pending main person");
				var editor = panel.createAssignmentListEditor();
				var modalAgencies = (OrganizationalChartPanel.EntryTable) field(editor, "agencies");
				JTable modalTable = (JTable) field(modalAgencies, "table");
				assertEquals("Pending main person", modalTable.getValueAt(0, 1));
				assertEquals("Main phone", modalTable.getValueAt(0, 3));
				modalTable.setValueAt("Cancelled agency", 0, 0);
				panel.pushToModel();
				Ics203Form form = controller.getData().getOrganizationalChart().getAssignmentList();
				assertEquals("Main agency", form.getAgencyRepresentatives().get(0).getTitle());

				editor = panel.createAssignmentListEditor();
				modalAgencies = (OrganizationalChartPanel.EntryTable) field(editor, "agencies");
				modalTable = (JTable) field(modalAgencies, "table");
				modalTable.setValueAt("Accepted agency", 0, 0);
				modalTable.setValueAt("Accepted person", 0, 1);
				modalTable.setValueAt("Accepted radio", 0, 2);
				assertTrue(modalTable.editCellAt(0, 3));
				((JTextField) modalTable.getEditorComponent()).setText("Accepted phone");
				panel.applyAssignmentListEditor(editor);
				assertEquals("Accepted agency", mainTable.getValueAt(0, 0));
				assertEquals("Accepted person", mainTable.getValueAt(0, 1));
				assertEquals("Accepted radio", mainTable.getValueAt(0, 2));
				assertEquals("Accepted phone", mainTable.getValueAt(0, 3));
				panel.pushToModel();
				assertEquals("Accepted person", form.getAgencyRepresentatives().get(0).getName());
				assertEquals("Accepted phone", form.getAgencyRepresentatives().get(0).getPhone());
				mainTable.setValueAt("Edited after acceptance", 0, 1);
				panel.pushToModel();
				assertEquals("Edited after acceptance", form.getAgencyRepresentatives().get(0).getName());
				editor = panel.createAssignmentListEditor();
				modalAgencies = (OrganizationalChartPanel.EntryTable) field(editor, "agencies");
				modalTable = (JTable) field(modalAgencies, "table");
				modalTable.setRowSelectionInterval(0, 0);
				button(modalAgencies, "Remove").doClick();
				panel.applyAssignmentListEditor(editor);
				panel.pushToModel();
				assertEquals(0, mainTable.getRowCount());
				assertTrue(form.getAgencyRepresentatives().isEmpty());
				assertTrue(controller.getData().getTCards().isEmpty());
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		});
	}

	/**
	 * Creates an empty incident controller without writing a workspace or requiring T-cards.
	 * @return controller for main-tab workflow tests.
	 */
	private static AppController sampleController() {
		return new AppController(new AppData(), new LocalRepository(Path.of("target", "org-chart-ui.json")),
				new PdfExportService(), new IncidentValidator());
	}

	@Test
	void visibleDivisionDialogKeepsRowsGroupedUnderSelectedBranch() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			Ics203Form form = new Ics203Form();
			var editor = new OrganizationalChartPanel.AssignmentListEditor(form);
			button(editor, "Add branch").doClick();
			Timer closeDivisionDialog = new Timer(200, event -> {
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog dialog && dialog.isShowing()
							&& dialog.getTitle().startsWith("Divisions / Groups")) {
						var divisions = findEntryTable(dialog);
						button(divisions, "Add").doClick();
						try {
							JTable table = (JTable) field(divisions, "table");
							table.setValueAt("Division A", 0, 0);
							table.setValueAt("Supervisor (T)", 0, 1);
						} catch (ReflectiveOperationException exception) {
							throw new AssertionError(exception);
						}
						button(dialog, "OK").doClick();
					}
				}
			});
			closeDivisionDialog.setRepeats(false);
			Timer operateEditor = new Timer(200, event -> {
				closeDivisionDialog.start();
				button(editor, "Edit selected branch divisions / groups...").doClick();
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog dialog && dialog.isShowing()
							&& dialog.getTitle().equals("ICS 203 workflow smoke test")) {
						button(dialog, "OK").doClick();
					}
				}
			});
			operateEditor.setRepeats(false);
			operateEditor.start();
			int result = JOptionPane.showConfirmDialog(null, editor, "ICS 203 workflow smoke test",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			assertEquals(JOptionPane.OK_OPTION, result);
			try {
				editor.applyTo(form);
			} catch (java.text.ParseException exception) {
				throw new AssertionError(exception);
			}
			assertEquals(1, form.getBranches().size());
			assertEquals("Division A", form.getBranches().get(0).getDivisions().get(0).getTitle());
			assertEquals("Supervisor (T)", form.getBranches().get(0).getDivisions().get(0).getName());
		});
	}

	/**
	 * Finds the visible agency/division table inside a modal editor.
	 * @param root dialog component tree.
	 * @return nested entry editor, or null.
	 */
	private static OrganizationalChartPanel.EntryTable findEntryTable(Component root) {
		if (root instanceof OrganizationalChartPanel.EntryTable entryTable) {
			return entryTable;
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				var match = findEntryTable(child);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}

	@Test
	void editorCommitsRowsPreparerAndInclusionWithoutLosingBranchGrouping() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				Ics203Form form = new Ics203Form();
				Ics203Form.Branch first = new Ics203Form.Branch();
				first.setName("North");
				OrgChartEntry division = new OrgChartEntry();
				division.setTitle("Division A");
				division.setName("Supervisor");
				division.setPhone("Preserved");
				first.setDivisions(List.of(division));
				form.setBranches(List.of(first));
				OrganizationalChartPanel.AssignmentListEditor editor =
						new OrganizationalChartPanel.AssignmentListEditor(form);
				@SuppressWarnings("unchecked")
				Map<Ics203Form.Position, JTextField> fields =
						(Map<Ics203Form.Position, JTextField>) field(editor, "positions");
				fields.forEach((position, text) -> text.setText("Name / Agency (T)"));
				var agencies = (OrganizationalChartPanel.EntryTable) field(editor, "agencies");
				button(agencies, "Add").doClick();
				JTable agencyTable = (JTable) field(agencies, "table");
				agencyTable.setValueAt("Agency", 0, 0);
				assertTrue(agencyTable.editCellAt(0, 1));
				((JTextField) agencyTable.getEditorComponent()).setText("Representative");
				button(editor, "Add branch").doClick();
				JTable branches = (JTable) field(editor, "branchTable");
				branches.setValueAt("South", 1, 0);
				branches.setValueAt("Director", 1, 1);
				branches.setValueAt("Deputy", 1, 2);
				((JTextField) field(editor, "preparedName")).setText("Planner");
				((JTextField) field(editor, "preparedTitle")).setText("Planning Chief");
				((JTextField) field(editor, "signature")).setText("Signed");
				((JCheckBox) field(editor, "include")).setSelected(false);
				((JCheckBox) field(editor, "hasPreparedDate")).doClick();
				LocalDateTime time = LocalDateTime.of(2026, 10, 10, 14, 30);
				((JSpinner) field(editor, "preparedDate")).setValue(AppController.toDate(time));
				editor.applyTo(form);
				assertEquals("Representative", form.getAgencyRepresentatives().get(0).getName());
				assertEquals(2, form.getBranches().size());
				assertEquals("Division A", form.getBranches().get(0).getDivisions().get(0).getTitle());
				assertEquals("Preserved", form.getBranches().get(0).getDivisions().get(0).getPhone());
				assertTrue(form.getBranches().get(1).getDivisions().isEmpty());
				assertEquals("Deputy", form.getBranches().get(1).getDeputy());
				assertEquals("Planner", form.getPreparedByName());
				assertEquals("Planning Chief", form.getPreparedByPositionTitle());
				assertEquals("Signed", form.getPreparedBySignature());
				assertEquals(time, form.getPreparedDateTime());
				assertFalse(form.isIncludeInIap());
				for (Ics203Form.Position position : Ics203Form.Position.values()) {
					assertEquals("Name / Agency (T)", form.getPosition(position));
				}
				var reloaded = new OrganizationalChartPanel.AssignmentListEditor(form);
				assertFalse(((JCheckBox) field(reloaded, "include")).isSelected());
				button(reloaded, "Remove branch").doClick();
				JTable reloadedBranches = (JTable) field(reloaded, "branchTable");
				reloadedBranches.setRowSelectionInterval(1, 1);
				button(reloaded, "Remove branch").doClick();
				reloaded.applyTo(form);
				assertEquals(1, form.getBranches().size());
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void unacceptedEditsAreDetachedAndEmptyPreparationDateRemainsOptional() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				Ics203Form form = new Ics203Form();
				OrgChartEntry agency = new OrgChartEntry();
				agency.setTitle("Original");
				agency.setPhone("Preserved");
				form.setAgencyRepresentatives(List.of(agency));
				ZoneId original = UiSupport.getDateTimeDisplayZone();
				try {
					UiSupport.setDateTimeDisplayZone(ZoneId.of("America/New_York"));
					var editor = new OrganizationalChartPanel.AssignmentListEditor(form);
					assertEquals("Resources Unit Leader", ((JTextField) field(editor, "preparedTitle")).getText());
					JSpinner spinner = (JSpinner) field(editor, "preparedDate");
					assertEquals("America/New_York",
							((JSpinner.DateEditor) spinner.getEditor()).getFormat().getTimeZone().getID());
					var agencies = (OrganizationalChartPanel.EntryTable) field(editor, "agencies");
					JTable table = (JTable) field(agencies, "table");
					table.setValueAt("Edited", 0, 0);
					agencies.getEntries();
					assertEquals("Original", agency.getTitle());
					editor.applyTo(form);
					assertNull(form.getPreparedDateTime());
					assertEquals("Edited", form.getAgencyRepresentatives().get(0).getTitle());
					assertEquals("Preserved", form.getAgencyRepresentatives().get(0).getPhone());
					table.setRowSelectionInterval(0, 0);
					button(agencies, "Remove").doClick();
					editor.applyTo(form);
					assertTrue(form.getAgencyRepresentatives().isEmpty());
				} finally {
					UiSupport.setDateTimeDisplayZone(original);
				}
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		});
	}

	/**
	 * Reads an editor field for headless workflow assertions.
	 * @param instance editor instance.
	 * @param name field name.
	 * @return field value.
	 * @throws ReflectiveOperationException if the field is unavailable.
	 */
	private static Object field(Object instance, String name) throws ReflectiveOperationException {
		Field field = instance.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	/**
	 * Finds a workflow button by visible label.
	 * @param root component tree.
	 * @param label button label.
	 * @return button, or null when absent.
	 */
	private static JButton button(Component root, String label) {
		if (root instanceof JButton candidate && candidate.getText().equals(label)) {
			return candidate;
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton match = button(child, label);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}
}
