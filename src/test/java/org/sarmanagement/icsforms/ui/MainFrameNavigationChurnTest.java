/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Measures descendant events and retained view state on a shown, event-separated workspace. */
class MainFrameNavigationChurnTest {
	@org.junit.jupiter.api.Test
	void liveSafetyOfficerAndModeChangesReachGuardedMedicalAndSafetyEditors() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-live-safety-refresh");
		AppData data = populatedIncident();
		data.getOrganizationalChart().setIncidentCommanderName("Shared officer");
		data.getOrganizationalChart().setSafetyOfficerName("Shared officer");
		data.getMedicalPlan().setReviewedBySafetyOfficer("");
		data.getCanineMedicalPlan().setReviewedBySafetyOfficer("");
		data.setIncidentMode(org.sarmanagement.icsforms.model.IncidentMode.GENERIC);
		MainFrame[] frame = new MainFrame[1];
		AppController[] controller = new AppController[1];
		Object[] medical = new Object[1];
		Object[] canine = new Object[1];
		JTabbedPane[] safetySections = new JTabbedPane[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				frame[0] = IncidentStoreTestFixtures.frame(data, directory, new PdfExportService(),
						new IncidentValidator(), directory);
				controller[0] = (AppController) Ics205PanelTest.field(frame[0], "controller");
				medical[0] = Ics205PanelTest.field(frame[0], "medicalPlanPanel");
				canine[0] = Ics205PanelTest.field(frame[0], "canineMedicalPanel");
				safetySections[0] = (JTabbedPane) Ics205PanelTest.field(
						Ics205PanelTest.field(frame[0], "safetyPanel"), "sections");
				frame[0].setVisible(true);
				assertEquals("Shared officer", ((JTextField) Ics205PanelTest.field(medical[0], "reviewedBy")).getText());
				assertEquals("Shared officer", ((JTextField) Ics205PanelTest.field(canine[0], "safetyOfficer")).getText());
				assertEquals(-1, safetySections[0].indexOfTab("Reported SAR Hazards"));
			});
			new Robot().waitForIdle();
			var cards = java.util.Base64.getEncoder().encodeToString(UiRefreshState.serialize(data.getTCards()));
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(controller[0].commitIfCurrent(controller[0].captureIncidentBinding(),
						() -> data.getOrganizationalChart().setSafetyOfficerName("")));
				((Timer) Ics205PanelTest.field(controller[0], "autosaveTimer")).stop();
			});
			SwingUtilities.invokeAndWait(() -> {
				assertEquals("", ((JTextField) Ics205PanelTest.field(medical[0], "reviewedBy")).getText());
				assertEquals("", ((JTextField) Ics205PanelTest.field(canine[0], "safetyOfficer")).getText());
				assertTrue(controller[0].commitIfCurrent(controller[0].captureIncidentBinding(),
						() -> data.setIncidentMode(org.sarmanagement.icsforms.model.IncidentMode.SAR)));
				((Timer) Ics205PanelTest.field(controller[0], "autosaveTimer")).stop();
			});
			SwingUtilities.invokeAndWait(() ->
					assertTrue(safetySections[0].indexOfTab("Reported SAR Hazards") >= 0));
			assertEquals(cards, java.util.Base64.getEncoder().encodeToString(UiRefreshState.serialize(data.getTCards())),
					"shared command card remains unchanged; no incidental T-card invalidation");
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (frame[0] != null) {
					frame[0].dispose();
					controller[0].close();
				}
			});
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"accepted", "rejected", "stale"})
	void visibleNavigationCommitsOrRetainsActiveCommunicationsDraft(String outcome) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-visible-draft");
		MainFrame[] frame = new MainFrame[1];
		AppController[] controller = new AppController[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				frame[0] = IncidentStoreTestFixtures.frame(populatedIncident(), directory,
						new PdfExportService(), new IncidentValidator(), directory);
				controller[0] = (AppController) Ics205PanelTest.field(frame[0], "controller");
				frame[0].setVisible(true);
				frame[0].openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_204));
			});
			new Robot().waitForIdle();
			SwingUtilities.invokeAndWait(() -> {
				JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frame[0], "tabs");
				Object panel = Ics205PanelTest.field(frame[0], "ics204Panel");
				JTable table = (JTable) Ics205PanelTest.field(panel, "communicationsTable");
				Component source = tabs.getSelectedComponent();
				if ("rejected".equals(outcome)) {
					table.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(new JTextField()) {
						/** {@inheritDoc} */
						@Override
						public boolean stopCellEditing() {
							return false;
						}
					});
				}
				assertTrue(table.editCellAt(0, 2));
				JTextField draft = (JTextField) table.getEditorComponent();
				draft.setText("Changed radio contact");
				((JTextField) Ics205PanelTest.field(panel, "operationsChiefContactField"))
						.setText("Changed chief contact");
				if ("stale".equals(outcome)) {
					controller[0].markDirty();
					((Timer) Ics205PanelTest.field(controller[0], "autosaveTimer")).stop();
				}
				Component destination = descendants(tabs).stream()
						.filter(Ics214Panel.class::isInstance).findFirst().orElseThrow();
				tabs.setSelectedComponent(destination);
				if ("accepted".equals(outcome)) {
					assertSame(destination, tabs.getSelectedComponent());
					assertFalse(table.isEditing());
					assertEquals("Changed radio contact", controller[0].getData().getForm204()
							.getCommunications().get(0).getPrimaryContact());
					assertEquals("Changed chief contact",
							controller[0].getData().getForm204().getOperationsSectionChiefContact());
					assertTrue(controller[0].hasUnsavedChanges());
				} else {
					assertSame(source, tabs.getSelectedComponent());
					assertTrue(table.isEditing());
					assertEquals("Changed radio contact", ((JTextField) table.getEditorComponent()).getText());
					assertEquals("Original radio contact", controller[0].getData().getForm204()
							.getCommunications().get(0).getPrimaryContact());
					String status = ((javax.swing.JLabel) Ics205PanelTest.field(frame[0], "validationLabel")).getText();
					assertTrue(status.contains("stale") || status.contains("not committed"), status);
				}
			});
			new Robot().waitForIdle();
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (frame[0] != null) {
					frame[0].dispose();
					controller[0].close();
				}
			});
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void tenUnchangedVisibleSwitchesRetainDescendantsAndSelection(boolean roleWindows) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		var directory = Files.createTempDirectory("icsforms-visible-navigation");
		MainFrame[] frames = new MainFrame[1];
		AppController[] controllers = new AppController[1];
		JTabbedPane[] tabs = new JTabbedPane[1];
		Ics214Panel[] logs = new Ics214Panel[1];
		JPanel[] contacts = new JPanel[1];
		Component[][] children = new Component[1][];
		List<JFrame> roles = new ArrayList<>();
		JTextField[] names = new JTextField[1];
		JTable[] resources = new JTable[1];
		Map<JScrollPane, Point> scrolls = new IdentityHashMap<>();
		Map<JTextComponent, int[]> carets = new IdentityHashMap<>();
		Map<JTable, AtomicInteger> tableEvents = new IdentityHashMap<>();
		Map<JTextComponent, AtomicInteger> documentEvents = new IdentityHashMap<>();
		Map<javax.swing.JList<?>, AtomicInteger> listEvents = new IdentityHashMap<>();
		Map<AsyncFormPreviewPanel, Object> previewGenerations = new IdentityHashMap<>();
		Map<AsyncFormPreviewPanel, Component[]> previewPages = new IdentityHashMap<>();
		AtomicInteger roleChildEvents = new AtomicInteger();
		AtomicInteger paintBatches = new AtomicInteger();
		javax.swing.RepaintManager originalRepaints = javax.swing.RepaintManager.currentManager(null);
		javax.swing.RepaintManager measuredRepaints = new javax.swing.RepaintManager() {
			/** {@inheritDoc} */
			@Override
			public void paintDirtyRegions() {
				paintBatches.incrementAndGet();
				super.paintDirtyRegions();
			}
		};
		AtomicInteger additions = new AtomicInteger();
		AtomicInteger removals = new AtomicInteger();
		AtomicInteger tabRemovals = new AtomicInteger();
		AtomicInteger notifications = new AtomicInteger();
		AppController.EditorCommitBinding[] binding = new AppController.EditorCommitBinding[1];
		Robot robot = new Robot();
		try {
			SwingUtilities.invokeAndWait(() -> {
				frames[0] = IncidentStoreTestFixtures.frame(populatedIncident(), directory,
						new PdfExportService(), new IncidentValidator(), directory);
				controllers[0] = (AppController) Ics205PanelTest.field(frames[0], "controller");
				tabs[0] = (JTabbedPane) Ics205PanelTest.field(frames[0], "tabs");
				logs[0] = (Ics214Panel) descendants(tabs[0]).stream()
						.filter(Ics214Panel.class::isInstance).findFirst().orElseThrow();
				Object assignment = Ics205PanelTest.field(frames[0], "ics204Panel");
				contacts[0] = (JPanel) Ics205PanelTest.field(assignment, "managementContactsPanel");
				names[0] = (JTextField) Ics205PanelTest.field(logs[0], "nameField");
				resources[0] = (JTable) Ics205PanelTest.field(logs[0], "resourcesTable");
				frames[0].setVisible(true);
				tabs[0].setSelectedComponent(logs[0]);
				if (roleWindows) {
					roles.add(RolePanelWindows.openSafety(frames[0], controllers[0], frames[0]::openWorkspace));
					roles.add(RolePanelWindows.openMedicalUnitLeader(frames[0], controllers[0],
							frames[0]::openWorkspace));
					roles.add(RolePanelWindows.openIncidentCommand(frames[0], controllers[0],
							frames[0]::openWorkspace));
					roles.add(RolePanelWindows.openOperations(frames[0], controllers[0], () -> null,
							id -> { }, id -> { }, task -> { }, () -> { }, id -> { }, () -> { }, () -> { }));
					roles.add(RolePanelWindows.openCommunications(frames[0], controllers[0],
							frames[0]::openWorkspace, () -> { }, () -> { }));
				}
				frames[0].toFront();
			});
			robot.waitForIdle();
			if (roleWindows) {
				AsyncFormPreviewPanelTest.waitFor(() -> roles.stream().flatMap(role -> descendants(role).stream())
						.filter(AsyncFormPreviewPanel.class::isInstance).map(AsyncFormPreviewPanel.class::cast)
						.filter(preview -> Ics205PanelTest.field(preview, "currentKey") != null)
						.allMatch(preview -> preview.displayedPageCount() > 0));
			}
			SwingUtilities.invokeAndWait(() -> tabs[0].setSelectedComponent(
					(Component) Ics205PanelTest.field(frames[0], "ics204Panel")));
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> tabs[0].setSelectedComponent(logs[0]));
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> {
				resources[0].setRowSelectionInterval(5, 5);
				names[0].select(2, 7);
				((JTextField) Ics205PanelTest.field(
						Ics205PanelTest.field(frames[0], "ics204Panel"), "operationsChiefNameField")).select(1, 6);
				for (Component editor : List.of(logs[0],
						(Component) Ics205PanelTest.field(frames[0], "ics204Panel"))) {
					for (Component component : descendants(editor)) {
						if (component instanceof JScrollPane scroll) {
							scroll.getVerticalScrollBar().setValue(Math.min(80, scroll.getVerticalScrollBar().getMaximum()));
						}
					}
				}
			});
			robot.waitForIdle();
			SwingUtilities.invokeAndWait(() -> {
				children[0] = contacts[0].getComponents();
				contacts[0].addContainerListener(new ContainerAdapter() {
					/** {@inheritDoc} */
					@Override
					public void componentAdded(ContainerEvent event) {
						additions.incrementAndGet();
					}

					/** {@inheritDoc} */
					@Override
					public void componentRemoved(ContainerEvent event) {
						removals.incrementAndGet();
					}
				});
				tabs[0].addContainerListener(new ContainerAdapter() {
					/** {@inheritDoc} */
					@Override
					public void componentRemoved(ContainerEvent event) {
						tabRemovals.incrementAndGet();
					}
				});
				List<Component> measuredDescendants = new ArrayList<>(descendants(tabs[0]));
				for (JFrame role : roles) {
					List<Component> components = descendants(role);
					measuredDescendants.addAll(components);
					for (Component component : components) {
						if (component instanceof Container container) {
							container.addContainerListener(new ContainerAdapter() {
								/** {@inheritDoc} */
								@Override
								public void componentAdded(ContainerEvent event) {
									roleChildEvents.incrementAndGet();
								}

								/** {@inheritDoc} */
								@Override
								public void componentRemoved(ContainerEvent event) {
									roleChildEvents.incrementAndGet();
								}
							});
						}
						if (component instanceof AsyncFormPreviewPanel preview && preview.displayedPageCount() > 0) {
							previewGenerations.put(preview, Ics205PanelTest.field(preview, "requestGeneration"));
							previewPages.put(preview, ((JPanel) Ics205PanelTest.field(preview, "pages")).getComponents());
						}
					}
				}
				if (roleWindows) {
					assertTrue(previewPages.size() >= 2, "completed safety and medical previews");
				}
				for (Component component : measuredDescendants) {
					if (component instanceof JTable table) {
						AtomicInteger count = new AtomicInteger();
						tableEvents.put(table, count);
						table.getModel().addTableModelListener(event -> count.incrementAndGet());
					} else if (component instanceof JTextComponent text) {
						AtomicInteger count = new AtomicInteger();
						documentEvents.put(text, count);
						watchDocument(text, count);
					} else if (component instanceof javax.swing.JList<?> list) {
						AtomicInteger count = new AtomicInteger();
						listEvents.put(list, count);
						UiRefreshStateTest.watchList(list, count);
					}
				}
				for (Component editor : List.of(logs[0],
						(Component) Ics205PanelTest.field(frames[0], "ics204Panel"))) {
					for (Component component : descendants(editor)) {
						if (component instanceof JScrollPane scroll) {
							scrolls.put(scroll, scroll.getViewport().getViewPosition());
						} else if (component instanceof JTextComponent text) {
							carets.put(text, new int[] {text.getCaretPosition(), text.getSelectionStart(), text.getSelectionEnd()});
						}
					}
				}
				binding[0] = controllers[0].captureEditorCommitBinding();
				assertFalse(controllers[0].hasUnsavedChanges());
				controllers[0].addIncidentChangeListener(change -> notifications.incrementAndGet());
				captureFrame(frames[0], roleWindows, "before");
				javax.swing.RepaintManager.setCurrentManager(measuredRepaints);
			});
			for (int pass = 0; pass < 10; pass++) {
				boolean showLog = pass % 2 == 1;
				SwingUtilities.invokeAndWait(() -> tabs[0].setSelectedComponent(showLog ? logs[0]
						: (Component) Ics205PanelTest.field(frames[0], "ics204Panel")));
				robot.waitForIdle();
				SwingUtilities.invokeAndWait(() -> {
					assertEquals(0, additions.get(), "ICS 204 management child additions");
					assertEquals(0, removals.get(), "ICS 204 management child removals");
					assertEquals(0, tabRemovals.get(), "top-level tab removals");
					assertEquals(0, roleChildEvents.get(), "role descendant additions/removals");
					listEvents.forEach((list, count) -> assertEquals(0, count.get(), "role worklist notifications"));
					previewGenerations.forEach((preview, generation) ->
							assertEquals(generation, Ics205PanelTest.field(preview, "requestGeneration"),
									"no replacement preview submitted"));
					previewPages.forEach((preview, pages) -> {
						Component[] current = ((JPanel) Ics205PanelTest.field(preview, "pages")).getComponents();
						org.junit.jupiter.api.Assertions.assertArrayEquals(pages, current, "completed preview pages retained");
					});
					tableEvents.forEach((table, count) -> assertEquals(0, count.get(),
							"table event: " + table.getModel().getClass().getSimpleName()));
					documentEvents.forEach((text, count) -> assertEquals(0, count.get(),
							"document event: " + text.getText()));
					assertEquals(binding[0], controllers[0].captureEditorCommitBinding());
					assertFalse(controllers[0].hasUnsavedChanges());
					assertFalse(((Timer) Ics205PanelTest.field(controllers[0], "autosaveTimer")).isRunning());
					assertEquals(0, notifications.get());
					assertEquals(5, resources[0].getSelectedRow());
					assertEquals("resource-5", logs[0].getCurrentForm().getResourcesAssigned().get(
							resources[0].convertRowIndexToModel(resources[0].getSelectedRow())).getResourceId());
					Component[] current = contacts[0].getComponents();
					assertEquals(children[0].length, current.length);
					for (int child = 0; child < current.length; child++) {
						assertSame(children[0][child], current[child]);
					}
					carets.forEach((text, state) -> {
						assertEquals(state[0], text.getCaretPosition(), "caret");
						assertEquals(state[1], text.getSelectionStart(), "selection start");
						assertEquals(state[2], text.getSelectionEnd(), "selection end");
					});
					scrolls.forEach((scroll, position) ->
							assertEquals(position, scroll.getViewport().getViewPosition(), "viewport position"));
				});
			}
			SwingUtilities.invokeAndWait(() -> {
				assertSame(logs[0], tabs[0].getSelectedComponent());
				assertTrue(frames[0].isShowing());
				assertTrue(paintBatches.get() > 0, "newly selected visible content was painted");
				captureFrame(frames[0], roleWindows, "after");
			});
			System.out.println("Visible navigation: " + System.getProperty("os.name") + ", Java "
					+ System.getProperty("java.version") + ", " + GraphicsEnvironment.getLocalGraphicsEnvironment()
							.getDefaultScreenDevice().getDisplayMode()
					+ ", role windows=" + roleWindows + "; 10 switches; descendant counters=0; view state retained; "
					+ previewPages.size() + " completed previews retained; paint batches=" + paintBatches.get()
					+ ". Structural/paint evidence, not a human flicker assessment.");
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				javax.swing.RepaintManager.setCurrentManager(originalRepaints);
				roles.forEach(JFrame::dispose);
				if (frames[0] != null) {
					frames[0].dispose();
					controllers[0].close();
				}
			});
		}
	}

	/**
	 * Creates meaningful populated navigation inputs, including scrollable log tables.
	 *
	 * @return incident with contacts, facilities, resources and activity entries.
	 */
	private static AppData populatedIncident() {
		AppData data = new AppData();
		data.setIapPhase(IapPhase.DURING_OP);
		data.getIncidentContext().setIncidentName("Shown navigation exercise");
		data.getForm204().setOperationsSectionChiefName("Operations chief");
		data.getForm204().setOperationsSectionChiefContact("Command channel");
		CommunicationEntry contact = new CommunicationEntry();
		contact.setName("Team contact");
		contact.setPrimaryContact("Original radio contact");
		data.getForm204().getCommunications().add(contact);
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-1");
		assignment.setAssignmentTeamNumber("Team 1");
		assignment.setResourceIdentifier("Team 1");
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		task.setAssignmentTeamNumber("Team 1");
		task.setResourceIdentifier("Team 1");
		task.setHazardsObserved("Recorded terrain hazard");
		data.getSarTaskAssignments().add(task);
		Ics214Form log = new Ics214Form();
		log.setName("Nonblank activity log name");
		log.setIcsPosition("Operations");
		log.setHomeAgency("Exercise agency");
		log.setPreparedByName("Log preparer");
		log.setPreparedByPositionTitle("Recorder");
		log.setPreparedDateTime(java.time.LocalDateTime.of(2026, 11, 8, 23, 0));
		for (int row = 0; row < 30; row++) {
			SarTaskResource resource = new SarTaskResource();
			resource.setResourceId("resource-" + row);
			resource.setName("Responder " + row);
			resource.setFunction("Search");
			log.getResourcesAssigned().add(resource);
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setTimestamp(java.time.LocalDateTime.of(2026, 11, 8, 23, row));
			entry.setNotableActivity("Recorded activity " + row);
			log.getActivityLog().add(entry);
		}
		data.getActivityLogs().add(log);
		VetFacility facility = new VetFacility();
		facility.setName("Exercise veterinary facility");
		data.getVetDirectory().getFacilities().add(facility);
		data.getCanineMedicalPlan().getSelectedFacilities().add(facility.copy());
		AppController.ensureDefaultHeaderCards(data.getTCards(), data.getIapPhase());
		return data;
	}

	/**
	 * Traverses actual descendants rather than measuring only tab-strip mutations.
	 *
	 * @param component root component.
	 * @return descendants in stable traversal order.
	 */
	private static List<Component> descendants(Component component) {
		List<Component> result = new ArrayList<>();
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				result.add(child);
				result.addAll(descendants(child));
			}
		}
		return result;
	}

	/**
	 * Counts all document mutations without counting caret movement as a content edit.
	 *
	 * @param text observed text component.
	 * @param count mutation counter.
	 */
	private static void watchDocument(JTextComponent text, AtomicInteger count) {
		text.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				count.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				count.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				count.incrementAndGet();
			}
		});
	}

	/**
	 * Optionally records rendered shown-frame images for visual inspection outside the repository.
	 *
	 * @param frame shown application frame.
	 * @param roles whether role windows are open.
	 * @param stage capture stage.
	 */
	private static void captureFrame(MainFrame frame, boolean roles, String stage) {
		if (!Boolean.getBoolean("icsforms.captureNavigationScreenshots")) {
			return;
		}
		try {
			var image = new Robot().createScreenCapture(new java.awt.Rectangle(frame.getLocationOnScreen(), frame.getSize()));
			java.nio.file.Path directory = java.nio.file.Path.of("/tmp", "icsforms-navigation-screenshots");
			java.nio.file.Files.createDirectories(directory);
			javax.imageio.ImageIO.write(image, "png", directory.resolve(
					"icsforms-navigation-" + roles + "-" + stage + ".png").toFile());
		} catch (java.io.IOException | java.awt.AWTException exception) {
			throw new AssertionError(exception);
		}
	}
}
