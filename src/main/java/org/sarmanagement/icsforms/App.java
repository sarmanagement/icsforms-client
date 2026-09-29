package org.sarmanagement.icsforms;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.pdf.ClueLogPdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics201PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics205aPdfRenderer;
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
import javax.swing.JOptionPane;
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
	 * @param args
	 *            command line arguments; currently unused.
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
				// Fall back to the default Swing look and feel if the system theme is
				// unavailable.
			}

			Path homeDir = Path.of(System.getProperty("user.home"));
			Path defaultDir = homeDir.resolve(".icsforms");

			IncidentStore store = new FileIncidentStore(defaultDir);
			StartupDialog startup = new StartupDialog(null, store, true);
			startup.setVisible(true);

			// The dialog is modal; execution continues here after it is dismissed.
			StartupDialog.StartupAction action = startup.getChosenAction();
			if (action == null) {
				// Window was closed without a selection; release store resources.
				store.close();
				return;
			}

			AppData data;
			IncidentId id;
			try {
				switch (action) {
					case OPEN_EXISTING, OPEN_NEW_PERIOD -> {
						id = startup.getChosenIncidentId();
						if (store instanceof FileIncidentStore fileStore
								&& (fileStore.isLockedElsewhere(id) || !fileStore.lock(id))) {
							throw new StoreConflictException("Incident is open in another window: " + id.value());
						}
						data = store.load(id);
						if (action == StartupDialog.StartupAction.OPEN_NEW_PERIOD) {
							data.advanceToNewOperationalPeriod();
							store.save(id, data);
						}
					}
					default -> {
						data = new AppData();
						IapPhase phase = switch (action) {
							case NEW_INITIAL_RESPONSE -> IapPhase.INITIAL_RESPONSE;
							case NEW_OPERATIONAL_PERIOD -> IapPhase.DURING_OP;
							default -> IapPhase.PRE_OP;
						};
						data.setIapPhase(phase);
						data.setIncidentMode(startup.getChosenMode());
						id = store.create(data);
						if (store instanceof FileIncidentStore fileStore && !fileStore.lock(id)) {
							throw new StoreConflictException("Incident is open in another window: " + id.value());
						}
					}
				}
			} catch (RuntimeException exception) {
				JOptionPane.showMessageDialog(null, exception.getMessage(),
						exception instanceof StoreConflictException ? "Incident already open" : "Unable to open incident",
						JOptionPane.ERROR_MESSAGE);
				store.close();
				return;
			}

			PdfExportService exportService = new PdfExportService(new Ics201PdfRenderer(), new Ics202PdfRenderer(),
					new Ics205aPdfRenderer(), new Ics207PdfRenderer(), new Ics204PdfRenderer(), new Ics214PdfRenderer(),
					new SarTaskAssignmentPdfRenderer(), new ClueLogPdfRenderer());
			try {
				MainFrame frame = new MainFrame(data, store, id, exportService, new IncidentValidator(), homeDir);
				frame.setVisible(true);
			} catch (RuntimeException exception) {
				JOptionPane.showMessageDialog(null, exception.getMessage(), "Unable to start editor",
						JOptionPane.ERROR_MESSAGE);
				store.close();
			}
		});
	}
}
