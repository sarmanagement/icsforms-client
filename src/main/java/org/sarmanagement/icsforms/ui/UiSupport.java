package org.sarmanagement.icsforms.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.text.JTextComponent;

import org.sarmanagement.icsforms.model.SarTaskAssignment;

/**
 * Shared Swing layout helpers for compact form editing panels.
 */
final class UiSupport {
	static final Color REQUIRED_FIELD_BACKGROUND = new Color(255, 248, 225);
	private static final String FORM_SPACER_PROPERTY = "uiSupport.formSpacer";
	private static ZoneId dateTimeDisplayZone = ZoneId.systemDefault();

	private UiSupport() {
	}

	static ZoneId getDateTimeDisplayZone() {
		return dateTimeDisplayZone;
	}

	static void setDateTimeDisplayZone(ZoneId zoneId) {
		dateTimeDisplayZone = zoneId == null ? ZoneId.systemDefault() : zoneId;
	}

	static void applyDateTimeDisplayZone(Component root) {
		if (root == null) {
			return;
		}
		if (root instanceof JSpinner spinner) {
			if (spinner.getEditor() instanceof JSpinner.DateEditor editor) {
				editor.getFormat().setTimeZone(java.util.TimeZone.getTimeZone(dateTimeDisplayZone));
			}
			spinner.setToolTipText("Displayed in " + dateTimeDisplayZone + "; stored in UTC");
		}
		if (root instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				applyDateTimeDisplayZone(child);
			}
		}
	}

	/**
	 * Creates a standard form panel with grid bag layout.
	 *
	 * @return configured panel.
	 */
	static JPanel formPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		return panel;
	}

	/**
	 * Adds a labeled component to a form panel.
	 *
	 * @param panel
	 *            target panel.
	 * @param row
	 *            row index.
	 * @param label
	 *            label text.
	 * @param component
	 *            field component.
	 */
	static void addRow(JPanel panel, int row, String label, JComponent component) {
		addRow(panel, row, label, component, false);
	}

	/**
	 * Adds a labeled required component to a form panel.
	 *
	 * @param panel
	 *            target panel.
	 * @param row
	 *            row index.
	 * @param label
	 *            label text.
	 * @param component
	 *            field component.
	 */
	static void addRequiredRow(JPanel panel, int row, String label, JComponent component) {
		addRow(panel, row, label, component, true);
	}

	/**
	 * Adds a full-width component row to a form panel.
	 *
	 * @param panel
	 *            target panel.
	 * @param row
	 *            row index.
	 * @param component
	 *            row component.
	 */
	static void addWideRow(JPanel panel, int row, JComponent component) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.gridwidth = 2;
		constraints.weightx = 1.0;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(3, 3, 3, 3);
		panel.add(component, constraints);
		updateBottomSpacer(panel, row + 1);
	}

	private static void addRow(JPanel panel, int row, String label, JComponent component, boolean required) {
		JLabel fieldLabel = new JLabel(required ? label + " (required)" : label);
		fieldLabel.setLabelFor(labelTarget(component));
		if (required) {
			markRequired(component, label);
		}

		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = row;
		left.anchor = GridBagConstraints.NORTHWEST;
		left.insets = new Insets(3, 3, 3, 8);
		panel.add(fieldLabel, left);

		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = row;
		right.weightx = 1.0;
		right.anchor = GridBagConstraints.NORTHWEST;
		right.fill = GridBagConstraints.HORIZONTAL;
		right.insets = new Insets(3, 0, 3, 3);
		panel.add(component, right);
		updateBottomSpacer(panel, row + 1);
	}

	private static void updateBottomSpacer(JPanel panel, int row) {
		Object existing = panel.getClientProperty(FORM_SPACER_PROPERTY);
		if (existing instanceof JPanel spacer) {
			panel.remove(spacer);
		}
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0;
		filler.gridy = row;
		filler.gridwidth = 2;
		filler.weightx = 1.0;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.BOTH;
		panel.add(spacer, filler);
		panel.putClientProperty(FORM_SPACER_PROPERTY, spacer);
	}

	private static JComponent labelTarget(JComponent component) {
		if (component instanceof JScrollPane scrollPane
				&& scrollPane.getViewport().getView() instanceof JComponent child) {
			return child;
		}
		return component;
	}

	private static void markRequired(JComponent component, String label) {
		applyRequiredBackground(component);
		JComponent accessibleTarget = labelTarget(component);
		String existingDescription = accessibleTarget.getAccessibleContext().getAccessibleDescription();
		String requiredDescription = label + " is a required field.";
		accessibleTarget.getAccessibleContext()
				.setAccessibleDescription(existingDescription == null || existingDescription.isBlank()
						? requiredDescription
						: existingDescription + " " + requiredDescription);
	}

	private static void applyRequiredBackground(JComponent component) {
		if (component instanceof JScrollPane scrollPane) {
			JViewport viewport = scrollPane.getViewport();
			viewport.setOpaque(true);
			viewport.setBackground(REQUIRED_FIELD_BACKGROUND);
			if (viewport.getView() instanceof JComponent child) {
				child.setOpaque(true);
				child.setBackground(REQUIRED_FIELD_BACKGROUND);
			}
			return;
		}
		component.setOpaque(true);
		component.setBackground(REQUIRED_FIELD_BACKGROUND);
	}

	/**
	 * Creates a multiline text area with line wrapping.
	 *
	 * @param rows
	 *            preferred row count.
	 * @return configured text area.
	 */
	static JTextArea textArea(int rows) {
		JTextArea area = new JTextArea(rows, 40);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setTabSize(4);
		return area;
	}

	/**
	 * Creates a standard text field.
	 *
	 * @return configured text field.
	 */
	static JTextField textField() {
		return new JTextField(24);
	}

	/**
	 * Creates a standard date/time spinner.
	 *
	 * @return configured date/time spinner.
	 */
	static JSpinner dateTimeSpinner() {
		JSpinner spinner = new JSpinner(new SpinnerDateModel());
		spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm"));
		if (spinner.getEditor() instanceof JSpinner.DateEditor editor) {
			editor.getFormat().setTimeZone(java.util.TimeZone.getTimeZone(dateTimeDisplayZone));
		}
		spinner.setToolTipText("Displayed in " + dateTimeDisplayZone + "; stored in UTC");
		spinner.setValue(new Date());
		spinner.setPreferredSize(new Dimension(180, spinner.getPreferredSize().height));
		return spinner;
	}

	/**
	 * Scrolls a table so its last row is visible.
	 *
	 * @param table
	 *            table to scroll.
	 */
	static void scrollTableToLastRow(JTable table) {
		if (table == null || table.getRowCount() <= 0) {
			return;
		}
		int lastRow = table.getRowCount() - 1;
		javax.swing.SwingUtilities.invokeLater(() -> table.scrollRectToVisible(table.getCellRect(lastRow, 0, true)));
	}

	/**
	 * Configures a combo box for use inside compact dialogs.
	 *
	 * <p>
	 * The combo is widened to a sensible minimum and left-clicking the field or its
	 * editor opens the popup so editable combos behave consistently with other
	 * pickers in the application.
	 * </p>
	 *
	 * @param comboBox
	 *            combo box to configure.
	 * @param minimumWidth
	 *            preferred minimum width in pixels.
	 */
	static void configureDialogComboBox(JComboBox<?> comboBox, int minimumWidth) {
		if (comboBox == null) {
			return;
		}
		comboBox.setMaximumRowCount(16);
		Dimension preferredSize = comboBox.getPreferredSize();
		comboBox.setPreferredSize(new Dimension(Math.max(preferredSize.width, minimumWidth), preferredSize.height));
		installPopupOpenOnClick(comboBox, comboBox);
		installPopupOpenOnKeys(comboBox, comboBox);
		if (comboBox.getEditor() != null && comboBox.getEditor().getEditorComponent() instanceof JTextComponent editor) {
			installPopupOpenOnClick(comboBox, editor);
			installPopupOpenOnKeys(comboBox, editor);
		}
	}

	/**
	 * Opens a filterable single-selection dialog for a list of string values.
	 *
	 * @param parent
	 *            parent component.
	 * @param title
	 *            dialog title.
	 * @param fieldLabel
	 *            filter field label.
	 * @param options
	 *            available selectable values.
	 * @param selectedValue
	 *            value to pre-select when present.
	 * @return selected value, or {@code null} when cancelled.
	 */
	static String showFilterableSelectionDialog(Component parent, String title, String fieldLabel, List<String> options,
			String selectedValue) {
		JTextField anchorField = new JTextField();
		anchorField.setText(selectedValue == null ? "" : selectedValue);
		final String[] selected = new String[1];
		openSelectionPickerDialog(anchorField, title, fieldLabel, options, selectedValue, choice -> selected[0] = choice);
		return selected[0];
	}

	/**
	 * Shows a resizable OK/cancel dialog for richer editors.
	 *
	 * @param parent
	 *            parent component.
	 * @param title
	 *            dialog title.
	 * @param component
	 *            dialog content.
	 * @param preferredSize
	 *            preferred minimum content size.
	 * @return {@code true} when OK was selected.
	 */
	static boolean showResizableConfirmDialog(Component parent, String title, JComponent component,
			Dimension preferredSize) {
		if (preferredSize != null) {
			component.setPreferredSize(preferredSize);
		}
		JOptionPane optionPane = new JOptionPane(component, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		JDialog dialog = optionPane.createDialog(parent, title);
		dialog.setResizable(true);
		dialog.pack();
		if (preferredSize != null) {
			dialog.setSize(new Dimension(Math.max(dialog.getWidth(), preferredSize.width),
					Math.max(dialog.getHeight(), preferredSize.height)));
		}
		dialog.setVisible(true);
		Object value = optionPane.getValue();
		dialog.dispose();
		return Integer.valueOf(JOptionPane.OK_OPTION).equals(value);
	}

	/**
	 * Shows a resizable option dialog with a custom shared button row.
	 *
	 * @return selected option index, or -1 when dismissed.
	 */
	static int showResizableOptionDialog(Component parent, String title, JComponent component, Dimension preferredSize,
			Object[] options, Object initialValue) {
		if (preferredSize != null) {
			component.setPreferredSize(preferredSize);
		}
		JOptionPane optionPane = new JOptionPane(component, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		optionPane.setOptions(options);
		optionPane.setInitialValue(initialValue);
		JDialog dialog = optionPane.createDialog(parent, title);
		dialog.setResizable(true);
		dialog.pack();
		if (preferredSize != null) {
			dialog.setSize(new Dimension(Math.max(dialog.getWidth(), preferredSize.width),
					Math.max(dialog.getHeight(), preferredSize.height)));
		}
		dialog.setVisible(true);
		Object value = optionPane.getValue();
		dialog.dispose();
		if (value == null || options == null) {
			return -1;
		}
		for (int i = 0; i < options.length; i++) {
			if (value.equals(options[i])) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Installs a name picker on a text field.
	 *
	 * <p>
	 * The field remains fully editable for free-form input. Clicking the field when
	 * the suggestion list is non-empty opens a modal pick-list dialog: a filter
	 * text box narrows the list in real time, and double-clicking (or pressing
	 * Enter on) an entry fills the field and fires the optional {@code onSelected}
	 * callback to auto-fill adjacent fields. This avoids focus-stealing and flicker
	 * problems associated with pop-up menu approaches.
	 * </p>
	 *
	 * @param nameField
	 *            target text field.
	 * @param suggestions
	 *            lazy supplier of the current suggestion list (queried at
	 *            dialog-open time).
	 * @param onSelected
	 *            optional callback fired when a suggestion is chosen; receives the
	 *            selected name string. May be {@code null}.
	 */
	static void installNameAutocomplete(JTextField nameField, Supplier<List<String>> suggestions,
			Consumer<String> onSelected) {
		installNameAutocomplete(nameField, suggestions, onSelected, null);
	}

	static void installNameAutocomplete(JTextField nameField, Supplier<List<String>> suggestions,
			Consumer<String> onSelected, java.util.function.Function<String, String> onCreate) {
		java.util.function.Consumer<String> openPicker = initialFilter -> openNamePicker(nameField, suggestions,
				onSelected, onCreate, initialFilter);
		nameField.putClientProperty("uiSupport.namePicker.open", openPicker);
		nameField.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() != MouseEvent.BUTTON1)
					return;
				openPicker.accept(nameField.getText().trim());
			}
		});
		nameField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				char ch = e.getKeyChar();
				if (Character.isISOControl(ch) || e.isAltDown() || e.isControlDown() || e.isMetaDown()) {
					return;
				}
				openPicker.accept(String.valueOf(ch));
				e.consume();
			}
		});
	}

	@SuppressWarnings("unchecked")
	static void openInstalledNamePicker(JTextField nameField) {
		Object opener = nameField.getClientProperty("uiSupport.namePicker.open");
		if (opener instanceof java.util.function.Consumer<?> consumer) {
			((java.util.function.Consumer<String>) consumer).accept(nameField.getText().trim());
		}
	}

	static void openNamePicker(JTextField nameField, Supplier<List<String>> suggestions, Consumer<String> onSelected,
			java.util.function.Function<String, String> onCreate, String initialFilter) {
		if (Boolean.TRUE.equals(nameField.getClientProperty("uiSupport.namePicker.opening"))) {
			return;
		}
		nameField.putClientProperty("uiSupport.namePicker.opening", Boolean.TRUE);
		try {
			List<String> all = suggestions.get();
			openPickerDialog(nameField, all, onSelected, onCreate, initialFilter);
		} finally {
			nameField.putClientProperty("uiSupport.namePicker.opening", Boolean.FALSE);
		}
	}

	/**
	 * Opens the modal name-picker dialog anchored to {@code anchor}.
	 *
	 * <p>
	 * The dialog contains a filter field and a scrollable list. Typing in the
	 * filter field narrows the list to prefix-matching entries. Double-clicking an
	 * item (or pressing Enter when one is selected) fills {@code nameField} and
	 * fires {@code onSelected}.
	 * </p>
	 */
	private static void openPickerDialog(JTextField nameField, List<String> allNames, Consumer<String> onSelected,
			java.util.function.Function<String, String> onCreate, String initialFilter) {
		Window owner = nameField.isShowing() ? (Window) javax.swing.SwingUtilities.getWindowAncestor(nameField) : null;
		JDialog dialog = new JDialog(owner, "Select person", java.awt.Dialog.ModalityType.APPLICATION_MODAL);

		// Filter field
		JTextField filterField = new JTextField(initialFilter == null ? "" : initialFilter.trim(), 20);

		// List model + list
		DefaultListModel<String> listModel = new DefaultListModel<>();
		JList<String> list = new JList<>(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(10);

		// Populate list according to current filter text
		JButton createButton = new JButton("Create");
		createButton.setVisible(onCreate != null);

		Runnable applyFilter = () -> {
			String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
			listModel.clear();
			allNames.stream().filter(s -> filter.isEmpty() || s.trim().toLowerCase(Locale.ROOT).contains(filter))
					.forEach(listModel::addElement);
			if (!listModel.isEmpty()) {
				list.setSelectedIndex(0);
			}
			createButton.setEnabled(onCreate != null && listModel.isEmpty() && !filterField.getText().trim().isBlank());
		};
		applyFilter.run();

		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter.run();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter.run();
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
			}
		});

		// Accept selection and close
		Runnable accept = () -> {
			String selected = list.getSelectedValue();
			if (selected != null) {
				nameField.setText(selected);
				dialog.dispose();
				if (onSelected != null) {
					onSelected.accept(selected);
				}
			}
		};

		// Double-click on list item
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 2)
					accept.run();
			}
		});

		// Enter key in filter field or list triggers acceptance
		filterField.addActionListener(ev -> accept.run());
		list.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
					accept.run();
			}
		});

		// Buttons
		JButton okButton = new JButton("Select");
		okButton.addActionListener(ev -> accept.run());
		createButton.addActionListener(ev -> {
			if (onCreate == null) {
				return;
			}
			String createdName = onCreate.apply(filterField.getText().trim());
			if (createdName != null && !createdName.isBlank()) {
				nameField.setText(createdName.trim());
				dialog.dispose();
				if (onSelected != null) {
					onSelected.accept(createdName.trim());
				}
			}
		});
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(ev -> dialog.dispose());

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(okButton);
		buttonPanel.add(createButton);
		buttonPanel.add(cancelButton);

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
		top.add(new JLabel("Name / filter (or new name):"), BorderLayout.WEST);
		top.add(filterField, BorderLayout.CENTER);

		JPanel center = new JPanel(new BorderLayout());
		center.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		center.add(new JScrollPane(list), BorderLayout.CENTER);

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(top, BorderLayout.NORTH);
		dialog.getContentPane().add(center, BorderLayout.CENTER);
		dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setMinimumSize(new Dimension(260, 280));
		dialog.setLocationRelativeTo(nameField);
		filterField.requestFocusInWindow();
		dialog.setVisible(true);
	}

	/**
	 * Opens a generic filterable selection dialog.
	 *
	 * @param anchor
	 *            anchor field used for dialog placement.
	 * @param title
	 *            dialog title.
	 * @param fieldLabel
	 *            filter field label.
	 * @param allOptions
	 *            available options.
	 * @param selectedValue
	 *            option to pre-select when present.
	 * @param onSelected
	 *            callback receiving the chosen option.
	 */
	private static void openSelectionPickerDialog(JTextField anchor, String title, String fieldLabel, List<String> allOptions,
			String selectedValue, Consumer<String> onSelected) {
		Window owner = anchor.isShowing() ? (Window) javax.swing.SwingUtilities.getWindowAncestor(anchor) : null;
		JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		JTextField filterField = new JTextField(selectedValue == null ? "" : selectedValue.trim(), 24);
		DefaultListModel<String> listModel = new DefaultListModel<>();
		JList<String> list = new JList<>(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(12);

		Runnable applyFilter = () -> {
			String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
			listModel.clear();
			allOptions.stream().filter(option -> filter.isEmpty() || option.toLowerCase(Locale.ROOT).contains(filter))
					.forEach(listModel::addElement);
			if (!listModel.isEmpty()) {
				int selectedIndex = 0;
				if (selectedValue != null && !selectedValue.isBlank()) {
					for (int i = 0; i < listModel.size(); i++) {
						if (selectedValue.equals(listModel.get(i))) {
							selectedIndex = i;
							break;
						}
					}
				}
				list.setSelectedIndex(selectedIndex);
				list.ensureIndexIsVisible(selectedIndex);
			}
		};
		applyFilter.run();
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				applyFilter.run();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				applyFilter.run();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
			}
		});

		Runnable accept = () -> {
			String choice = list.getSelectedValue();
			if (choice != null) {
				dialog.dispose();
				onSelected.accept(choice);
			}
		};
		filterField.addActionListener(event -> accept.run());
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() >= 2) {
					accept.run();
				}
			}
		});
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER) {
					accept.run();
				}
			}
		});

		JButton selectButton = new JButton("Select");
		selectButton.addActionListener(event -> accept.run());
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> dialog.dispose());

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
		top.add(new JLabel(fieldLabel), BorderLayout.WEST);
		top.add(filterField, BorderLayout.CENTER);

		JPanel center = new JPanel(new BorderLayout());
		center.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		center.add(new JScrollPane(list), BorderLayout.CENTER);

		JPanel buttons = new JPanel();
		buttons.add(selectButton);
		buttons.add(cancelButton);

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(top, BorderLayout.NORTH);
		dialog.getContentPane().add(center, BorderLayout.CENTER);
		dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setMinimumSize(new Dimension(320, 320));
		dialog.setLocationRelativeTo(parentFor(anchor));
		filterField.requestFocusInWindow();
		dialog.setVisible(true);
	}

	/**
	 * Returns the best parent component for positioning child dialogs.
	 *
	 * @param component
	 *            anchor component.
	 * @return parent window or the component itself.
	 */
	private static Component parentFor(Component component) {
		Window owner = component == null ? null : javax.swing.SwingUtilities.getWindowAncestor(component);
		return owner == null ? component : owner;
	}

	/**
	 * Installs left-click popup-open behavior on a combo box or its editor.
	 *
	 * @param comboBox
	 *            combo box to open.
	 * @param target
	 *            component receiving the click.
	 */
	private static void installPopupOpenOnClick(JComboBox<?> comboBox, Component target) {
		if (target == null) {
			return;
		}
		target.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				if (event.getButton() == MouseEvent.BUTTON1 && comboBox.isEnabled() && !comboBox.isPopupVisible()) {
					javax.swing.SwingUtilities.invokeLater(comboBox::showPopup);
				}
			}
		});
	}

	/**
	 * Installs keyboard shortcuts that open a combo box popup from the focused
	 * field or editor.
	 *
	 * @param comboBox
	 *            combo box to open.
	 * @param target
	 *            focused component that should respond to the shortcut.
	 */
	private static void installPopupOpenOnKeys(JComboBox<?> comboBox, Component target) {
		if (!(target instanceof JComponent component)) {
			return;
		}
		component.registerKeyboardAction(event -> showPopup(comboBox),
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK),
				JComponent.WHEN_FOCUSED);
		component.registerKeyboardAction(event -> showPopup(comboBox), javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0),
				JComponent.WHEN_FOCUSED);
	}

	/**
	 * Opens a combo box popup when the control is enabled.
	 *
	 * @param comboBox
	 *            combo box to open.
	 */
	private static void showPopup(JComboBox<?> comboBox) {
		if (comboBox != null && comboBox.isEnabled()) {
			javax.swing.SwingUtilities.invokeLater(comboBox::showPopup);
		}
	}

	/**
	 * Returns a human-readable label for a SAR task assignment, combining the team
	 * number and assignment name when both are available. Used in picklists and
	 * dialogs.
	 *
	 * @param task
	 *            the assignment to label.
	 * @return a non-null label string (may be empty if neither field is set).
	 */
	static String taskLabel(SarTaskAssignment task) {
		if (task == null) {
			return "";
		}
		String number = task.getAssignmentTeamNumber() != null ? task.getAssignmentTeamNumber().trim() : "";
		String resource = task.getResourceIdentifier() != null ? task.getResourceIdentifier().trim() : "";
		String leader = task.getLeader() != null ? task.getLeader().trim() : "";
		String assignmentPreview = firstWords(task.getAssignment(), 6);
		StringBuilder label = new StringBuilder();
		if (!number.isBlank()) {
			label.append(number);
		}
		if (!resource.isBlank()) {
			if (!label.isEmpty())
				label.append(" · ");
			label.append(resource);
		}
		if (!leader.isBlank()) {
			if (!label.isEmpty())
				label.append(" · ");
			label.append("Lead: ").append(leader);
		}
		if (!assignmentPreview.isBlank()) {
			if (!label.isEmpty())
				label.append(" · ");
			label.append(assignmentPreview);
		}
		return label.toString();
	}

	/**
	 * Returns a detecting-task label combining the task team number and the
	 * resource identifier (ICS 214 form name) of the resource reporting the clue.
	 * Format: {@code "<teamNumber> – <resourceName>"}.
	 *
	 * <p>
	 * Used when recording and displaying the detecting task in the clue log so the
	 * entry shows both which task and which specific resource observed the clue.
	 * </p>
	 *
	 * @param task
	 *            the SAR task assignment (provides the team number).
	 * @param resourceName
	 *            the ICS 214 form name / resource identifier for the detecting
	 *            resource.
	 * @return a combined label; falls back to just the resource name if the team
	 *         number is blank.
	 */
	static String detectingTaskLabel(SarTaskAssignment task, String resourceName) {
		String number = task != null && task.getAssignmentTeamNumber() != null
				? task.getAssignmentTeamNumber().trim()
				: "";
		String name = resourceName != null ? resourceName.trim() : "";
		String base = taskLabel(task);
		if (!base.isBlank() && !name.isBlank()) {
			return base + " · " + name;
		}
		if (!number.isBlank() && !name.isBlank()) {
			return number + " · " + name;
		}
		return !number.isBlank() ? number : name;
	}

	private static String firstWords(String value, int maxWords) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String[] words = value.trim().split("\\s+");
		int limit = Math.min(maxWords, words.length);
		String joined = String.join(" ", java.util.Arrays.copyOf(words, limit));
		return words.length > limit ? joined + "…" : joined;
	}
}
