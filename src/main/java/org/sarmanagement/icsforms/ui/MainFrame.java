package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import org.sarmanagement.icsforms.validation.ValidationMessage;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Swing frame for editing shared incident data, ICS 202, ICS 204, and SAR task scaffolds.
 */
public class MainFrame extends JFrame {
    private static final String CORE_GROUP = "Core";
    private static final String ACTIVITY_LOGS_GROUP = "Activity Logs";
    /** Tab group for SAR-specific features hidden when mode is Generic. */
    private static final String SAR_ONLY_GROUP = "SAR Features";
    /** Tab group for T-Card (ICS 219) resource status tabs. */
    private static final String T_CARDS_GROUP = "T-Cards";

    private final AppController controller;
    private final JLabel validationLabel = new JLabel("Ready", SwingConstants.LEFT);
    private final IncidentContextPanel incidentContextPanel;
    private final OrganizationalChartPanel organizationalChartPanel;
    private final Ics201Panel ics201Panel;
    private final Ics202Panel ics202Panel;
    private final Ics204Panel ics204Panel;
    private final List<Ics214Panel> ics214Panels = new ArrayList<>();
    private final SarTaskPanel sarTaskPanel;
    private final ClueLogPanel clueLogPanel;
    private final TCardPanel tCardPanel;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<Component, AppController.LinkSource> tabSources = new IdentityHashMap<>();
    private final Map<Component, String> tabGroups = new IdentityHashMap<>();
    private final Map<Component, String> tabTitles = new IdentityHashMap<>();
    private final Map<String, Boolean> groupVisible = new LinkedHashMap<>();
    private int lastSelectedTabIndex = -1;
    private boolean rebuildingTabs;

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
        this.ics201Panel = new Ics201Panel(controller);
        this.ics202Panel = new Ics202Panel(controller);
        this.ics204Panel = new Ics204Panel(controller);
        this.sarTaskPanel = new SarTaskPanel(controller);
        this.clueLogPanel = new ClueLogPanel(controller);
        this.tCardPanel = new TCardPanel(controller);
        ics204Panel.setOn214Request(this::addOrOpenLog214ForResource);
        sarTaskPanel.setOn214Request(this::openIcs214ForSarTask);
        groupVisible.put(CORE_GROUP, true);
        groupVisible.put(ACTIVITY_LOGS_GROUP, true);
        groupVisible.put(SAR_ONLY_GROUP, controller.getIncidentMode() == IncidentMode.SAR);
        groupVisible.put(T_CARDS_GROUP, true);
        registerTab("Shared", incidentContextPanel, AppController.LinkSource.SHARED, CORE_GROUP);
        registerTab("Org Chart", organizationalChartPanel, AppController.LinkSource.ORG_CHART, CORE_GROUP);
        registerTab("ICS 201", ics201Panel, AppController.LinkSource.NONE, CORE_GROUP);
        registerTab("ICS 202", ics202Panel, AppController.LinkSource.ICS202, CORE_GROUP);
        registerTab("ICS 204", ics204Panel, AppController.LinkSource.ICS204, CORE_GROUP);
        registerTab("SAR Tasks", sarTaskPanel, AppController.LinkSource.NONE, SAR_ONLY_GROUP);
        registerTab("Clue Log", clueLogPanel, AppController.LinkSource.NONE, SAR_ONLY_GROUP);
        registerTab("T-Cards", tCardPanel, AppController.LinkSource.NONE, T_CARDS_GROUP);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1120, 820));
        setJMenuBar(createMenuBar(defaultDirectory));
        setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabs.addChangeListener(event -> {
            if (rebuildingTabs) {
                return;
            }
            int selectedIndex = tabs.getSelectedIndex();
            if (selectedIndex == lastSelectedTabIndex) {
                return;
            }
            if (lastSelectedTabIndex >= 0) {
                pushToModel(linkSourceForTab(lastSelectedTabIndex));
            }
            refreshFromModel();
            lastSelectedTabIndex = tabs.getSelectedIndex();
        });
        content.add(tabs, BorderLayout.CENTER);

        validationLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(content, BorderLayout.CENTER);
        add(validationLabel, BorderLayout.SOUTH);
        refreshFromModel();
        lastSelectedTabIndex = tabs.getSelectedIndex();
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
        JMenu viewMenu = new JMenu("View");
        JMenu exportMenu = new JMenu("Export");
        JMenu logsMenu = new JMenu("Logs");
        JMenu configMenu = new JMenu("Configuration");
        JMenu logsListMenu = new JMenu("Go to Log");

        JMenuItem newItem = new JMenuItem("New…");
        newItem.addActionListener(event -> {
            StartupDialog startup = new StartupDialog(MainFrame.this, defaultDirectory, false);
            startup.setVisible(true);
            StartupDialog.StartupAction action = startup.getChosenAction();
            if (action == null) {
                return; // cancelled
            }
            switch (action) {
                case OPEN_EXISTING -> {
                    controller.open(startup.getChosenPath());
                }
                case OPEN_NEW_PERIOD -> {
                    controller.open(startup.getChosenPath());
                    controller.newOperationalPeriod();
                }
                case NEW_PRE_OP, NEW_INITIAL_RESPONSE, NEW_OPERATIONAL_PERIOD -> {
                    controller.newDocument();
                    IapPhase phase = switch (action) {
                        case NEW_INITIAL_RESPONSE -> IapPhase.INITIAL_RESPONSE;
                        case NEW_OPERATIONAL_PERIOD -> IapPhase.DURING_OP;
                        case NEW_PRE_OP -> IapPhase.PRE_OP;
                        default -> IapPhase.PRE_OP;
                    };
                    controller.setIapPhase(phase);
                    controller.setIncidentMode(startup.getChosenMode());
                }
            }
            refreshFromModel();
        });

        JMenuItem openItem = new JMenuItem("Open…");
        openItem.addActionListener(event -> {
            IncidentPickerDialog picker = new IncidentPickerDialog(MainFrame.this, defaultDirectory);
            picker.setVisible(true);
            Path chosen = picker.getChosenPath();
            if (chosen != null) {
                controller.open(chosen);
                refreshFromModel();
            }
        });

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(event -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            pushToModel(source);
            controller.save(source);
            refreshStatus();
        });

        JMenuItem saveAsItem = new JMenuItem("Save As…");
        saveAsItem.addActionListener(event -> chooseFile(defaultDirectory, true, path -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            pushToModel(source);
            controller.saveAs(path, source);
            refreshStatus();
        }));

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(event -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            pushToModel(source);
            controller.save(source);
            dispose();
        });

        for (Map.Entry<String, Boolean> entry : groupVisible.entrySet()) {
            // CORE_GROUP is always visible; SAR_ONLY_GROUP is controlled exclusively by the
            // Incident Mode menu (Configuration > Incident Mode) and must not appear here.
            if (CORE_GROUP.equals(entry.getKey()) || SAR_ONLY_GROUP.equals(entry.getKey())) {
                continue;
            }
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(entry.getKey(), entry.getValue());
            item.addActionListener(event -> {
                groupVisible.put(entry.getKey(), item.isSelected());
                rebuildVisibleTabs();
                refreshStatus();
            });
            viewMenu.add(item);
        }

        JMenuItem export201Item = new JMenuItem("Export ICS 201 PDF…");
        export201Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 201"));

        JMenuItem export202Item = new JMenuItem("Export ICS 202 PDF…");
        export202Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 202"));

        JMenuItem export204Item = new JMenuItem("Export ICS 204 PDF…");
        export204Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 204"));

        JMenuItem exportSarTaskItem = new JMenuItem("Export SAR Task Assignment PDF…");
        exportSarTaskItem.addActionListener(event -> exportOne(defaultDirectory, "SAR Task Assignment"));

        JMenuItem export214Item = new JMenuItem("Export ICS 214 PDF…");
        export214Item.addActionListener(event -> exportOne(defaultDirectory, "ICS 214"));

        JMenuItem exportClueLogItem = new JMenuItem("Export Clue Log PDF…");
        exportClueLogItem.addActionListener(event -> exportOne(defaultDirectory, "Clue Log"));

        JMenuItem exportAllItem = new JMenuItem("Export All PDFs…");
        exportAllItem.addActionListener(event -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            chooseDirectory(defaultDirectory, directory -> {
                if (!handleValidationBeforeExport()) {
                    return;
                }
                pushToModel(source);
                try {
                    controller.exportAll(directory, source);
                    JOptionPane.showMessageDialog(this, "Exported available PDFs to\n" + directory, "Export complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException exception) {
                    showError("Failed to export PDFs", exception);
                }
            });
        });

        JMenuItem exportIapBundleItem = new JMenuItem("Export IAP Bundle (all PDFs merged)…");
        exportIapBundleItem.addActionListener(event -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            chooseDirectory(defaultDirectory, directory -> {
                if (!handleValidationBeforeExport()) {
                    return;
                }
                pushToModel(source);
                try {
                    Path bundlePath = controller.exportIapBundle(directory, source);
                    JOptionPane.showMessageDialog(this, "Exported IAP bundle to\n" + bundlePath, "Export complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException exception) {
                    showError("Failed to export IAP bundle", exception);
                }
            });
        });

        JMenuItem exportResourcesCsvItem = new JMenuItem("Export Resources as CSV…");
        exportResourcesCsvItem.addActionListener(event -> tCardPanel.exportToCsv());

        JMenuItem addLogItem = new JMenuItem("Add Log");
        addLogItem.addActionListener(event -> addLog());

        JMenuItem removeCurrentLogItem = new JMenuItem("Remove Current Log");
        removeCurrentLogItem.addActionListener(event -> removeCurrentLog());

        logsMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                rebuildLogsListMenu(logsListMenu);
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        });

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        exportMenu.add(export201Item);
        exportMenu.add(export202Item);
        exportMenu.add(export204Item);
        exportMenu.add(export214Item);
        exportMenu.add(exportSarTaskItem);
        exportMenu.add(exportClueLogItem);
        exportMenu.add(exportResourcesCsvItem);
        exportMenu.add(exportAllItem);
        exportMenu.addSeparator();
        exportMenu.add(exportIapBundleItem);
        logsMenu.add(addLogItem);
        logsMenu.add(removeCurrentLogItem);
        logsMenu.addSeparator();
        logsMenu.add(logsListMenu);
        JMenuItem manageEventTypesItem = new JMenuItem("Manage Event Types…");
        manageEventTypesItem.addActionListener(event -> manageEventTypes());
        configMenu.add(manageEventTypesItem);

        JMenu modeMenu = new JMenu("Incident Mode");
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButtonMenuItem sarModeItem = new JRadioButtonMenuItem("SAR Mode", controller.getIncidentMode() == IncidentMode.SAR);
        JRadioButtonMenuItem genericModeItem = new JRadioButtonMenuItem("Generic Incident Mode", controller.getIncidentMode() == IncidentMode.GENERIC);
        sarModeItem.addActionListener(e -> setIncidentMode(IncidentMode.SAR));
        genericModeItem.addActionListener(e -> setIncidentMode(IncidentMode.GENERIC));
        modeGroup.add(sarModeItem);
        modeGroup.add(genericModeItem);
        modeMenu.add(sarModeItem);
        modeMenu.add(genericModeItem);
        configMenu.addSeparator();
        configMenu.add(modeMenu);

        JMenu iapPhaseMenu = new JMenu("IAP Phase");
        ButtonGroup phaseGroup = new ButtonGroup();
        JRadioButtonMenuItem preOpItem  = new JRadioButtonMenuItem("Pre-Operational (planning)",
                controller.getIapPhase() == IapPhase.PRE_OP);
        JRadioButtonMenuItem initialResponseItem = new JRadioButtonMenuItem("Initial Incident Response",
                controller.getIapPhase() == IapPhase.INITIAL_RESPONSE);
        JRadioButtonMenuItem duringOpItem = new JRadioButtonMenuItem("Subsequent Operational Period",
                controller.getIapPhase() == IapPhase.DURING_OP);
        preOpItem.addActionListener(e -> { controller.setIapPhase(IapPhase.PRE_OP); refreshFromModel(); });
        initialResponseItem.addActionListener(e -> { controller.setIapPhase(IapPhase.INITIAL_RESPONSE); refreshFromModel(); });
        duringOpItem.addActionListener(e -> { controller.setIapPhase(IapPhase.DURING_OP); refreshFromModel(); });
        phaseGroup.add(preOpItem);
        phaseGroup.add(initialResponseItem);
        phaseGroup.add(duringOpItem);
        iapPhaseMenu.add(preOpItem);
        iapPhaseMenu.add(initialResponseItem);
        iapPhaseMenu.add(duringOpItem);
        configMenu.addSeparator();
        configMenu.add(iapPhaseMenu);

        bar.add(fileMenu);
        bar.add(viewMenu);
        bar.add(exportMenu);
        bar.add(logsMenu);
        bar.add(configMenu);
        return bar;
    }

    /**
     * Exports a single form after validation.
     *
     * @param defaultDirectory chooser starting directory.
     * @param formKey form to export.
     */
    private void exportOne(Path defaultDirectory, String formKey) {
        AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
        chooseDirectory(defaultDirectory, directory -> {
            if (!handleValidationBeforeExport()) {
                return;
            }
            pushToModel(source);
            try {
                Path output = controller.exportSelected(formKey, directory, source);
                JOptionPane.showMessageDialog(this, "Exported " + formKey + " to\n" + output, "Export complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException exception) {
                showError("Failed to export " + formKey, exception);
            }
        });
    }

    private void pushToModel(AppController.LinkSource source) {
        incidentContextPanel.pushToModel();
        organizationalChartPanel.pushToModel();
        ics201Panel.pushToModel();
        ics202Panel.pushToModel();
        ics204Panel.pushToModel();
        for (Ics214Panel panel : ics214Panels) {
            panel.saveToModel();
        }
        sarTaskPanel.pushToModel();
        clueLogPanel.pushToModel();
        tCardPanel.pushToModel();
        controller.markDirty(source);
    }

    private void ensureLogs(AppData data) {
        if (data.getActivityLogs().isEmpty() && data.getIapPhase() != IapPhase.PRE_OP) {
            Ics214Form form = new Ics214Form();
            form.setPreparedByName(data.getForm204().getPreparedByName());
            form.setPreparedByPositionTitle(data.getForm204().getPreparedByPositionTitle());
            form.setPreparedDateTime(data.getForm204().getPreparedDateTime());
            data.getActivityLogs().add(form);
        }
    }

    private void rebuildLogTabs() {
        rebuildingTabs = true;
        try {
            for (Ics214Panel panel : new ArrayList<>(ics214Panels)) {
                tabs.remove(panel);
                tabSources.remove(panel);
                tabGroups.remove(panel);
                tabTitles.remove(panel);
            }
            ics214Panels.clear();
            AppData data = controller.getData();
            for (Ics214Form form : data.getActivityLogs()) {
                Ics214Panel panel = new Ics214Panel(controller);
                panel.loadFromModel(form, data);
                ics214Panels.add(panel);
                registerTab(shortLogTabTitle(form, data), panel, AppController.LinkSource.ICS214, ACTIVITY_LOGS_GROUP);
            }
        } finally {
            rebuildingTabs = false;
        }
    }

    /** Reloads panel state from the active incident document. */
    private void refreshFromModel() {
        Component selectedComponent = tabs.getSelectedComponent();
        int selectedLogIndex = selectedLogIndex(selectedComponent);
        incidentContextPanel.refreshFromModel();
        organizationalChartPanel.refreshFromModel();
        ics201Panel.refreshFromModel();
        ics202Panel.refreshFromModel();
        ics204Panel.refreshFromModel();
        ensureLogs(controller.getData());
        rebuildLogTabs();
        sarTaskPanel.refreshFromModel();
        clueLogPanel.refreshFromModel();
        tCardPanel.refreshFromModel();
        // Keep SAR-only tabs visible only in SAR mode.
        groupVisible.put(SAR_ONLY_GROUP, controller.getIncidentMode() == IncidentMode.SAR);
        rebuildVisibleTabs(selectedComponent, selectedLogIndex);
        refreshStatus();
    }

    private void rebuildVisibleTabs() {
        rebuildVisibleTabs(tabs.getSelectedComponent(), selectedLogIndex(tabs.getSelectedComponent()));
    }

    private void rebuildVisibleTabs(Component preferredComponent, int preferredLogIndex) {
        rebuildingTabs = true;
        try {
            tabs.removeAll();
            addVisibleTab(incidentContextPanel);
            addVisibleTab(organizationalChartPanel);
            addVisibleTab(ics201Panel);
            addVisibleTab(ics202Panel);
            addVisibleTab(ics204Panel);
            // sarTaskPanel and clueLogPanel belong to SAR_ONLY_GROUP; addVisibleTab() checks
            // group visibility and skips them automatically in GENERIC mode.
            addVisibleTab(sarTaskPanel);
            // ICS-214 activity log tabs are to the right of SAR Tasks.
            if (isGroupVisible(ACTIVITY_LOGS_GROUP)) {
                for (Ics214Panel panel : ics214Panels) {
                    addVisibleTab(panel);
                }
            }
            addVisibleTab(clueLogPanel);
            addVisibleTab(tCardPanel);
            Component selection = resolveSelection(preferredComponent, preferredLogIndex);
            if (selection != null && tabs.indexOfComponent(selection) >= 0) {
                tabs.setSelectedComponent(selection);
            } else if (tabs.getTabCount() > 0) {
                tabs.setSelectedIndex(0);
            }
        } finally {
            rebuildingTabs = false;
        }
        lastSelectedTabIndex = tabs.getSelectedIndex();
    }

    private Component resolveSelection(Component preferredComponent, int preferredLogIndex) {
        if (preferredLogIndex >= 0 && preferredLogIndex < ics214Panels.size() && isGroupVisible(ACTIVITY_LOGS_GROUP)) {
            return ics214Panels.get(preferredLogIndex);
        }
        if (preferredComponent != null && tabs.indexOfComponent(preferredComponent) >= 0) {
            return preferredComponent;
        }
        return incidentContextPanel;
    }

    private void addVisibleTab(Component component) {
        String group = tabGroups.getOrDefault(component, CORE_GROUP);
        if (!CORE_GROUP.equals(group) && !isGroupVisible(group)) {
            return;
        }
        tabs.addTab(tabTitles.getOrDefault(component, ""), component);
    }

    private boolean isGroupVisible(String group) {
        return groupVisible.getOrDefault(group, true);
    }

    private void registerTab(String title, Component component, AppController.LinkSource source, String group) {
        tabSources.put(component, source);
        tabGroups.put(component, group);
        tabTitles.put(component, title);
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
        List<ValidationMessage> messages = controller.validate(linkSourceForTab(tabs.getSelectedIndex()));
        validationLabel.setText(messages.isEmpty() ? "Ready" : messages.get(0).message());
        setTitle((controller.isDirty() ? "* " : "") + "ICS Forms Desktop");
    }

    /**
     * Validates before export and shows a blocking summary when required fields are missing.
     *
     * @return {@code true} when export may continue.
     */
    private boolean handleValidationBeforeExport() {
        AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
        pushToModel(source);
        List<ValidationMessage> messages = controller.validate(source);
        refreshStatus();
        if (!messages.isEmpty()) {
            StringBuilder builder = new StringBuilder("Please resolve the following before export:\n\n");
            for (ValidationMessage message : messages) {
                if (message.field().equals("incidentName") || message.field().equals("operationalPeriodStart")
                        || message.field().equals("operationalPeriodEnd") || message.field().equals("operationalPeriod")
                        || message.field().contains("assignmentTeamNumber")) {
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

    private void manageEventTypes() {
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
        AppData data = controller.getData();
        List<ActivityEventType> initial = data.getActivityEventTypes().isEmpty()
                ? ActivityEventType.defaultTypes() : new ArrayList<>(data.getActivityEventTypes());
        Ics214Panel.EventTypeManagerDialog dialog = new Ics214Panel.EventTypeManagerDialog(initial);
        JScrollPane scrollPane = new JScrollPane(dialog.panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (UiSupport.showResizableConfirmDialog(this, "Manage event types", scrollPane, new Dimension(480, 380))) {
            data.setActivityEventTypes(dialog.getEventTypes());
            controller.markDirty(AppController.LinkSource.ICS214);
            for (Ics214Panel panel : ics214Panels) {
                panel.refreshEventTypes();
            }
        }
    }

    /**
     * Switches the incident operational mode and refreshes SAR-only tab visibility.
     *
     * @param mode new incident mode.
     */
    private void setIncidentMode(IncidentMode mode) {
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
        controller.setIncidentMode(mode);
        groupVisible.put(SAR_ONLY_GROUP, mode == IncidentMode.SAR);
        rebuildVisibleTabs();
        refreshStatus();
    }

    /**
     * Opens or creates the ICS 214 activity log for the given SAR task assignment.
     *
     * @param task SAR task assignment to open the log for.
     */
    private void openIcs214ForSarTask(SarTaskAssignment task) {
        if (task == null) {
            return;
        }
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
        AppData data = controller.getData();
        String assignmentId = task.getAssignmentId();
        for (int i = 0; i < data.getActivityLogs().size(); i++) {
            Ics214Form existing = data.getActivityLogs().get(i);
            if (assignmentId.equals(existing.getLinkedSarTaskAssignmentId())) {
                groupVisible.put(ACTIVITY_LOGS_GROUP, true);
                rebuildVisibleTabs(ics214Panels.get(i), i);
                if (tabs.indexOfComponent(ics214Panels.get(i)) >= 0) {
                    tabs.setSelectedComponent(ics214Panels.get(i));
                }
                return;
            }
        }
        // No existing log – create one linked to this SAR task.
        Ics214Form form = new Ics214Form();
        form.setLinkedSarTaskAssignmentId(assignmentId);
        form.setName(task.getResourceIdentifier());
        form.setResourcesAssigned(new ArrayList<>(task.getResourcesAssigned()));
        if (!task.getResourcesAssigned().isEmpty()) {
            form.setIcsPosition(task.getResourcesAssigned().get(0).getIcsPosition());
        }
        data.getActivityLogs().add(form);
        controller.markDirty(AppController.LinkSource.ICS214);
        rebuildLogTabs();
        groupVisible.put(ACTIVITY_LOGS_GROUP, true);
        int newIndex = data.getActivityLogs().size() - 1;
        rebuildVisibleTabs(newIndex < ics214Panels.size() ? ics214Panels.get(newIndex) : null, newIndex);
        refreshStatus();
    }

    private void addOrOpenLog214ForResource(ResourceAssignment resource) {
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
        AppData data = controller.getData();
        String assignmentId = resource.getAssignmentId();
        for (int i = 0; i < data.getActivityLogs().size(); i++) {
            Ics214Form existing = data.getActivityLogs().get(i);
            if (assignmentId.equals(existing.getLinkedSarTaskAssignmentId())) {
                groupVisible.put(ACTIVITY_LOGS_GROUP, true);
                rebuildVisibleTabs(ics214Panels.get(i), i);
                if (tabs.indexOfComponent(ics214Panels.get(i)) >= 0) {
                    tabs.setSelectedComponent(ics214Panels.get(i));
                }
                return;
            }
        }
        Ics214Form form = new Ics214Form();
        form.setLinkedSarTaskAssignmentId(assignmentId);
        String resourceName = resource.getResourceIdentifier();
        form.setName(resourceName);
        // Look up the linked SAR task assignment to populate section 6 and derive the leader's ICS position.
        data.getSarTaskAssignments().stream()
                .filter(a -> assignmentId.equals(a.getAssignmentId()))
                .findFirst()
                .ifPresentOrElse(
                        taf -> {
                            form.setResourcesAssigned(new java.util.ArrayList<>(taf.getResourcesAssigned()));
                            taf.getResourcesAssigned().stream()
                                    .filter(r -> resource.getLeaderRole().equalsIgnoreCase(r.getFunction())
                                            || resource.getLeaderRole().equalsIgnoreCase(r.getIcsPosition()))
                                    .findFirst()
                                    .ifPresentOrElse(
                                            leader -> form.setIcsPosition(leader.getIcsPosition()),
                                            () -> form.setIcsPosition(resource.getLeaderRole()));
                        },
                        () -> form.setIcsPosition(resource.getLeaderRole()));
        data.getActivityLogs().add(form);
        controller.markDirty(AppController.LinkSource.ICS214);
        rebuildLogTabs();
        groupVisible.put(ACTIVITY_LOGS_GROUP, true);
        int newIndex = data.getActivityLogs().size() - 1;
        rebuildVisibleTabs(newIndex < ics214Panels.size() ? ics214Panels.get(newIndex) : null, newIndex);
        refreshStatus();
    }

    private void addLog() {
        pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
        AppData data = controller.getData();
        ensureLogs(data);
        javax.swing.JComboBox<ActivityLogScope> scopeCombo = new javax.swing.JComboBox<>(ActivityLogScope.values());
        javax.swing.JComboBox<AssociableDocument> linkedDocCombo = new javax.swing.JComboBox<>();
        documentsForScope(data, ActivityLogScope.ICP).forEach(linkedDocCombo::addItem);
        JPanel input = UiSupport.formPanel();
        UiSupport.addRow(input, 0, "Scope", scopeCombo);
        UiSupport.addRow(input, 1, "Associated document", linkedDocCombo);
        scopeCombo.addActionListener(event -> {
            ActivityLogScope selected = (ActivityLogScope) scopeCombo.getSelectedItem();
            linkedDocCombo.removeAllItems();
            documentsForScope(data, selected).forEach(linkedDocCombo::addItem);
        });
        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(input);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Add Activity Log", scrollPane, new Dimension(480, 160))) {
            return;
        }
        Ics214Form form = new Ics214Form();
        AssociableDocument selectedDoc = (AssociableDocument) linkedDocCombo.getSelectedItem();
        if (selectedDoc != null) {
            if (!selectedDoc.ics204FormId().isBlank()) {
                form.setLinkedIcs204FormId(selectedDoc.ics204FormId());
            } else if (!selectedDoc.sarTaskAssignmentId().isBlank()) {
                form.setLinkedSarTaskAssignmentId(selectedDoc.sarTaskAssignmentId());
            }
        }
        data.getActivityLogs().add(form);
        controller.markDirty(AppController.LinkSource.ICS214);
        rebuildLogTabs();
        groupVisible.put(ACTIVITY_LOGS_GROUP, true);
        int newIndex = data.getActivityLogs().size() - 1;
        rebuildVisibleTabs(newIndex < ics214Panels.size() ? ics214Panels.get(newIndex) : null, newIndex);
        refreshStatus();
    }

    private void removeCurrentLog() {
        if (!(tabs.getSelectedComponent() instanceof Ics214Panel selectedPanel)) {
            JOptionPane.showMessageDialog(this,
                    "Select an ICS 214 activity log tab first.",
                    "Cannot Remove",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        AppData data = controller.getData();
        ensureLogs(data);
        int logIndex = ics214Panels.indexOf(selectedPanel);
        if (logIndex < 0) {
            return;
        }
        if (data.getActivityLogs().size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "At least one activity log must remain.",
                    "Cannot Remove",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Ics214Form active = data.getActivityLogs().get(logIndex);
        if (!active.getActivityLog().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Cannot remove a log that contains activity entries. Remove all entries first.",
                    "Cannot Remove",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove the currently selected activity log?",
                "Remove Log",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        data.getActivityLogs().remove(logIndex);
        controller.markDirty(AppController.LinkSource.ICS214);
        rebuildLogTabs();
        int newIndex = Math.max(0, logIndex - 1);
        rebuildVisibleTabs(newIndex < ics214Panels.size() ? ics214Panels.get(newIndex) : incidentContextPanel, newIndex);
        refreshStatus();
    }

    private void rebuildLogsListMenu(JMenu logsListMenu) {
        logsListMenu.removeAll();
        ButtonGroup group = new ButtonGroup();
        int selectedLogIndex = selectedLogIndex(tabs.getSelectedComponent());
        List<Ics214Form> logs = controller.getData().getActivityLogs();
        for (int i = 0; i < logs.size(); i++) {
            final int logIndex = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(logMenuLabel(logs.get(i)), i == selectedLogIndex);
            item.addActionListener(event -> {
                groupVisible.put(ACTIVITY_LOGS_GROUP, true);
                rebuildVisibleTabs(ics214Panels.get(logIndex), logIndex);
                if (tabs.indexOfComponent(ics214Panels.get(logIndex)) >= 0) {
                    tabs.setSelectedComponent(ics214Panels.get(logIndex));
                }
            });
            group.add(item);
            logsListMenu.add(item);
        }
        logsListMenu.setEnabled(!logs.isEmpty());
    }

    private int selectedLogIndex(Component component) {
        if (component instanceof Ics214Panel panel) {
            return ics214Panels.indexOf(panel);
        }
        return -1;
    }

    private String logMenuLabel(Ics214Form form) {
        String linked = resolveLinkedDocumentLabel(form, controller.getData());
        if (linked.isBlank()) {
            return form.getLogScope().getLabel();
        }
        return form.getLogScope().getLabel() + " – " + linked;
    }

    private static String shortLogTabTitle(Ics214Form form, AppData data) {
        // For resource-linked logs (TASK_ASSIGNMENT), identify the tab by the resource name (section 3)
        // so it reads "ICS 214 – Team X" matching the Resource column in the ICS 204 grid.
        if (form.getLogScope() == ActivityLogScope.TASK_ASSIGNMENT) {
            String name = form.getName();
            return name.isBlank() ? "ICS 214 – Task" : "ICS 214 – " + truncate(name, 20);
        }
        String scope = switch (form.getLogScope()) {
            case ICP -> "ICP";
            case ASSIGNMENT_LIST -> "Asmt";
            default -> "";
        };
        String linked = truncate(resolveLinkedDocumentLabel(form, data), 10);
        return linked.isBlank() ? "ICS 214 – " + scope : "ICS 214 – " + scope + " " + linked;
    }

    /**
     * Resolves the display label for the document typed-linked to the given form.
     * Returns an empty string for ICP-level logs.
     */
    private static String resolveLinkedDocumentLabel(Ics214Form form, AppData data) {
        if (data == null) {
            return "";
        }
        String ics204Id = form.getLinkedIcs204FormId();
        if (!ics204Id.isBlank()) {
            if (ics204Id.equals(data.getForm204().getFormId())) {
                return labelStringFor204(data.getForm204(), 1);
            }
            List<Ics204Form> additional = data.getAdditionalForms204();
            for (int i = 0; i < additional.size(); i++) {
                if (ics204Id.equals(additional.get(i).getFormId())) {
                    return labelStringFor204(additional.get(i), i + 2);
                }
            }
            return "";
        }
        String sarId = form.getLinkedSarTaskAssignmentId();
        if (!sarId.isBlank()) {
            return data.getSarTaskAssignments().stream()
                    .filter(a -> sarId.equals(a.getAssignmentId()))
                    .findFirst()
                    .map(MainFrame::labelStringForAssignment)
                    .orElse("");
        }
        return "";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** A document that can be linked to an ICS 214 activity log. */
    private record AssociableDocument(String label, String ics204FormId, String sarTaskAssignmentId) {
        /** No associated document (ICP-level log). */
        static AssociableDocument none() {
            return new AssociableDocument("(none)", "", "");
        }

        /** Linked to an ICS 204 assignment list, referenced by its stable {@code formId}. */
        static AssociableDocument forIcs204(String label, String formId) {
            return new AssociableDocument(label, formId, "");
        }

        /** Linked to a SAR task assignment, referenced by its {@code assignmentId}. */
        static AssociableDocument forSarTask(String label, String assignmentId) {
            return new AssociableDocument(label, "", assignmentId);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Builds the list of documents available for linking based on the selected scope. */
    private List<AssociableDocument> documentsForScope(AppData data, ActivityLogScope scope) {
        List<AssociableDocument> docs = new ArrayList<>();
        if (data == null || scope == ActivityLogScope.ICP) {
            docs.add(AssociableDocument.none());
            return docs;
        }
        if (scope == ActivityLogScope.ASSIGNMENT_LIST) {
            docs.add(labelFor204(data.getForm204(), 1));
            List<Ics204Form> additional = data.getAdditionalForms204();
            for (int i = 0; i < additional.size(); i++) {
                docs.add(labelFor204(additional.get(i), i + 2));
            }
        } else {
            for (SarTaskAssignment assignment : data.getSarTaskAssignments()) {
                docs.add(labelForAssignment(assignment));
            }
        }
        if (docs.isEmpty()) {
            docs.add(AssociableDocument.none());
        }
        return docs;
    }

    private static AssociableDocument labelFor204(Ics204Form form, int ordinal) {
        return AssociableDocument.forIcs204(labelStringFor204(form, ordinal), form.getFormId());
    }

    private static String labelStringFor204(Ics204Form form, int ordinal) {
        String context = form.getSelectedContextValue();
        String heading = form.getSelectedContextHeading();
        if (context != null && !context.isBlank()) {
            return "ICS 204 – " + heading + " " + context;
        }
        if (form.getIapPage() != null && !form.getIapPage().isBlank()) {
            return "ICS 204 – Page " + form.getIapPage();
        }
        return "ICS 204 #" + ordinal;
    }

    private static AssociableDocument labelForAssignment(SarTaskAssignment assignment) {
        return AssociableDocument.forSarTask(labelStringForAssignment(assignment), assignment.getAssignmentId());
    }

    private static String labelStringForAssignment(SarTaskAssignment assignment) {
        String id = assignment.getAssignmentId();
        String resource = assignment.getResourceIdentifier();
        if (id != null && !id.isBlank()) {
            return "Assignment " + id + (resource != null && !resource.isBlank() ? " – " + resource : "");
        }
        if (resource != null && !resource.isBlank()) {
            return "Assignment – " + resource;
        }
        return "Assignment";
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
