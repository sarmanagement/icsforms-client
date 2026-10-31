/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReuseService;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.persistence.IncidentSummary;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Explicit, selective plan reuse from completed periods or another saved incident.
 */
public final class BringForwardDialog extends JDialog {
	private final AppController controller;
	private final AppController.IncidentBinding binding;
	private final JRadioButton previous = new JRadioButton("Previous operational period(s)", true);
	private final JRadioButton other = new JRadioButton("Import from another incident");
	private final JComboBox<IncidentChoice> incidents = new JComboBox<>();
	private final JComboBox<PeriodChoice> periods = new JComboBox<>();
	private final Map<PlanReviewArea, JCheckBox> choices = new EnumMap<>(PlanReviewArea.class);
	private final JCheckBox ics215Choice = new JCheckBox("Operational planning (ICS 215)");
	private final JCheckBox retainIcs215Required = new JCheckBox("Retain Required quantities");
	private final JButton apply = new JButton("Bring Forward");
	private final JLabel status = new JLabel(" ");

	/**
	 * Builds a modal dialog; no model content changes until the operator confirms.
	 *
	 * @param owner owning window.
	 * @param controller active incident controller.
	 */
	public BringForwardDialog(Window owner, AppController controller) {
		super(owner, "Bring Forward Plans", ModalityType.APPLICATION_MODAL);
		this.controller = controller;
		this.binding = controller.captureIncidentBinding();
		ApplicationIcon.install(this);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JPanel fields = new JPanel();
		fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
		ButtonGroup group = new ButtonGroup();
		group.add(previous);
		group.add(other);
		fields.add(previous);
		fields.add(other);
		fields.add(new JLabel("Source incident:"));
		fields.add(incidents);
		fields.add(new JLabel("Source operational period:"));
		fields.add(periods);
		for (PlanReviewArea area : PlanReviewArea.values()) {
			JCheckBox choice = new JCheckBox(switch (area) {
				case COMMUNICATIONS -> "Communications (ICS 205)";
				case SAFETY -> "Safety (ICS 215A and ICS 208)";
				case HUMAN_MEDICAL -> "Human medical (ICS 206)";
				case CANINE_MEDICAL -> "Canine medical (ICS 206CANINE)";
			});
			choices.put(area, choice);
			choice.addActionListener(event -> updateApply());
			fields.add(choice);
		}
		ics215Choice.addActionListener(event -> updateApply());
		fields.add(ics215Choice);
		retainIcs215Required.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
		fields.add(retainIcs215Required);
		fields.add(new JLabel("<html>Selected plans replace current content; other plans remain unchanged.<br>"
				+ "Incident and operational period stay unchanged. Copied plans require a new review.<br>"
				+ "Repeat for other source periods to combine different plan areas.</html>"));
		fields.add(status);
		content.add(fields, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(event -> dispose());
		apply.addActionListener(event -> applySelection());
		actions.add(cancel);
		actions.add(apply);
		content.add(actions, BorderLayout.SOUTH);
		setContentPane(content);
		if (controller.getStore() != null) {
			for (IncidentSummary summary : controller.getStore().list()) {
				if (summary.incidentId() != null && !summary.incidentId().equals(controller.getActiveIncidentId())) {
					incidents.addItem(new IncidentChoice(summary));
				}
			}
		}
		previous.setEnabled(controller.hasPreviousPlans());
		other.setEnabled(incidents.getItemCount() > 0);
		if (!previous.isEnabled() && other.isEnabled()) {
			other.setSelected(true);
		}
		previous.addActionListener(event -> reloadSources());
		other.addActionListener(event -> reloadSources());
		incidents.addActionListener(event -> reloadSources());
		periods.addActionListener(event -> updateChoices());
		reloadSources();
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Opens the reusable dialog from a File menu or editor panel, flushing and
	 * refreshing all editors through the controller's configured callbacks.
	 *
	 * @param owner parent component.
	 * @param controller active controller.
	 */
	public static void showDialog(Component owner, AppController controller) {
		Window window = owner instanceof Window owningWindow ? owningWindow
				: owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		controller.withPlanReuseEditors(() -> new BringForwardDialog(window, controller).setVisible(true));
	}

	/**
	 * Routes an embedded editor through its frame's shared operation so stale sibling
	 * controls cannot overwrite imported plans. Standalone editors retain local flushing.
	 *
	 * @param editor launching editor.
	 * @param controller active controller.
	 * @param flushStandalone commits a standalone editor's pending edits.
	 * @param refreshStandalone reloads a standalone editor after the interaction.
	 */
	public static void showFromEditor(Component editor, AppController controller,
			Runnable flushStandalone, Runnable refreshStandalone) {
		if (SwingUtilities.getWindowAncestor(editor) instanceof MainFrame frame) {
			frame.openBringForwardPlans();
			return;
		}
		flushStandalone.run();
		try {
			showDialog(editor, controller);
		} finally {
			refreshStandalone.run();
		}
	}

	/** Loads sources without switching the active incident or claiming its edit lock. */
	private void reloadSources() {
		periods.removeAllItems();
		incidents.setEnabled(other.isSelected() && other.isEnabled());
		try {
			List<AppData.OperationalPeriodRecord> sources;
			if (other.isSelected() && incidents.getSelectedItem() instanceof IncidentChoice incident) {
				sources = controller.getImportPlanSources(incident.summary().incidentId());
			} else {
				sources = controller.getPreviousPlanSources();
			}
			for (int index = 0; index < sources.size(); index++) {
				periods.addItem(new PeriodChoice(sources.get(index), index + 1));
			}
			status.setText(sources.isEmpty() ? "No saved plans are available in this source." : " ");
		} catch (RuntimeException exception) {
			status.setText("Unable to load source: " + exception.getMessage());
		}
		updateChoices();
	}

	/** Enables only plan types actually present in the selected source snapshot. */
	private void updateChoices() {
		PeriodChoice selected = (PeriodChoice) periods.getSelectedItem();
		var available = PlanReuseService.availablePlans(selected == null ? null : selected.period());
		choices.forEach((area, choice) -> {
			choice.setEnabled(available.contains(area));
			choice.setSelected(false);
		});
		ics215Choice.setEnabled(PlanReuseService.hasIcs215Plan(selected == null ? null : selected.period()));
		ics215Choice.setSelected(false);
		retainIcs215Required.setEnabled(ics215Choice.isEnabled());
		retainIcs215Required.setSelected(false);
		updateApply();
	}

	/** Requires an explicit selection before replacing any plan content. */
	private void updateApply() {
		apply.setEnabled(ics215Choice.isEnabled() && ics215Choice.isSelected()
				|| choices.values().stream().anyMatch(choice -> choice.isEnabled() && choice.isSelected()));
	}

	/** Confirms replacement and applies the explicit selection to the original incident only. */
	private void applySelection() {
		if (!checkBinding()) {
			return;
		}
		PeriodChoice selected = (PeriodChoice) periods.getSelectedItem();
		EnumSet<PlanReviewArea> areas = EnumSet.noneOf(PlanReviewArea.class);
		choices.forEach((area, choice) -> {
			if (choice.isEnabled() && choice.isSelected()) {
				areas.add(area);
			}
		});
		boolean includeIcs215 = ics215Choice.isEnabled() && ics215Choice.isSelected();
		if (selected == null || areas.isEmpty() && !includeIcs215 || JOptionPane.showConfirmDialog(this,
				"Replace selected current plans? ICS 215 is copied to the next-period worksheet. "
						+ "Dates, Have evidence, allocations, approvals, and signatures are cleared.",
				"Bring Forward", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		if (!checkBinding()) {
			return;
		}
		try {
			controller.bringForwardPlans(selected.period(), areas, other.isSelected(), includeIcs215,
					retainIcs215Required.isEnabled() && retainIcs215Required.isSelected());
			dispose();
		} catch (RuntimeException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Bring Forward", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Rejects a stale incident or operational period, including changes made while
	 * the confirmation dialog's nested event loop was running.
	 *
	 * @return true when the original incident and period remain active.
	 */
	private boolean checkBinding() {
		if (controller.isCurrent(binding)) {
			return true;
		}
		JOptionPane.showMessageDialog(this, "The active incident changed. Reopen Bring Forward.",
				"Bring Forward", JOptionPane.WARNING_MESSAGE);
		dispose();
		return false;
	}

	/**
	 * Human-readable incident choice that retains its stable source identity.
	 *
	 * @param summary saved incident summary.
	 */
	private record IncidentChoice(IncidentSummary summary) {
		/** {@inheritDoc} */
		@Override
		public String toString() {
			return summary.displayLabel() + " — " + summary.incidentId().value();
		}
	}

	/**
	 * Human-readable period choice, including an ordinal for undated legacy periods.
	 *
	 * @param period detached plan source.
	 * @param ordinal one-based source ordering.
	 */
	private record PeriodChoice(AppData.OperationalPeriodRecord period, int ordinal) {
		/** {@inheritDoc} */
		@Override
		public String toString() {
			return "Source " + ordinal + ": " + (period.getPeriodStart() == null ? "(start not recorded)"
					: period.getPeriodStart()) + " — " + (period.getPeriodEnd() == null ? "(end not recorded)"
					: period.getPeriodEnd());
		}
	}
}
