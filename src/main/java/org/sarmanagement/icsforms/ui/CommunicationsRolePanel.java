/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.comms.ChannelLinkSource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Live Communications Unit channel workbench with stable links, explicit radio
 * checks, coverage guidance, review, and existing CSV workflows.
 */
public class CommunicationsRolePanel extends JPanel {
	private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final Consumer<WorkspaceDestination> navigator;
	private final Runnable importCsv;
	private final Runnable exportCsv;
	private final AppController.IncidentBinding binding;
	private final JLabel banner = new JLabel();
	private final JTextField filter = new JTextField(22);
	private final ChannelModel channelModel = new ChannelModel();
	private final JTable channels = new JTable(channelModel);
	private final TableRowSorter<ChannelModel> sorter = new TableRowSorter<>(channelModel);
	private final JTextArea assignment = readOnlyArea("Selected channel assignment");
	private final JTextArea coverage = editableArea("Coverage guidance");
	private final JTextArea relay = editableArea("Relay guidance");
	private final JComboBox<RadioCheckStatus> checkStatus = new JComboBox<>(RadioCheckStatus.values());
	private final JTextField checkedBy = new JTextField(20);
	private final JTextArea checkNotes = editableArea("Radio-check notes");
	private final JLabel checkedAt = new JLabel("Not recorded");
	private final javax.swing.DefaultListModel<String> linkModel = new javax.swing.DefaultListModel<>();
	private final JList<String> links = new JList<>(linkModel);
	private final JLabel saveStatus = new JLabel(" ");
	private final PlanReviewStrip review;
	private final AppController.IncidentChangeSubscription subscription;
	private RadioChannel baseline;
	private String loadedChannelId = "";
	private boolean refreshing;
	private boolean editorDirty;

