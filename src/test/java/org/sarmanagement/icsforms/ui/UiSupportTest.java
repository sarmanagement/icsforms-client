package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
