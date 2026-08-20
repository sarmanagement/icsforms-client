package org.sarmanagement.icsforms;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.ClueLogPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics201PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics207PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics214PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.ui.MainFrame;
import org.sarmanagement.icsforms.ui.StartupDialog;
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

            Path homeDir = Path.of(System.getProperty("user.home"));
            Path defaultDir = homeDir.resolve(".icsforms");

            StartupDialog startup = new StartupDialog(null, defaultDir, true);
            startup.setVisible(true);

            // The dialog is modal; execution continues here after it is dismissed.
            StartupDialog.StartupAction action = startup.getChosenAction();
            if (action == null) {
                // Window was closed without a selection — exit already triggered by windowClosing.
                return;
            }

            LocalRepository repository = new LocalRepository();
            AppData data;

            switch (action) {
                case OPEN_EXISTING -> data = new LocalRepository(startup.getChosenPath()).loadOrDefault();
                case OPEN_NEW_PERIOD -> {
                    data = new LocalRepository(startup.getChosenPath()).loadOrDefault();
                    data.advanceToNewOperationalPeriod();
                }
                default -> {
                    // New-incident actions — use a blank document with the requested phase.
                    data = new AppData();
                    IapPhase phase = switch (action) {
                        case NEW_INITIAL_RESPONSE -> IapPhase.INITIAL_RESPONSE;
                        case NEW_OPERATIONAL_PERIOD -> IapPhase.DURING_OP;
                        default -> IapPhase.PRE_OP;
                    };
                    data.setIapPhase(phase);
                }
            }

            data.setIncidentMode(startup.getChosenMode());

            PdfExportService exportService = new PdfExportService(
                    new Ics201PdfRenderer(),
                    new Ics202PdfRenderer(),
                    new Ics207PdfRenderer(),
                    new Ics204PdfRenderer(),
                    new Ics214PdfRenderer(),
                    new SarTaskAssignmentPdfRenderer(),
                    new ClueLogPdfRenderer()
            );
            MainFrame frame = new MainFrame(data, repository, exportService, new IncidentValidator(), homeDir);
            frame.setVisible(true);
        });
    }
}
