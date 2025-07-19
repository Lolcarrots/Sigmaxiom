package com.sigmaxiom;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class TerminalPanel extends JPanel {
    private JTabbedPane terminalTabs;
    private JupyterNotebookIDE ide;
    private int terminalCounter = 1;
    private boolean isVisible = false;
    
    public TerminalPanel(JupyterNotebookIDE ide) {
        this.ide = ide;
        setLayout(new BorderLayout());
        setBackground(new Color(6, 6, 20));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 40, 100)));
        
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(10, 10, 25));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JLabel titleLabel = new JLabel("Terminal");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(new Color(10, 10, 25));
        
        
        JButton newTerminalButton = new JButton("+");
        newTerminalButton.setBackground(new Color(10, 10, 25));
        newTerminalButton.setForeground(Color.GREEN);
        newTerminalButton.setBorder(null);
        newTerminalButton.setFocusPainted(false);
        newTerminalButton.setPreferredSize(new Dimension(20, 20));
        newTerminalButton.setToolTipText("New Terminal");
        newTerminalButton.addActionListener(e -> addNewTerminal());
        
        
        JButton closeButton = new JButton("✕");
        closeButton.setBackground(new Color(10, 10, 25));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBorder(null);
        closeButton.setFocusPainted(false);
        closeButton.setPreferredSize(new Dimension(20, 20));
        closeButton.addActionListener(e -> hideTerminal());
        
        buttonPanel.add(newTerminalButton);
        buttonPanel.add(closeButton);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        
        
        terminalTabs = new JTabbedPane();
        terminalTabs.setBackground(new Color(13, 13, 35));
        terminalTabs.setForeground(Color.WHITE);
        terminalTabs.setTabPlacement(JTabbedPane.BOTTOM);
        
        
        add(headerPanel, BorderLayout.NORTH);
        add(terminalTabs, BorderLayout.CENTER);
        
        
        addNewTerminal();
        
        
        setVisible(false);
    }
    
    private void addNewTerminal() {
        String tabName = "Terminal " + terminalCounter++;
        SingleTerminalPanel terminal = new SingleTerminalPanel(ide);
        
        
        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setOpaque(false);
        
        JLabel tabLabel = new JLabel(tabName);
        tabLabel.setForeground(Color.WHITE);
        
        JButton closeTabButton = new JButton("×");
        closeTabButton.setPreferredSize(new Dimension(16, 16));
        closeTabButton.setMargin(new Insets(0, 0, 0, 0));
        closeTabButton.setContentAreaFilled(false);
        closeTabButton.setBorderPainted(false);
        closeTabButton.setForeground(Color.GRAY);
        closeTabButton.addActionListener(e -> closeTerminalTab(terminal));
        
        tabPanel.add(tabLabel, BorderLayout.CENTER);
        tabPanel.add(closeTabButton, BorderLayout.EAST);
        
        terminalTabs.addTab(tabName, terminal);
        int index = terminalTabs.getTabCount() - 1;
        terminalTabs.setTabComponentAt(index, tabPanel);
        terminalTabs.setSelectedIndex(index);
        
        
        SwingUtilities.invokeLater(() -> terminal.focusInput());
    }
    
    private void closeTerminalTab(SingleTerminalPanel terminal) {
        if (terminalTabs.getTabCount() > 1) {
            int index = terminalTabs.indexOfComponent(terminal);
            if (index >= 0) {
                terminal.cleanup();
                terminalTabs.remove(index);
            }
        } else {
            
            hideTerminal();
        }
    }
    
    public void showTerminal() {
        if (!isVisible) {
            setVisible(true);
            isVisible = true;
            
            
            SingleTerminalPanel current = getCurrentTerminal();
            if (current != null) {
                SwingUtilities.invokeLater(() -> current.focusInput());
            }
        }
    }
    
    public void hideTerminal() {
        if (isVisible) {
            setVisible(false);
            isVisible = false;
            
            
            NotebookPanel currentNotebook = ide.getCurrentNotebook();
            if (currentNotebook != null) {
                SwingUtilities.invokeLater(() -> {
                    currentNotebook.requestFocusInWindow();
                });
            }
        }
    }
    
    public void toggleTerminal() {
        if (isVisible) {
            hideTerminal();
        } else {
            showTerminal();
        }
    }
    
    private SingleTerminalPanel getCurrentTerminal() {
        Component selected = terminalTabs.getSelectedComponent();
        if (selected instanceof SingleTerminalPanel) {
            return (SingleTerminalPanel) selected;
        }
        return null;
    }
    
    public void cleanup() {
        
        for (int i = 0; i < terminalTabs.getTabCount(); i++) {
            Component comp = terminalTabs.getComponentAt(i);
            if (comp instanceof SingleTerminalPanel) {
                ((SingleTerminalPanel) comp).cleanup();
            }
        }
    }
}


