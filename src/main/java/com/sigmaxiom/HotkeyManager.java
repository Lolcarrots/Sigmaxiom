package com.sigmaxiom;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.lang.reflect.Field;


public class HotkeyManager {
    
    private final Map<String, HotkeyConfig> hotkeyConfigs = new HashMap<>();
    
    private final JupyterNotebookIDE ide;
    
    private final File configFile;
    
    
    public HotkeyManager(JupyterNotebookIDE ide) {
        this.ide = ide;
        this.configFile = new File(System.getProperty("user.home"), ".sigmaxiom_hotkeys.properties");
        
        registerDefaultActions();
        loadHotkeyConfig();
        installHotkeys();
    }
    
    
    private void registerDefaultActions() {
        
        registerAction("RunCell", "Run Current Cell", this::runCurrentCell);
        
        
        registerAction("PreviousCell", "Go to Previous Cell", this::moveToPreviousCell);
        
        
        registerAction("NextCell", "Go to Next Cell", this::moveToNextCell);
        
        
        registerAction("RunAllCells", "Run All Cells", this::runAllCells);
        
        
        registerAction("AddCellBelow", "Add Cell Below", this::addCellBelow);
        
        
        registerAction("DeleteCell", "Delete Current Cell", this::deleteCurrentCell);
        
        registerAction("Search", "Find in Notebook", this::showSearch);
        
        registerAction("FindNext", "Find Next Match", this::findNext);
        
        registerAction("FindPrevious", "Find Previous Match", this::findPrevious);

        registerAction("ToggleTerminal", "Toggle Terminal", this::toggleTerminal);
    }
    
    
    private void registerAction(String actionId, String actionName, Runnable runnable) {
        hotkeyConfigs.put(actionId, new HotkeyConfig(actionId, actionName, null, runnable));
    }
    
    
    private void loadHotkeyConfig() {
        Properties props = new Properties();
        
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                
                
                for (String actionId : hotkeyConfigs.keySet()) {
                    String keyStrokeStr = props.getProperty(actionId);
                    if (keyStrokeStr != null && !keyStrokeStr.isEmpty()) {
                        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyStrokeStr);
                        if (keyStroke != null) {
                            HotkeyConfig config = hotkeyConfigs.get(actionId);
                            config.setKeyStroke(keyStroke);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading hotkey config: " + e.getMessage());
                setDefaultHotkeys(); 
            }
        } else {
            
            setDefaultHotkeys();
        }
    }
    
    
    private void setDefaultHotkeys() {
        
        HotkeyConfig runCellConfig = hotkeyConfigs.get("RunCell");
        if (runCellConfig != null) {
            runCellConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
        }
        
        
        HotkeyConfig prevCellConfig = hotkeyConfigs.get("PreviousCell");
        if (prevCellConfig != null) {
            prevCellConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK));
        }
        
        
        HotkeyConfig nextCellConfig = hotkeyConfigs.get("NextCell");
        if (nextCellConfig != null) {
            nextCellConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK));
        }
        
        
        HotkeyConfig runAllConfig = hotkeyConfigs.get("RunAllCells");
        if (runAllConfig != null) {
            runAllConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 
                                                            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        }
        
        
        HotkeyConfig addCellConfig = hotkeyConfigs.get("AddCellBelow");
        if (addCellConfig != null) {
            addCellConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        }
        
        
        HotkeyConfig deleteCellConfig = hotkeyConfigs.get("DeleteCell");
        if (deleteCellConfig != null) {
            deleteCellConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        }
        
        HotkeyConfig searchConfig = hotkeyConfigs.get("Search");
        if (searchConfig != null) {
            searchConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        }

        
        HotkeyConfig findNextConfig = hotkeyConfigs.get("FindNext");
        if (findNextConfig != null) {
            findNextConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        }

        
        HotkeyConfig findPrevConfig = hotkeyConfigs.get("FindPrevious");
        if (findPrevConfig != null) {
            findPrevConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
        }

        
        HotkeyConfig terminalConfig = hotkeyConfigs.get("ToggleTerminal");
        if (terminalConfig != null) {
            terminalConfig.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK));
        }
        
        
        saveHotkeyConfig();
    }
    
    
    public void saveHotkeyConfig() {
        Properties props = new Properties();
        
        
        for (HotkeyConfig config : hotkeyConfigs.values()) {
            KeyStroke keyStroke = config.getKeyStroke();
            if (keyStroke != null) {
                props.setProperty(config.getActionId(), keyStroke.toString());
            }
        }
        
        
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "Sigmaxiom Hotkey Configuration");
        } catch (IOException e) {
            System.err.println("Error saving hotkey config: " + e.getMessage());
        }
    }
    
    
    public void installHotkeys() {
        
        for (HotkeyConfig config : hotkeyConfigs.values()) {
            KeyStroke keyStroke = config.getKeyStroke();
            if (keyStroke != null) {
                
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(config.getDispatcher());
            }
        }
        
        
        for (HotkeyConfig config : hotkeyConfigs.values()) {
            KeyStroke keyStroke = config.getKeyStroke();
            if (keyStroke != null) {
                
                KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        
                        if (e.getID() != KeyEvent.KEY_PRESSED) {
                            return false;
                        }
                        
                        
                        KeyStroke pressedKey = KeyStroke.getKeyStrokeForEvent(e);
                        if (keyStroke.equals(pressedKey)) {
                            
                            config.getAction().actionPerformed(
                                new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, config.getActionId())
                            );
                            
                            e.consume();
                            return true;
                        }
                        
                        
                        return false;
                    }
                };
                
                
                config.setDispatcher(dispatcher);
                
                
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(dispatcher);
            }
        }
    }
    
    
    public void showHotkeyConfigDialog() {
        
        HotkeyTableModel tableModel = new HotkeyTableModel();
        
        
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        
        table.setBackground(new Color(16, 16, 45));
        table.setForeground(Color.WHITE);
        table.setGridColor(new Color(40, 40, 100));
        table.getTableHeader().setBackground(new Color(13, 13, 35));
        table.getTableHeader().setForeground(Color.WHITE);
        
        
        table.getColumnModel().getColumn(1).setCellRenderer(new KeyStrokeRenderer());
        
        
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        table.getColumnModel().getColumn(2).setCellRenderer(buttonRenderer);
        table.getColumnModel().getColumn(2).setCellEditor(new ButtonEditor(new JCheckBox(), this, tableModel));
        
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(new Color(13, 13, 35));
        scrollPane.getViewport().setBackground(new Color(13, 13, 35));
        scrollPane.setPreferredSize(new Dimension(500, 300));
        
        
        JDialog dialog = new JDialog(ide, "Configure Hotkeys", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.getContentPane().setBackground(new Color(13, 13, 35));
        
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(new Color(13, 13, 35));
        
        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.setBackground(new Color(0x171717));
        resetButton.setForeground(new Color(0xFF6B68)); 
        resetButton.addActionListener(e -> {
            setDefaultHotkeys();
            tableModel.fireTableDataChanged();
        });
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(new Color(0x171717));
        okButton.setForeground(new Color(0x4EC9B0)); 
        okButton.addActionListener(e -> {
            saveHotkeyConfig();
            installHotkeys();
            dialog.dispose();
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(new Color(0x171717));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(resetButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        
        dialog.pack();
        dialog.setLocationRelativeTo(ide);
        dialog.setVisible(true);
    }
    
    
    public KeyStroke captureKeyStroke(String actionId) {
        
        JDialog dialog = new JDialog(ide, "Press Hotkey", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(new Color(16, 16, 45));
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(16, 16, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel label = new JLabel("Press a key combination for '" + hotkeyConfigs.get(actionId).getActionName() + "'");
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setForeground(Color.WHITE);
        
        JTextField keyField = new JTextField("Press a key combination...");
        keyField.setEditable(false);
        keyField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        keyField.setHorizontalAlignment(JTextField.CENTER);
        keyField.setBackground(new Color(20, 20, 60));
        keyField.setForeground(Color.WHITE);
        keyField.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(new Color(0x171717));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        
        panel.add(label, BorderLayout.NORTH);
        panel.add(keyField, BorderLayout.CENTER);
        panel.add(cancelButton, BorderLayout.SOUTH);
        
        dialog.setContentPane(panel);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(ide);
        
        
        final KeyStroke[] result = new KeyStroke[1];
        
        
        
        KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                
                
                if (keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_CONTROL ||
                    keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_META) {
                    return;
                }
                
                
                if (keyCode == KeyEvent.VK_ESCAPE) {
                    dialog.dispose();
                    return;
                }
                
                
                int modifiers = e.getModifiersEx();
                KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
                
                
                keyField.setText(keyStrokeToString(keyStroke));
                
                
                result[0] = keyStroke;
                
                
                javax.swing.Timer timer = new javax.swing.Timer(500, evt -> dialog.dispose());
                timer.setRepeats(false);
                timer.start();
                
                e.consume();
            }
        };
        
        
        dialog.addKeyListener(keyAdapter);
        keyField.addKeyListener(keyAdapter);
        panel.addKeyListener(keyAdapter);
        
        
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                keyField.requestFocusInWindow();
            }
        });
        
        
        dialog.setVisible(true);
        
        return result[0];
    }
    
    
    public String getHotkeyHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available hotkeys:\n\n");
        
        for (HotkeyConfig config : hotkeyConfigs.values()) {
            KeyStroke keyStroke = config.getKeyStroke();
            if (keyStroke != null) {
                sb.append(config.getActionName())
                  .append(": ")
                  .append(keyStrokeToString(keyStroke))
                  .append("\n");
            }
        }
        
        return sb.toString();
    }
    
    
    private String keyStrokeToString(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return "Not Set";
        }
        
        String modifiers = KeyEvent.getModifiersExText(keyStroke.getModifiers());
        String keyText = KeyEvent.getKeyText(keyStroke.getKeyCode());
        
        if (!modifiers.isEmpty()) {
            return modifiers + "+" + keyText;
        } else {
            return keyText;
        }
    }
    
    
    
    private void runCurrentCell() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.runSelectedCell();
        }
    }
    
    private void moveToPreviousCell() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.navigateToPreviousCell();
        }
    }
    
    private void moveToNextCell() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.navigateToNextCell();
        }
    }
    
    private void runAllCells() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.runAllCellsSequentially();
        }
    }
    
    private void addCellBelow() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.addCellBelowCurrent();
        }
    }
    
    private void deleteCurrentCell() {
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.deleteSelectedCell();
        }
    }
    private void showSearch() {
        
        try {
            Field searchPanelField = JupyterNotebookIDE.class.getDeclaredField("searchPanel");
            searchPanelField.setAccessible(true);
            SearchPanel searchPanel = (SearchPanel) searchPanelField.get(ide);
            
            if (searchPanel != null) {
                searchPanel.showSearch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    private void findNext() {
        try {
            Field searchPanelField = JupyterNotebookIDE.class.getDeclaredField("searchPanel");
            searchPanelField.setAccessible(true);
            SearchPanel searchPanel = (SearchPanel) searchPanelField.get(ide);
            
            if (searchPanel != null && searchPanel.isVisible()) {
                Field nextButtonField = SearchPanel.class.getDeclaredField("nextButton");
                nextButtonField.setAccessible(true);
                JButton nextButton = (JButton) nextButtonField.get(searchPanel);
                nextButton.doClick();
            } else if (searchPanel != null) {
                searchPanel.showSearch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    private void findPrevious() {
        try {
            Field searchPanelField = JupyterNotebookIDE.class.getDeclaredField("searchPanel");
            searchPanelField.setAccessible(true);
            SearchPanel searchPanel = (SearchPanel) searchPanelField.get(ide);
            
            if (searchPanel != null && searchPanel.isVisible()) {
                Field prevButtonField = SearchPanel.class.getDeclaredField("prevButton");
                prevButtonField.setAccessible(true);
                JButton prevButton = (JButton) prevButtonField.get(searchPanel);
                prevButton.doClick();
            } else if (searchPanel != null) {
                searchPanel.showSearch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleTerminal() {
        ide.toggleTerminal();
    }
    
    
    private class HotkeyConfig {
        private final String actionId;
        private final String actionName;
        private KeyStroke keyStroke;
        private final Action action;
        private KeyEventDispatcher dispatcher;
        
        public HotkeyConfig(String actionId, String actionName, KeyStroke keyStroke, Runnable runnable) {
            this.actionId = actionId;
            this.actionName = actionName;
            this.keyStroke = keyStroke;
            
            
            this.action = new AbstractAction(actionName) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    runnable.run();
                }
            };
        }
        
        public String getActionId() {
            return actionId;
        }
        
        public String getActionName() {
            return actionName;
        }
        
        public KeyStroke getKeyStroke() {
            return keyStroke;
        }
        
        public void setKeyStroke(KeyStroke keyStroke) {
            this.keyStroke = keyStroke;
        }
        
        public Action getAction() {
            return action;
        }
        
        public KeyEventDispatcher getDispatcher() {
            return dispatcher;
        }
        
        public void setDispatcher(KeyEventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }
    }
    
    
    private class HotkeyTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Action", "Shortcut", ""};
        private final List<HotkeyConfig> configs;
        
        public HotkeyTableModel() {
            configs = new ArrayList<>(hotkeyConfigs.values());
        }
        
        @Override
        public int getRowCount() {
            return configs.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HotkeyConfig config = configs.get(rowIndex);
            
            switch (columnIndex) {
                case 0:
                    return config.getActionName();
                case 1:
                    return keyStrokeToString(config.getKeyStroke());
                case 2:
                    return "Set Hotkey";
                default:
                    return null;
            }
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2; 
        }
        
        public HotkeyConfig getConfigAt(int rowIndex) {
            return configs.get(rowIndex);
        }
    }
    
    
    private class KeyStrokeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                    boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
            
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            
            
            if (isSelected) {
                label.setBackground(new Color(40, 40, 100));
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(new Color(16, 16, 45));
                label.setForeground(Color.WHITE);
            }
            
            return label;
        }
    }
    
    
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setBorderPainted(false);
            setBackground(new Color(0x171717));
            setForeground(new Color(0x4080FF)); 
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                    boolean hasFocus, int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }
    
    
    private class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String label;
        private final HotkeyManager manager;
        private final HotkeyTableModel model;
        private int currentRow;
        
        public ButtonEditor(JCheckBox checkBox, HotkeyManager manager, HotkeyTableModel model) {
            super(checkBox);
            this.manager = manager;
            this.model = model;
            this.currentRow = -1;
            
            button = new JButton();
            button.setOpaque(true);
            button.setBackground(new Color(0x171717));
            button.setForeground(new Color(0x4080FF)); 
            button.setBorderPainted(false);
            button.addActionListener(e -> {
                fireEditingStopped();
                
                
                if (currentRow >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        
                        HotkeyConfig config = model.getConfigAt(currentRow);
                        String actionId = config.getActionId();
                        
                        
                        KeyStroke keyStroke = manager.captureKeyStroke(actionId);
                        if (keyStroke != null) {
                            
                            config.setKeyStroke(keyStroke);
                            model.fireTableDataChanged();
                            
                            
                            manager.installHotkeys();
                        }
                    });
                }
            });
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, 
                                                   boolean isSelected, int row, int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);
            currentRow = row;
            return button;
        }
        
        @Override
        public Object getCellEditorValue() {
            return label;
        }
    }
}
