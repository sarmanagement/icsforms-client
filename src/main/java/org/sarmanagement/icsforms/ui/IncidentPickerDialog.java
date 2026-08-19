package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.persistence.IncidentSummary;
import org.sarmanagement.icsforms.persistence.LocalRepository;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modal dialog that lists locally available incident workspace files for the operator to choose
 * from.
 *
 * <p>The dialog scans a known local directory (typically {@code ~/.icsforms}) for JSON workspace
 * files, shows a summary list sorted by last-modified time, and lets the operator either pick an
 * entry from the list or browse the filesystem for a file that is not in the list.  This design
 * is intended to generalise in future to remote incident repositories.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *     IncidentPickerDialog picker = new IncidentPickerDialog(owner, localDir);
 *     picker.setVisible(true);
 *     Path chosen = picker.getChosenPath();   // null if the operator cancelled
 * </pre>
 */
public class IncidentPickerDialog extends JDialog {

    private static final DateTimeFormatter LABEL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private Path chosenPath;

    /**
     * Creates the incident picker dialog.
     *
     * @param owner    owning window (may be {@code null}).
     * @param localDir directory to scan for saved incidents (typically {@code ~/.icsforms}).
     */
    public IncidentPickerDialog(Window owner, Path localDir) {
        super(owner, "Open Incident", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        List<IncidentSummary> summaries = LocalRepository.listLocalIncidents(localDir);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));

        // ----- list of known incidents -----
        String[] labels = summaries.stream()
                .map(s -> buildLabel(s))
                .toArray(String[]::new);

        JList<String> list = new JList<>(labels);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(520, 200));

        JLabel hint;
        if (summaries.isEmpty()) {
            hint = new JLabel("No saved incidents found in " + localDir + ".", SwingConstants.LEFT);
        } else {
            hint = new JLabel("Select a saved incident or browse for a file.", SwingConstants.LEFT);
        }
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
                chosenPath = summaries.get(idx).path();
                dispose();
            }
        });

        JButton browseButton = new JButton("Browse…");
        browseButton.addActionListener(e -> {
            Path browsed = chooseFile(localDir);
            if (browsed != null) {
                chosenPath = browsed;
                dispose();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        list.addListSelectionListener(e -> openButton.setEnabled(list.getSelectedIndex() >= 0));

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
     * Returns the path chosen by the operator, or {@code null} when the dialog was cancelled.
     *
     * @return chosen path, or {@code null}.
     */
    public Path getChosenPath() {
        return chosenPath;
    }

    // -------------------------------------------------------------------------

    private String buildLabel(IncidentSummary summary) {
        String modified = LABEL_FMT.format(summary.lastModified());
        String filename = summary.path().getFileName().toString();
        return summary.displayLabel() + "    (" + filename + ", last saved " + modified + ")";
    }

    private Path chooseFile(Path startDir) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (startDir != null && startDir.toFile().isDirectory()) {
            chooser.setCurrentDirectory(startDir.toFile());
        }
        int result = chooser.showOpenDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }
}
