package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

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
