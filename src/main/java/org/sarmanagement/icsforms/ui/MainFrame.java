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
import javax.swing.JButton;
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
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
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
    /** Tab group for ICS 201 – shown by default only in the Initial Response phase. */
    private static final String ICS_201_GROUP = "ICS 201 (Initial Response)";

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
    private IapPhase lastKnownPhase;

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
        controller.setPersonnelCardCreator(tCardPanel::createPersonnelCardFromPicker);
        ics204Panel.setOn214Request(this::addOrOpenLog214ForResource);
        ics204Panel.setOnEditSarTaskRequest(this::openSarTaskEditorForAssignment);
        ics204Panel.setOnChangeSarTaskStatusRequest(sarTaskPanel::openTaskStatusDialogByAssignmentId);
        sarTaskPanel.setOn214Request(this::openIcs214ForSarTask);
        sarTaskPanel.setOnNewAssignmentRequest(ics204Panel::openNewAssignmentEditor);
        sarTaskPanel.setOnEditIcs204AssignmentRequest(this::openIcs204EditorForAssignment);
        tCardPanel.setOnEditSarAssignmentRequest(assignmentId -> openSarTaskEditorForAssignment(assignmentId, false));
        groupVisible.put(CORE_GROUP, true);
        groupVisible.put(ACTIVITY_LOGS_GROUP, true);
        groupVisible.put(SAR_ONLY_GROUP, controller.getIncidentMode() == IncidentMode.SAR);
        groupVisible.put(ICS_201_GROUP, controller.getIapPhase() == IapPhase.INITIAL_RESPONSE);
        groupVisible.put(T_CARDS_GROUP, true);
        registerTab("Shared", incidentContextPanel, AppController.LinkSource.SHARED, CORE_GROUP);
        registerTab("Org Chart", organizationalChartPanel, AppController.LinkSource.ORG_CHART, CORE_GROUP);
        registerTab("ICS 201", ics201Panel, AppController.LinkSource.NONE, ICS_201_GROUP);
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
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        JMenu importMenu = new JMenu("Import");
        importMenu.setMnemonic(KeyEvent.VK_I);
        JMenu exportMenu = new JMenu("Export");
        exportMenu.setMnemonic(KeyEvent.VK_X);
        JMenu logsMenu = new JMenu("Logs");
        logsMenu.setMnemonic(KeyEvent.VK_L);
        JMenu configMenu = new JMenu("Configuration");
        configMenu.setMnemonic(KeyEvent.VK_C);
        JMenu logsListMenu = new JMenu("Go to Log");
        logsListMenu.setMnemonic(KeyEvent.VK_G);

        JMenuItem newItem = new JMenuItem("New…");
        newItem.setMnemonic(KeyEvent.VK_N);
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
        openItem.setMnemonic(KeyEvent.VK_O);
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
        saveItem.setMnemonic(KeyEvent.VK_S);
        saveItem.addActionListener(event -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            pushToModel(source);
            controller.save(source);
            refreshStatus();
        });

        JMenuItem saveAsItem = new JMenuItem("Save As…");
        saveAsItem.setMnemonic(KeyEvent.VK_A);
        saveAsItem.addActionListener(event -> chooseFile(defaultDirectory, true, path -> {
            AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
            pushToModel(source);
            controller.saveAs(path, source);
            refreshStatus();
        }));

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic(KeyEvent.VK_E);
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

        JMenuItem exportFormsPdfItem = new JMenuItem("Export forms as PDF…");
        exportFormsPdfItem.setMnemonic(KeyEvent.VK_P);
        exportFormsPdfItem.addActionListener(event -> openFormsExportDialog(defaultDirectory));

        JMenuItem exportResourcesCsvItem = new JMenuItem("Export resources as CSV…");
        exportResourcesCsvItem.setMnemonic(KeyEvent.VK_R);
        exportResourcesCsvItem.addActionListener(event -> tCardPanel.exportToCsv());

        JMenuItem importResourcesCsvItem = new JMenuItem("Import Resources from CSV…");
        importResourcesCsvItem.setMnemonic(KeyEvent.VK_R);
        importResourcesCsvItem.addActionListener(event -> tCardPanel.importFromCsv());

        JMenuItem addLogItem = new JMenuItem("Add Log");
        addLogItem.setMnemonic(KeyEvent.VK_A);
        addLogItem.addActionListener(event -> addLog());

        JMenuItem removeCurrentLogItem = new JMenuItem("Remove Current Log");
        removeCurrentLogItem.setMnemonic(KeyEvent.VK_R);
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
        exportMenu.add(exportFormsPdfItem);
        exportMenu.add(exportResourcesCsvItem);
        logsMenu.add(addLogItem);
        logsMenu.add(removeCurrentLogItem);
        logsMenu.addSeparator();
        logsMenu.add(logsListMenu);
        JMenuItem manageEventTypesItem = new JMenuItem("Manage Event Types…");
        manageEventTypesItem.setMnemonic(KeyEvent.VK_E);
        manageEventTypesItem.addActionListener(event -> manageEventTypes());
        configMenu.add(manageEventTypesItem);

        JMenu modeMenu = new JMenu("Incident Mode");
        modeMenu.setMnemonic(KeyEvent.VK_M);
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButtonMenuItem sarModeItem = new JRadioButtonMenuItem("SAR Mode", controller.getIncidentMode() == IncidentMode.SAR);
        sarModeItem.setMnemonic(KeyEvent.VK_S);
        JRadioButtonMenuItem genericModeItem = new JRadioButtonMenuItem("Generic Incident Mode", controller.getIncidentMode() == IncidentMode.GENERIC);
        genericModeItem.setMnemonic(KeyEvent.VK_G);
        sarModeItem.addActionListener(e -> setIncidentMode(IncidentMode.SAR));
        genericModeItem.addActionListener(e -> setIncidentMode(IncidentMode.GENERIC));
        modeGroup.add(sarModeItem);
        modeGroup.add(genericModeItem);
        modeMenu.add(sarModeItem);
        modeMenu.add(genericModeItem);
        configMenu.addSeparator();
        configMenu.add(modeMenu);

        JMenu iapPhaseMenu = new JMenu("IAP Phase");
        iapPhaseMenu.setMnemonic(KeyEvent.VK_P);
        ButtonGroup phaseGroup = new ButtonGroup();
        JRadioButtonMenuItem preOpItem  = new JRadioButtonMenuItem("Pre-Operational (planning)",
                controller.getIapPhase() == IapPhase.PRE_OP);
        preOpItem.setMnemonic(KeyEvent.VK_P);
        JRadioButtonMenuItem initialResponseItem = new JRadioButtonMenuItem("Initial Incident Response",
                controller.getIapPhase() == IapPhase.INITIAL_RESPONSE);
        initialResponseItem.setMnemonic(KeyEvent.VK_I);
        JRadioButtonMenuItem duringOpItem = new JRadioButtonMenuItem("Subsequent Operational Period",
                controller.getIapPhase() == IapPhase.DURING_OP);
        duringOpItem.setMnemonic(KeyEvent.VK_S);
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

        JMenu timeZoneMenu = new JMenu("Date/Time Zone");
        ButtonGroup timeZoneGroup = new ButtonGroup();
        JRadioButtonMenuItem systemTimeZoneItem = new JRadioButtonMenuItem("Use system local time zone",
                controller.getData().isUseSystemTimeZone());
        JRadioButtonMenuItem specifiedTimeZoneItem = new JRadioButtonMenuItem("Use specified time zone…",
                !controller.getData().isUseSystemTimeZone());
        systemTimeZoneItem.addActionListener(e -> {
            controller.getData().setUseSystemTimeZone(true);
            applyConfiguredTimeZone();
            refreshFromModel();
            controller.markDirty();
        });
        specifiedTimeZoneItem.addActionListener(e -> {
            String current = controller.getData().getConfiguredTimeZoneId();
            String chosen = JOptionPane.showInputDialog(this,
                    "Enter time zone ID (e.g., UTC, America/Denver):",
                    current == null || current.isBlank() ? "UTC" : current);
            if (chosen == null) {
                systemTimeZoneItem.setSelected(controller.getData().isUseSystemTimeZone());
                specifiedTimeZoneItem.setSelected(!controller.getData().isUseSystemTimeZone());
                return;
            }
            try {
                ZoneId.of(chosen.trim());
            } catch (DateTimeException ex) {
                JOptionPane.showMessageDialog(this, "Invalid time zone: " + chosen,
                        "Date/Time Zone", JOptionPane.WARNING_MESSAGE);
                systemTimeZoneItem.setSelected(controller.getData().isUseSystemTimeZone());
                specifiedTimeZoneItem.setSelected(!controller.getData().isUseSystemTimeZone());
                return;
            }
            controller.getData().setUseSystemTimeZone(false);
            controller.getData().setConfiguredTimeZoneId(chosen.trim());
            applyConfiguredTimeZone();
            refreshFromModel();
            controller.markDirty();
        });
        timeZoneGroup.add(systemTimeZoneItem);
        timeZoneGroup.add(specifiedTimeZoneItem);
        timeZoneMenu.add(systemTimeZoneItem);
        timeZoneMenu.add(specifiedTimeZoneItem);
        configMenu.addSeparator();
        configMenu.add(timeZoneMenu);

        importMenu.add(importResourcesCsvItem);

        bar.add(fileMenu);
        bar.add(viewMenu);
        bar.add(importMenu);
        bar.add(exportMenu);
        bar.add(logsMenu);
        bar.add(configMenu);
        return bar;
    }

    private void openFormsExportDialog(Path defaultDirectory) {
        List<String> formKeys = controller.exportableFormKeys();
        if (formKeys.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No PDF forms are available to export.",
                    "Export forms as PDF", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPanel form = UiSupport.formPanel();
        java.util.LinkedHashMap<String, javax.swing.JCheckBox> selectors = new java.util.LinkedHashMap<>();
        JPanel checksPanel = new JPanel();
        checksPanel.setLayout(new javax.swing.BoxLayout(checksPanel, javax.swing.BoxLayout.Y_AXIS));
        for (String key : formKeys) {
            javax.swing.JCheckBox check = new javax.swing.JCheckBox(exportFormDisplayLabel(key), true);
            check.setOpaque(false);
            selectors.put(key, check);
            checksPanel.add(check);
        }
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton selectAll = new JButton("Select All");
        JButton selectNone = new JButton("Select None");
        selectAll.addActionListener(e -> selectors.values().forEach(c -> c.setSelected(true)));
        selectNone.addActionListener(e -> selectors.values().forEach(c -> c.setSelected(false)));
        controls.add(selectAll);
        controls.add(selectNone);
        javax.swing.JRadioButton individual = new javax.swing.JRadioButton("Export selected forms as individual PDFs", true);
        javax.swing.JRadioButton combined = new javax.swing.JRadioButton("Export selected forms as combined IAP PDF");
        javax.swing.ButtonGroup modeGroup = new javax.swing.ButtonGroup();
        modeGroup.add(individual);
        modeGroup.add(combined);
        JPanel modePanel = new JPanel();
        modePanel.setOpaque(false);
        modePanel.setLayout(new javax.swing.BoxLayout(modePanel, javax.swing.BoxLayout.Y_AXIS));
        modePanel.add(individual);
        modePanel.add(combined);
        UiSupport.addRow(form, 0, "Forms", new JScrollPane(checksPanel));
        UiSupport.addRow(form, 1, "", controls);
        UiSupport.addRow(form, 2, "Output", modePanel);
        JScrollPane pane = new JScrollPane(form);
        pane.setBorder(BorderFactory.createEmptyBorder());
        if (!UiSupport.showResizableConfirmDialog(this, "Export forms as PDF",
                pane, new Dimension(560, 440))) {
            return;
        }

        List<String> selected = selectors.entrySet().stream()
                .filter(en -> en.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one form to export.",
                    "Export forms as PDF", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
        chooseDirectory(defaultDirectory, directory -> {
            if (!handleValidationBeforeExport()) {
                return;
            }
            pushToModel(source);
            try {
                if (combined.isSelected()) {
                    Path bundlePath = controller.exportSelectedIapBundle(selected, directory, source);
                    JOptionPane.showMessageDialog(this, "Exported IAP bundle to\n" + bundlePath,
                            "Export complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    controller.exportSelectedForms(selected, directory, source);
                    JOptionPane.showMessageDialog(this, "Exported selected PDFs to\n" + directory,
                            "Export complete", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IOException exception) {
                showError("Failed to export PDFs", exception);
            }
        });
    }

    static String exportFormDisplayLabel(String formKey) {
        return switch (formKey) {
            case "ICS 201" -> "ICS 201 – Incident Briefing";
            case "ICS 202" -> "ICS 202 – Incident Objectives";
            case "ICS 204" -> "ICS 204 – Assignment List";
            case "ICS 207" -> "ICS 207 – Incident Organization Chart";
            case "ICS 214" -> "ICS 214 – Activity Log";
            case "SAR Task Assignment" -> "SAR Task Assignment Forms";
            case "Clue Log" -> "Clue Log";
            default -> formKey;
        };
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
        applyConfiguredTimeZone();
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
        // Reset ICS 201 group visibility to the phase-based default only when the phase changes
        // (e.g. on document load or explicit phase switch).  User toggles via the View menu are
        // preserved during routine tab-switch refreshes where the phase has not changed.
        IapPhase currentPhase = controller.getIapPhase();
        if (currentPhase != lastKnownPhase) {
            groupVisible.put(ICS_201_GROUP, currentPhase == IapPhase.INITIAL_RESPONSE);
            lastKnownPhase = currentPhase;
        }
        rebuildVisibleTabs(selectedComponent, selectedLogIndex);
        UiSupport.applyDateTimeDisplayZone(this);
        refreshStatus();
    }

    private void applyConfiguredTimeZone() {
        AppData data = controller.getData();
        if (data.isUseSystemTimeZone()) {
            UiSupport.setDateTimeDisplayZone(ZoneId.systemDefault());
            return;
        }
        try {
            UiSupport.setDateTimeDisplayZone(ZoneId.of(data.getConfiguredTimeZoneId()));
        } catch (DateTimeException ex) {
            UiSupport.setDateTimeDisplayZone(ZoneId.systemDefault());
        }
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

    private void openSarTaskEditorForAssignment(String assignmentId) {
        openSarTaskEditorForAssignment(assignmentId, true);
    }

    private void openSarTaskEditorForAssignment(String assignmentId, boolean switchToSarTab) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return;
        }
        Component previousTab = tabs.getSelectedComponent();
        int previousLogIndex = selectedLogIndex(previousTab);
        groupVisible.put(SAR_ONLY_GROUP, true);
        rebuildVisibleTabs(switchToSarTab ? sarTaskPanel : previousTab, switchToSarTab ? -1 : previousLogIndex);
        if (switchToSarTab) {
            tabs.setSelectedComponent(sarTaskPanel);
        }
        if (!sarTaskPanel.openAssignmentEditorById(assignmentId)) {
            JOptionPane.showMessageDialog(this,
                    "No SAR task found for assignment ID " + assignmentId,
                    "Open SAR Task", JOptionPane.INFORMATION_MESSAGE);
            if (!switchToSarTab && previousTab != null && tabs.indexOfComponent(previousTab) >= 0) {
                tabs.setSelectedComponent(previousTab);
            }
            return;
        }
        if (!switchToSarTab && previousTab != null && tabs.indexOfComponent(previousTab) >= 0) {
            tabs.setSelectedComponent(previousTab);
        }
    }

    private void openIcs204EditorForAssignment(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return;
        }
        tabs.setSelectedComponent(ics204Panel);
        if (!ics204Panel.openEditorForAssignmentId(assignmentId)) {
            JOptionPane.showMessageDialog(this,
                    "No ICS 204 assignment found for assignment ID " + assignmentId,
                    "Open Assignment", JOptionPane.INFORMATION_MESSAGE);
        }
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
                "Remove activity log '" + logMenuLabel(active) + "'?",
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
