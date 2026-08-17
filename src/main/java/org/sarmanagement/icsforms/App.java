package org.sarmanagement.icsforms;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.ClueLogPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics201PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics214PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.ui.MainFrame;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

/**
 * Application entry point for the first-cut ICS desktop editor.
 */
public final class App {
    private App() {
    }

    /**
     * Launches the Swing desktop application.
     *
     * @param args command line arguments; currently unused.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default Swing look and feel if the system theme is unavailable.
            }

            LocalRepository repository = new LocalRepository();
            AppData data = repository.loadOrDefault();
            PdfExportService exportService = new PdfExportService(
                    new Ics201PdfRenderer(),
                    new Ics202PdfRenderer(),
                    new Ics204PdfRenderer(),
                    new Ics214PdfRenderer(),
                    new SarTaskAssignmentPdfRenderer(),
                    new ClueLogPdfRenderer()
            );
            MainFrame frame = new MainFrame(data, repository, exportService, new IncidentValidator(), Path.of(System.getProperty("user.home")));
            frame.setVisible(true);
        });
    }
}