class SingleTerminalPanel extends JPanel {
    private JTextPane terminalOutput;
    private JTextField commandInput;
    private Process bashProcess;
    private BufferedWriter processWriter;
    private BufferedReader processReader;
    private BufferedReader processErrorReader;
    private ExecutorService outputReaderExecutor;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private JupyterNotebookIDE ide;
    
    
    private boolean inREPL = false;
    
    
    private static final Color TERMINAL_BG = new Color(6, 6, 20);
    private static final Color TERMINAL_FG = new Color(30, 190, 255);
    private static final Color TERMINAL_CURSOR = new Color(30, 190, 255);
    private static final Color TERMINAL_SELECTION = new Color(50, 50, 100);
    
    public SingleTerminalPanel(JupyterNotebookIDE ide) {
        this.ide = ide;
        setLayout(new BorderLayout());
        setBackground(TERMINAL_BG);
        
        
        terminalOutput = new JTextPane();
        terminalOutput.setBackground(TERMINAL_BG);
        terminalOutput.setForeground(TERMINAL_FG);
        terminalOutput.setCaretColor(TERMINAL_CURSOR);
        terminalOutput.setSelectionColor(TERMINAL_SELECTION);
        terminalOutput.setFont(new Font("Monospaced", Font.PLAIN, 14));
        terminalOutput.setEditable(false);
        
        
        JScrollPane scrollPane = new JScrollPane(terminalOutput);
        scrollPane.setBackground(TERMINAL_BG);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        
        styleScrollBar(scrollPane.getVerticalScrollBar());
        styleScrollBar(scrollPane.getHorizontalScrollBar());
        
        
        commandInput = new JTextField();
        commandInput.setBackground(new Color(6, 6, 20));
        commandInput.setForeground(TERMINAL_FG);
        commandInput.setCaretColor(TERMINAL_CURSOR);
        commandInput.setSelectionColor(TERMINAL_SELECTION);
        commandInput.setFont(new Font("Monospaced", Font.PLAIN, 14));
        commandInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 40, 100)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(TERMINAL_BG);
        
        JLabel promptLabel = new JLabel("$ ");
        promptLabel.setForeground(new Color(50, 170, 200));
        promptLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        promptLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        
        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(commandInput, BorderLayout.CENTER);
        
        
        JButton syncDirButton = new JButton("⟲");
        syncDirButton.setToolTipText("Sync IDE directory with terminal");
        syncDirButton.setBackground(new Color(20, 20, 40));
        syncDirButton.setForeground(new Color(100, 200, 255));
        syncDirButton.setFocusPainted(false);
        syncDirButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        syncDirButton.addActionListener(e -> syncDirectory());
        
        inputPanel.add(syncDirButton, BorderLayout.EAST);
        
        
        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        
        
