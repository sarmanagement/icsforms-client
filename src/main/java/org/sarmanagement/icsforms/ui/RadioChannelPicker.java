/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.DefaultCellEditor;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shared searchable channel picker that deliberately preserves legacy free-text fields.
 */
final class RadioChannelPicker {
	/** Prevents construction of this utility. */
	private RadioChannelPicker() {
	}

	/**
	 * Adds an accessible popup and keyboard shortcut without replacing the field or its value.
	 *
	 * @param field existing editable contact/radio field.
	 * @param controller current incident controller.
	 */
	static void install(JTextField field, AppController controller) {
		if (Boolean.TRUE.equals(field.getClientProperty("radioChannelPicker"))) {
			return;
		}
		field.putClientProperty("radioChannelPicker", Boolean.TRUE);
		AbstractAction action = new AbstractAction("Choose communications plan channel…") {
			/** {@inheritDoc} */
			@Override
			public void actionPerformed(ActionEvent event) {
				RadioChannel channel = choose(field, controller);
				if (channel != null) {
					field.setText(displayName(channel));
					field.putClientProperty("radioChannelId", channel.getChannelId());
				}
			}
		};
		JPopupMenu menu = field.getComponentPopupMenu();
		if (menu == null) {
			menu = new JPopupMenu();
			field.setComponentPopupMenu(menu);
		}
		menu.add(new JMenuItem(action));
		field.getInputMap().put(KeyStroke.getKeyStroke("alt DOWN"), "pick-radio-channel");
		field.getActionMap().put("pick-radio-channel", action);
		field.setToolTipText("Free text, or Alt+Down / right-click to choose a communications plan channel");
	}

	/**
	 * Adds the picker to a legacy radio/contact table column without restricting free text.
	 *
	 * @param table editable legacy table.
	 * @param column model column index.
	 * @param controller active incident controller.
	 */
	static void install(JTable table, int column, AppController controller) {
		JTextField field = new JTextField();
		install(field, controller);
		table.getColumnModel().getColumn(column).setCellEditor(new DefaultCellEditor(field));
	}

	/**
	 * Finds matching channels in original order, including channels excluded from printed forms.
	 *
	 * @param controller current incident controller.
	 * @param filter case-insensitive name, zone, number, function, assignment or frequency filter.
	 * @return matching channels.
	 */
	static List<RadioChannel> matches(AppController controller, String filter) {
		String query = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
		return controller.getData().getForm205().getChannels().stream()
				.filter(channel -> query.isEmpty() || (displayName(channel) + " " + channel.getZoneGroup() + " "
						+ channel.getChannelNumber() + " " + channel.getFunction() + " " + channel.getAssignment()
						+ " " + channel.getRxFrequency() + " " + channel.getTxFrequency())
						.toLowerCase(Locale.ROOT).contains(query))
				.toList();
	}

	/**
	 * Returns the fallback string stored by legacy fields; no frequency is inferred.
	 *
	 * @param channel chosen channel.
	 * @return channel label.
	 */
	static String displayName(RadioChannel channel) {
		if (!channel.getChannelName().isBlank()) {
			return channel.getChannelName();
		}
		if (!channel.getChannelNumber().isBlank()) {
			return channel.getChannelNumber();
		}
		return channel.getChannelId();
	}

	/**
	 * Opens a parent-scoped picker, with explicit transactional channel creation.
	 *
	 * @param parent owning editor.
	 * @param controller active incident controller.
	 * @return selected channel or null when cancelled.
	 */
	static RadioChannel choose(Component parent, AppController controller) {
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Choose radio channel",
				Dialog.ModalityType.DOCUMENT_MODAL);
		JTextField filter = new JTextField(28);
		filter.getAccessibleContext().setAccessibleName("Filter radio channels");
		DefaultListModel<RadioChannel> model = new DefaultListModel<>();
		JList<RadioChannel> list = new JList<>(model);
		list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer((component, value, index, selected, focus) -> {
			JLabel label = new JLabel(displayName(value) + " | " + value.getZoneGroup() + " | "
					+ value.getFunction() + " | RX " + value.getRxFrequency() + " | TX " + value.getTxFrequency());
			label.setOpaque(true);
			label.setBackground(selected ? component.getSelectionBackground() : component.getBackground());
			label.setForeground(selected ? component.getSelectionForeground() : component.getForeground());
			return label;
		});
		Runnable update = () -> {
			model.clear();
			matches(controller, filter.getText()).forEach(model::addElement);
			if (!model.isEmpty()) {
				list.setSelectedIndex(0);
			}
		};
		filter.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				update.run();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				update.run();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				update.run();
			}
		});
		update.run();
		RadioChannel[] result = {null};
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton select = new JButton("Use selected channel");
		select.addActionListener(event -> {
			if (list.getSelectedValue() != null) {
				result[0] = list.getSelectedValue();
				dialog.dispose();
			}
		});
		JButton create = new JButton("Create channel…");
		create.addActionListener(event -> {
			RadioChannel channel = new RadioChannel();
			channel.setChannelName(filter.getText());
			channel.setChannelId(UUID.randomUUID().toString());
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(channel);
			if (UiSupport.showResizableConfirmDialog(dialog, "Create radio channel", editor, new Dimension(780, 680))) {
				try {
					result[0] = editor.editedChannel();
					controller.getData().getForm205().getChannels().add(result[0]);
					controller.markDirty();
					dialog.dispose();
				} catch (IllegalArgumentException exception) {
					showError(dialog, exception.getMessage());
				}
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(event -> dialog.dispose());
		buttons.add(create);
		buttons.add(select);
		buttons.add(cancel);
		dialog.add(filter, BorderLayout.NORTH);
		dialog.add(new JScrollPane(list), BorderLayout.CENTER);
		dialog.add(buttons, BorderLayout.SOUTH);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setSize(780, 440);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		return result[0];
	}

	/**
	 * Displays errors with document modality instead of blocking unrelated incident windows.
	 *
	 * @param parent owning editor.
	 * @param message error description.
	 */
	static void showError(Component parent, String message) {
		UiSupport.showResizableOptionDialog(parent, "Communications plan", new JLabel(message),
				new Dimension(680, 160), new String[]{"Close"}, "Close");
	}
}
