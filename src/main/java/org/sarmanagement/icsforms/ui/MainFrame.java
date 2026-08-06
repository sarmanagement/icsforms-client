package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Swing frame for editing shared incident data, ICS 202, ICS 204, and SAR task scaffolds.
 */
public class MainFrame extends JFrame {
    private final AppController controller;
    private final JLabel validationLabel = new JLabel("Ready", SwingConstants.LEFT);
    private final IncidentContextPanel incidentContextPanel;
    private final OrganizationalChartPanel organizationalChartPanel;
    private final Ics202Panel ics202Panel;
    private final Ics204Panel ics204Panel;
    private final SarTaskPanel sarTaskPanel;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<java.awt.Component, AppController.LinkSource> tabSources = new IdentityHashMap<>();
    private int lastSelectedTabIndex;

    /**
     * Creates the main application frame.
     *
     * @param data initial data.
     * @param repository persistence repository.
     * @param exportService PDF export service.
     * @param validator validation service.
     * @param defaultDirectory default directory for file chooser startup.
     */
    public MainFrame(AppData data, LocalRepository repository, PdfExportService exportService, IncidentValidator validator, Path defaultDirectory) {
        super("ICS Forms Desktop");
        this.controller = new AppController(data, repository, exportService, validator);
        this.incidentContextPanel = new IncidentContextPanel(controller);
        this.organizationalChartPanel = new OrganizationalChartPanel(controller);
        this.ics202Panel = new Ics202Panel(controller);
        this.ics204Panel = new Ics204Panel(controller);
        this.sarTaskPanel = new SarTaskPanel(controller);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1120, 820));
        setJMenuBar(createMenuBar(defaultDirectory));
        setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabs.addTab("Shared", incidentContextPanel);
        tabSources.put(incidentContextPanel, AppController.LinkSource.SHARED);
        tabs.addTab("Org Chart", organizationalChartPanel);
        tabSources.put(organizationalChartPanel, AppController.LinkSource.ORG_CHART);
        tabs.addTab("ICS 202", ics202Panel);
        tabSources.put(ics202Panel, AppController.LinkSource.ICS202);
        tabs.addTab("ICS 204", ics204Panel);
        tabSources.put(ics204Panel, AppController.LinkSource.ICS204);
        tabs.addTab("SAR Tasks", sarTaskPanel);
        tabs.addChangeListener(event -> {
            int selectedIndex = tabs.getSelectedIndex();
            if (selectedIndex == lastSelectedTabIndex) {
                return;
            }
            pushToModel(linkSourceForTab(lastSelectedTabIndex));
            refreshFromModel();
            lastSelectedTabIndex = selectedIndex;
        });
        content.add(tabs, BorderLayout.CENTER);

        validationLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(content, BorderLayout.CENTER);
        add(validationLabel, BorderLayout.SOUTH);
        refreshFromModel();
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Builds the application menu for file and export workflows.
     *
     * @param defaultDirectory default chooser directory.
     * @return configured menu bar.
     */
    private JMenuBar createMenuBar(Path defaultDirectory) {
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu exportMenu = new JMenu("Export");

        JMenuItem newItem = new JMenuItem("New");
        newItem.addActionListener(event -> {
            controller.newDocument();
            refreshFromModel();
        });

        JMenuItem openItem = new JMenuItem("Open…");
        openItem.addActionListener(event -> chooseFile(defaultDirectory, false, path -> {
            controller.open(path);
            refreshFromModel();
        }));

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(event -> {
            pushToModel();
            controller.save();
            refreshStatus();
        });

        JMenuItem saveAsItem = new JMenuItem("Save As…");
        saveAsItem.addActionListener(event -> chooseFile(defaultDirectory, true, path -> {
            pushToModel();
            controller.saveAs(path);
            refreshStatus();
        }));

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(event -> {
            pushToModel();
            controller.save();
            dispose();
        });

        JMenuItem export202Item = new JMenuItem("Export ICS 202 PDF…");
        export202Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 202"));

        JMenuItem export204Item = new JMenuItem("Export ICS 204 PDF…");
        export204Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 204"));

        JMenuItem exportAllItem = new JMenuItem("Export All PDFs…");
        exportAllItem.addActionListener(event -> chooseDirectory(defaultDirectory, directory -> {
            if (!handleValidationBeforeExport()) {
                return;
            }
            pushToModel();
            try {
                controller.exportAll(directory);
                JOptionPane.showMessageDialog(this, "Exported ICS 202 and ICS 204 PDFs to\n" + directory, "Export complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException exception) {
                showError("Failed to export PDFs", exception);
            }
        }));

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        exportMenu.add(export202Item);
        exportMenu.add(export204Item);
        exportMenu.add(exportAllItem);
        bar.add(fileMenu);
        bar.add(exportMenu);
        return bar;
    }

    /**
     * Exports a single form after validation.
     *
     * @param defaultDirectory chooser starting directory.
     * @param formKey form to export.
     */
    private void exportOne(Path defaultDirectory, String formKey) {
        chooseDirectory(defaultDirectory, directory -> {
            if (!handleValidationBeforeExport()) {
                return;
            }
            pushToModel();
            try {
                Path output = controller.exportSelected(formKey, directory);
                JOptionPane.showMessageDialog(this, "Exported " + formKey + " to\n" + output, "Export complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException exception) {
                showError("Failed to export " + formKey, exception);
            }
        });
    }

    /**
     * Pushes UI edits into the shared incident document.
     */
    private void pushToModel() {
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
    }

    private void pushToModel(AppController.LinkSource source) {
        incidentContextPanel.pushToModel();
        organizationalChartPanel.pushToModel();
        ics202Panel.pushToModel();
        ics204Panel.pushToModel();
        sarTaskPanel.refreshTable();
        controller.markDirty(source);
    }

    /**
     * Reloads panel state from the active incident document.
     */
    private void refreshFromModel() {
        incidentContextPanel.refreshFromModel();
        organizationalChartPanel.refreshFromModel();
        ics202Panel.refreshFromModel();
        ics204Panel.refreshFromModel();
        sarTaskPanel.refreshTable();
        refreshStatus();
    }

    private AppController.LinkSource linkSourceForTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabs.getTabCount()) {
            return AppController.LinkSource.NONE;
        }
        return tabSources.getOrDefault(tabs.getComponentAt(tabIndex), AppController.LinkSource.NONE);
    }

    /**
     * Updates status text and unsaved indicator.
     */
    private void refreshStatus() {
        List<ValidationMessage> messages = controller.validate();
        validationLabel.setText(messages.isEmpty() ? "Ready" : messages.get(0).message());
        setTitle((controller.isDirty() ? "* " : "") + "ICS Forms Desktop");
    }

    /**
     * Validates before export and shows a blocking summary when required fields are missing.
     *
     * @return {@code true} when export may continue.
     */
    private boolean handleValidationBeforeExport() {
        pushToModel();
        List<ValidationMessage> messages = controller.validate();
        refreshStatus();
        if (!messages.isEmpty()) {
            StringBuilder builder = new StringBuilder("Please resolve the following before export:\n\n");
            for (ValidationMessage message : messages) {
                if (message.field().equals("incidentName") || message.field().equals("operationalPeriodStart")
                        || message.field().equals("operationalPeriodEnd") || message.field().equals("operationalPeriod")) {
                    builder.append("- ").append(message.message()).append('\n');
                }
            }
            if (builder.toString().equals("Please resolve the following before export:\n\n")) {
                return true;
            }
            JOptionPane.showMessageDialog(this, builder.toString(), "Validation required", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Opens a file chooser for save/open operations.
     *
     * @param defaultDirectory chooser starting directory.
     * @param saveMode whether chooser is for save.
     * @param consumer callback for selected path.
     */
    private void chooseFile(Path defaultDirectory, boolean saveMode, java.util.function.Consumer<Path> consumer) {
        JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
        int result = saveMode ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            consumer.accept(chooser.getSelectedFile().toPath());
        }
    }

    /**
     * Opens a directory chooser.
     *
     * @param defaultDirectory chooser starting directory.
     * @param consumer callback for selected directory.
     */
    private void chooseDirectory(Path defaultDirectory, java.util.function.Consumer<Path> consumer) {
        JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            consumer.accept(chooser.getSelectedFile().toPath());
        }
    }

    /**
     * Shows an error dialog for unexpected failures.
     *
     * @param title dialog title.
     * @param exception failure to display.
     */
    private void showError(String title, Exception exception) {
        JOptionPane.showMessageDialog(this, title + ":\n" + exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
