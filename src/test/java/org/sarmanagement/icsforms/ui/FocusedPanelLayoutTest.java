/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import javax.swing.JList;
import javax.swing.LookAndFeel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
	void defaultMissingPlanningFitsWithoutScrollbars() throws Exception {
		assertIncidentLayout(new AppData());
	}

	@Test
	@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
	void populatedCurrentPlanningFitsWithoutScrollbars() throws Exception {
		assertIncidentLayout(populated(false));
	}

	@Test
	@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
	void longIncidentAndReviewerNamesDoNotDisplacePlanning() throws Exception {
		assertIncidentLayout(populated(true));
	}

	@Test
	@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
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
						if (!strip.isShowing()) {
							continue;
						}
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
	void safetyWorkSelectionShowsMatchingPlanAndReview() throws Exception {
		AppController controller = controller(populated(true));
		SafetyRolePanel[] panel = new SafetyRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SafetyRolePanel(controller, ignored -> {
			});
			JList<?> worklist = descendants(panel[0], JList.class).get(0);
			javax.swing.JPanel focusedSafetyFields = (javax.swing.JPanel) field(panel[0], "focusedSafetyFields");
			javax.swing.JPanel previewCards = (javax.swing.JPanel) field(panel[0], "previewCards");
			javax.swing.JPanel reviewCards = (javax.swing.JPanel) field(panel[0], "reviewCards");
			AsyncFormPreviewPanel safetyPreview = (AsyncFormPreviewPanel) field(panel[0], "safetyPreview");
			AsyncFormPreviewPanel humanPreview = (AsyncFormPreviewPanel) field(panel[0], "humanPreview");
			AsyncFormPreviewPanel caninePreview = (AsyncFormPreviewPanel) field(panel[0], "caninePreview");
			List<PlanReviewStrip> strips = descendants(panel[0], PlanReviewStrip.class);

			assertTrue(focusedSafetyFields.isVisible());
			assertTrue(visibleReview(strips, PlanReviewArea.SAFETY));
			assertTrue(previewCards.isVisible());
			assertTrue(safetyPreview.isVisible());

			worklist.setSelectedValue("Human medical plan review", true);
			assertFalse(focusedSafetyFields.isVisible());
			assertTrue(visibleReview(strips, PlanReviewArea.HUMAN_MEDICAL));
			assertFalse(visibleReview(strips, PlanReviewArea.SAFETY));
			assertTrue(previewCards.isVisible());
			assertTrue(humanPreview.isVisible());
			assertFalse(safetyPreview.isVisible());
			assertTrue(reviewCards.isVisible());

			worklist.setSelectedValue("Canine medical plan review", true);
			assertTrue(visibleReview(strips, PlanReviewArea.CANINE_MEDICAL));
			assertFalse(visibleReview(strips, PlanReviewArea.HUMAN_MEDICAL));
			assertTrue(caninePreview.isVisible());
			assertFalse(humanPreview.isVisible());
			panel[0].dispose();
		});
	}

	/**
	 * Checks whether the review strip for one area is the visible card.
	 *
	 * @param strips all available review strips.
	 * @param area selected review area.
	 * @return true when the matching strip is visible.
	 */
	private boolean visibleReview(List<PlanReviewStrip> strips, PlanReviewArea area) {
		String reviewerName = area.displayLabel() + " plan reviewer";
		return strips.stream().filter(PlanReviewStrip::isVisible)
				.anyMatch(strip -> descendants(strip, JTextField.class).stream().anyMatch(field ->
						reviewerName.equals(field.getAccessibleContext().getAccessibleName())));
	}

	/**
	 * Reads a private field used to verify Swing card visibility.
	 *
	 * @param target component containing the field.
	 * @param name field name.
	 * @return field value.
	 */
	private Object field(Object target, String name) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
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
				assertEquals(RolePanelSupport.PENDING, badge(panel, "Safety").getBackground());
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
					assertEquals(RolePanelSupport.PENDING, badge(recordedPanel, "Objectives").getBackground());
					assertEquals(RolePanelSupport.PENDING, badge(recordedPanel, "Assignment coverage").getBackground());
					assertEquals(RolePanelSupport.PENDING, badge(recordedPanel, "Canine medical").getBackground());
					assertTrue(badge(recordedPanel, "Assignment coverage").getText().contains("not approval"));
				} finally {
					recordedPanel.dispose();
				}
				data.recordPlanReview(PlanReviewArea.SAFETY, "Reviewer", null);
				panel.refreshFromModel();
				assertEquals(RolePanelSupport.CURRENT, badge(panel, "Safety").getBackground());
				data.getForm208().setGeneralRequirements("Changed safety instructions");
				panel.refreshFromModel();
				assertEquals(RolePanelSupport.PENDING, badge(panel, "Safety").getBackground());
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
				assertEquals(List.of("Objectives", "Assignment coverage", "Communications", "Safety", "Human medical",
						"Canine medical"), badges.stream()
								.map(badge -> badge.getAccessibleContext().getAccessibleName().split(":", 2)[0])
								.toList());
				assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, badges.get(0)),
						"Planning badges should not require a scroll pane");
				JPanel content = (JPanel) badges.get(0).getParent();
				for (int index = 0; index < badges.size(); index += 2) {
					assertEquals(badges.get(index).getY(), badges.get(index + 1).getY());
					if (index > 0) {
						assertTrue(badges.get(index).getY() > badges.get(index - 2).getY());
					}
				}
				for (int index = 2; index < badges.size(); index++) {
					if (index == 2 || index == 4) {
						assertTrue(badges.get(index).getHeight() < badges.get(0).getHeight(),
							"Review badge should use only its own text height: " + badges.get(index).getText());
					}
				}
				JSplitPane objectiveSplit = (JSplitPane) field(panel, "objectiveSplit");
				Component badgeSection = objectiveSplit.getBottomComponent();
				assertTrue(badgeSection.getHeight() <= badgeSection.getPreferredSize().height + 4,
						"Badge section should stay at its minimum content height");
				for (JLabel badge : badges) {
					assertTextFits(badge);
					Rectangle bounds = SwingUtilities.convertRectangle(badge.getParent(), badge.getBounds(), content);
					assertTrue(new Rectangle(content.getSize()).contains(bounds), badge.getText());
					content.scrollRectToVisible(bounds);
					assertTrue(content.getVisibleRect().contains(bounds), badge.getText());
				}
				assertTextFits((JLabel) content.getComponent(0));
				assertControls(frame[0]);
				assertBanners(frame[0]);
				frame[0].setSize(860, 620);
				frame[0].validate();
			});
			SwingUtilities.invokeAndWait(() -> {
				IncidentCommandRolePanel panel = (IncidentCommandRolePanel) frame[0].getContentPane();
				List<JLabel> badges = badges(panel);
				JPanel content = (JPanel) badges.get(0).getParent();
				for (JLabel badge : badges) {
					assertTextFits(badge);
					Rectangle bounds = SwingUtilities.convertRectangle(badge.getParent(), badge.getBounds(), content);
					assertTrue(new Rectangle(content.getSize()).contains(bounds), badge.getText());
				}
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
	 * Finds a planning badge by its accessible label.
	 *
	 * @param panel command panel.
	 * @param name badge name.
	 * @return matching badge.
	 */
	private JLabel badge(IncidentCommandRolePanel panel, String name) {
		return badges(panel).stream()
				.filter(label -> label.getAccessibleContext().getAccessibleName().startsWith(name + ":"))
				.findFirst().orElseThrow();
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
