/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Measures displayed default role/reference layouts rather than oversized panels. */
class FocusedPanelLayoutTest {
	private static final String LONG_NAME = "Incident <north> & east ".repeat(18);
	private static LookAndFeel previousLookAndFeel;
	@TempDir
	Path directory;

	/**
	 * Measures the same system look and feel selected by the application.
	 *
	 * @throws Exception if Swing cannot install the system theme.
	 */
	@BeforeAll
	static void installApplicationLookAndFeel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			previousLookAndFeel = UIManager.getLookAndFeel();
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception exception) {
				throw new IllegalStateException("Unable to install application look and feel", exception);
			}
			System.out.println("Displayed layout theme: " + UIManager.getLookAndFeel().getName());
		});
	}

	/**
	 * Restores the previous theme for unrelated UI tests.
	 *
	 * @throws Exception if Swing cannot restore the theme.
	 */
	@AfterAll
	static void restoreLookAndFeel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				UIManager.setLookAndFeel(previousLookAndFeel);
			} catch (Exception exception) {
				throw new IllegalStateException("Unable to restore look and feel", exception);
			}
		});
	}

	@Test
	void defaultMissingPlanningFitsWithoutScrollbars() throws Exception {
		assertIncidentLayout(new AppData());
	}

	@Test
	void populatedCurrentPlanningFitsWithoutScrollbars() throws Exception {
		assertIncidentLayout(populated(false));
	}

	@Test
	void longIncidentAndReviewerNamesDoNotDisplacePlanning() throws Exception {
		assertIncidentLayout(populated(true));
	}

	@Test
	void stalePlanningFitsWithoutScrollbars() throws Exception {
		AppData data = populated(true);
		data.getForm202().getStructuredObjectives().get(0).setText("Changed objective");
		for (PlanReviewArea area : PlanReviewArea.values()) {
			data.getReviewAssertion(area).setReviewedContentDigest("stale");
		}
		assertIncidentLayout(data);
	}

	@Test
	void otherRoleAndBothReferenceWindowsContainActionsAndReviewControls() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Displayed layouts require Xvfb");
		AppController controller = controller(populated(true));
		List<JFrame> frames = new ArrayList<>();
		try {
			SwingUtilities.invokeAndWait(() -> {
				frames.add(RolePanelWindows.openSafety(null, controller, ignored -> {
				}));
				frames.add(RolePanelWindows.openMedicalUnitLeader(null, controller, ignored -> {
				}));
				frames.add(RolePanelWindows.openOperations(null, controller, () -> null, ignored -> {
				}, ignored -> {
				}, ignored -> {
				}, () -> {
				}, ignored -> {
				}, () -> {
				}, () -> {
				}));
				frames.add(RolePanelWindows.openCommunications(null, controller, ignored -> {
				}, () -> {
				}, () -> {
				}));
				frames.add(HumanMedicalReferenceWindow.open(null, controller, ignored -> {
				}));
				frames.add(CanineMedicalReferenceWindow.open(null, controller, ignored -> {
				}));
			});
			SwingUtilities.invokeAndWait(() -> {
				for (JFrame frame : frames) {
					frame.validate();
					assertControls(frame);
					assertBanners(frame);
					for (PlanReviewStrip strip : descendants(frame, PlanReviewStrip.class)) {
						JTextField reviewer = descendants(strip, JTextField.class).get(0);
						assertTrue(reviewer.getWidth() >= 80, "Reviewer input must remain usable");
						assertTrue(reviewer.getHeight() >= reviewer.getPreferredSize().height);
						assertTrue(new Rectangle(reviewer.getParent().getSize()).contains(reviewer.getBounds()));
						reviewer.scrollRectToVisible(new Rectangle(reviewer.getSize()));
						assertTrue(reviewer.getVisibleRect().contains(new Rectangle(reviewer.getSize())));
						for (JLabel status : descendants(strip, JLabel.class)) {
							assertTextFits(status);
							if (status.isOpaque()) {
								assertTrue(status.getToolTipText().contains(LONG_NAME));
							}
						}
					}
					System.out.println(frame.getContentPane().getClass().getSimpleName() + " client "
							+ frame.getContentPane().getSize());
				}
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> frames.forEach(JFrame::dispose));
		}
	}

	@Test
	void badgeColorsDistinguishPresenceReviewAndOptionalAbsence() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AppData data = new AppData();
			AppController controller = controller(data);
			IncidentCommandRolePanel panel = new IncidentCommandRolePanel(controller, ignored -> {
			});
			try {
				for (JLabel badge : badges(panel)) {
					assertTrue(badge.isOpaque());
					assertEquals(badge.getText().contains("Canine medical")
							? RolePanelSupport.OPTIONAL : RolePanelSupport.MISSING, badge.getBackground());
				}
				data.getForm208().setGeneralRequirements("Wear helmets");
				panel.refreshFromModel();
				assertEquals(RolePanelSupport.PENDING, badges(panel).get(2).getBackground());
				AppData recorded = populated(false);
				AppController recordedController = controller(recorded);
				IncidentCommandRolePanel recordedPanel = new IncidentCommandRolePanel(recordedController, ignored -> {
				});
				try {
					for (JLabel badge : badges(recordedPanel)) {
						assertEquals(RolePanelSupport.CURRENT, badge.getBackground(), badge.getText());
					}
					recorded.getForm204().getResourcesAssigned().get(0).setObjectiveIds(List.of());
					recorded.getForm202().getStructuredObjectives().get(0).setText("Changed objective");
					recorded.getCanineMedicalPlan().setEmergencyProcedures("Changed veterinary plan");
					recordedPanel.refreshFromModel();
					assertEquals(RolePanelSupport.PENDING, badges(recordedPanel).get(0).getBackground());
					assertEquals(RolePanelSupport.PENDING, badges(recordedPanel).get(1).getBackground());
					assertEquals(RolePanelSupport.PENDING, badges(recordedPanel).get(4).getBackground());
					assertTrue(badges(recordedPanel).get(1).getText().contains("not approval"));
				} finally {
					recordedPanel.dispose();
				}
				data.recordPlanReview(PlanReviewArea.SAFETY, "Reviewer", null);
				panel.refreshFromModel();
				assertEquals(RolePanelSupport.CURRENT, badges(panel).get(2).getBackground());
				data.getForm208().setGeneralRequirements("Changed safety instructions");
				panel.refreshFromModel();
				assertEquals(RolePanelSupport.PENDING, badges(panel).get(2).getBackground());
			} finally {
				panel.dispose();
			}
		});
	}

	/**
	 * Checks actual default-frame progress bounds, then verifies that shrinking
	 * preserves overflow access instead of disabling scrollbars.
	 *
	 * @param data incident scenario.
	 * @throws Exception if EDT dispatch fails.
	 */
	private void assertIncidentLayout(AppData data) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Displayed layouts require Xvfb");
		JFrame[] frame = new JFrame[1];
		try {
			SwingUtilities.invokeAndWait(() -> frame[0] = RolePanelWindows.openIncidentCommand(null,
					controller(data), ignored -> {
					}));
			SwingUtilities.invokeAndWait(() -> {
				frame[0].validate();
				IncidentCommandRolePanel panel = (IncidentCommandRolePanel) frame[0].getContentPane();
				List<JLabel> badges = badges(panel);
				assertEquals(6, badges.size());
				JScrollPane progress = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, badges.get(0));
				assertFalse(progress.getHorizontalScrollBar().isVisible(), "Planning horizontal scrollbar");
				assertFalse(progress.getVerticalScrollBar().isVisible(), "Planning vertical scrollbar");
				for (JLabel badge : badges) {
					assertTextFits(badge);
					Rectangle bounds = SwingUtilities.convertRectangle(badge.getParent(), badge.getBounds(),
							progress.getViewport());
					assertTrue(new Rectangle(progress.getViewport().getExtentSize()).contains(bounds), badge.getText());
				}
				JPanel content = (JPanel) progress.getViewport().getView();
				assertTextFits((JLabel) content.getComponent(0));
				assertControls(frame[0]);
				assertBanners(frame[0]);
				System.out.println("IncidentCommand client " + panel.getSize() + "; progress viewport "
						+ progress.getViewport().getExtentSize() + "; badge " + badges.get(0).getSize());
				frame[0].setSize(860, 620);
				frame[0].validate();
				assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, progress.getHorizontalScrollBarPolicy());
				assertEquals(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, progress.getVerticalScrollBarPolicy());
				Dimension extent = progress.getViewport().getExtentSize();
				Dimension preferred = content.getPreferredSize();
				assertTrue(preferred.width <= extent.width || progress.getHorizontalScrollBar().isVisible());
				assertTrue(preferred.height <= extent.height || progress.getVerticalScrollBar().isVisible());
			});
		} finally {
			if (frame[0] != null) {
				SwingUtilities.invokeAndWait(frame[0]::dispose);
			}
		}
	}

	/**
	 * Ensures action labels fit and each action remains reachable through any
	 * enclosing scrollable editor.
	 *
	 * @param frame displayed window.
	 */
	private void assertControls(JFrame frame) {
		for (JButton button : descendants(frame, JButton.class)) {
			if (button.getText() == null || button.getText().isBlank() || !button.isShowing()) {
				continue;
			}
			assertTrue(button.getWidth() >= button.getPreferredSize().width, button.getText() + " width");
			assertTrue(button.getHeight() >= button.getPreferredSize().height, button.getText() + " height");
			assertTrue(new Rectangle(button.getParent().getSize()).contains(button.getBounds()),
					button.getText() + " outside parent");
			button.scrollRectToVisible(new Rectangle(button.getSize()));
			assertTrue(button.getVisibleRect().contains(new Rectangle(button.getSize())),
					button.getText() + " unreachable in " + frame.getTitle());
		}
	}

	/**
	 * Checks banner line widths and full-name accessibility at displayed bounds.
	 *
	 * @param frame displayed role or reference window.
	 */
	private void assertBanners(JFrame frame) {
		for (JLabel label : descendants(frame, JLabel.class)) {
			String description = label.getAccessibleContext().getAccessibleDescription();
			if (description != null && description.contains("; Incident: ")) {
				assertTextFits(label);
				if (description.contains(LONG_NAME)) {
					assertEquals(LONG_NAME, label.getToolTipText());
					assertTrue(label.getText().contains("&lt;north&gt; &amp; east"));
					assertFalse(label.getText().contains(LONG_NAME));
				}
			}
		}
	}

	/**
	 * Asserts natural text dimensions fit without clipping.
	 *
	 * @param label displayed label.
	 */
	private void assertTextFits(JLabel label) {
		assertTrue(label.getPreferredSize().width <= label.getWidth(),
				() -> label.getText() + " preferred " + label.getPreferredSize() + " actual " + label.getSize());
		assertTrue(label.getPreferredSize().height <= label.getHeight(), label.getText());
	}

	/**
	 * Returns the six accessible planning badges in display order.
	 *
	 * @param panel command panel.
	 * @return planning badges.
	 */
	private List<JLabel> badges(IncidentCommandRolePanel panel) {
		return descendants(panel, JLabel.class).stream()
				.filter(label -> label.getAccessibleContext().getAccessibleName() != null
						&& label.getAccessibleContext().getAccessibleName().matches(
								"(Objectives|Assignment coverage|Safety|Human medical|Canine medical|Communications):.*"))
				.toList();
	}

	/**
	 * Collects descendants of a specific Swing type.
	 *
	 * @param root container to inspect.
	 * @param type requested component class.
	 * @param <T> component type.
	 * @return matching descendants.
	 */
	private <T extends JComponent> List<T> descendants(Container root, Class<T> type) {
		List<T> matches = new ArrayList<>();
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

	/**
	 * Builds a populated incident with synchronized task/default data before
	 * recording explicit current reviews for every area.
	 *
	 * @param longNames whether incident and reviewer names are unusually long.
	 * @return incident fixture.
	 */
	private AppData populated(boolean longNames) {
		AppData data = new AppData();
		String name = longNames ? LONG_NAME : "Commander";
		data.getIncidentContext().setIncidentName(name);
		data.getIncidentContext().setCurrentUser(name);
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Search north", 0)));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setResourceIdentifier("Ground team");
		assignment.setObjectiveIds(List.of(data.getForm202().getStructuredObjectives().get(0).getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);
		data.getForm208().setGeneralRequirements("Wear helmets");
		data.getMedicalPlan().setEmergencyProcedures("Call medical");
		data.getCanineMedicalPlan().setEmergencyProcedures("Call veterinary");
		data.getForm205().setSpecialInstructions("Monitor command");
		controller(data);
		data.getForm202().recordObjectiveApproval(name, "Signature", LocalDateTime.of(2026, 1, 1, 12, 0));
		for (PlanReviewArea area : PlanReviewArea.values()) {
			data.recordPlanReview(area, name, null);
		}
		return data;
	}

	/**
	 * Creates an isolated controller.
	 *
	 * @param data incident fixture.
	 * @return controller.
	 */
	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}
}