	/**
	 * Creates the Communications Unit panel.
	 *
	 * @param controller incident controller.
	 * @param navigator typed full-workspace navigator.
	 * @param importCsv established native/CHIRP import workflow.
	 * @param exportCsv established native export workflow.
	 */
	public CommunicationsRolePanel(AppController controller, Consumer<WorkspaceDestination> navigator,
			Runnable importCsv, Runnable exportCsv) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.navigator = navigator;
		this.importCsv = importCsv;
		this.exportCsv = exportCsv;
		binding = controller.captureIncidentBinding();
		review = new PlanReviewStrip(controller, PlanReviewArea.COMMUNICATIONS, this::commitFocusedEdits);
		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		installListeners();
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			if (controller.isCurrent(binding)) {
				refreshFromModel();
			}
		});
	}

	/** Releases the live incident listener. */
	public void dispose() {
		subscription.close();
	}

	/**
	 * Refreshes detached channel and link snapshots while preserving selected
	 * identity and unsaved focused input.
	 */
	public void refreshFromModel() {
		refreshing = true;
		AppData data = controller.getData();
		String selectedId = selectedChannelId();
		if (selectedId.isBlank()) {
			selectedId = loadedChannelId;
		}
		RolePanelSupport.banner(banner, "COMMUNICATIONS UNIT", data.getIncidentContext().getIncidentName(),
				"Radio checks are explicit operator assertions; programming and coverage are not inferred");
		channelModel.setRows(data.getForm205().getChannels());
		restoreSelection(selectedId);
		review.refreshFromModel();
		refreshing = false;
		if (!editorDirty || !Objects.equals(loadedChannelId, selectedId)) {
			loadSelectedChannel();
		} else {
			rebuildLinks(loadedChannelId);
		}
	}

	/**
	 * Returns the selected stable channel identity for focused tests.
	 *
	 * @return channel ID, or blank.
	 */
	String selectedChannelId() {
		int viewRow = channels.getSelectedRow();
		if (viewRow < 0) {
			return "";
		}
		return channelModel.row(channels.convertRowIndexToModel(viewRow)).channelId();
	}

	/**
	 * Returns detached displayed links for one selected channel.
	 *
	 * @return immutable link labels.
	 */
	List<String> displayedLinks() {
		return java.util.Collections.list(linkModel.elements());
	}

	/**
	 * Builds incident context, import/export, and full-workspace actions.
	 *
	 * @return header panel.
	 */
	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.add(banner, BorderLayout.CENTER);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING));
		JButton fullPlan = new JButton("Open full ICS 205");
		fullPlan.addActionListener(event -> navigator.accept(WorkspaceDestination.of(WorkspaceId.ICS_205)));
		JButton importButton = new JButton("Import CSV…");
		importButton.addActionListener(event -> importCsv.run());
		JButton exportButton = new JButton("Export CSV…");
		exportButton.addActionListener(event -> exportCsv.run());
		actions.add(fullPlan);
		actions.add(importButton);
		actions.add(exportButton);
		header.add(actions, BorderLayout.SOUTH);
		return header;
	}

	/**
	 * Builds sortable channel list and selected-channel operations editor.
	 *
	 * @return split panel.
	 */
	private JSplitPane buildBody() {
		JPanel inventory = new JPanel(new BorderLayout(4, 4));
		inventory.setBorder(javax.swing.BorderFactory.createTitledBorder("Channel inventory"));
		JPanel search = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		search.add(new JLabel("Filter"));
		filter.getAccessibleContext().setAccessibleName("Filter communications channels");
		search.add(filter);
		inventory.add(search, BorderLayout.NORTH);
		channels.setRowSorter(sorter);
		channels.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		channels.getAccessibleContext().setAccessibleName("Sortable communications channels");
		inventory.add(new JScrollPane(channels), BorderLayout.CENTER);

		JPanel details = UiSupport.formPanel();
		UiSupport.addRow(details, 0, "Assignment / location / team", new JScrollPane(assignment));
		UiSupport.addRow(details, 1, "Coverage guidance", new JScrollPane(coverage));
		UiSupport.addRow(details, 2, "Relay guidance", new JScrollPane(relay));
		checkStatus.getAccessibleContext().setAccessibleName("Explicit radio-check status");
		UiSupport.addRow(details, 3, "Radio check", checkStatus);
		checkedBy.getAccessibleContext().setAccessibleName("Radio check recorded by");
		UiSupport.addRow(details, 4, "Checked by", checkedBy);
		UiSupport.addRow(details, 5, "Checked at", checkedAt);
		UiSupport.addRow(details, 6, "Check notes", new JScrollPane(checkNotes));
		links.getAccessibleContext().setAccessibleName("Current stable references to selected channel");
		UiSupport.addRow(details, 7, "Linked current-period uses", new JScrollPane(links));
		JButton save = new JButton("Save channel operations details");
		save.addActionListener(event -> saveFocusedEdits());
		JPanel saveRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
		saveRow.add(save);
		saveRow.add(saveStatus);
		UiSupport.addRow(details, 8, "", saveRow);
		UiSupport.addRow(details, 9, "", review);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inventory, new JScrollPane(details));
		split.setResizeWeight(0.55);
		split.setPreferredSize(new Dimension(1120, 680));
		return split;
	}

	/** Installs selection, filter, and draft-change listeners. */
	private void installListeners() {
		channels.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !refreshing) {
				loadSelectedChannel();
			}
		});
		filter.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				applyFilter();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				applyFilter();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				applyFilter();
			}
		});
		DocumentListener dirtyListener = new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				markEditorDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				markEditorDirty();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				markEditorDirty();
			}
		};
		for (javax.swing.text.JTextComponent field : List.of(coverage, relay, checkedBy, checkNotes)) {
			field.getDocument().addDocumentListener(dirtyListener);
		}
		checkStatus.addActionListener(event -> markEditorDirty());
	}

	/** Applies a case-insensitive literal filter across displayed channel columns. */
	private void applyFilter() {
		String text = filter.getText();
		sorter.setRowFilter(text.isBlank() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
	}

	/** Marks focused input dirty unless fields are being loaded. */
	private void markEditorDirty() {
		if (!refreshing) {
			editorDirty = true;
			saveStatus.setText("Unsaved focused changes");
		}
	}

	/**
	 * Restores selection by stable identity after model refresh.
	 *
	 * @param channelId prior channel identity.
	 */
	private void restoreSelection(String channelId) {
		channels.clearSelection();
		for (int modelRow = 0; modelRow < channelModel.getRowCount(); modelRow++) {
			if (channelId.equals(channelModel.row(modelRow).channelId())) {
				int viewRow = channels.convertRowIndexToView(modelRow);
				if (viewRow >= 0) {
					channels.setRowSelectionInterval(viewRow, viewRow);
				}
				return;
			}
		}
		if (channels.getRowCount() > 0 && !editorDirty) {
			channels.setRowSelectionInterval(0, 0);
		}
	}

	/** Loads the selected model channel into focused fields and captures a baseline. */
	private void loadSelectedChannel() {
		String id = selectedChannelId();
		RadioChannel channel = controller.getData().getForm205().getChannels().stream()
				.filter(candidate -> id.equals(candidate.getChannelId())).findFirst().orElse(null);
		refreshing = true;
		if (channel == null) {
			if (!editorDirty) {
				loadedChannelId = "";
				baseline = null;
				assignment.setText("");
				coverage.setText("");
				relay.setText("");
				checkStatus.setSelectedItem(RadioCheckStatus.NOT_RECORDED);
				checkedBy.setText("");
				checkedAt.setText("Not recorded");
				checkNotes.setText("");
			}
			rebuildLinks("");
			saveStatus.setText(editorDirty ? "Selected channel changed; reload before saving" : " ");
			refreshing = false;
			return;
		}
		loadedChannelId = id;
		baseline = channel.deepCopy();
		assignment.setText(channel.getAssignment());
		coverage.setText(channel.getCoverageGuidance());
		relay.setText(channel.getRelayGuidance());
		checkStatus.setSelectedItem(channel.getRadioCheckStatus());
		checkedBy.setText(channel.getRadioCheckedBy());
		checkedAt.setText(channel.getRadioCheckedAt() == null ? "Not recorded"
				: CHECK_TIME.format(channel.getRadioCheckedAt()));
		checkNotes.setText(channel.getRadioCheckNotes());
		rebuildLinks(id);
		editorDirty = false;
		saveStatus.setText(" ");
		refreshing = false;
	}

	/**
	 * Rebuilds stable-link snapshots for one channel without modifying source models.
	 *
	 * @param channelId selected channel identity.
	 */
	private void rebuildLinks(String channelId) {
		linkModel.clear();
		for (ChannelLinkSource.ChannelLink link : ChannelLinkSource.links(controller.getData())) {
			if (channelId.equals(link.channelId())
					&& link.status() == ChannelLinkSource.LinkStatus.LINKED
					&& link.sourceKind() != ChannelLinkSource.SourceKind.ICS205_ASSIGNMENT) {
				linkModel.addElement(link.sourceKind() + " — " + link.sourceLabel());
			}
		}
		if (linkModel.isEmpty() && !channelId.isBlank()) {
			linkModel.addElement("No stable current-period references");
		}
	}

	/** Saves focused edits and presents validation or stale-edit feedback. */
	private void saveFocusedEdits() {
		try {
			commitFocusedEdits();
		} catch (IllegalArgumentException | IllegalStateException exception) {
			UiSupport.showDocumentMessage(this, exception.getMessage(), "Unable to save channel details",
					javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Commits focused fields through the optimistic channel operation.
	 *
	 * @throws IllegalArgumentException when explicit check details are incomplete.
	 * @throws IllegalStateException when the loaded baseline is stale.
	 */
	private void commitFocusedEdits() {
		if (baseline == null || loadedChannelId.isBlank() || !editorDirty) {
			return;
		}
		RadioCheckStatus selectedStatus = (RadioCheckStatus) checkStatus.getSelectedItem();
		LocalDateTime time = selectedStatus == RadioCheckStatus.NOT_RECORDED ? null
				: selectedStatus == baseline.getRadioCheckStatus() && baseline.getRadioCheckedAt() != null
						? baseline.getRadioCheckedAt()
						: LocalDateTime.now().withSecond(0).withNano(0);
		controller.updateRadioChannelOperations(loadedChannelId, baseline, coverage.getText(), relay.getText(),
				selectedStatus, checkedBy.getText(), time, checkNotes.getText());
		loadSelectedChannel();
		saveStatus.setText("Saved");
	}

	/**
	 * Creates a wrapped editable area with an accessible name.
	 *
	 * @param accessibleName accessible name.
	 * @return configured text area.
	 */
	private static JTextArea editableArea(String accessibleName) {
		JTextArea area = new JTextArea(3, 32);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.getAccessibleContext().setAccessibleName(accessibleName);
		return area;
	}

	/**
	 * Creates a wrapped read-only area.
	 *
	 * @param accessibleName accessible name.
	 * @return configured text area.
	 */
	private static JTextArea readOnlyArea(String accessibleName) {
		JTextArea area = editableArea(accessibleName);
		area.setEditable(false);
		return area;
	}

	/**
	 * Detached row used by the sortable role table.
	 *
	 * @param channelId stable channel identity.
	 * @param name display name.
	 * @param function function.
	 * @param assignment assignment.
	 * @param rx receive frequency.
	 * @param tx transmit frequency.
	 * @param check radio-check label.
	 */
	private record ChannelRow(String channelId, String name, String function, String assignment, String rx, String tx,
			String check) {
	}

	/** Read-only table model containing detached channel summaries. */
	private static final class ChannelModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private static final String[] COLUMNS = {
				"Channel", "Function", "Assignment", "RX", "TX", "Radio check"};
		private List<ChannelRow> rows = List.of();

		/**
		 * Rebuilds detached rows.
		 *
		 * @param channels current model channels.
		 */
		void setRows(List<RadioChannel> channels) {
			List<ChannelRow> updated = new ArrayList<>();
			for (RadioChannel channel : channels) {
				updated.add(new ChannelRow(channel.getChannelId(), ChannelLinkSource.channelLabel(channel),
						channel.getFunction(), channel.getAssignment(), channel.getRxFrequency(),
						channel.getTxFrequency(), channel.getRadioCheckStatus().displayLabel()));
			}
			rows = List.copyOf(updated);
			fireTableDataChanged();
		}

		/**
		 * Returns one detached row.
		 *
		 * @param row model row.
		 * @return row snapshot.
		 */
		ChannelRow row(int row) {
			return rows.get(row);
		}

		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return rows.size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		/** {@inheritDoc} */
		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int row, int column) {
			ChannelRow value = rows.get(row);
			return switch (column) {
				case 0 -> value.name();
				case 1 -> value.function();
				case 2 -> value.assignment();
				case 3 -> value.rx();
				case 4 -> value.tx();
				default -> value.check();
			};
		}

		/** {@inheritDoc} */
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	}
}
