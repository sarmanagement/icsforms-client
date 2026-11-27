/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Verifies that dialogs are kept inside the screen and scroll when their content cannot fit. */
class ScreenFitTest {
	@Test
	void oversizedContentIsScrolledAndSmallContentIsLeftAlone() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			Rectangle usable = ScreenFit.usableBounds(null);
			JPanel small = new JPanel();
			small.setPreferredSize(new Dimension(200, 100));
			assertSame(small, ScreenFit.bounded(small, null));
			JPanel large = new JPanel();
			large.setPreferredSize(new Dimension(usable.width * 3, usable.height * 3));
			var wrapped = ScreenFit.bounded(large, null);
			assertTrue(wrapped instanceof JScrollPane);
			assertTrue(wrapped.getPreferredSize().width < usable.width);
			assertTrue(wrapped.getPreferredSize().height < usable.height);
		});
	}

	@Test
	void fitShrinksAnOversizedDialogOntoTheScreenMakesItResizableAndScrollsItsContent() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			Rectangle usable = ScreenFit.usableBounds(null);
			JDialog dialog = new JDialog((java.awt.Frame) null, "Large", true);
			dialog.setResizable(false);
			JPanel content = new JPanel();
			content.setPreferredSize(new Dimension(usable.width * 2, usable.height * 2));
			dialog.add(content);
			dialog.pack();
			dialog.setLocation(usable.x + usable.width - 50, usable.y + usable.height - 50);
			ScreenFit.fit(dialog);
			Rectangle bounds = dialog.getBounds();
			assertTrue(dialog.isResizable());
			assertTrue(usable.contains(bounds), bounds + " outside " + usable);
			assertTrue(dialog.getContentPane() instanceof JScrollPane);
			assertEquals(2, ((JScrollPane) dialog.getContentPane()).getViewport().getView().getPreferredSize().width / usable.width);
			dialog.dispose();
		});
	}
}
