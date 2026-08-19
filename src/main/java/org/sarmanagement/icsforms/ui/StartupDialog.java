package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.IncidentMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.nio.file.Path;

/**
 * Modal startup dialog presented when the application launches or when File → New is chosen.
 *
 * <p>The operator selects an action (start a new incident in one of the three phases, open an
 * existing incident, or open an existing incident and advance to a new operational period) and
 * chooses the incident mode (SAR or Generic).  When {@code exitOnClose} is {@code true} (the
 * default for application startup) dismissing the dialog without a selection terminates the JVM;
 * when {@code false} (used from the File → New menu action) it simply disposes the dialog.</p>
 */
public class StartupDialog extends JDialog {

    /** The action the operator chose at startup. */
    public enum StartupAction {
        /** Start a new incident in the pre-operational planning phase. */
        NEW_PRE_OP,
        /** Start a new incident in the initial incident response phase (ICS 201 primary). */
        NEW_INITIAL_RESPONSE,
        /** Start a new incident workspace for a subsequent operational period (ICS 201 read-only). */
        NEW_OPERATIONAL_PERIOD,
        /** Open an existing saved incident workspace. */
        OPEN_EXISTING,
        /** Open an existing saved incident workspace and advance it to a new operational period. */
        OPEN_NEW_PERIOD
    }

    private StartupAction chosenAction;
    private IncidentMode chosenMode = IncidentMode.SAR;
    private Path chosenPath;
    private final boolean exitOnClose;

    /**
     * Creates the startup dialog.
     *
     * @param owner          owning frame (may be {@code null}).
     * @param defaultDirectory directory offered to the incident picker and file chooser.
     * @param exitOnClose    when {@code true}, closing without a selection terminates the JVM;
     *                       when {@code false}, the dialog is simply disposed (used from File → New).
     */
    public StartupDialog(Frame owner, Path defaultDirectory, boolean exitOnClose) {
        super(owner, "ICS Forms Desktop — Start", true);
        this.exitOnClose = exitOnClose;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        content.add(buildActionPanel(defaultDirectory), BorderLayout.CENTER);
        content.add(buildModePanel(), BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(480, getHeight()));
        setLocationRelativeTo(owner);

        // Exit the application if the dialog is closed without choosing an action at startup.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (exitOnClose) {
                    System.exit(0);
                }
            }
        });
    }

    /**
     * Returns the action the operator selected, or {@code null} if the dialog was dismissed
     * without a selection.
     *
     * @return chosen startup action.
     */
    public StartupAction getChosenAction() {
        return chosenAction;
    }

    /**
     * Returns the incident mode selected by the operator.
     *
     * @return chosen incident mode.
     */
    public IncidentMode getChosenMode() {
        return chosenMode;
    }

    /**
     * Returns the file path chosen when the operator selected an open action, or {@code null}
     * for new-incident actions.
     *
     * @return chosen file path, or {@code null}.
     */
    public Path getChosenPath() {
        return chosenPath;
    }

    // -------------------------------------------------------------------------

    private JPanel buildActionPanel(Path defaultDirectory) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Choose an action"));

        panel.add(buildActionRow(
                "New incident — Pre-Operational (planning)",
                "<html>Prepare for a potential or anticipated incident before response begins.<br>"
                        + "ICS 201 is available but not yet primary.</html>",
                StartupAction.NEW_PRE_OP, defaultDirectory));
        panel.add(Box.createVerticalStrut(6));

        panel.add(buildActionRow(
                "New incident — Initial Incident Response",
                "<html>An incident has started.  ICS 201 is the primary capture tool<br>"
                        + "and is fully editable.</html>",
                StartupAction.NEW_INITIAL_RESPONSE, defaultDirectory));
        panel.add(Box.createVerticalStrut(6));

        panel.add(buildActionRow(
                "New incident — Subsequent Operational Period",
                "<html>Start a fresh workspace for a new operational period.<br>"
                        + "ICS 202 and ICS 204 are the primary forms; ICS 201 is read-only.</html>",
                StartupAction.NEW_OPERATIONAL_PERIOD, defaultDirectory));
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(10));

        panel.add(buildActionRow(
                "Open existing incident…",
                "<html>Continue working on a previously saved incident workspace.</html>",
                StartupAction.OPEN_EXISTING, defaultDirectory));
        panel.add(Box.createVerticalStrut(6));

        panel.add(buildActionRow(
                "Open existing incident and add a new operational period…",
                "<html>Load an existing workspace and advance it to the next<br>"
                        + "operational period, clearing operational forms while keeping<br>"
                        + "the ICS 201 and org chart as historical context.</html>",
                StartupAction.OPEN_NEW_PERIOD, defaultDirectory));

        return panel;
    }

    private JPanel buildActionRow(String title, String description, StartupAction action,
                                Path defaultDirectory) {
        JButton button = new JButton(title);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(event -> {
            if (action == StartupAction.OPEN_EXISTING || action == StartupAction.OPEN_NEW_PERIOD) {
                Path chosen = pickIncident(defaultDirectory);
                if (chosen == null) {
                    return;
                }
                chosenPath = chosen;
            }
            chosenAction = action;
            dispose();
        });

        JLabel desc = new JLabel(description);
        desc.setAlignmentX(LEFT_ALIGNMENT);
        desc.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(button);
        row.add(desc);
        return row;
    }

    private Path pickIncident(Path defaultDirectory) {
        IncidentPickerDialog picker = new IncidentPickerDialog(this, defaultDirectory);
        picker.setVisible(true);
        return picker.getChosenPath();
    }

    private JPanel buildModePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Incident mode"));

        JRadioButton sarButton = new JRadioButton("SAR (Search and Rescue)", true);
        JRadioButton genericButton = new JRadioButton("Generic Incident");
        ButtonGroup group = new ButtonGroup();
        group.add(sarButton);
        group.add(genericButton);

        sarButton.addActionListener(e -> chosenMode = IncidentMode.SAR);
        genericButton.addActionListener(e -> chosenMode = IncidentMode.GENERIC);

        panel.add(sarButton);
        panel.add(genericButton);
        return panel;
    }
}
