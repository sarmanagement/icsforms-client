/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.ResourceDirectorySource;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Desktop smoke coverage for opening and painting the imported documentation example. */
class MapleRidgeUiTest {
	@TempDir
	Path directory;

	@Test
	void importedExampleOpensAndPaintsInTheEditor() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			var id = store.importFile(Path.of("docs/examples/maple-ridge-dementia-op1.json")).orElseThrow();
			var data = store.load(id);
			SwingUtilities.invokeAndWait(() -> {
				var previousZone = UiSupport.getDateTimeDisplayZone();
				try {
					MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
							new IncidentValidator(), directory);
					try {
						frame.setVisible(true);
						assertTrue(frame.isShowing());
						assertDoesNotThrow(() -> assertStaffOnIcpBoard(frame));
						BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
								BufferedImage.TYPE_INT_RGB);
						var graphics = image.createGraphics();
						try {
							frame.paint(graphics);
						} finally {
							graphics.dispose();
						}
					} finally {
						frame.dispose();
					}
				} finally {
					UiSupport.setDateTimeDisplayZone(previousZone);
				}
			});
		}
	}

	/**
	 * Synchronizes linked cards as an editor commit does, then checks the actual status rack.
	 *
	 * @param frame opened example editor.
	 * @throws ReflectiveOperationException if the rack fields cannot be inspected.
	 */
	private static void assertStaffOnIcpBoard(MainFrame frame) throws ReflectiveOperationException {
		var controllerField = MainFrame.class.getDeclaredField("controller");
		controllerField.setAccessible(true);
		AppController controller = (AppController) controllerField.get(frame);
		controller.syncTCards();
		var panelField = MainFrame.class.getDeclaredField("tCardPanel");
		panelField.setAccessible(true);
		TCardPanel panel = (TCardPanel) panelField.get(frame);
		panel.refreshFromModel();
		var selectorField = TCardPanel.class.getDeclaredField("viewSelector");
		selectorField.setAccessible(true);
		((JComboBox<?>) selectorField.get(panel)).setSelectedItem("Rack View");
		var rackField = TCardPanel.class.getDeclaredField("rackScroll");
		rackField.setAccessible(true);
		Container rack = (Container) ((JScrollPane) rackField.get(panel)).getViewport().getView();
		int staffCount = 0;
		for (Component column : rack.getComponents()) {
			List<TCard> cards = components(column).filter(JComponent.class::isInstance)
					.map(JComponent.class::cast).map(c -> c.getClientProperty("tcard"))
					.filter(TCard.class::isInstance).map(TCard.class::cast).toList();
			List<TCard> staff = cards.stream()
					.filter(c -> c.getResourceIdentifier().startsWith("MR-IMT-")).toList();
			if (!staff.isEmpty()) {
				assertEquals(rack.getComponent(0), column, "Staff must render in ICP, not Assigned or Available");
				assertTrue(components(column).filter(JLabel.class::isInstance)
						.map(JLabel.class::cast).anyMatch(label -> label.getText().equals("ICP")));
				assertTrue(staff.stream().allMatch(c -> c.getStatus().equals("ICP")
						&& c.getLocation().contains("50 Civic Road")));
				staffCount += staff.size();
			}
		}
		assertEquals(6, staffCount);
		var overhead = ResourceDirectorySource.build(controller.getData()).stream()
				.filter(r -> r.card().getResourceIdentifier().startsWith("MR-IMT-")).toList();
		assertEquals(5, overhead.stream().filter(r -> r.card().getSourceRef().startsWith("org:")
				&& !r.assignedPosition().isBlank()).count());
	}

	/**
	 * Traverses a Swing component subtree, including its root.
	 *
	 * @param root subtree root.
	 * @return all descendants and the root.
	 */
	private static Stream<Component> components(Component root) {
		return Stream.concat(Stream.of(root), root instanceof Container container
				? Arrays.stream(container.getComponents()).flatMap(MapleRidgeUiTest::components) : Stream.empty());
	}
}