        commandInput.addActionListener(e -> executeCommand());
        
        
        commandInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        navigateHistoryUp();
                        e.consume();
                        break;
                    case KeyEvent.VK_DOWN:
                        navigateHistoryDown();
                        e.consume();
                        break;
                    case KeyEvent.VK_C:
                        if (e.isControlDown()) {
                            sendCtrlC();
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_D:
                        if (e.isControlDown()) {
                            sendCtrlD();
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_L:
                        if (e.isControlDown()) {
                            clearTerminal();
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        if (inREPL) {
                            
                            sendEscape();
                        } else {
                            ide.toggleTerminal();
                        }
                        e.consume();
                        break;
                }
            }
        });
        
        
        initializeBashProcess();
    }
    
    private void styleScrollBar(JScrollBar scrollBar) {
        if (scrollBar != null) {
            scrollBar.setBackground(new Color(13, 13, 35));
            scrollBar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    this.thumbColor = new Color(40, 40, 100);
                    this.trackColor = new Color(20, 20, 50);
                }
            });
        }
    }
    
    private void initializeBashProcess() {
        try {
            File workingDir = ide.getCurrentDirectory();
            
            
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/script", "-q", "-c", "/bin/bash -i", "/dev/null");
            pb.redirectErrorStream(false);
            pb.directory(workingDir);
            
            
            Map<String, String> env = pb.environment();
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("COLUMNS", "120");
            env.put("LINES", "30");
            
            
            String currentPath = System.getenv("PATH");
            if (currentPath != null) {
                env.put("PATH", currentPath);
            }
            
            bashProcess = pb.start();
            
            processWriter = new BufferedWriter(new OutputStreamWriter(bashProcess.getOutputStream()));
            processReader = new BufferedReader(new InputStreamReader(bashProcess.getInputStream()));
            processErrorReader = new BufferedReader(new InputStreamReader(bashProcess.getErrorStream()));
            
            outputReaderExecutor = Executors.newFixedThreadPool(2);
            outputReaderExecutor.submit(new OutputReader(processReader, false));
            outputReaderExecutor.submit(new OutputReader(processErrorReader, true));
            
            
            Thread.sleep(100);
            
            
            sendCommand("export PS1='\\[\\033[95m\\]\\u@\\h\\[\\033[00m\\]:\\[\\033[34m\\]\\w\\[\\033[00m\\]\\$ '");
            
            
            Thread.sleep(100);
            clearTerminal();
            
            appendToTerminal("Terminal initialized in: " + workingDir.getAbsolutePath() + "\n");
            appendToTerminal("Click ⟲ to sync IDE directory with terminal pwd\n\n");
            
        } catch (Exception e) {
            appendToTerminal("Failed to initialize terminal: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
    
    private void executeCommand() {
        String command = commandInput.getText();
        if (command.isEmpty() && !inREPL) return;
        
        
        if (!inREPL || !command.trim().isEmpty()) {
            commandHistory.add(command);
            historyIndex = commandHistory.size();
        }
        
        
        commandInput.setText("");
        
        
        if (!inREPL && (command.equals("python") || command.equals("python3") || 
                       command.equals("node") || command.equals("irb") || 
                       command.equals("scala") || command.equals("ghci"))) {
            inREPL = true;
        }
        
        
        if (!inREPL) {
            if (command.equals("clear")) {
                clearTerminal();
                return;
            } else if (command.equals("exit")) {
                ide.toggleTerminal();
                return;
            }
        }
        
        
        sendCommand(command);
    }
    
    private void sendCommand(String command) {
        try {
            processWriter.write(command + "\n");
            processWriter.flush();
        } catch (IOException e) {
            appendToTerminal("Error sending command: " + e.getMessage() + "\n");
        }
    }
    
    private void sendCtrlC() {
        try {
            
            processWriter.write("\u0003");
            processWriter.flush();
            
            
            if (inREPL) {
                inREPL = false;
                appendToTerminal("\n^C\n");
            }
        } catch (IOException e) {
            appendToTerminal("Error sending Ctrl+C: " + e.getMessage() + "\n");
        }
    }
    
    private void sendCtrlD() {
        try {
            
            processWriter.write("\u0004");
            processWriter.flush();
            
            
            if (inREPL) {
                inREPL = false;
            }
        } catch (IOException e) {
            appendToTerminal("Error sending Ctrl+D: " + e.getMessage() + "\n");
        }
    }
    
    private void sendEscape() {
        try {
            
            processWriter.write("\u001B");
            processWriter.flush();
        } catch (IOException e) {
            appendToTerminal("Error sending ESC: " + e.getMessage() + "\n");
        }
    }
    
    private void syncDirectory() {
        
        sendCommand("pwd");
        
        
        Timer timer = new Timer(200, e -> {
            
            String content = getTerminalContent();
            String[] lines = content.split("\n");
            
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (!line.isEmpty() && !line.startsWith("$") && !line.contains("pwd") && 
                    new File(line).exists() && new File(line).isDirectory()) {
                    
                    File newDir = new File(line);
                    ide.setCurrentDirectoryAndUpdateTree(newDir);
                    appendToTerminal("IDE directory synced to: " + line + "\n");
                    break;
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
    
    private String getTerminalContent() {
        try {
            Document doc = terminalOutput.getDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }
    
    private void clearTerminal() {
        try {
            terminalOutput.getDocument().remove(0, terminalOutput.getDocument().getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void navigateHistoryUp() {
        if (historyIndex > 0) {
            historyIndex--;
            commandInput.setText(commandHistory.get(historyIndex));
        }
    }
    
    private void navigateHistoryDown() {
        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            commandInput.setText(commandHistory.get(historyIndex));
        } else {
            historyIndex = commandHistory.size();
            commandInput.setText("");
        }
    }
    
    private void appendToTerminal(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = terminalOutput.getStyledDocument();
                
                
                processAnsiCodes(text, doc);
                
                
                terminalOutput.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void processAnsiCodes(String text, StyledDocument doc) throws BadLocationException {
        
        text = text.replaceAll("\u001B\\]0;[^\u0007]*\u0007", "");  
        text = text.replaceAll("\u001B\\[\\?[0-9]+[hl]", "");      
        text = text.replaceAll("\u001B\\[[0-9]*[GJKST]", "");      
        text = text.replaceAll("\u001B\\[K", "");                   
        text = text.replaceAll("\\r", "");                          
        
        
        String[] parts = text.split("\u001B\\[");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, TERMINAL_FG);
            StyleConstants.setBackground(attrs, TERMINAL_BG);
            StyleConstants.setFontFamily(attrs, "Monospaced");
            StyleConstants.setFontSize(attrs, 14);
            
            String content = part;
            
            
            if (part.matches("\\d+(;\\d+)*m.*")) {
                int mIndex = part.indexOf('m');
                String codes = part.substring(0, mIndex);
                content = part.substring(mIndex + 1);
                
                
                String[] codeArray = codes.split(";");
                for (String code : codeArray) {
                    switch (code) {
                        case "0": 
                            StyleConstants.setForeground(attrs, TERMINAL_FG);
                            StyleConstants.setBold(attrs, false);
                            break;
                        case "1": 
                            StyleConstants.setBold(attrs, true);
                            break;
                        case "30": case "90": 
                            StyleConstants.setForeground(attrs, Color.GRAY);
                            break;
                        case "31": case "91": 
                            StyleConstants.setForeground(attrs, new Color(255, 85, 85));
                            break;
                        case "32": case "92": 
                            StyleConstants.setForeground(attrs, new Color(85, 255, 85));
                            break;
                        case "33": case "93": 
                            StyleConstants.setForeground(attrs, new Color(255, 255, 85));
                            break;
                        case "34": case "94": 
                            StyleConstants.setForeground(attrs, new Color(85, 85, 255));
                            break;
                        case "35": case "95": 
                            StyleConstants.setForeground(attrs, new Color(255, 85, 255));
                            break;
                        case "36": case "96": 
                            StyleConstants.setForeground(attrs, new Color(85, 255, 255));
                            break;
                        case "37": case "97": 
                            StyleConstants.setForeground(attrs, Color.WHITE);
                            break;
                    }
                }
            }
            
            
            doc.insertString(doc.getLength(), content, attrs);
        }
    }
    
    public void focusInput() {
        commandInput.requestFocusInWindow();
    }
    
    public void cleanup() {
        if (outputReaderExecutor != null) {
            outputReaderExecutor.shutdownNow();
        }
        
        if (bashProcess != null) {
            bashProcess.destroyForcibly();
        }
    }
    
    private class OutputReader implements Runnable {
        private BufferedReader reader;
        private boolean isError;
        
        public OutputReader(BufferedReader reader, boolean isError) {
            this.reader = reader;
            this.isError = isError;
        }
        
        @Override
        public void run() {
            try {
                char[] buffer = new char[1024];
                int charsRead;
                
                while ((charsRead = reader.read(buffer)) != -1) {
                    String output = new String(buffer, 0, charsRead);
                    
                    if (!isError) {
                        
                        boolean isPythonRepl = output.contains(">>>") || output.contains("...");
                        boolean isOtherRepl = output.contains("irb(main)") || output.contains("scala>") || output.contains("node>");
                        boolean isFutharkRepl = output.matches(".*\\[\\d+]>.*") || output.contains("futhark>");

                        if (isPythonRepl || isOtherRepl || isFutharkRepl) {
                            inREPL = true;
                        }

                        
                        if (output.contains("$ ") && inREPL) {
                            inREPL = false;
                        }
                    }
                    
                    appendToTerminal(output);
                }
            } catch (IOException e) {
                if (!e.getMessage().contains("Stream closed")) {
                    appendToTerminal("Error reading output: " + e.getMessage() + "\n");
                }
            }
        }
    }
}
