package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TCardPanelDirectoryTest {

    @Test
    void directorySelectionMapsBackToUnderlyingCardAfterFilterAndSort() throws Exception {
        TCard zulu = personnelCard("Zoey Zulu", "OR");
        TCard alpha = personnelCard("Amy Alpha", "WA");
        AppData data = new AppData();
        data.setTCards(List.of(zulu, alpha));

        TCardPanel panel = new TCardPanel(createController(data));
        SwingUtilities.invokeAndWait(() -> {
            panel.refreshFromModel();
            combo(panel, "viewSelector").setSelectedItem("Directory View");

            JTextField nameFilter = field(panel, "directoryNameFilterField", JTextField.class);
            JTable directoryTable = field(panel, "directoryTable", JTable.class);
            nameFilter.setText("zoey");
            assertEquals(1, directoryTable.getRowCount());
            directoryTable.setRowSelectionInterval(0, 0);
            assertSame(zulu, selectedCard(panel));

            nameFilter.setText("");
            directoryTable.getRowSorter().toggleSortOrder(0); // descending from default ascending
            directoryTable.setRowSelectionInterval(0, 0);
            assertSame(zulu, selectedCard(panel));
        });
    }

    @Test
    void directoryRefreshClearsRemovedFilterChoice() throws Exception {
        AppData data = new AppData();
        data.setTCards(List.of(personnelCard("Zoey Zulu", "OR")));

        TCardPanel panel = new TCardPanel(createController(data));
        SwingUtilities.invokeAndWait(() -> {
            panel.refreshFromModel();
            combo(panel, "viewSelector").setSelectedItem("Directory View");
            JComboBox<String> stateFilter = combo(panel, "directoryStateFilter");
            stateFilter.setSelectedItem("OR");
            assertEquals("OR", stateFilter.getSelectedItem());

            data.setTCards(List.of(personnelCard("Amy Alpha", "WA")));
            panel.refreshFromModel();

            assertEquals("All", stateFilter.getSelectedItem());
            JTable directoryTable = field(panel, "directoryTable", JTable.class);
            assertEquals(1, directoryTable.getRowCount());
        });
    }

    @Test
    void directoryRefreshRestoresSelectionForEditedCardReplacement() throws Exception {
        TCard original = personnelCard("Zoey Zulu", "OR");
        AppData data = new AppData();
        data.setTCards(List.of(original));

        TCardPanel panel = new TCardPanel(createController(data));
        SwingUtilities.invokeAndWait(() -> {
            panel.refreshFromModel();
            combo(panel, "viewSelector").setSelectedItem("Directory View");
            JTable directoryTable = field(panel, "directoryTable", JTable.class);
            directoryTable.setRowSelectionInterval(0, 0);
            assertSame(original, selectedCard(panel));

            Object tableModel = field(panel, "tableModel", Object.class);
            TCard replacement = personnelCard("Zoey Zulu", "OR");
            invoke(tableModel, "replaceCard", new Class<?>[]{int.class, TCard.class}, 0, replacement);
            invoke(panel, "refreshDerivedViewsFromTableModel", new Class<?>[]{TCard.class}, replacement);

            assertSame(replacement, selectedCard(panel));
        });
    }

    private static TCard personnelCard(String name, String state) {
        TCard card = new TCard();
        card.setCardType(TCardType.PERSONNEL);
        card.setPersonName(name);
        card.setHomeState(state);
        return card;
    }

    private static AppController createController(AppData data) {
        return new AppController(
                data,
                new LocalRepository(Path.of(System.getProperty("java.io.tmpdir"), "tcard-directory-test.json")),
                new PdfExportService(),
                new IncidentValidator());
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> combo(TCardPanel panel, String fieldName) {
        return field(panel, fieldName, JComboBox.class);
    }

    private static <T> T field(TCardPanel panel, String fieldName, Class<T> type) {
        try {
            Field field = TCardPanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(panel));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static TCard selectedCard(TCardPanel panel) {
        return (TCard) invoke(panel, "selectedCard", new Class<?>[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
