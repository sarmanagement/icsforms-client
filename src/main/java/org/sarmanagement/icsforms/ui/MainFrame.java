/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogScope;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.medical.OrsApiKeyStore;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.pdf.FormOrder;
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
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.SpinnerNumberModel;
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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.Set;
import java.util.UUID;

/**
 * Main Swing frame for editing shared incident data, ICS 202, ICS 204, and SAR
 * task scaffolds.
 */
public class MainFrame extends JFrame {
	private static final String CORE_GROUP = "Core";
	private static final String ACTIVITY_LOGS_GROUP = "Activity Logs";
	/** Tab group for SAR-specific features hidden when mode is Generic. */
	private static final String SAR_ONLY_GROUP = "SAR Features";
	/** Independent regular action tracker tab group. */
	private static final String ACTION_TRACKER_GROUP = "Open Action Tracker";
	private static final String COMMUNICATIONS_GROUP = "Communications Plan (ICS 205)";
	private static final String SAFETY_DASHBOARD_GROUP = "Safety Dashboard";
	private static final String DASHBOARD_PREFERENCE = "workspace-dashboard";
	/** Tab group for T-Card (ICS 219) resource status tabs. */
	private static final String T_CARDS_GROUP = "T-Cards";
	/**
	 * Tab group for ICS 201 – shown by default only in the Initial Response phase.
	 */
	private static final String ICS_201_GROUP = "ICS 201 (Initial Response)";

	private final AppController controller;
	private final JLabel validationLabel = new JLabel("Ready", SwingConstants.LEFT);
	private final IncidentContextPanel incidentContextPanel;
	private final OrganizationalChartPanel organizationalChartPanel;
	private final Ics201Panel ics201Panel;
	private final Ics202Panel ics202Panel;
	private final Ics204Panel ics204Panel;
	private final Ics205Panel ics205Panel;
	private final JCheckBoxMenuItem communicationsPlanItem = new JCheckBoxMenuItem(COMMUNICATIONS_GROUP);
	private final Ics206Panel medicalPlanPanel;
	private final CanineMedicalPanel canineMedicalPanel;
	private final MedicalWorkspacePanel medicalWorkspacePanel;
	private final List<Ics214Panel> ics214Panels = new ArrayList<>();
	private final SarTaskPanel sarTaskPanel;
	private final ClueLogPanel clueLogPanel;
	private final OpenActionTrackerPanel openActionTrackerPanel;
	private final TCardPanel tCardPanel;
	private final SafetyPanel safetyPanel;
	private final SafetyDashboardPanel safetyDashboardPanel;
	private final JTabbedPane tabs = new JTabbedPane();
	private final Preferences userPreferences = Preferences.userNodeForPackage(MainFrame.class);
	private final Map<WorkspaceId, WorkspaceDefinition> workspaces = new EnumMap<>(WorkspaceId.class);
	private final Map<Component, WorkspaceDefinition> tabWorkspaces = new IdentityHashMap<>();
	private final List<WorkspaceId> registeredWorkspaceIds = new ArrayList<>();
	private final IdentityHashMap<Ics214Form, String> activityLogInstanceIds = new IdentityHashMap<>();
	private final Map<String, Ics214Form> activityLogFormsById = new LinkedHashMap<>();
	private final Map<String, Ics214Panel> activityLogPanelsById = new LinkedHashMap<>();
	private final Map<String, Boolean> groupVisible = new LinkedHashMap<>();
	private int lastSelectedTabIndex = -1;
	private boolean rebuildingTabs;
	private boolean canineMedicalManuallyOpened;
	private boolean safetyDashboardManuallyVisible;
	private IapPhase lastKnownPhase;
	private AppData lastRefreshedIncident;
	private Dashboard activeDashboard = Dashboard.from(
			Preferences.userNodeForPackage(MainFrame.class).get(DASHBOARD_PREFERENCE, "IC/ICP"));
	private final Set<Ics214Form> closedLogForms = new HashSet<>();
	private AppData lastData;

	/**
	 * Creates the main application frame.
	 *
	 * @param data
	 *            initial data.
	 * @param repository
	 *            persistence repository.
	 * @param exportService
	 *            PDF export service.
	 * @param validator
	 *            validation service.
	 * @param defaultDirectory
	 *            default directory for file chooser startup.
	 * @deprecated use the incident-store constructor for new application code.
	 */
	@Deprecated
	public MainFrame(AppData data, LocalRepository repository, PdfExportService exportService,
			IncidentValidator validator, Path defaultDirectory) {
		this(new AppController(data, repository, exportService, validator), defaultDirectory);
	}

	/**
	 * Creates the editor for a store-backed incident.
	 *
	 * @param data loaded incident data.
	 * @param store incident store.
	 * @param incidentId active incident identifier.
	 * @param exportService PDF export service.
	 * @param validator validation service.
	 * @param defaultDirectory default directory for file choosers.
	 */
	public MainFrame(AppData data, IncidentStore store, IncidentId incidentId, PdfExportService exportService,
			IncidentValidator validator, Path defaultDirectory) {
		this(new AppController(data, store, incidentId, exportService, validator), defaultDirectory);
	}

