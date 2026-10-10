package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.persistence.IncidentSummary;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;

/**
 * Modal picker for incidents listed by the configured store.
 */
public class IncidentPickerDialog extends JDialog {

	private IncidentId chosenIncidentId;

	/**
	 * Creates the incident picker dialog.
	 *
	 * @param owner
	 *            owning window (may be {@code null}).
	 * @param store incident store to list.
	 */
	public IncidentPickerDialog(Window owner, IncidentStore store) {
		super(owner, "Open Incident", ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		List<IncidentSummary> summaries = store.list();

		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));

		// ----- list of known incidents -----
		String[] labels = summaries.stream().map(this::buildLabel).toArray(String[]::new);

		JList<String> list = new JList<>(labels);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(8);

		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(520, 200));

		var descriptor = store.describe();
		String location = descriptor.type() + ": " + descriptor.location();
		JLabel hint = new JLabel((summaries.isEmpty() ? "No saved incidents in " : "Select an incident from ")
				+ location + ", or browse for JSON.", SwingConstants.LEFT);
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.add(hint);
		centerPanel.add(scroll);
		content.add(centerPanel, BorderLayout.CENTER);

		// ----- buttons -----
		JButton openButton = new JButton("Open");
		openButton.setEnabled(false);
		openButton.addActionListener(e -> {
			int idx = list.getSelectedIndex();
			if (idx >= 0) {
				chosenIncidentId = summaries.get(idx).incidentId();
				dispose();
			}
		});

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> dispose());

		JButton browseButton = new JButton("Browse…");
		browseButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileFilter(new FileNameExtensionFilter("Incident JSON (*.json)", "json"));
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				try {
					IncidentId imported = store.importFile(chooser.getSelectedFile().toPath()).orElse(null);
					if (imported != null) {
						chosenIncidentId = imported;
						dispose();
					}
				} catch (RuntimeException exception) {
					JOptionPane.showMessageDialog(this, exception.getMessage(), "Unable to import incident",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		list.addListSelectionListener(e -> openButton.setEnabled(list.getSelectedIndex() >= 0));
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) {
					int idx = list.locationToIndex(e.getPoint());
					if (idx >= 0) {
						chosenIncidentId = summaries.get(idx).incidentId();
						dispose();
					}
				}
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(openButton);
		buttons.add(browseButton);
		buttons.add(cancelButton);
		content.add(buttons, BorderLayout.SOUTH);

		getRootPane().setDefaultButton(openButton);
		setContentPane(content);
		pack();
		setMinimumSize(new Dimension(540, getHeight()));
		setLocationRelativeTo(owner);
	}

	/**
	 * Returns the identifier chosen by the operator, or {@code null} when the dialog was
	 * cancelled.
	 *
	 * @return chosen identifier, or {@code null}.
	 */
	public IncidentId getChosenIncidentId() {
		return chosenIncidentId;
	}

	// -------------------------------------------------------------------------

	/**
	 * Formats a store summary for the picker.
	 *
	 * @param summary listed incident.
	 * @return readable label.
	 */
	private String buildLabel(IncidentSummary summary) {
		String number = summary.incidentNumber();
		return summary.displayLabel() + (number == null || number.isBlank() ? "" : " — " + number)
				+ "    (" + summary.incidentId().value() + ")";
	}
}