	/**
	 * Initializes the editor with its incident controller.
	 *
	 * @param controller incident controller.
	 * @param defaultDirectory default directory for file choosers.
	 */
	private MainFrame(AppController controller, Path defaultDirectory) {
		super("ICS Forms Desktop");
		ApplicationIcon.install(this);
		this.controller = controller;
		this.lastData = controller.getData();
		this.controller.setAutosaveErrorHandler(exception -> showSaveError(exception));
		this.incidentContextPanel = new IncidentContextPanel(controller);
		this.organizationalChartPanel = new OrganizationalChartPanel(controller);
		this.ics201Panel = new Ics201Panel(controller);
		this.ics202Panel = new Ics202Panel(controller);
		this.ics204Panel = new Ics204Panel(controller);
		this.ics205Panel = new Ics205Panel(controller);
		this.medicalPlanPanel = new Ics206Panel(controller);
		this.canineMedicalPanel = new CanineMedicalPanel(controller, defaultDirectory);
		this.medicalWorkspacePanel = new MedicalWorkspacePanel(medicalPlanPanel, canineMedicalPanel);
		this.sarTaskPanel = new SarTaskPanel(controller);
		this.clueLogPanel = new ClueLogPanel(controller);
		this.openActionTrackerPanel = new OpenActionTrackerPanel(controller);
		this.tCardPanel = new TCardPanel(controller);
		this.safetyPanel = new SafetyPanel(controller);
		this.safetyDashboardPanel = new SafetyDashboardPanel(this::openWorkspace);
		safetyPanel.setExportRequest(formKey -> openFormsExportDialog(defaultDirectory, formKey));
		medicalPlanPanel.setExportRequest(formKey -> openFormsExportDialog(defaultDirectory, formKey));
		canineMedicalPanel.setExportRequest(formKey -> openFormsExportDialog(defaultDirectory, formKey));
		controller.setPersonnelCardCreator(tCardPanel::createPersonnelCardFromPicker);
		ics204Panel.setOn214Request(this::addOrOpenLog214ForResource);
		ics204Panel.setOnEditSarTaskRequest(this::openSarTaskEditorForAssignment);
		ics204Panel.setOnChangeSarTaskStatusRequest(sarTaskPanel::openTaskStatusDialogByAssignmentId);
		sarTaskPanel.setOn214Request(this::openIcs214ForSarTask);
		sarTaskPanel.setOnNewAssignmentRequest(ics204Panel::openNewAssignmentEditor);
		sarTaskPanel.setOnEditIcs204AssignmentRequest(this::openIcs204EditorForAssignment);
		tCardPanel.setOnEditSarAssignmentRequest(assignmentId -> openSarTaskEditorForAssignment(assignmentId, false));
		ics204Panel.setOnAddLogEntryRequest((source, assignment) -> showAddLogEntry(
				new LogEntryContext(source, "", assignment.getAssignmentId(), assignment.getResourceIdentifier())));
		sarTaskPanel.setOnAddLogEntryRequest((source, assignment) -> showAddLogEntry(
				new LogEntryContext(source, assignment.getTaskId(), assignment.getAssignmentId(),
						org.sarmanagement.icsforms.model.SarTaskSupport.taskResourceDisplayLabel(assignment))));
		tCardPanel.setOnAddLogEntryRequest(this::showAddLogEntry);
		groupVisible.put(CORE_GROUP, true);
		groupVisible.put(ACTIVITY_LOGS_GROUP, true);
		groupVisible.put(SAR_ONLY_GROUP, controller.getIncidentMode() == IncidentMode.SAR);
		groupVisible.put(ICS_201_GROUP, controller.getIapPhase() == IapPhase.INITIAL_RESPONSE);
		groupVisible.put(T_CARDS_GROUP, true);
		groupVisible.put(ACTION_TRACKER_GROUP, controller.getData().isOpenActionTrackerEnabled());
		groupVisible.put(COMMUNICATIONS_GROUP, controller.getData().isIcs205Enabled());
		medicalWorkspacePanel.setCanineVisible(controller.getData().isCanineMedicalWorkspaceEligible());
		groupVisible.put(SAFETY_DASHBOARD_GROUP,
				safetyDashboardVisible(activeDashboard, safetyDashboardManuallyVisible));
		registerWorkspace(WorkspaceId.SHARED, incidentContextPanel, AppController.LinkSource.SHARED, CORE_GROUP);
		registerWorkspace(WorkspaceId.ORG_CHART, organizationalChartPanel, AppController.LinkSource.ORG_CHART,
				CORE_GROUP);
		registerWorkspace(WorkspaceId.ICS_201, ics201Panel, AppController.LinkSource.NONE, ICS_201_GROUP);
		registerWorkspace(WorkspaceId.ICS_202, ics202Panel, AppController.LinkSource.ICS202, CORE_GROUP);
		registerWorkspace(WorkspaceId.ICS_204, ics204Panel, AppController.LinkSource.ICS204, CORE_GROUP);
		registerWorkspace(WorkspaceId.ICS_205, ics205Panel, AppController.LinkSource.NONE, COMMUNICATIONS_GROUP);
		registerWorkspace(WorkspaceId.MEDICAL, medicalWorkspacePanel, AppController.LinkSource.NONE, CORE_GROUP);
		registerNestedWorkspace(WorkspaceId.HUMAN_MEDICAL_PLAN, medicalWorkspacePanel);
		registerNestedWorkspace(WorkspaceId.CANINE_MEDICAL_PLAN, medicalWorkspacePanel);
		registerWorkspace(WorkspaceId.SAFETY, safetyPanel, AppController.LinkSource.NONE, CORE_GROUP);
		registerWorkspace(WorkspaceId.SAFETY_OVERVIEW, safetyDashboardPanel, AppController.LinkSource.NONE,
				SAFETY_DASHBOARD_GROUP);
		registerWorkspace(WorkspaceId.SAR_TASKS, sarTaskPanel, AppController.LinkSource.NONE, SAR_ONLY_GROUP);
		registerWorkspace(WorkspaceId.CLUE_LOG, clueLogPanel, AppController.LinkSource.NONE, SAR_ONLY_GROUP);
		registerWorkspace(WorkspaceId.OPEN_ACTION_TRACKER, openActionTrackerPanel, AppController.LinkSource.NONE,
				ACTION_TRACKER_GROUP);
		registerWorkspace(WorkspaceId.TCARDS, tCardPanel, AppController.LinkSource.NONE, T_CARDS_GROUP);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				closeAfterSave();
			}
		});
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
	 * Builds a radio-item dashboard submenu for the View menu.
	 *
	 * @param selected active dashboard.
	 * @param selector callback for a new dashboard selection.
	 * @return dashboard submenu.
	 */
	static JMenu createDashboardMenu(Dashboard selected, Consumer<Dashboard> selector) {
		JMenu menu = new JMenu("Dashboard");
		ButtonGroup group = new ButtonGroup();
		for (Dashboard dashboard : Dashboard.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(dashboard.label(), dashboard == selected);
			item.addActionListener(event -> selector.accept(dashboard));
			group.add(item);
			menu.add(item);
		}
		return menu;
	}

	/**
	 * Determines whether the Safety Overview remains available in the current view.
	 *
	 * @param dashboard active role dashboard.
	 * @param manuallyVisible whether the operator opened the overview from View.
	 * @return true for the Safety Officer dashboard or explicit operator visibility.
	 */
	static boolean safetyDashboardVisible(Dashboard dashboard, boolean manuallyVisible) {
		return manuallyVisible || dashboard == Dashboard.SAFETY_OFFICER;
	}

	/**
	 * Applies a dashboard choice while retaining all shared editor instances.
	 *
	 * @param selected dashboard chosen in View > Dashboard.
	 */
	private void selectDashboard(Dashboard selected) {
		if (selected == null || selected == activeDashboard) {
			return;
		}
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		activeDashboard = selected;
		userPreferences.put(DASHBOARD_PREFERENCE, selected.label());
		groupVisible.put(SAFETY_DASHBOARD_GROUP,
				safetyDashboardVisible(activeDashboard, safetyDashboardManuallyVisible));
		rebuildVisibleTabs();
	}

	/**
	 * Builds the application menu for file and export workflows.
	 *
	 * @param defaultDirectory
	 *            default chooser directory.
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
		viewMenu.add(createDashboardMenu(activeDashboard, this::selectDashboard));
		viewMenu.addSeparator();
		JMenu openLogInNewWindowMenu = new JMenu("Open Log in New Window");
		openLogInNewWindowMenu.setMnemonic(KeyEvent.VK_O);

		JMenuItem newItem = new JMenuItem("New…");
		newItem.setMnemonic(KeyEvent.VK_N);
		newItem.addActionListener(event -> {
			if (controller.getStore() == null || !saveBeforeSwitch()) {
				return;
			}
			StartupDialog startup = new StartupDialog(MainFrame.this, controller.getStore(), false);
			startup.setVisible(true);
			StartupDialog.StartupAction action = startup.getChosenAction();
			if (action == null) {
				return; // cancelled
			}
			try {
				switch (action) {
					case OPEN_EXISTING -> controller.open(startup.getChosenIncidentId());
					case OPEN_NEW_PERIOD -> {
						controller.open(startup.getChosenIncidentId());
						controller.newOperationalPeriod(startup.isBringForwardCommunicationsPlan());
					}
					case NEW_PRE_OP, NEW_INITIAL_RESPONSE, NEW_OPERATIONAL_PERIOD -> {
						IapPhase phase = switch (action) {
							case NEW_INITIAL_RESPONSE -> IapPhase.INITIAL_RESPONSE;
							case NEW_OPERATIONAL_PERIOD -> IapPhase.DURING_OP;
							default -> IapPhase.PRE_OP;
						};
						controller.newDocument(phase, startup.getChosenMode());
					}
				}
				refreshFromModel();
			} catch (RuntimeException exception) {
				showSaveError(exception);
			}
		});

		JMenuItem openItem = new JMenuItem("Open…");
		openItem.setMnemonic(KeyEvent.VK_O);
		openItem.addActionListener(event -> {
			if (controller.getStore() == null || !saveBeforeSwitch()) {
				return;
			}
			IncidentPickerDialog picker = new IncidentPickerDialog(MainFrame.this, controller.getStore());
			picker.setVisible(true);
			IncidentId chosen = picker.getChosenIncidentId();
			if (chosen != null) {
				try {
					controller.open(chosen);
					refreshFromModel();
				} catch (RuntimeException exception) {
					showSaveError(exception);
				}
			}
		});

		JMenuItem saveItem = new JMenuItem("Save");
		saveItem.setMnemonic(KeyEvent.VK_S);
		saveItem.addActionListener(event -> {
			AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
			pushToModel(source);
			try {
				controller.save(source);
				refreshStatus();
			} catch (RuntimeException exception) {
				showSaveError(exception);
			}
		});

		JMenuItem saveAsItem = new JMenuItem("Export Incident JSON…");
		saveAsItem.setMnemonic(KeyEvent.VK_A);
		saveAsItem.addActionListener(event -> chooseFile(defaultDirectory, true, path -> {
			AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
			pushToModel(source);
			try {
				controller.saveAs(path, source);
				refreshStatus();
			} catch (RuntimeException exception) {
				showSaveError(exception);
			}
		}));

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.setMnemonic(KeyEvent.VK_E);
		exitItem.addActionListener(event -> {
			closeAfterSave();
		});

		JMenuItem duplicateItem = new JMenuItem("Duplicate Incident");
		duplicateItem.addActionListener(event -> {
			if (!saveBeforeSwitch()) {
				return;
			}
			try {
				controller.duplicateIncident();
				refreshFromModel();
			} catch (RuntimeException exception) {
				showSaveError(exception);
			}
		});
		JMenuItem importIncidentItem = new JMenuItem("Import Incident JSON…");
		importIncidentItem.addActionListener(event -> chooseFile(defaultDirectory, false, path -> {
			if (!saveBeforeSwitch()) {
				return;
			}
			try {
				if (controller.importIncident(path)) {
					refreshFromModel();
				} else {
					JOptionPane.showMessageDialog(this, "No incident was imported.", "Import Incident",
							JOptionPane.WARNING_MESSAGE);
				}
			} catch (RuntimeException exception) {
				showSaveError(exception);
			}
		}));

		for (Map.Entry<String, Boolean> entry : groupVisible.entrySet()) {
			// CORE_GROUP is always visible; SAR_ONLY_GROUP is controlled exclusively by the
			// Incident Mode menu (Configuration > Incident Mode) and must not appear here.
			if (CORE_GROUP.equals(entry.getKey()) || SAR_ONLY_GROUP.equals(entry.getKey())
					|| ACTION_TRACKER_GROUP.equals(entry.getKey()) || COMMUNICATIONS_GROUP.equals(entry.getKey())) {
				continue;
			}
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(entry.getKey(), entry.getValue());
			item.addActionListener(event -> {
				groupVisible.put(entry.getKey(), item.isSelected());
				if (SAFETY_DASHBOARD_GROUP.equals(entry.getKey())) {
					safetyDashboardManuallyVisible = item.isSelected();
				}
				if (SAFETY_DASHBOARD_GROUP.equals(entry.getKey()) && item.isSelected()) {
					openWorkspace(WorkspaceDestination.of(WorkspaceId.SAFETY_OVERVIEW));
				} else {
					rebuildVisibleTabs();
				}
				refreshStatus();
			});
			viewMenu.add(item);
		}
		JMenuItem openCaninePlanItem = new JMenuItem("Open Canine Medical Plan (saved workspace)");
		openCaninePlanItem.addActionListener(
				event -> openWorkspace(WorkspaceDestination.canineMedicalPlan()));
		viewMenu.add(openCaninePlanItem);
		communicationsPlanItem.setSelected(controller.getData().isIcs205Enabled());
		communicationsPlanItem.addActionListener(event -> {
			pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
			controller.getData().setIcs205Enabled(communicationsPlanItem.isSelected());
			controller.markDirty();
			refreshFromModel();
			if (communicationsPlanItem.isSelected()) {
				openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_205));
			}
		});
		viewMenu.add(communicationsPlanItem);

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

		JMenuItem removeLogItem = new JMenuItem("Remove Log");
		removeLogItem.setMnemonic(KeyEvent.VK_R);
		removeLogItem.addActionListener(event -> removeLog());

		logsMenu.addMenuListener(new MenuListener() {
			@Override
			/** {@inheritDoc} */
			public void menuSelected(MenuEvent event) {
				rebuildLogsListMenu(logsListMenu);
				rebuildOpenLogInNewWindowMenu(openLogInNewWindowMenu);
			}

			@Override
			/** {@inheritDoc} */
			public void menuDeselected(MenuEvent event) {
			}

			@Override
			/** {@inheritDoc} */
			public void menuCanceled(MenuEvent event) {
			}
		});

		fileMenu.add(newItem);
		fileMenu.add(openItem);
		fileMenu.add(saveItem);
		fileMenu.add(saveAsItem);
		fileMenu.add(duplicateItem);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);
		exportMenu.add(exportFormsPdfItem);
		exportMenu.add(exportResourcesCsvItem);
		JMenuItem exportChannels = new JMenuItem("Export communications plan CSV…");
		exportChannels.addActionListener(event -> new RadioChannelCsvDialogs(ics205Panel, controller).exportCsv(false));
		exportMenu.add(exportChannels);
		JMenuItem exportChirp = new JMenuItem("Export communications plan CHIRP CSV…");
		exportChirp.addActionListener(event -> new RadioChannelCsvDialogs(ics205Panel, controller).exportCsv(true));
		exportMenu.add(exportChirp);
		importMenu.add(importIncidentItem);
		JMenuItem importChannels = new JMenuItem("Import communications plan CSV…");
		importChannels.addActionListener(event -> new RadioChannelCsvDialogs(ics205Panel, controller).importCsv());
		importMenu.add(importChannels);
		logsMenu.add(logsListMenu);
		logsMenu.add(openLogInNewWindowMenu);
		logsMenu.addSeparator();
		logsMenu.add(addLogItem);
		logsMenu.add(removeLogItem);
		rebuildLogsListMenu(logsListMenu);
		rebuildOpenLogInNewWindowMenu(openLogInNewWindowMenu);
		JMenuItem manageEventTypesItem = new JMenuItem("Manage Event Types…");
		manageEventTypesItem.setMnemonic(KeyEvent.VK_E);
		manageEventTypesItem.addActionListener(event -> manageEventTypes());
		configMenu.add(manageEventTypesItem);
		JMenuItem pdfExportSettingsItem = new JMenuItem("PDF Export…");
		pdfExportSettingsItem.setMnemonic(KeyEvent.VK_P);
		pdfExportSettingsItem.addActionListener(event -> openPdfExportSettingsDialog());
		configMenu.add(pdfExportSettingsItem);
		JMenuItem actionSettingsItem = new JMenuItem("Open Action Tracker…");
		actionSettingsItem.addActionListener(event -> openActionSettingsDialog());
		configMenu.add(actionSettingsItem);
		JMenuItem medicalRoutingSettingsItem = new JMenuItem("Medical Routing (OpenRouteService)…");
		medicalRoutingSettingsItem.addActionListener(event -> openMedicalRoutingSettingsDialog());
		configMenu.add(medicalRoutingSettingsItem);

		JMenu modeMenu = new JMenu("Incident Mode");
		modeMenu.setMnemonic(KeyEvent.VK_M);
		ButtonGroup modeGroup = new ButtonGroup();
		JRadioButtonMenuItem sarModeItem = new JRadioButtonMenuItem("SAR Mode",
				controller.getIncidentMode() == IncidentMode.SAR);
		sarModeItem.setMnemonic(KeyEvent.VK_S);
		JRadioButtonMenuItem genericModeItem = new JRadioButtonMenuItem("Generic Incident Mode",
				controller.getIncidentMode() == IncidentMode.GENERIC);
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
		JRadioButtonMenuItem preOpItem = new JRadioButtonMenuItem("Pre-Operational (planning)",
				controller.getIapPhase() == IapPhase.PRE_OP);
		preOpItem.setMnemonic(KeyEvent.VK_P);
		JRadioButtonMenuItem initialResponseItem = new JRadioButtonMenuItem("Initial Incident Response",
				controller.getIapPhase() == IapPhase.INITIAL_RESPONSE);
		initialResponseItem.setMnemonic(KeyEvent.VK_I);
		JRadioButtonMenuItem duringOpItem = new JRadioButtonMenuItem("Subsequent Operational Period",
				controller.getIapPhase() == IapPhase.DURING_OP);
		duringOpItem.setMnemonic(KeyEvent.VK_S);
		preOpItem.addActionListener(e -> {
			controller.setIapPhase(IapPhase.PRE_OP);
			refreshFromModel();
		});
		initialResponseItem.addActionListener(e -> {
			controller.setIapPhase(IapPhase.INITIAL_RESPONSE);
			refreshFromModel();
		});
		duringOpItem.addActionListener(e -> {
			controller.setIapPhase(IapPhase.DURING_OP);
			refreshFromModel();
		});
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
		Runnable refreshTimeZoneLabels = () -> {
			systemTimeZoneItem.setText("Use system local time zone (" + humanReadableTimeZone(ZoneId.systemDefault()) + ")");
			String configured = controller.getData().isUseSystemTimeZone()
					? "currently using system local time zone"
					: humanReadableTimeZone(configuredZoneId(controller.getData()));
			specifiedTimeZoneItem.setText("Use specified time zone… (" + configured + ")");
		};
		systemTimeZoneItem.addActionListener(e -> {
			controller.getData().setUseSystemTimeZone(true);
			applyConfiguredTimeZone();
			refreshTimeZoneLabels.run();
			refreshFromModel();
			controller.markDirty();
		});
		specifiedTimeZoneItem.addActionListener(e -> {
			String current = controller.getData().getConfiguredTimeZoneId();
			String chosen = UiSupport.showFilterableSelectionDialog(this, "Date/Time Zone", "Time zone ID",
					supportedTimeZoneIds(), current == null || current.isBlank() ? "UTC" : current);
			if (chosen == null) {
				systemTimeZoneItem.setSelected(controller.getData().isUseSystemTimeZone());
				specifiedTimeZoneItem.setSelected(!controller.getData().isUseSystemTimeZone());
				return;
			}
			try {
				ZoneId.of(chosen.trim());
			} catch (DateTimeException ex) {
				JOptionPane.showMessageDialog(this, "Invalid time zone: " + chosen, "Date/Time Zone",
						JOptionPane.WARNING_MESSAGE);
				systemTimeZoneItem.setSelected(controller.getData().isUseSystemTimeZone());
				specifiedTimeZoneItem.setSelected(!controller.getData().isUseSystemTimeZone());
				return;
			}
			controller.getData().setUseSystemTimeZone(false);
			controller.getData().setConfiguredTimeZoneId(chosen.trim());
			applyConfiguredTimeZone();
			refreshTimeZoneLabels.run();
			refreshFromModel();
			controller.markDirty();
		});
		timeZoneGroup.add(systemTimeZoneItem);
		timeZoneGroup.add(specifiedTimeZoneItem);
		timeZoneMenu.add(systemTimeZoneItem);
		timeZoneMenu.add(specifiedTimeZoneItem);
		refreshTimeZoneLabels.run();
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

	/**
	 * Offers registered form exports with non-persistent print-time layout choices.
	 *
	 * @param defaultDirectory initial destination directory.
	 */
	private void openFormsExportDialog(Path defaultDirectory) {
		openFormsExportDialog(defaultDirectory, null);
	}

	/**
	 * Offers registered form exports, preselecting a form requested by its workspace.
	 *
	 * @param defaultDirectory initial destination directory.
	 * @param requestedFormKey form key to add to the default selections.
	 */
	private void openFormsExportDialog(Path defaultDirectory, String requestedFormKey) {
		ics201Panel.pushToModel();
		List<String> formKeys = sortExportFormKeys(controller.exportableFormKeys());
		if (formKeys.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No PDF forms are available to export.", "Export forms as PDF",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JPanel form = UiSupport.formPanel();
		java.util.LinkedHashMap<String, javax.swing.JCheckBox> selectors = new java.util.LinkedHashMap<>();
		List<javax.swing.JCheckBox> formChecks = new ArrayList<>();
		for (String key : formKeys) {
			if (!isExportFormVisible(key, controller.getData())) {
				continue;
			}
			javax.swing.JCheckBox check = new javax.swing.JCheckBox(exportFormDisplayLabel(key),
					key.equals(requestedFormKey) || isExportFormSelectedByDefault(key, controller.getData()));
			if ("ICS 203".equals(key)) {
				check.setToolTipText("Individual export always available; IAP inclusion requires Org Chart entries "
						+ "and the optional setting in ICS 203 details.");
			}
			check.setOpaque(false);
			selectors.put(key, check);
			formChecks.add(check);
		}
		JPanel checksPanel = createExportFormChoicesPanel(formChecks);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JButton selectAll = new JButton("Select All");
		JButton selectNone = new JButton("Select None");
		selectAll.addActionListener(e -> selectors.values().forEach(c -> c.setSelected(true)));
		selectNone.addActionListener(e -> selectors.values().forEach(c -> c.setSelected(false)));
		controls.add(selectAll);
		controls.add(selectNone);
		ExportModeControls exportModeControls = createExportModeControls();
		PdfPrintOptionsPanel printOptions = new PdfPrintOptionsPanel(formKeys);
		UiSupport.addWideRow(form, 0, checksPanel);
		UiSupport.addRow(form, 1, "", controls);
		UiSupport.addRow(form, 2, "Output", exportModeControls.panel());
		UiSupport.addRow(form, 3, "Print options", printOptions);
		if (!UiSupport.showResizableConfirmDialog(this, "Export forms as PDF", form, null)) {
			return;
		}

		List<String> selected = printOptions.selectedFormKeys(selectors.entrySet().stream()
				.filter(en -> en.getValue().isSelected()).map(Map.Entry::getKey).toList());
		if (selected.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select at least one form to export.", "Export forms as PDF",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
		chooseDirectory(defaultDirectory, directory -> {
			try {
				if (!handleValidationBeforeExport()) {
					return;
				}
				pushToModel(source);
				addSelectedFormsToIcs202Attachments(selected);
				if (exportModeControls.combined().isSelected()) {
					Path bundlePath = controller.exportSelectedIapBundle(selected, directory, source,
							printOptions.ics205Orientation(), printOptions.ics233Orientation());
					JPanel completion = new JPanel(new BorderLayout(0, 8));
					JTextArea summary = new JTextArea(controller.getLastPdfBundleSummary(), 3, 50);
					summary.setEditable(false);
					summary.setLineWrap(true);
					summary.setWrapStyleWord(true);
					completion.add(summary, BorderLayout.NORTH);
					completion.add(createExportCompletePanel(List.of(bundlePath)), BorderLayout.CENTER);
					JOptionPane.showMessageDialog(this, completion, "Export complete",
							JOptionPane.INFORMATION_MESSAGE);
				} else {
					Map<String, Path> exported = controller.exportSelectedForms(selected, directory, source,
							printOptions.ics205Orientation(), printOptions.ics233Orientation());
					JOptionPane.showMessageDialog(this, createExportCompletePanel(new ArrayList<>(exported.values())), "Export complete",
							JOptionPane.INFORMATION_MESSAGE);
				}
			} catch (IOException | RuntimeException exception) {
				showPdfExportError("Failed to export PDFs", exception);
			}
		});
	}

	/**
	 * Creates a full-height, non-scrolling list for the export form choices.
	 *
	 * @param checkboxes one checkbox per available form.
	 * @return panel containing every form choice.
	 */
	static JPanel createExportFormChoicesPanel(List<javax.swing.JCheckBox> checkboxes) {
		JPanel checksPanel = new JPanel();
		checksPanel.setLayout(new javax.swing.BoxLayout(checksPanel, javax.swing.BoxLayout.Y_AXIS));
		checkboxes.forEach(checksPanel::add);
		checksPanel.setPreferredSize(new Dimension(600, Math.max(28, checkboxes.size() * 25)));
		return checksPanel;
	}

	/**
	 * Creates a selectable list of exported paths with a filename-copy action.
	 *
	 * @param files successfully exported files in export order.
	 * @return completion panel with read-only paths and a clipboard button.
	 */
	static JPanel createExportCompletePanel(List<Path> files) {
		JTextArea paths = new JTextArea(String.join("\n", files.stream().map(Path::toString).toList()),
				Math.min(8, Math.max(2, files.size())), 50);
		paths.setEditable(false);
		paths.setCaretPosition(0);
		paths.getAccessibleContext().setAccessibleName("Exported files");
		JButton copy = new JButton("Copy filenames");
		copy.setEnabled(!files.isEmpty());
		copy.addActionListener(event -> {
			JTextArea filenames = new JTextArea(
					String.join("\n", files.stream().map(path -> path.getFileName().toString()).toList()));
			filenames.selectAll();
			filenames.copy();
		});
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel("Exported files (select paths to copy):"), BorderLayout.NORTH);
		panel.add(new JScrollPane(paths), BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.add(copy);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Configures the local OpenRouteService credential used for geocoding and routing.
	 */
	private void openMedicalRoutingSettingsDialog() {
		JPasswordField apiKey = new JPasswordField(32);
		JPanel panel = new JPanel(new BorderLayout(4, 8));
		panel.add(new JLabel("<html>OpenRouteService is the default geocoder and driving-route provider. "
				+ "The API key is stored locally in ~/.icsforms/ors-api-key, not in incident data. "
				+ "ORS_API_KEY in the environment takes precedence.</html>"), BorderLayout.NORTH);
		JPanel keyRow = new JPanel(new BorderLayout(4, 0));
		keyRow.add(new JLabel("API key"), BorderLayout.WEST);
		keyRow.add(apiKey, BorderLayout.CENTER);
		panel.add(keyRow, BorderLayout.CENTER);
		if (JOptionPane.showConfirmDialog(this, panel, "Medical Routing (OpenRouteService)",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		char[] key = apiKey.getPassword();
		try {
			new OrsApiKeyStore().saveApiKey(new String(key));
			JOptionPane.showMessageDialog(this, "OpenRouteService key saved locally.",
					"Medical Routing", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException | IllegalArgumentException exception) {
			JOptionPane.showMessageDialog(this, "Unable to save the local OpenRouteService key.",
					"Medical Routing", JOptionPane.ERROR_MESSAGE);
		} finally {
			java.util.Arrays.fill(key, '\0');
			apiKey.setText("");
		}
	}

	/** Edits paper size and page margins for PDF output. */
	private void openPdfExportSettingsDialog() {
		AppData data = controller.getData();
		PdfLayoutSettings settings = data.getPdfLayoutSettings();
		JPanel form = UiSupport.formPanel();
		JComboBox<PdfLayoutSettings.PaperSize> paperSize = new JComboBox<>(PdfLayoutSettings.PaperSize.values());
		paperSize.setSelectedItem(settings.getPaperSize());
		JSpinner marginInches = new JSpinner(
				new SpinnerNumberModel(pointsToInches(settings.getPageMarginPoints()), 0.0d, 5.0d, 0.25d));
		JFormattedTextField marginField = ((JSpinner.NumberEditor) marginInches.getEditor()).getTextField();
		marginField.setColumns(4);
		UiSupport.addRow(form, 0, "Paper size", paperSize);
		UiSupport.addRow(form, 1, "Page margin (in.)", marginInches);
		if (!UiSupport.showResizableConfirmDialog(this, "PDF Export", form, new Dimension(380, 180))) {
			return;
		}
		applyPdfLayoutSettings(data, (PdfLayoutSettings.PaperSize) paperSize.getSelectedItem(),
				((Number) marginInches.getValue()).doubleValue());
		controller.markDirty();
		refreshStatus();
	}

	/**
	 * Returns whether an export choice is offered for the active incident.
	 * @param formKey renderer key
	 * @param data active incident preferences
	 * @return false for the disabled tracker or the adapted layout offered as a print option
	 */
	static boolean isExportFormVisible(String formKey, AppData data) {
		return !PdfPrintOptionsPanel.ADAPTED_CLUE_LOG.equals(formKey)
				&& (!"ICS 233-CG".equals(formKey) || data.isOpenActionTrackerEnabled());
	}

	/**
	 * Returns standard clue-log defaults independently of legacy print preferences.
	 * @param formKey renderer key
	 * @param data active incident preferences
	 * @return true when selected by default
	 */
	static boolean isExportFormSelectedByDefault(String formKey, AppData data) {
		return switch (formKey) {
			case "ICS 201" -> hasBriefingContent(data.getForm201());
			case "ICS 205" -> data.isIcs205Enabled();
			case "ICS 233-CG" -> data.isOpenActionTrackerEnabled();
			case "Clue Log (ICS 233-CG adapted)" -> false;
			case "ICS 208", "ICS 215A" -> data.getForm202().getIncidentActionPlanAttachments().stream()
					.anyMatch(item -> formKey.equalsIgnoreCase(item));
			default -> true;
		};
	}

	/**
	 * Checks for entered briefing content rather than shared incident identity or
	 * automatically populated preparation names and dates. Empty table rows do not count.
	 *
	 * @param form incident briefing, possibly absent in an older document.
	 * @return true when briefing-specific content was entered.
	 */
	private static boolean hasBriefingContent(Ics201Form form) {
		return form != null && (!form.getIncidentNumber().isBlank()
				|| !form.getMapSketch().isBlank()
				|| !form.getSituationSummary().isBlank()
				|| !form.getPreparedBySignature().isBlank()
				|| form.getCurrentObjectives().stream().anyMatch(value -> value != null && !value.isBlank())
				|| form.getCurrentActions().stream().anyMatch(row -> row != null
						&& (!row.getTime().isBlank() || !row.getActions().isBlank()))
				|| form.getResources().stream().anyMatch(row -> row != null
						&& (!row.getResource().isBlank() || !row.getResourceIdentifier().isBlank()
						|| row.getDateTimeOrdered() != null || row.getEta() != null || row.isArrived()
						|| !row.getNotes().isBlank())));
	}

	/**
	 * Adds printed forms to the ICS 202 attachment list without removing prior selections.
	 *
	 * @param selectedFormKeys forms selected in the export dialog.
	 */
	private void addSelectedFormsToIcs202Attachments(List<String> selectedFormKeys) {
		List<String> attachments = controller.getData().getForm202().getIncidentActionPlanAttachments();
		List<String> merged = mergeSelectedFormsWithIcs202Attachments(attachments, selectedFormKeys);
		attachments.clear();
		attachments.addAll(merged);
		Ics202Panel.synchronizeMedicalPlanInclusion(controller.getData(), merged);
		ics202Panel.refreshFromModel();
		controller.markDirty();
	}

	/**
	 * Adds print selections to the existing ICS 202 attachment list without deselecting prior forms.
	 *
	 * @param existingAttachments current ICS 202 attachments.
	 * @param selectedFormKeys forms selected for printing.
	 * @return case-insensitively deduplicated attachment list.
	 */
	static List<String> mergeSelectedFormsWithIcs202Attachments(List<String> existingAttachments,
			List<String> selectedFormKeys) {
		List<String> merged = new ArrayList<>();
		for (String formKey : existingAttachments) {
			if (formKey != null && !formKey.isBlank()
					&& merged.stream().noneMatch(item -> item.equalsIgnoreCase(formKey))) {
				merged.add(formKey);
			}
		}
		for (String formKey : selectedFormKeys) {
			if (formKey != null && !formKey.isBlank()
					&& merged.stream().noneMatch(item -> item.equalsIgnoreCase(formKey))) {
				merged.add(formKey);
			}
		}
		return merged;
	}

	/**
	 * Sorts ICS exports by form number while preserving the relative order of other exports.
	 *
	 * @param formKeys registered form identifiers.
	 * @return identifiers in display order.
	 */
	static List<String> sortExportFormKeys(List<String> formKeys) {
		return FormOrder.sort(formKeys);
	}

	/**
	 * Returns a descriptive export checkbox label.
	 *
	 * @param formKey renderer key
	 * @return display label
	 */
	static String exportFormDisplayLabel(String formKey) {
		return switch (formKey) {
			case "ICS 201" -> "ICS 201 – Incident Briefing";
			case "ICS 202" -> "ICS 202 – Incident Objectives";
			case "ICS 203" -> "ICS 203 – Organization Assignment List";
			case "ICS 205" -> "ICS 205 Incident Radio Communications Plan";
			case "ICS 205A" -> "ICS 205A Communications List";
			case "ICS 206" -> "ICS 206 – Medical Plan";
			case "ICS 206CANINE" -> "ICS 206Canine - Canine Medical Plan";
			case "ICS 204" -> "ICS 204 – Assignment List";
			case "ICS 207" -> "ICS 207 – Incident Organization Chart";
			case "ICS 208" -> "ICS 208 – Safety Message / Plan";
			case "ICS 214" -> "ICS 214 – Activity Log";
			case "ICS 215A" -> "ICS 215A – Incident Action Plan Safety Analysis";
			case "SAR Task Assignment" -> "SAR Task Assignment Forms";
			case "Clue Log" -> "Clue Log";
			case "ICS 233-CG" -> "ICS 233-CG – Open Action Tracker";
			case "Clue Log (ICS 233-CG adapted)" -> "Clue Log – Adapted ICS 233-CG";
			default -> formKey;
		};
	}

	/**
	 * Creates individual and combined PDF output choices.
	 * @return mutually exclusive controls, defaulting to combined output
	 */
	static ExportModeControls createExportModeControls() {
		JRadioButton individual = new JRadioButton("Export selected forms as individual PDFs");
		JRadioButton combined = new JRadioButton("Export selected forms as combined IAP PDF", true);
		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(individual);
		modeGroup.add(combined);
		JPanel modePanel = new JPanel();
		modePanel.setOpaque(false);
		modePanel.setLayout(new javax.swing.BoxLayout(modePanel, javax.swing.BoxLayout.Y_AXIS));
		modePanel.add(combined);
		modePanel.add(individual);
		return new ExportModeControls(individual, combined, modePanel);
	}

	/**
	 * Applies PDF page settings, converting inches to points.
	 * @param data active incident
	 * @param paperSize selected paper size
	 * @param marginInches nonnegative page margin in inches
	 */
	static void applyPdfLayoutSettings(AppData data, PdfLayoutSettings.PaperSize paperSize, double marginInches) {
		PdfLayoutSettings settings = data.getPdfLayoutSettings();
		settings.setPaperSize(paperSize);
		settings.setPageMarginPoints((float) (Math.max(0d, marginInches) * 72d));
	}

	/**
	 * Converts PDF points to inches rounded to two decimal places.
	 * @param points page margin in points
	 * @return nonnegative inches
	 */
	static double pointsToInches(float points) {
		return Math.round((Math.max(0f, points) / 72d) * 100d) / 100d;
	}

	/**
	 * Output mode controls grouped for embedding in the export dialog.
	 * @param individual individual file choice
	 * @param combined combined IAP choice
	 * @param panel container for both choices
	 */
	record ExportModeControls(JRadioButton individual, JRadioButton combined, JPanel panel) {
	}

	/**
	 * Exports a single form after validation.
	 *
	 * @param defaultDirectory
	 *            chooser starting directory.
	 * @param formKey
	 *            form to export.
	 */
	private void exportOne(Path defaultDirectory, String formKey) {
		AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
		chooseDirectory(defaultDirectory, directory -> {
			try {
				if (!handleValidationBeforeExport()) {
					return;
				}
				pushToModel(source);
				Path output = controller.exportSelected(formKey, directory, source);
				JOptionPane.showMessageDialog(this, createExportCompletePanel(List.of(output)), "Export complete",
						JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException | RuntimeException exception) {
				showPdfExportError("Failed to export " + formKey, exception);
			}
		});
	}

	/**
	 * Flushes all editor fields and synchronizes linked document data.
	 * @param source editor that initiated synchronization
	 */
	private void pushToModel(AppController.LinkSource source) {
		incidentContextPanel.pushToModel();
		organizationalChartPanel.pushToModel();
		ics201Panel.pushToModel();
		ics202Panel.pushToModel();
		ics204Panel.pushToModel();
		ics205Panel.pushToModel();
		medicalPlanPanel.pushToModel();
		canineMedicalPanel.pushToModel();
		safetyPanel.pushToModel();
		for (Ics214Panel panel : ics214Panels) {
			panel.saveToModel();
		}
		sarTaskPanel.pushToModel();
		clueLogPanel.pushToModel();
		tCardPanel.pushToModel();
		controller.markDirty(source);
	}

	/**
	 * Ensures the active document has its initial activity log.
	 * @param data active incident
	 */
	private void ensureLogs(AppData data) {
		if (data.getActivityLogs().isEmpty() && data.getIapPhase() != IapPhase.PRE_OP) {
			Ics214Form form = new Ics214Form();
			form.setPreparedByName(data.getForm204().getPreparedByName());
			form.setPreparedByPositionTitle(data.getForm204().getPreparedByPositionTitle());
			form.setPreparedDateTime(data.getForm204().getPreparedDateTime());
			data.getActivityLogs().add(form);
		}
	}

	/** Recreates activity-log editors for the current incident's logs. */
	private void rebuildLogTabs() {
		rebuildingTabs = true;
		try {
			for (Ics214Panel panel : new ArrayList<>(ics214Panels)) {
				tabs.remove(panel);
				tabWorkspaces.remove(panel);
			}
			ics214Panels.clear();
			activityLogPanelsById.clear();
			AppData data = controller.getData();
			activityLogInstanceIds.keySet().retainAll(data.getActivityLogs());
			activityLogFormsById.entrySet().removeIf(entry -> !data.getActivityLogs().contains(entry.getValue()));
			for (Ics214Form form : data.getActivityLogs()) {
				String instanceId = activityLogInstanceId(form);
				if (closedLogForms.contains(form)) {
					continue;
				}
				Ics214Panel panel = new Ics214Panel(controller);
				panel.loadFromModel(form, data);
				ics214Panels.add(panel);
				registerActivityLogTab(panel, shortLogTabTitle(form, data), AppController.LinkSource.ICS214,
						ACTIVITY_LOGS_GROUP, instanceId);
			}
		} finally {
			rebuildingTabs = false;
		}
	}

	/** Configures availability of the independent incident tracker. */
	private void openActionSettingsDialog() {
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		ActionTrackerSettingsPanel settings = new ActionTrackerSettingsPanel(controller.getData());
		if (UiSupport.showResizableConfirmDialog(this, "Open Action Tracker", settings,
				new Dimension(620, 220))) {
			settings.applyTo(controller.getData());
			controller.markDirty();
			refreshFromModel();
		}
	}

	/** Reloads panel state from the active incident document. */
	private void refreshFromModel() {
		if (lastRefreshedIncident != null && lastRefreshedIncident != controller.getData()) {
			canineMedicalManuallyOpened = false;
		}
		lastRefreshedIncident = controller.getData();
		applyConfiguredTimeZone();
		Component selectedComponent = tabs.getSelectedComponent();
		int selectedLogIndex = selectedLogIndex(selectedComponent);
		incidentContextPanel.refreshFromModel();
		organizationalChartPanel.refreshFromModel();
		ics201Panel.refreshFromModel();
		ics202Panel.refreshFromModel();
		ics204Panel.refreshFromModel();
		ics205Panel.refreshFromModel();
		medicalPlanPanel.refreshFromModel();
		canineMedicalPanel.refreshFromModel();
		medicalWorkspacePanel.setCanineVisible(
				canineMedicalManuallyOpened || controller.getData().isCanineMedicalWorkspaceEligible());
		safetyPanel.refreshFromModel();
		safetyDashboardPanel.refreshFromModel(controller.getData());
		if (lastData != controller.getData()) {
			closedLogForms.clear();
			activityLogInstanceIds.clear();
			activityLogFormsById.clear();
			activityLogPanelsById.clear();
			lastData = controller.getData();
		} else {
			closedLogForms.retainAll(controller.getData().getActivityLogs());
		}
		ensureLogs(controller.getData());
		rebuildLogTabs();
		sarTaskPanel.refreshFromModel();
		clueLogPanel.refreshFromModel();
		openActionTrackerPanel.refreshFromModel();
		tCardPanel.refreshFromModel();
		// Keep SAR-only tabs visible only in SAR mode.
		groupVisible.put(SAR_ONLY_GROUP, controller.getIncidentMode() == IncidentMode.SAR);
		groupVisible.put(SAFETY_DASHBOARD_GROUP,
				safetyDashboardVisible(activeDashboard, safetyDashboardManuallyVisible));
		groupVisible.put(ACTION_TRACKER_GROUP, controller.getData().isOpenActionTrackerEnabled());
		groupVisible.put(COMMUNICATIONS_GROUP, controller.getData().isIcs205Enabled());
		communicationsPlanItem.setSelected(controller.getData().isIcs205Enabled());
		// Reset ICS 201 group visibility to the phase-based default only when the phase
		// changes
		// (e.g. on document load or explicit phase switch). User toggles via the View
		// menu are
		// preserved during routine tab-switch refreshes where the phase has not
		// changed.
		IapPhase currentPhase = controller.getIapPhase();
		if (currentPhase != lastKnownPhase) {
			groupVisible.put(ICS_201_GROUP, currentPhase == IapPhase.INITIAL_RESPONSE);
			lastKnownPhase = currentPhase;
		}
		rebuildVisibleTabs(selectedComponent, selectedLogIndex);
		UiSupport.applyDateTimeDisplayZone(this);
		refreshStatus();
	}

	/** Applies the active incident display timezone, keeping stored dates in UTC. */
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

	/**
	 * Returns the configured timezone, falling back to the system default when the
	 * configured ID is invalid.
	 *
	 * @param data
	 *            incident document containing the timezone preference.
	 * @return configured display timezone.
	 */
	static ZoneId configuredZoneId(AppData data) {
		if (data == null || data.isUseSystemTimeZone()) {
			return ZoneId.systemDefault();
		}
		try {
			return ZoneId.of(data.getConfiguredTimeZoneId());
		} catch (DateTimeException exception) {
			return ZoneId.systemDefault();
		}
	}

	/**
	 * Returns the supported IANA timezone IDs sorted for use in the picker dialog.
	 *
	 * @return sorted timezone IDs.
	 */
	static List<String> supportedTimeZoneIds() {
		return ZoneId.getAvailableZoneIds().stream().sorted().toList();
	}

	/**
	 * Returns a human-readable timezone label including offset and abbreviation.
	 *
	 * @param zoneId
	 *            timezone to describe.
	 * @return readable timezone label.
	 */
	static String humanReadableTimeZone(ZoneId zoneId) {
		ZoneId resolved = zoneId == null ? ZoneId.systemDefault() : zoneId;
		java.time.ZonedDateTime now = java.time.ZonedDateTime.now(resolved);
		return resolved.getId() + " (UTC" + now.getOffset() + ", " + now.format(java.time.format.DateTimeFormatter.ofPattern("z"))
				+ ")";
	}

	/** Rebuilds visible tabs while retaining the current selection. */
	private void rebuildVisibleTabs() {
		rebuildVisibleTabs(tabs.getSelectedComponent(), selectedLogIndex(tabs.getSelectedComponent()));
	}

	/**
	 * Rebuilds the tab strip while retaining the selected editor where possible.
	 * @param preferredComponent previously selected editor
	 * @param preferredLogIndex previously selected activity log index
	 */
	private void rebuildVisibleTabs(Component preferredComponent, int preferredLogIndex) {
		rebuildingTabs = true;
		try {
			tabs.removeAll();
			for (Component component : dashboardWorkspaces()) {
				addVisibleTab(component);
			}
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

	/**
	 * Resolves one dashboard's ordered list of existing editor components.
	 *
	 * @return ordered shared workspaces and current activity logs.
	 */
	private List<Component> dashboardWorkspaces() {
		List<Component> ordered = new ArrayList<>();
		for (WorkspaceId id : activeDashboard.workspaceIds()) {
			if (id == WorkspaceId.ACTIVITY_LOG) {
				if (isGroupVisible(ACTIVITY_LOGS_GROUP)) {
					ordered.addAll(ics214Panels);
				}
				continue;
			}
			WorkspaceDefinition definition = workspaces.get(id);
			Component component = definition == null ? null : definition.component();
			if (component != null && !ordered.contains(component)) {
				ordered.add(component);
			}
		}
		for (WorkspaceId id : registeredWorkspaceIds) {
			Component component = workspaces.get(id).component();
			if (!ordered.contains(component)) {
				ordered.add(component);
			}
		}
		for (Ics214Panel panel : ics214Panels) {
			if (!ordered.contains(panel)) {
				ordered.add(panel);
			}
		}
		return ordered;
	}

	/**
	 * Opens an existing workspace destination after flushing the current editor state.
	 *
	 * @param destination typed workspace navigation target.
	 */
	public void openWorkspace(WorkspaceDestination destination) {
		if (destination == null) {
			return;
		}
		try {
			pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		} catch (RuntimeException exception) {
			showSaveError(exception);
			return;
		}

		if (destination.workspaceId() == WorkspaceId.ACTIVITY_LOG) {
			openActivityLog(destination.instanceId());
			return;
		}

		WorkspaceDefinition definition = workspaces.get(destination.workspaceId());
		if (definition == null) {
			return;
		}
		if (destination.workspaceId() == WorkspaceId.HUMAN_MEDICAL_PLAN) {
			medicalWorkspacePanel.selectHumanPlan();
		} else if (destination.workspaceId() == WorkspaceId.CANINE_MEDICAL_PLAN) {
			canineMedicalManuallyOpened = true;
			medicalWorkspacePanel.openCaninePlan();
		} else if (destination.workspaceId() == WorkspaceId.SAFETY_OVERVIEW) {
			safetyDashboardManuallyVisible = true;
			groupVisible.put(SAFETY_DASHBOARD_GROUP, true);
		} else if (destination.workspaceId() == WorkspaceId.ICS_205) {
			controller.getData().setIcs205Enabled(true);
			controller.markDirty();
			communicationsPlanItem.setSelected(true);
			groupVisible.put(COMMUNICATIONS_GROUP, true);
		}
		rebuildVisibleTabs(definition.component(), -1);
		refreshStatus();
	}

	/**
	 * Opens the activity-log editor identified by its per-incident runtime ID.
	 *
	 * @param instanceId requested activity-log identifier.
	 */
	private void openActivityLog(String instanceId) {
		Ics214Form form = activityLogFormsById.get(instanceId);
		if (form == null || !controller.getData().getActivityLogs().contains(form)) {
			return;
		}
		groupVisible.put(ACTIVITY_LOGS_GROUP, true);
		if (closedLogForms.remove(form)) {
			rebuildLogTabs();
		}
		Ics214Panel panel = activityLogPanelsById.get(instanceId);
		if (panel != null) {
			int logIndex = ics214Panels.indexOf(panel);
			rebuildVisibleTabs(panel, logIndex);
			refreshStatus();
		}
	}

	/**
	 * Resolves the editor to select after rebuilding tabs.
	 * @param preferredComponent previously selected editor
	 * @param preferredLogIndex previously selected activity log index
	 * @return available editor, or null when none exists
	 */
	private Component resolveSelection(Component preferredComponent, int preferredLogIndex) {
		if (preferredLogIndex >= 0 && preferredLogIndex < ics214Panels.size() && isGroupVisible(ACTIVITY_LOGS_GROUP)) {
			return ics214Panels.get(preferredLogIndex);
		}
		if (preferredComponent != null && tabs.indexOfComponent(preferredComponent) >= 0) {
			return preferredComponent;
		}
		return incidentContextPanel;
	}

	/**
	 * Adds an editor only when its group is visible.
	 * @param component registered editor
	 */
	private void addVisibleTab(Component component) {
		WorkspaceDefinition definition = tabWorkspaces.get(component);
		if (definition == null || (!CORE_GROUP.equals(definition.group())
				&& !isGroupVisible(definition.group()))) {
			return;
		}
		tabs.addTab(definition.displayLabel(), component);
	}

	/**
	 * Returns the current visibility of a tab group.
	 * @param group group identifier
	 * @return visibility, defaulting to true
	 */
	private boolean isGroupVisible(String group) {
		return groupVisible.getOrDefault(group, true);
	}

	/**
	 * Registers one top-level workspace and its presentation metadata.
	 *
	 * @param id stable workspace identifier.
	 * @param component existing shared editor instance.
	 * @param source linked-data synchronization source.
	 * @param group tab visibility group.
	 */
	private void registerWorkspace(WorkspaceId id, Component component, AppController.LinkSource source,
			String group) {
		WorkspaceDefinition definition = new WorkspaceDefinition(id, id.displayLabel(), component, source, group);
		workspaces.put(id, definition);
		tabWorkspaces.put(component, definition);
		registeredWorkspaceIds.add(id);
	}

	/**
	 * Registers a nested destination that selects within an existing workspace.
	 *
	 * @param id nested destination identifier.
	 * @param component containing workspace component.
	 */
	private void registerNestedWorkspace(WorkspaceId id, Component component) {
		workspaces.put(id, new WorkspaceDefinition(id, id.displayLabel(), component,
				AppController.LinkSource.NONE, CORE_GROUP));
	}

	/**
	 * Returns the stable runtime identifier assigned to one activity-log form instance.
	 *
	 * @param form activity-log form.
	 * @return unique runtime instance identifier.
	 */
	private String activityLogInstanceId(Ics214Form form) {
		String instanceId = activityLogInstanceIds.computeIfAbsent(form, ignored -> UUID.randomUUID().toString());
		activityLogFormsById.put(instanceId, form);
		return instanceId;
	}

	/**
	 * Opens the registered tab for one component while preserving its label mapping.
	 *
	 * @param component existing workspace component.
	 * @param title display label for this particular tab instance.
	 * @param source linked-data synchronization source.
	 * @param group tab visibility group.
	 * @param instanceId unique activity-log identifier, or null for a base workspace.
	 */
	private void registerActivityLogTab(Ics214Panel component, String title, AppController.LinkSource source,
			String group, String instanceId) {
		tabWorkspaces.put(component, new WorkspaceDefinition(WorkspaceId.ACTIVITY_LOG, title, component, source, group));
		activityLogPanelsById.put(instanceId, component);
	}

	/**
	 * Immutable navigation mapping from an identifier to its existing workspace component.
	 *
	 * @param id stable navigation identifier.
	 * @param displayLabel user-facing label.
	 * @param component shared existing editor component.
	 * @param source linked-data synchronization source.
	 * @param group tab visibility group.
	 */
	private record WorkspaceDefinition(WorkspaceId id, String displayLabel, Component component,
			AppController.LinkSource source, String group) {
	}

	/**
	 * Resolves a visible tab to its linked-data synchronization source.
	 * @param tabIndex visible tab index
	 * @return source, or NONE when unavailable
	 */
	private AppController.LinkSource linkSourceForTab(int tabIndex) {
		if (tabIndex < 0 || tabIndex >= tabs.getTabCount()) {
			return AppController.LinkSource.NONE;
		}
		WorkspaceDefinition definition = tabWorkspaces.get(tabs.getComponentAt(tabIndex));
		return definition == null ? AppController.LinkSource.NONE : definition.source();
	}

	/**
	 * Updates status text and unsaved indicator.
	 */
	private void refreshStatus() {
		List<ValidationMessage> messages = controller.validate(linkSourceForTab(tabs.getSelectedIndex()));
		validationLabel.setText(messages.isEmpty() ? "Ready" : messages.get(0).message());
		IncidentId id = controller.getActiveIncidentId();
		String incidentName = controller.getData().getIncidentContext().getIncidentName();
		String label = incidentName == null || incidentName.isBlank() ? "(unnamed)" : incidentName.trim();
		setTitle((controller.isDirty() ? "* " : "") + "ICS Forms Desktop — " + label
				+ (id == null ? "" : " [" + id.value().substring(0, 8) + "]"));
	}

	/**
	 * Saves pending edits before changing incidents so failed saves cannot discard them.
	 *
	 * @return {@code true} if it is safe to switch incidents.
	 */
	private boolean saveBeforeSwitch() {
		if (!resolveOutstandingEditors()) {
			return false;
		}
		AppController.LinkSource source = linkSourceForTab(tabs.getSelectedIndex());
		pushToModel(source);
		try {
			controller.save(source);
			return true;
		} catch (RuntimeException exception) {
			showSaveError(exception);
			return false;
		}
	}

	/**
	 * Requires pending editors to be saved or explicitly discarded before replacing
	 * their incident, including during save-conflict recovery.
	 *
	 * @return true when no outstanding editor can still modify the old incident.
	 */
	private boolean resolveOutstandingEditors() {
		List<java.awt.Window> outstanding = UiSupport.outstandingIncidentWindows(this, controller.getData());
		if (!outstanding.isEmpty()) {
			Object[] options = {"Return to editors to save", "Discard open editors", "Cancel"};
			int choice = JOptionPane.showOptionDialog(this,
					"Editors or detached logs are still open. Return to them to save pending edits before "
							+ "switching or closing this incident, or explicitly discard their pending edits.\n"
							+ "Activity entries already added to the incident will be retained.",
					"Resolve open incident editors", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
					null, options, options[0]);
			if (choice != 1) {
				if (choice == 0) {
					outstanding.get(outstanding.size() - 1).toFront();
				}
				return false;
			}
			outstanding.forEach(java.awt.Window::dispose);
			UiSupport.disposeIncidentWindows(controller.getData());
		}
		return true;
	}

	/**
	 * Closes the editor only after its active incident has been saved successfully.
	 */
	private void closeAfterSave() {
		if (!saveBeforeSwitch()) {
			return;
		}
		if (controller.getStore() != null) {
			controller.getStore().close();
		}
		dispose();
	}

	/**
	 * Explains a save conflict without silently overwriting another editor's changes.
	 *
	 * @param exception persistence failure.
	 */
	private void showSaveError(RuntimeException exception) {
		if (exception instanceof StoreConflictException && controller.getStore() != null) {
			Object[] choices = {"Duplicate my changes", "Reload stored incident", "Keep editing"};
			int selected = JOptionPane.showOptionDialog(this,
					"Another editor changed or locked this incident. Your edits are still in this window.\n"
							+ exception.getMessage() + "\nDuplicate to preserve your edits, or reload to discard them.",
					"Incident save conflict", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
					null, choices, choices[0]);
			if (selected == 0 && resolveOutstandingEditors()) {
				try {
					controller.duplicateIncident();
					refreshFromModel();
				} catch (RuntimeException duplicateFailure) {
					showError("Could not duplicate incident", duplicateFailure);
				}
			} else if (selected == 1 && resolveOutstandingEditors()
					&& JOptionPane.showConfirmDialog(this, "Discard your local changes and reload from the store?",
							"Discard local changes", JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
				try {
					controller.open(controller.getActiveIncidentId());
					refreshFromModel();
				} catch (RuntimeException reloadFailure) {
					showError("Could not reload incident", reloadFailure);
				}
			}
		} else {
			showError("Incident operation failed", exception);
		}
		refreshStatus();
	}

	/**
	 * Validates before export and shows a blocking summary when required fields are
	 * missing.
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
	 * @param defaultDirectory
	 *            chooser starting directory.
	 * @param saveMode
	 *            whether chooser is for save.
	 * @param consumer
	 *            callback for selected path.
	 */
	private void chooseFile(Path defaultDirectory, boolean saveMode, java.util.function.Consumer<Path> consumer) {
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Incident JSON (*.json)", "json"));
		int result = saveMode ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			Path selected = chooser.getSelectedFile().toPath();
			if (saveMode && !selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
				selected = selected.resolveSibling(selected.getFileName() + ".json");
			}
			if (saveMode && java.nio.file.Files.exists(selected)
					&& JOptionPane.showConfirmDialog(this, "Replace " + selected + "?", "Overwrite incident export",
							JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
				return;
			}
			consumer.accept(selected);
		}
	}

	/**
	 * Opens a directory chooser.
	 *
	 * @param defaultDirectory
	 *            chooser starting directory.
	 * @param consumer
	 *            callback for selected directory.
	 */
	private void chooseDirectory(Path defaultDirectory, java.util.function.Consumer<Path> consumer) {
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			consumer.accept(chooser.getSelectedFile().toPath());
		}
	}

	/** Opens the incident's activity-event type editor. */
	private void manageEventTypes() {
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		AppData data = controller.getData();
		List<ActivityEventType> initial = data.getActivityEventTypes().isEmpty()
				? ActivityEventType.defaultTypes()
				: new ArrayList<>(data.getActivityEventTypes());
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
	 * @param mode
	 *            new incident mode.
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
	 * @param task
	 *            SAR task assignment to open the log for.
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
				openWorkspace(WorkspaceDestination.activityLog(activityLogInstanceId(existing)));
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
		openWorkspace(WorkspaceDestination.activityLog(activityLogInstanceId(form)));
	}

	/**
	 * Opens or creates an activity log for an assigned resource.
	 * @param resource resource requesting a log
	 */
	private void addOrOpenLog214ForResource(ResourceAssignment resource) {
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		AppData data = controller.getData();
		String assignmentId = resource.getAssignmentId();
		for (int i = 0; i < data.getActivityLogs().size(); i++) {
			Ics214Form existing = data.getActivityLogs().get(i);
			if (assignmentId.equals(existing.getLinkedSarTaskAssignmentId())) {
				openWorkspace(WorkspaceDestination.activityLog(activityLogInstanceId(existing)));
				return;
			}
		}
		Ics214Form form = new Ics214Form();
		form.setLinkedSarTaskAssignmentId(assignmentId);
		String resourceName = resource.getResourceIdentifier();
		form.setName(resourceName);
		// Look up the linked SAR task assignment to populate section 6 and derive the
		// leader's ICS position.
		data.getSarTaskAssignments().stream().filter(a -> assignmentId.equals(a.getAssignmentId())).findFirst()
				.ifPresentOrElse(taf -> {
					form.setResourcesAssigned(new java.util.ArrayList<>(taf.getResourcesAssigned()));
					taf.getResourcesAssigned().stream()
							.filter(r -> resource.getLeaderRole().equalsIgnoreCase(r.getFunction())
									|| resource.getLeaderRole().equalsIgnoreCase(r.getIcsPosition()))
							.findFirst().ifPresentOrElse(leader -> form.setIcsPosition(leader.getIcsPosition()),
									() -> form.setIcsPosition(resource.getLeaderRole()));
				}, () -> form.setIcsPosition(resource.getLeaderRole()));
		data.getActivityLogs().add(form);
		controller.markDirty(AppController.LinkSource.ICS214);
		rebuildLogTabs();
		openWorkspace(WorkspaceDestination.activityLog(activityLogInstanceId(form)));
	}

	/**
	 * Opens the SAR task linked to an assignment.
	 * @param assignmentId stable assignment identifier
	 */
	private void openSarTaskEditorForAssignment(String assignmentId) {
		openSarTaskEditorForAssignment(assignmentId, true);
	}

	/**
	 * Opens an assignment's SAR editor, optionally selecting the SAR tab.
	 * @param assignmentId stable assignment identifier
	 * @param switchToSarTab true to select the SAR tab
	 */
	private void openSarTaskEditorForAssignment(String assignmentId, boolean switchToSarTab) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return;
		}
		sarTaskPanel.refreshFromModel();
		Component previousTab = tabs.getSelectedComponent();
		int previousLogIndex = selectedLogIndex(previousTab);
		groupVisible.put(SAR_ONLY_GROUP, true);
		rebuildVisibleTabs(switchToSarTab ? sarTaskPanel : previousTab, switchToSarTab ? -1 : previousLogIndex);
		if (switchToSarTab) {
			tabs.setSelectedComponent(sarTaskPanel);
		}
		if (!sarTaskPanel.openAssignmentEditorById(assignmentId)) {
			JOptionPane.showMessageDialog(this, "No SAR task found for assignment ID " + assignmentId, "Open SAR Task",
					JOptionPane.INFORMATION_MESSAGE);
			if (!switchToSarTab && previousTab != null && tabs.indexOfComponent(previousTab) >= 0) {
				tabs.setSelectedComponent(previousTab);
			}
			return;
		}
		if (!switchToSarTab && previousTab != null && tabs.indexOfComponent(previousTab) >= 0) {
			tabs.setSelectedComponent(previousTab);
		}
	}

	/**
	 * Opens the ICS 204 editor for a linked assignment.
	 * @param assignmentId stable assignment identifier
	 */
	private void openIcs204EditorForAssignment(String assignmentId) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return;
		}
		ics204Panel.refreshFromModel();
		tabs.setSelectedComponent(ics204Panel);
		if (!ics204Panel.openEditorForAssignmentId(assignmentId)) {
			JOptionPane.showMessageDialog(this, "No ICS 204 assignment found for assignment ID " + assignmentId,
					"Open Assignment", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/**
	 * Launches the shared entry dialog without pushing or closing any pending editor.
	 *
	 * @param context initiating component and task/resource context
	 */
	private void showAddLogEntry(LogEntryContext context) {
		AppData incident = controller.getData();
		int previousLogCount = incident.getActivityLogs().size();
		Component owner = ActivityEntryDialog.resolveOwner(context.source(), this);
		if (!new ActivityEntryDialog(controller, context, null).show(owner) || controller.getData() != incident) {
			return;
		}
		if (previousLogCount != controller.getData().getActivityLogs().size()) {
			rebuildLogTabs();
		}
		for (Ics214Panel panel : ics214Panels) {
			panel.refreshActivityEntries();
		}
		clueLogPanel.refreshFromModel();
	}

	/** Creates an activity log using the selected scope and linked document. */
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
		int newIndex = ics214Panels.size() - 1;
		rebuildVisibleTabs(newIndex >= 0 ? ics214Panels.get(newIndex) : null, newIndex);
		if (newIndex >= 0 && tabs.indexOfComponent(ics214Panels.get(newIndex)) >= 0) {
			tabs.setSelectedComponent(ics214Panels.get(newIndex));
		}
		refreshStatus();
	}

	/**
	 * Removes an open activity log from the interface, deleting it only if empty and not the ICP log.
	 */
	void removeLog() {
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		AppData data = controller.getData();
		ensureLogs(data);
		List<Ics214Form> openLogs = data.getActivityLogs().stream()
				.filter(log -> !closedLogForms.contains(log))
				.toList();
		if (openLogs.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No open activity logs are available to remove.", "Remove Log",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Ics214Form activeForm = null;
		if (tabs.getSelectedComponent() instanceof Ics214Panel activePanel) {
			activeForm = activePanel.getCurrentForm();
		}
		JPanel panel = UiSupport.formPanel();
		JLabel explanation = new JLabel("<html><body style='width: 320px;'>"
				+ "This removes the log from the interface (and only deletes it if empty (no log entries), and the ICP log can't be deleted)."
				+ "</body></html>");
		JComboBox<LogRemovalChoice> logCombo = new JComboBox<>();
		LogRemovalChoice selectedChoice = null;
		for (Ics214Form log : openLogs) {
			LogRemovalChoice choice = new LogRemovalChoice(log, logMenuLabel(log));
			logCombo.addItem(choice);
			if (log == activeForm) {
				selectedChoice = choice;
			}
		}
		if (selectedChoice != null) {
			logCombo.setSelectedItem(selectedChoice);
		}
		UiSupport.addRow(panel, 0, "", explanation);
		UiSupport.addRow(panel, 1, "Log", logCombo);
		UiSupport.configureDialogComboBox(logCombo, 300);
		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		if (!UiSupport.showResizableConfirmDialog(this, "Remove Log", scrollPane, new Dimension(480, 180))) {
			return;
		}
		LogRemovalChoice chosen = (LogRemovalChoice) logCombo.getSelectedItem();
		if (chosen == null || chosen.form() == null) {
			return;
		}
		Ics214Form formToRemove = chosen.form();
		boolean isIcp = formToRemove.getLogScope() == ActivityLogScope.ICP;
		boolean isEmpty = formToRemove.getActivityLog().isEmpty();
		if (isEmpty && !isIcp) {
			data.getActivityLogs().remove(formToRemove);
			closedLogForms.remove(formToRemove);
			Ics214Panel.closePopOutWindow(formToRemove);
		} else {
			closedLogForms.add(formToRemove);
		}
		controller.markDirty(AppController.LinkSource.ICS214);
		rebuildLogTabs();
		int newIndex = Math.max(0, ics214Panels.size() - 1);
		rebuildVisibleTabs(newIndex < ics214Panels.size() ? ics214Panels.get(newIndex) : incidentContextPanel,
				newIndex);
		refreshStatus();
	}

	/**
	 * Removes the current activity log, delegating to {@link #removeLog()}.
	 *
	 * @deprecated Use {@link #removeLog()} instead.
	 */
	@Deprecated
	void removeCurrentLog() {
		removeLog();
	}

	/**
	 * Refreshes available logs to open in detached windows.
	 *
	 * @param openLogInNewWindowMenu submenu to rebuild
	 */
	private void rebuildOpenLogInNewWindowMenu(JMenu openLogInNewWindowMenu) {
		openLogInNewWindowMenu.removeAll();
		List<Ics214Form> logs = controller.getData().getActivityLogs();
		for (Ics214Form log : logs) {
			JMenuItem item = new JMenuItem(logMenuLabel(log));
			item.addActionListener(event -> openLogInNewWindow(log));
			openLogInNewWindowMenu.add(item);
		}
		openLogInNewWindowMenu.setEnabled(!logs.isEmpty());
	}

	/**
	 * Opens an existing activity log in an independent window.
	 *
	 * @param log form to display in an independent window
	 */
	private void openLogInNewWindow(Ics214Form log) {
		if (log == null) {
			return;
		}
		pushToModel(linkSourceForTab(tabs.getSelectedIndex()));
		Ics214Panel.openInNewWindow(controller, log, controller.getData(), () -> {
			refreshStatus();
			for (Ics214Panel panel : ics214Panels) {
				if (panel.getCurrentForm() == log) {
					panel.refreshActivityEntries();
				}
			}
			clueLogPanel.refreshFromModel();
		});
	}

	/**
	 * Refreshes navigation choices for the current activity logs.
	 *
	 * @param logsListMenu navigation menu to rebuild
	 */
	private void rebuildLogsListMenu(JMenu logsListMenu) {
		logsListMenu.removeAll();
		ButtonGroup group = new ButtonGroup();
		Component selectedComponent = tabs.getSelectedComponent();
		Ics214Form activeForm = selectedComponent instanceof Ics214Panel panel ? panel.getCurrentForm() : null;
		List<Ics214Form> logs = controller.getData().getActivityLogs();
		for (Ics214Form log : logs) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(logMenuLabel(log), log == activeForm);
			item.addActionListener(event -> openWorkspace(
					WorkspaceDestination.activityLog(activityLogInstanceId(log))));
			group.add(item);
			logsListMenu.add(item);
		}
		logsListMenu.setEnabled(!logs.isEmpty());
	}

	/**
	 * Selection item for log removal.
	 *
	 * @param form activity log form
	 * @param label display label
	 */
	private record LogRemovalChoice(Ics214Form form, String label) {
		/**
		 * Returns the display label for the selection choice.
		 *
		 * @return display label
		 */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * Resolves an editor to an activity-log index.
	 * @param component selected editor
	 * @return log index, or -1 when not an activity log
	 */
	private int selectedLogIndex(Component component) {
		if (component instanceof Ics214Panel panel) {
			return ics214Panels.indexOf(panel);
		}
		return -1;
	}

	/**
	 * Builds a descriptive activity-log navigation label.
	 * @param form activity log
	 * @return navigation label
	 */
	private String logMenuLabel(Ics214Form form) {
		String linked = resolveLinkedDocumentLabel(form, controller.getData());
		if (linked.isBlank()) {
			return form.getLogScope().getLabel();
		}
		return form.getLogScope().getLabel() + " – " + linked;
	}

	/**
	 * Builds a compact tab title using the log's scope and linkage.
	 * @param form activity log
	 * @param data active incident
	 * @return compact title
	 */
	private static String shortLogTabTitle(Ics214Form form, AppData data) {
		// For resource-linked logs (TASK_ASSIGNMENT), identify the tab by the resource
		// name (section 3)
		// so it reads "ICS 214 – Team X" matching the Resource column in the ICS 204
		// grid.
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
			return data.getSarTaskAssignments().stream().filter(a -> sarId.equals(a.getAssignmentId())).findFirst()
					.map(MainFrame::labelStringForAssignment).orElse("");
		}
		return "";
	}

	/**
	 * Shortens display text to a bounded length.
	 * @param value source text
	 * @param maxLength maximum display length
	 * @return shortened text
	 */
	private static String truncate(String value, int maxLength) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
	}

	/**
	 * Normalizes optional text for display.
	 * @param value nullable text
	 * @return nonnull display text
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * A document that can be linked to an ICS 214 activity log.
	 *
	 * @param label
	 *            display label shown in selectors.
	 * @param ics204FormId
	 *            linked ICS 204 form identifier, or blank.
	 * @param sarTaskAssignmentId
	 *            linked SAR task assignment identifier, or blank.
	 */
	private record AssociableDocument(String label, String ics204FormId, String sarTaskAssignmentId) {
		/** No associated document (ICP-level log). */
		static AssociableDocument none() {
			return new AssociableDocument("(none)", "", "");
		}

		/**
		 * Linked to an ICS 204 assignment list, referenced by its stable
		 * {@code formId}.
		 */
		static AssociableDocument forIcs204(String label, String formId) {
			return new AssociableDocument(label, formId, "");
		}

		/** Linked to a SAR task assignment, referenced by its {@code assignmentId}. */
		static AssociableDocument forSarTask(String label, String assignmentId) {
			return new AssociableDocument(label, "", assignmentId);
		}

		@Override
		/** {@inheritDoc} */
		public String toString() {
			return label;
		}
	}

	/**
	 * Builds the list of documents available for linking based on the selected
	 * scope.
	 */
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

	/**
	 * Creates a linkable ICS 204 document choice.
	 * @param form assignment list
	 * @param ordinal one-based list number
	 * @return document choice
	 */
	private static AssociableDocument labelFor204(Ics204Form form, int ordinal) {
		return AssociableDocument.forIcs204(labelStringFor204(form, ordinal), form.getFormId());
	}

	/**
	 * Builds an ICS 204 choice label from context or list number.
	 * @param form assignment list
	 * @param ordinal one-based list number
	 * @return display label
	 */
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

	/**
	 * Creates a linkable SAR assignment choice.
	 * @param assignment SAR task
	 * @return document choice
	 */
	private static AssociableDocument labelForAssignment(SarTaskAssignment assignment) {
		return AssociableDocument.forSarTask(labelStringForAssignment(assignment), assignment.getAssignmentId());
	}

	/**
	 * Builds a SAR choice label from its identifier and resource.
	 * @param assignment SAR task
	 * @return display label
	 */
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
	 * Role-oriented orderings of the same incident workspaces.
	 */
	enum Dashboard {
		IC_ICP("IC/ICP", List.of(WorkspaceId.SHARED, WorkspaceId.ORG_CHART, WorkspaceId.ICS_201,
				WorkspaceId.ICS_202, WorkspaceId.ICS_204, WorkspaceId.ICS_205, WorkspaceId.MEDICAL, WorkspaceId.SAFETY,
				WorkspaceId.SAR_TASKS, WorkspaceId.OPEN_ACTION_TRACKER, WorkspaceId.ACTIVITY_LOG,
				WorkspaceId.CLUE_LOG, WorkspaceId.TCARDS)),
		SAFETY_OFFICER("Safety Officer", List.of(WorkspaceId.SAFETY_OVERVIEW, WorkspaceId.SAFETY,
				WorkspaceId.MEDICAL, WorkspaceId.ORG_CHART, WorkspaceId.SHARED, WorkspaceId.TCARDS,
				WorkspaceId.ICS_204, WorkspaceId.ICS_205, WorkspaceId.SAR_TASKS, WorkspaceId.CLUE_LOG, WorkspaceId.ACTIVITY_LOG,
				WorkspaceId.OPEN_ACTION_TRACKER, WorkspaceId.ICS_202, WorkspaceId.ICS_201)),
		OPERATIONS("Operations", List.of(WorkspaceId.ICS_204, WorkspaceId.SAR_TASKS, WorkspaceId.TCARDS,
				WorkspaceId.CLUE_LOG, WorkspaceId.ACTIVITY_LOG, WorkspaceId.SAFETY, WorkspaceId.ORG_CHART,
				WorkspaceId.MEDICAL, WorkspaceId.SHARED, WorkspaceId.ICS_202, WorkspaceId.ICS_201,
				WorkspaceId.OPEN_ACTION_TRACKER, WorkspaceId.ICS_205)),
		COMMUNICATIONS("Communications", List.of(WorkspaceId.ICS_205, WorkspaceId.SAFETY, WorkspaceId.ORG_CHART, WorkspaceId.MEDICAL,
				WorkspaceId.TCARDS, WorkspaceId.ACTIVITY_LOG, WorkspaceId.CLUE_LOG, WorkspaceId.ICS_204,
				WorkspaceId.SAR_TASKS, WorkspaceId.SHARED, WorkspaceId.ICS_202, WorkspaceId.ICS_201,
				WorkspaceId.OPEN_ACTION_TRACKER));

		private final String label;
		private final List<WorkspaceId> workspaceIds;

		/**
		 * Creates one role-specific ordering for the shared workspaces.
		 *
		 * @param label display label.
		 * @param workspaceIds ordered stable workspace identifiers.
		 */
		Dashboard(String label, List<WorkspaceId> workspaceIds) {
			this.label = label;
			this.workspaceIds = workspaceIds;
		}

		/**
		 * Returns the label presented in the dashboard selector.
		 *
		 * @return dashboard label.
		 */
		public String label() {
			return label;
		}

		/**
		 * Returns this dashboard's order of shared editor identifiers.
		 *
		 * @return ordered workspace IDs.
		 */
		public List<WorkspaceId> workspaceIds() {
			return workspaceIds;
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			return label;
		}

		/**
		 * Resolves a saved dashboard setting safely.
		 *
		 * @param label saved label.
		 * @return matching dashboard or the legacy default.
		 */
		private static Dashboard from(String label) {
			for (Dashboard dashboard : values()) {
				if (dashboard.label.equals(label)) {
					return dashboard;
				}
			}
			return IC_ICP;
		}
	}

	/**
	 * Logs a PDF failure with its complete cause and displays actionable details.
	 *
	 * @param title export operation that failed.
	 * @param exception rendering, validation, or filesystem failure.
	 */
	private void showPdfExportError(String title, Exception exception) {
		java.util.logging.Logger.getLogger(MainFrame.class.getName())
				.log(java.util.logging.Level.SEVERE, title, exception);
		String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
		JOptionPane.showMessageDialog(this, title + ":\n" + detail, title, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Shows an error dialog for unexpected failures.
	 *
	 * @param title
	 *            dialog title.
	 * @param exception
	 *            failure to display.
	 */
	private void showError(String title, Exception exception) {
		JOptionPane.showMessageDialog(this, title + ":\n" + exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
	}
}
