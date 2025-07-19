package com.sigmaxiom;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

import javax.swing.filechooser.FileFilter;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.plaf.basic.BasicComboBoxUI;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;
import org.json.JSONArray;
import org.json.JSONObject;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.border.*;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.scilab.forge.jlatexmath.TeXConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Queue;

import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.DecimalFormat;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.tree.ExpandVetoException;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;



public class JupyterNotebookIDE extends JFrame implements KernelStatusListener {
    private JTabbedPane tabbedPane;
    private Map<String, NotebookPanel> openNotebooks;
    private Map<String, JupyterKernelClient> kernelClients;
    private Map<NotebookPanel, String> notebookToKernelMap;
    private int kernelCounter = 0;
    private static final String SHARED_KERNEL_ID = "shared_kernel";
    private JLabel statusLabel;
    private JLabel kernelStatus;
    private boolean usingDirectMethod = false;
    private HotkeyManager hotkeyManager;
    private SearchPanel searchPanel;
    private TerminalPanel terminalPanel;
    private JSplitPane mainSplitPane;
    private int lastTerminalHeight = 300;
    private String selectedEnvPath;
    private String futharkTranspilerPath;
    private JPanel currentSideBar;

    public String getSelectedEnvPath() {
        return this.selectedEnvPath;
    }

    public String getFutharkTranspilerPath() {
        return this.futharkTranspilerPath;
    }

    private String locateFutharkTranspiler() {
        String scriptName = "l2f_cli.py";
        
        
        File scriptFile = new File(System.getProperty("user.dir"), scriptName);
        if (scriptFile.exists()) {
            System.out.println("Found transpiler script at: " + scriptFile.getAbsolutePath());
            return scriptFile.getAbsolutePath();
        }

        
        JOptionPane.showMessageDialog(this,
            "Could not automatically find the Futhark transpiler script (l2f_cli.py).\nPlease locate it.",
            "Transpiler Not Found",
            JOptionPane.INFORMATION_MESSAGE);

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Locate " + scriptName);
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile.getName().equals(scriptName)) {
                return selectedFile.getAbsolutePath();
            }
        }

        
        JOptionPane.showMessageDialog(this,
            "Transpiler not selected. LaTeX to Futhark feature will be disabled.",
            "Warning",
            JOptionPane.WARNING_MESSAGE);
        return null; 
    }

    public JupyterKernelClient getCurrentKernelClient() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            String kernelId = notebookToKernelMap.get(currentNotebook);
            if (kernelId != null) {
                return kernelClients.get(kernelId);
            }
        }
        
        return kernelClients.get(SHARED_KERNEL_ID);
    }

    private void updateStatusForCurrentTab() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            String kernelId = notebookToKernelMap.get(currentNotebook);
            if (kernelId != null) {
                
                
                
                kernelStatus.setText("Kernel: " + kernelId + " (Idle)");
                kernelStatus.setForeground(new Color(0, 255, 0)); 
            }
        } else {
            kernelStatus.setText("Kernel: None");
            kernelStatus.setForeground(new Color(180, 180, 220)); 
        }
    }

    @Override
    public void onStatusChange(String status) {
        
        SwingUtilities.invokeLater(() -> {
            
            String kernelId = findKernelIdForStatus(status);
            
            
            NotebookPanel currentNotebook = getCurrentNotebook();
            if (currentNotebook != null && kernelId.equals(notebookToKernelMap.get(currentNotebook))) {
                String statusText = "Kernel: " + kernelId + " (" + capitalize(status) + ")";
                kernelStatus.setText(statusText);
                
                
                if ("busy".equalsIgnoreCase(status) || "starting".equalsIgnoreCase(status)) {
                    kernelStatus.setForeground(new Color(255, 165, 0)); 
                } else if ("idle".equalsIgnoreCase(status)) {
                    kernelStatus.setForeground(new Color(0, 255, 0)); 
                } else {
                    kernelStatus.setForeground(Color.RED); 
                }
            }
        });
    }

    private String findKernelIdForStatus(String status) {
        
        
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            return notebookToKernelMap.get(currentNotebook);
        }
        return "Unknown";
    }

    private void exportCurrentNotebookWithWrappers() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.exportWithWrappers();
        } else {
            JOptionPane.showMessageDialog(this, 
                "No notebook is currently open.", 
                "Export Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public JupyterNotebookIDE() {
        
        try {
            
            FlatDarkLaf.setup();
            
            UIManager.put("RSyntaxTextAreaUI.errorForeground", Color.RED);
            UIManager.put("RSyntaxTextArea.markOccurrencesBorder", BorderFactory.createLineBorder(Color.RED));
            UIManager.put("RSyntaxTextArea.markOccurrencesColor", new Color(255, 50, 50, 60));

            
            UIManager.put("@background", "#1a1a2e");  
            UIManager.put("@foreground", "#e0e0e0");    
            UIManager.put("Panel.background", "#1a1a2e");
            UIManager.put("TextField.background", "#101035");  
            UIManager.put("TextArea.background", "#101035");
    
            
            UIManager.put("TabbedPane.underlineColor", new Color(80, 80, 180));
            UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.background", new Color(13, 13, 35));
            UIManager.put("TabbedPane.tabAreaBackground", new Color(13, 13, 35));
            UIManager.put("TabbedPane.selectedBackground", new Color(25, 25, 60));
            UIManager.put("TabbedPane.hoverBackground", new Color(30, 30, 70));
            UIManager.put("TabbedPane.contentAreaBackground", new Color(13, 13, 35));
            UIManager.put("TabbedPane.foreground", new Color(220, 220, 240));
            UIManager.put("TabbedPane.selectedForeground", new Color(255, 255, 255));
    
            
            UIManager.put("MenuBar.background", new Color(10, 10, 30));
            UIManager.put("MenuBar.foreground", new Color(230, 230, 250));
            UIManager.put("Menu.background", new Color(10, 10, 30));
            UIManager.put("Menu.foreground", new Color(230, 230, 250));
            UIManager.put("MenuItem.background", new Color(10, 10, 30));
            UIManager.put("MenuItem.foreground", new Color(230, 230, 250));
            UIManager.put("MenuItem.selectionBackground", new Color(40, 40, 80));
            UIManager.put("MenuItem.selectionForeground", Color.WHITE);
            UIManager.put("MenuItem.acceleratorForeground", new Color(180, 180, 200));

            UIManager.put("PopupMenu.background", new Color(10, 10, 30));

            UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
    
            
            UIManager.put("InternalFrame.activeTitleBackground", new Color(10, 10, 30));
            UIManager.put("InternalFrame.activeTitleForeground", Color.WHITE);
            UIManager.put("InternalFrame.titleBackground", new Color(10, 10, 30));
            UIManager.put("InternalFrame.titleForeground", Color.WHITE);
    
            
            UIManager.put("ScrollPane.background", new Color(13, 13, 35));
            UIManager.put("ScrollBar.thumbColor", new Color(13, 13, 35));
            UIManager.put("ScrollBar.trackColor", new Color(16, 16, 45));

            UIManager.put("ComboBox.buttonArrowColor", new Color(0x4080FF));
            UIManager.put("ComboBox.buttonBackground", new Color(0x171717));
            updateAllComponentsToBlueTheme();
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF: " + ex.getMessage());
        }
    
        
        setTitle("Sigmaxiom");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        
        statusLabel = new JLabel("Initializing...");
        kernelStatus = new JLabel("Kernel: None");
        kernelClients = new ConcurrentHashMap<>();
        notebookToKernelMap = new HashMap<>();

        
        KernelSelectionDialog kernelDialog = new KernelSelectionDialog(null); 
        kernelDialog.setVisible(true);
        
        
        if (kernelDialog.wasDialogCancelled()) {
            
            System.exit(0);
            return;
        }
        
        
        String selectedKernel = kernelDialog.getSelectedKernelName();
        boolean useDirectMethod = kernelDialog.useDirectMethod();
        this.selectedEnvPath = kernelDialog.getSelectedEnvPath();
        this.futharkTranspilerPath = locateFutharkTranspiler();

        try {
            JupyterKernelClient sharedKernel;
            
            if (selectedKernel.equals("Python (Direct)") && useDirectMethod) {
                
                System.out.println("Starting Python kernel with direct method using environment: " + selectedEnvPath);
                sharedKernel = JupyterKernelClient.startPythonKernelDirectly(selectedEnvPath);
                usingDirectMethod = true;
            } else {
                
                System.out.println("Starting " + selectedKernel + " kernel with standard method");
                
                
                
                String kernelSpecName = findKernelSpecName(selectedKernel); 
                if (kernelSpecName == null) {
                    throw new Exception("Kernel spec not found for: " + selectedKernel);
                }
                
                
                sharedKernel = JupyterKernelClient.startKernel(kernelSpecName, this.selectedEnvPath);
                usingDirectMethod = false;
            }
            
            sharedKernel.setStatusListener(this);
            kernelClients.put(SHARED_KERNEL_ID, sharedKernel);
            
            
            sharedKernel.executeCode("1+1").thenAccept(result -> {
                System.out.println("Shared kernel warmed up: " + result);
            });
            
            statusLabel.setText("Connected to " + selectedKernel + " kernel");
            kernelStatus.setText("Kernel: Idle");
            
        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = "Failed to start " + selectedKernel + " kernel: " + e.getMessage();
            statusLabel.setText("Failed to connect to kernel");
            kernelStatus.setText("Kernel: None");
            
            JOptionPane.showMessageDialog(this, errorMsg, "Kernel Error", JOptionPane.ERROR_MESSAGE);
            
            
            System.exit(1);
            return;
        }
    
        
        openNotebooks = new HashMap<>();
        tabbedPane = new JTabbedPane();
        
        tabbedPane.setBackground(new Color(13, 13, 35));
        tabbedPane.setOpaque(true);
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        showTabPopupMenu(tabIndex, e.getX(), e.getY());
                    }
                }
            }
        });
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(createSideBar(), BorderLayout.WEST);
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(createStatusBar(), BorderLayout.SOUTH);
        setupMenuBar();
        setupSearchPanel();
        setupTerminalPanel();
        enableGlobalScrolling();
        hotkeyManager = new HotkeyManager(this);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Window closing event triggered.");
                
                if (terminalPanel != null) {
                    terminalPanel.cleanup();
                }
                shutdownApplication(false);
            }
        });
    }
    
    private String findKernelSpecName(String displayName) {
        
        Map<String, JupyterKernelClient.KernelSpec> kernels = JupyterKernelClient.discoverKernels(this.selectedEnvPath);
        
        for (Map.Entry<String, JupyterKernelClient.KernelSpec> entry : kernels.entrySet()) {
            if (entry.getValue().getDisplayName().equals(displayName)) {
                return entry.getKey();
            }
        }
        
        
        return displayName.toLowerCase().replace(" ", "");
    }

    
    private void shutdownApplication(boolean showProgressDialog) {
        System.out.println("Initiating application shutdown...");
        
        ShutdownWorker worker = new ShutdownWorker();
    
        if (showProgressDialog) {
            
            worker.execute();
            
            
            
            SwingUtilities.invokeLater(worker::showDialog);
        } else {
            
            worker.execute();
            
        }
    }       
    
    private JPanel createSideBar() {
        currentSideBar = new JPanel(new BorderLayout());
        currentSideBar.setBackground(new Color(13, 13, 35));
        currentSideBar.setPreferredSize(new Dimension(250, getHeight()));
        
        File rootFile = new File(System.getProperty("user.home"));
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootFile);
        
        
        createFileTree(rootFile, rootNode);
        
        JTree fileTree = new JTree(rootNode) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return false;
            }
        };
        
        
        fileTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                TreePath path = event.getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                
                
                if (node.getChildCount() == 1 && 
                    ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject().equals("...")) {
                    
                    
                    
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            File file = (File) node.getUserObject();
                            if (file.isDirectory()) {
                                
                                node.removeAllChildren();
                                
                                createFileTree(file, node);
                            }
                            return null;
                        }

                        @Override
                        protected void done() {
                            
                            ((javax.swing.tree.DefaultTreeModel) fileTree.getModel()).reload(node);
                        }
                    }.execute();
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                
            }
        });
        

        fileTree.setCellRenderer(ipynbRenderer);
        fileTree.setBackground(new Color(13, 13, 35));
        fileTree.setOpaque(true);
        addTreeMouseListener(fileTree);
        SwingUtilities.updateComponentTreeUI(fileTree);
        
        JScrollPane treeScrollPane = new JScrollPane(fileTree);
        treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        JScrollBar treeHBar = treeScrollPane.getHorizontalScrollBar();
        if (treeHBar != null) {
            treeHBar.setBackground(new Color(13, 13, 35));
        }
        
        currentSideBar.add(treeScrollPane, BorderLayout.CENTER);
        return currentSideBar;
    }

    private void createFileTree(File fileRoot, DefaultMutableTreeNode node) {
        File[] files = fileRoot.listFiles();
        if (files == null) {
            return;
        }

        
        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        for (File file : files) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(file);
            node.add(childNode);
            
            
            if (file.isDirectory()) {
                childNode.add(new DefaultMutableTreeNode("...")); 
            }
        }
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(13, 13, 35));  
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(13, 13, 35));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        
        
        JLabel dirLabel = new JLabel("Dir:");
        dirLabel.setForeground(new Color(180, 180, 220)); 
        leftPanel.add(dirLabel, BorderLayout.WEST);
        
        
        statusLabel = new JLabel("No kernel connected");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        leftPanel.add(statusLabel, BorderLayout.CENTER);
        
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(new Color(13, 13, 35));
        kernelStatus = new JLabel("Kernel: None");
        kernelStatus.setForeground(new Color(180, 180, 220));
        rightPanel.add(kernelStatus);
        
        statusBar.add(leftPanel, BorderLayout.CENTER);
        statusBar.add(rightPanel, BorderLayout.EAST);
        
        return statusBar;
    }

    private void setupTerminalPanel() {
        
        terminalPanel = new TerminalPanel(this);
        
        
        JPanel mainContentPanel = new JPanel(new BorderLayout());
        mainContentPanel.setBackground(new Color(13, 13, 35));
        
        
        if (currentSideBar == null) {
            currentSideBar = createSideBar();
        }
        mainContentPanel.add(currentSideBar, BorderLayout.WEST);
        mainContentPanel.add(tabbedPane, BorderLayout.CENTER);
        mainContentPanel.add(createStatusBar(), BorderLayout.SOUTH);
        
        
        searchPanel.setVisible(false);
        mainContentPanel.add(searchPanel, BorderLayout.NORTH);
        
        
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainContentPanel, terminalPanel);
        mainSplitPane.setDividerLocation(getHeight() - lastTerminalHeight);
        mainSplitPane.setResizeWeight(1.0);
        mainSplitPane.setDividerSize(8);
        mainSplitPane.setBackground(new Color(13, 13, 35));
        
        
        mainSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(new Color(40, 40, 100));
                        g.fillRect(0, 0, getSize().width, getSize().height);
                        
                        
                        g.setColor(new Color(60, 60, 120));
                        int midY = getHeight() / 2;
                        for (int i = 20; i < getWidth() - 20; i += 5) {
                            g.fillRect(i, midY - 1, 2, 3);
                        }
                    }
                };
            }
        });
        
        
        terminalPanel.setVisible(false);
        mainSplitPane.setDividerLocation(getHeight());
        
        
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainSplitPane, BorderLayout.CENTER);
        
        
        mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (terminalPanel.isVisible()) {
                lastTerminalHeight = getHeight() - mainSplitPane.getDividerLocation();
            }
        });
    }

    public void toggleTerminal() {
        if (terminalPanel == null) return;
        
        if (!terminalPanel.isVisible()) {
            
            terminalPanel.setVisible(true);
            
            
            final int targetLocation = getHeight() - lastTerminalHeight;
            final int currentLocation = mainSplitPane.getDividerLocation();
            final int distance = currentLocation - targetLocation;
            final int steps = 10;
            final int delay = 20;
            
            Timer animationTimer = new Timer(delay, new ActionListener() {
                private int step = 0;
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    step++;
                    int newLocation = currentLocation - (distance * step / steps);
                    mainSplitPane.setDividerLocation(newLocation);
                    
                    if (step >= steps) {
                        mainSplitPane.setDividerLocation(targetLocation);
                        ((Timer)e.getSource()).stop();
                        terminalPanel.showTerminal();
                    }
                }
            });
            animationTimer.start();
        } else {
            
            terminalPanel.hideTerminal();
            
            
            final int targetLocation = getHeight();
            final int currentLocation = mainSplitPane.getDividerLocation();
            final int distance = targetLocation - currentLocation;
            final int steps = 10;
            final int delay = 20;
            
            Timer animationTimer = new Timer(delay, new ActionListener() {
                private int step = 0;
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    step++;
                    int newLocation = currentLocation + (distance * step / steps);
                    mainSplitPane.setDividerLocation(newLocation);
                    
                    if (step >= steps) {
                        mainSplitPane.setDividerLocation(targetLocation);
                        ((Timer)e.getSource()).stop();
                        terminalPanel.setVisible(false);
                    }
                }
            });
            animationTimer.start();
        }
    }

    
    private File currentDirectory = new File(System.getProperty("user.home"));

    
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectoryAndUpdateTree(File directory) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            currentDirectory = directory;
            updateFileTree(directory);
            
            
            String absolutePath = directory.getAbsolutePath();
            statusLabel.setText("Current directory: " + absolutePath);
            
            
            JupyterKernelClient client = getCurrentKernelClient();
            if (client != null) {
                String syncCode = "import os\n" +
                                "try:\n" +
                                "    os.chdir(r'" + absolutePath.replace("\\", "\\\\") + "')\n" +
                                "    # No print statement here\n" +
                                "except Exception as e:\n" +
                                "    print(f'Error changing directory: {e}')\n";
                client.executeCode(syncCode);
            }
        }
    }

    private void showTabPopupMenu(int tabIndex, int x, int y) {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBackground(new Color(13, 13, 35));
        popupMenu.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
        
        
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.setBackground(new Color(13, 13, 35));
        closeItem.setForeground(Color.WHITE);
        closeItem.addActionListener(e -> closeTab(tabIndex));
        popupMenu.add(closeItem);
        
        
        JMenuItem closeAllItem = new JMenuItem("Close All");
        closeAllItem.setBackground(new Color(13, 13, 35));
        closeAllItem.setForeground(Color.WHITE);
        closeAllItem.addActionListener(e -> closeAllTabs());
        popupMenu.add(closeAllItem);
        
        
        JMenuItem closeOthersItem = new JMenuItem("Close Others");
        closeOthersItem.setBackground(new Color(13, 13, 35));
        closeOthersItem.setForeground(Color.WHITE);
        closeOthersItem.addActionListener(e -> closeOtherTabs(tabIndex));
        popupMenu.add(closeOthersItem);
        
        
        popupMenu.addSeparator();
        
        
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setBackground(new Color(13, 13, 35));
        saveItem.setForeground(Color.WHITE);
        saveItem.addActionListener(e -> saveTab(tabIndex));
        popupMenu.add(saveItem);
        
        
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setBackground(new Color(13, 13, 35));
        saveAsItem.setForeground(Color.WHITE);
        saveAsItem.addActionListener(e -> saveTabAs(tabIndex));
        popupMenu.add(saveAsItem);
        
        
        popupMenu.show(tabbedPane, x, y);
    }
    
    private void closeTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            String title = tabbedPane.getTitleAt(tabIndex);
            Component comp = tabbedPane.getComponentAt(tabIndex);

            if (comp instanceof NotebookPanel) {
                NotebookPanel panel = (NotebookPanel) comp;
                String kernelId = notebookToKernelMap.get(panel);

                
                openNotebooks.remove(title);
                notebookToKernelMap.remove(panel);
                tabbedPane.remove(tabIndex);

                
                if (kernelId != null && !kernelId.equals(SHARED_KERNEL_ID)) {
                    if (!notebookToKernelMap.containsValue(kernelId)) {
                        System.out.println("Closing dedicated kernel: " + kernelId);
                        JupyterKernelClient clientToClose = kernelClients.remove(kernelId);
                        if (clientToClose != null) {
                            
                            new Thread(clientToClose::close).start();
                        }
                    }
                }
                statusLabel.setText("Closed notebook: " + title);
            }
        }
    }
    
    
    private void closeAllTabs() {
        
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            closeTab(i);
        }
    }
    
    
    private void closeOtherTabs(int keepTabIndex) {
        
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            if (i != keepTabIndex) {
                closeTab(i);
            }
        }
    }
    
    
    private void saveTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            Component comp = tabbedPane.getComponentAt(tabIndex);
            if (comp instanceof NotebookPanel) {
                ((NotebookPanel) comp).save();
            }
        }
    }
    
    
    private void saveTabAs(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            Component comp = tabbedPane.getComponentAt(tabIndex);
            if (comp instanceof NotebookPanel) {
                ((NotebookPanel) comp).saveAs();
            }
        }
    }

    public void updateTabTitle(NotebookPanel panel, String title) {
        int index = tabbedPane.indexOfComponent(panel);
        if (index >= 0) {
            
            String oldTitle = tabbedPane.getTitleAt(index);
            
            
            tabbedPane.setTitleAt(index, title);
            
            
            openNotebooks.remove(oldTitle);
            openNotebooks.put(title, panel);
            
            
            statusLabel.setText("Notebook saved as: " + title);
        }
    }
    
    private void saveAllLatex() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook == null) {
            JOptionPane.showMessageDialog(this, 
                "No notebook is currently open.", 
                "Save Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        
        JSONObject allLatex = new JSONObject();
        JSONArray latexArray = new JSONArray();
        
        
        Component[] components = getCodeCellsFromNotebook(currentNotebook);
        
        for (int cellIndex = 0; cellIndex < components.length; cellIndex++) {
            Component component = components[cellIndex];
            if (component instanceof CodeCell) {
                CodeCell cell = (CodeCell) component;
                LatexEditorPanel latexPanel = getLatexPanelFromCell(cell);
                
                if (latexPanel != null) {
                    String latexContent = latexPanel.getLatexText();
                    
                    if (latexContent != null && !latexContent.trim().isEmpty()) {
                        JSONObject cellLatex = new JSONObject();
                        cellLatex.put("index", cellIndex);
                        cellLatex.put("content", latexContent);
                        latexArray.put(cellLatex);
                    }
                }
            }
        }
        
        
        allLatex.put("latexEquations", latexArray);
        allLatex.put("notebookName", getCurrentNotebookName());
        allLatex.put("savedDate", new java.util.Date().toString());
        
        
        JFileChooser fileChooser = new JFileChooser(currentDirectory);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            
            @Override
            public String getDescription() {
                return "LaTeX JSON Files (*.json)";
            }
        });
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getPath();
            if (!path.toLowerCase().endsWith(".json")) {
                path += ".json";
                file = new File(path);
            }
            
            try {
                
                Files.write(file.toPath(), allLatex.toString(2).getBytes(StandardCharsets.UTF_8));
                statusLabel.setText("All LaTeX equations saved to " + file.getName());
                
                
                currentDirectory = file.getParentFile();
                
                JOptionPane.showMessageDialog(this, 
                    "All LaTeX content saved successfully.", 
                    "Save Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error saving LaTeX content: " + e.getMessage(), 
                    "Save Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    
    private String getCurrentNotebookName() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            return tabbedPane.getTitleAt(selectedIndex);
        }
        return "Untitled";
    }
    
    
    private void loadLatexIntoNotebook() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook == null) {
            JOptionPane.showMessageDialog(this, 
                "No notebook is currently open.", 
                "Load Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        
        JFileChooser fileChooser = new JFileChooser(currentDirectory);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            
            @Override
            public String getDescription() {
                return "LaTeX JSON Files (*.json)";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            try {
                
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                
                
                String notebookName = json.optString("notebookName", "Unknown");
                String savedDate = json.optString("savedDate", "Unknown date");
                
                JSONArray latexArray = json.getJSONArray("latexEquations");
                
                
                int maxIndex = -1;
                for (int i = 0; i < latexArray.length(); i++) {
                    JSONObject cellLatex = latexArray.getJSONObject(i);
                    int index = cellLatex.getInt("index");
                    if (index > maxIndex) {
                        maxIndex = index;
                    }
                }
                
                
                Component[] existingCells = getCodeCellsFromNotebook(currentNotebook);
                int existingCellCount = existingCells.length;
                
                
                if (maxIndex >= existingCellCount) {
                    int cellsToAdd = maxIndex - existingCellCount + 1;
                    int option = JOptionPane.showConfirmDialog(this,
                        "This LaTeX file requires " + cellsToAdd + " additional cell(s) to be created.\n" +
                        "Do you want to proceed?",
                        "Create Additional Cells",
                        JOptionPane.YES_NO_OPTION);
                    
                    if (option != JOptionPane.YES_OPTION) {
                        return;
                    }
                    
                    
                    for (int i = 0; i < cellsToAdd; i++) {
                        currentNotebook.addCodeCell();
                    }
                }
                
                
                Component[] updatedCells = getCodeCellsFromNotebook(currentNotebook);
                
                
                StringBuilder summary = new StringBuilder();
                summary.append("Loading LaTeX from: ").append(file.getName()).append("\n");
                summary.append("Originally from notebook: ").append(notebookName).append("\n");
                summary.append("Saved on: ").append(savedDate).append("\n\n");
                summary.append("Loading ").append(latexArray.length()).append(" equation(s) into notebook cells...");
                
                statusLabel.setText("Loading LaTeX from " + file.getName());
                
                
                int loadedCount = 0;
                for (int i = 0; i < latexArray.length(); i++) {
                    JSONObject cellLatex = latexArray.getJSONObject(i);
                    int index = cellLatex.getInt("index");
                    String latexContent = cellLatex.getString("content");
                    
                    if (index < updatedCells.length) {
                        if (updatedCells[index] instanceof CodeCell) {
                            CodeCell cell = (CodeCell) updatedCells[index];
                            LatexEditorPanel latexPanel = getLatexPanelFromCell(cell);
                            if (latexPanel != null) {
                                latexPanel.setLatexText(latexContent);
                                latexPanel.renderLatex(); 
                                loadedCount++;
                            }
                        }
                    }
                }
                
                
                currentDirectory = file.getParentFile();
                
                statusLabel.setText(loadedCount + " LaTeX equations loaded from " + file.getName());
                
                
                summary.append("\n\nSuccessfully loaded ").append(loadedCount).append(" equation(s).");
                JOptionPane.showMessageDialog(this, 
                    summary.toString(), 
                    "LaTeX Load Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error loading LaTeX content: " + e.getMessage(), 
                    "Load Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showHotkeyHelp() {
        JTextArea textArea = new JTextArea(hotkeyManager.getHotkeyHelpText());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setBackground(new Color(16, 16, 53));
        textArea.setForeground(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        scrollPane.setBackground(new Color(16, 16, 53));
        
        
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        if (vBar != null) {
            vBar.setBackground(new Color(13, 13, 35));
            vBar.setUI(new BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    this.thumbColor = new Color(40, 40, 100);
                    this.trackColor = new Color(20, 20, 50);
                }
            });
        }
        
        JOptionPane.showMessageDialog(
            this,
            scrollPane,
            "Keyboard Shortcuts",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    
    private Component[] getCodeCellsFromNotebook(NotebookPanel notebook) {
        
        try {
            java.lang.reflect.Field field = NotebookPanel.class.getDeclaredField("cellsContainer");
            field.setAccessible(true);
            JPanel cellsContainer = (JPanel) field.get(notebook);
            return cellsContainer.getComponents();
        } catch (Exception e) {
            e.printStackTrace();
            return new Component[0];
        }
    }
    
    
    private LatexEditorPanel getLatexPanelFromCell(CodeCell cell) {
        try {
            
            java.lang.reflect.Field field = CodeCell.class.getDeclaredField("sidebarSplitPane");
            field.setAccessible(true);
            JSplitPane sidebarSplitPane = (JSplitPane) field.get(cell);
            
            
            if (sidebarSplitPane.getTopComponent() instanceof LatexEditorPanel) {
                return (LatexEditorPanel) sidebarSplitPane.getTopComponent();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void openFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            updateFileTree(selectedFolder);
            
            
            currentDirectory = selectedFolder;
            String absolutePath = selectedFolder.getAbsolutePath();
            statusLabel.setText("Current directory: " + absolutePath);
            
            
            JupyterKernelClient client = getCurrentKernelClient();
            if (client != null) {
                String syncCode = "import os\n" +
                                 "try:\n" +
                                 "    os.chdir(r'" + absolutePath.replace("\\", "\\\\") + "')\n" +
                                 "    # No print statement here\n" +
                                 "except Exception as e:\n" +
                                 "    print(f'Error changing directory: {e}')\n";
                client.executeCode(syncCode);
            }
        }
    }
    
    private void updateFileTree(File rootFolder) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootFolder);
        createFileTree(rootFolder, rootNode);
        
        if (currentSideBar != null) {
            Component[] components = currentSideBar.getComponents();
            for (Component comp : components) {
                if (comp instanceof JScrollPane) {
                    JScrollPane treeScrollPane = (JScrollPane) comp;
                    JTree newTree = new JTree(rootNode);
                    newTree.setCellRenderer(ipynbRenderer);
                    newTree.setBackground(new Color(13, 13, 35));
                    newTree.setForeground(Color.WHITE);
                    addTreeMouseListener(newTree);
                    treeScrollPane.setViewportView(newTree);
                    
                    
                    newTree.expandRow(0);
                    
                    
                    treeScrollPane.revalidate();
                    treeScrollPane.repaint();
                    break;
                }
            }
        }
    }
    
    DefaultTreeCellRenderer ipynbRenderer = new DefaultTreeCellRenderer() {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                       boolean selected, boolean expanded,
                                                       boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus);
    
            
            label.setBackground(selected ? getBackgroundSelectionColor() : new Color(13, 13, 35));
            label.setOpaque(true);
            
            
            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof File) {
                    File file = (File) userObject;
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".ipynb")) {
                        label.setForeground(new Color(0x8c, 0xba, 0xfa));
                    } else {
                        label.setForeground(Color.WHITE);
                    }
                }
            }
            return label;
        }
    };    

    private void addTreeMouseListener(JTree tree) {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    tree.getLastSelectedPathComponent();
                if (node != null) {
                    File file = (File) node.getUserObject();
                    if (file.isDirectory()) {
                        
                        currentDirectory = file;
                        String absolutePath = file.getAbsolutePath();
                        statusLabel.setText("Current directory: " + absolutePath);
                        
                        
                        JupyterKernelClient client = getCurrentKernelClient();
                        if (client != null) {
                            String syncCode = "import os\n" +
                                             "try:\n" +
                                             "    os.chdir(r'" + absolutePath.replace("\\", "\\\\") + "')\n" +
                                             "    # No print statement here\n" +
                                             "except Exception as e:\n" +
                                             "    print(f'Error changing directory: {e}')\n";
                            client.executeCode(syncCode);
                        }
                    } else if (e.getClickCount() == 2 && file.isFile() && 
                            file.getName().toLowerCase().endsWith(".ipynb")) {
                        
                        currentDirectory = file.getParentFile();
                        String absolutePath = currentDirectory.getAbsolutePath();
                        statusLabel.setText("Current directory: " + absolutePath);
                        
                        
                        JupyterKernelClient client = getCurrentKernelClient();
                        if (client != null) {
                            String syncCode = "import os\n" +
                                             "try:\n" +
                                             "    os.chdir(r'" + absolutePath.replace("\\", "\\\\") + "')\n" +
                                             "    # No print statement here\n" +
                                             "except Exception as e:\n" +
                                             "    print(f'Error changing directory: {e}')\n";
                            client.executeCode(syncCode);
                        }
                        
                        openNotebookFile(file);
                    }
                }
            }
        });
    }
    
    
    private void openNotebookFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject notebook = new JSONObject(content);
            
            JupyterKernelClient client = kernelClients.get(SHARED_KERNEL_ID);
            NotebookPanel notebookPanel = new NotebookPanel(client, file.getParentFile());
        
            notebookPanel.loadFromJson(notebook);
            
            
            notebookPanel.setSavedFile(file);
            
            String title = file.getName();
            tabbedPane.addTab(title, notebookPanel);
            tabbedPane.setSelectedComponent(notebookPanel);
            openNotebooks.put(title, notebookPanel);

            notebookToKernelMap.put(notebookPanel, SHARED_KERNEL_ID);
            
            statusLabel.setText("Opened notebook: " + file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error opening notebook: " + e.getMessage(), 
                "Open Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleErrorChecking(boolean enabled) {
        
        for (NotebookPanel notebook : openNotebooks.values()) {
            notebook.setErrorCheckingEnabled(enabled);
        }
        
        
        if (enabled) {
            statusLabel.setText("Error underlining enabled");
        } else {
            statusLabel.setText("Error underlining disabled");
        }
    }

    private void updateKernelList(JMenu kernelMenu) {
        kernelMenu.removeAll();
        
        
        
        Map<String, JupyterKernelClient.KernelSpec> kernels = JupyterKernelClient.discoverKernels(this.selectedEnvPath);
        
        if (kernels.isEmpty()) {
            JMenuItem noKernelsItem = new JMenuItem("No kernels found");
            noKernelsItem.setEnabled(false);
            kernelMenu.add(noKernelsItem);
        } else {
            for (JupyterKernelClient.KernelSpec kernelSpec : kernels.values()) {
                JMenuItem kernelItem = new JMenuItem(kernelSpec.getDisplayName());
                kernelItem.addActionListener(e -> changeKernel(kernelSpec.getName()));
                kernelMenu.add(kernelItem);
            }
        }
    }

    private void changeKernel(String kernelName) {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook == null) {
            JOptionPane.showMessageDialog(this, "No active notebook to change kernel for.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String oldKernelId = notebookToKernelMap.get(currentNotebook);

        try {
            
            JupyterKernelClient newKernelClient = JupyterKernelClient.startKernel(kernelName, this.selectedEnvPath);
            newKernelClient.setStatusListener(JupyterNotebookIDE.this);
            
            
            kernelCounter++;
            String newKernelId = "Kernel " + kernelCounter + " (" + kernelName + ")";
            kernelClients.put(newKernelId, newKernelClient);

            
            currentNotebook.setKernelClient(newKernelClient);
            notebookToKernelMap.put(currentNotebook, newKernelId);
            
            
            int tabIndex = tabbedPane.indexOfComponent(currentNotebook);
            if (tabIndex != -1) {
                String oldTitle = tabbedPane.getTitleAt(tabIndex);
                
                if (oldTitle.contains(" (Kernel")) {
                    oldTitle = oldTitle.substring(0, oldTitle.indexOf(" (Kernel"));
                }
                tabbedPane.setTitleAt(tabIndex, oldTitle + " (" + newKernelId + ")");
            }

            statusLabel.setText("Switched to " + newKernelId);
            updateStatusForCurrentTab();

            
            if (oldKernelId != null && !notebookToKernelMap.containsValue(oldKernelId)) {
                JupyterKernelClient oldClient = kernelClients.remove(oldKernelId);
                if (oldClient != null) {
                    new Thread(oldClient::close).start();
                    System.out.println("Cleaned up unused kernel: " + oldKernelId);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error changing kernel: " + e.getMessage(),
                "Kernel Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setOpaque(true);
        
        
        JMenu fileMenu = new JMenu("File");

        JMenuItem newNotebookItem = new JMenuItem("New Notebook (Shared Kernel)");
        newNotebookItem.addActionListener(e -> createNewNotebook(SHARED_KERNEL_ID));

        JMenuItem newNotebookNewKernelItem = new JMenuItem("New Notebook (New Kernel)");
        newNotebookNewKernelItem.addActionListener(e -> createNewNotebookWithNewKernel());

        JMenuItem openNotebookItem = new JMenuItem("Open Notebook...");
        openNotebookItem.addActionListener(e -> openNotebook());

        JMenuItem openFolderItem = new JMenuItem("Open Folder...");
        openFolderItem.addActionListener(e -> openFolder());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveCurrentNotebook());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(e -> saveCurrentNotebookAs());

        JMenuItem exportWrappersItem = new JMenuItem("Export with Wrappers...");
        exportWrappersItem.addActionListener(e -> exportCurrentNotebookWithWrappers());
        

        JMenuItem setDefaultKernelItem = new JMenuItem("Set Default Kernel...");
        setDefaultKernelItem.addActionListener(e -> {
            KernelSelectionDialog dialog = new KernelSelectionDialog(this);
            dialog.setVisible(true);
            
            if (!dialog.wasDialogCancelled()) {
                JOptionPane.showMessageDialog(this,
                    "Default kernel preference saved. It will be used on next startup.",
                    "Preference Saved",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            System.out.println("Exit menu item clicked.");
            
            shutdownApplication(true);
        });

        
        fileMenu.add(newNotebookItem);
        fileMenu.add(newNotebookNewKernelItem);
        fileMenu.add(openNotebookItem);
        fileMenu.add(openFolderItem);
        fileMenu.addSeparator();
        fileMenu.add(exportWrappersItem); 
        fileMenu.addSeparator(); 
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        
        JMenu editMenu = new JMenu("Edit");
        editMenu.add(new JMenuItem("Undo"));
        editMenu.add(new JMenuItem("Redo"));
        editMenu.addSeparator();
        editMenu.add(new JMenuItem("Cut"));
        editMenu.add(new JMenuItem("Copy"));
        editMenu.add(new JMenuItem("Paste"));
        editMenu.addSeparator();
        JCheckBoxMenuItem errorCheckItem = new JCheckBoxMenuItem("Error Underlining");
        errorCheckItem.setState(true); 
        errorCheckItem.addActionListener(e -> toggleErrorChecking(errorCheckItem.getState()));
        editMenu.add(errorCheckItem);
        
        
        JMenu cellMenu = new JMenu("Cell");
        JMenuItem runCellItem = new JMenuItem("Run Cell");
        runCellItem.addActionListener(e -> runCurrentCell());
        JMenuItem runAllItem = new JMenuItem("Run All");
        runAllItem.addActionListener(e -> runAllCells());
        JMenuItem addCellAboveItem = new JMenuItem("Add Cell Above");
        JMenuItem addCellBelowItem = new JMenuItem("Add Cell Below");
        JMenuItem deleteCellItem = new JMenuItem("Delete Cell");
        cellMenu.add(runCellItem);
        cellMenu.add(runAllItem);
        cellMenu.addSeparator();
        cellMenu.add(addCellAboveItem);
        cellMenu.add(addCellBelowItem);
        cellMenu.add(deleteCellItem);
        
        
        JMenu kernelMenu = new JMenu("Kernel");
        JMenuItem restartKernelItem = new JMenuItem("Restart Kernel");
        restartKernelItem.addActionListener(e -> restartKernel(true));
        JMenuItem interruptKernelItem = new JMenuItem("Interrupt Kernel");
        interruptKernelItem.addActionListener(e -> interruptKernel());
        JMenu changeKernelMenu = new JMenu("Change Kernel");
        updateKernelList(changeKernelMenu);
        JMenuItem refreshKernelsItem = new JMenuItem("Refresh Kernel List");
        refreshKernelsItem.addActionListener(e -> updateKernelList(changeKernelMenu));
        kernelMenu.add(restartKernelItem);
        kernelMenu.add(interruptKernelItem);
        kernelMenu.addSeparator();
        kernelMenu.add(setDefaultKernelItem);
        kernelMenu.addSeparator();
        kernelMenu.add(changeKernelMenu);
        kernelMenu.add(refreshKernelsItem);

        JMenu hotkeysMenu = new JMenu("Hotkeys");

        JMenuItem configureHotkeysItem = new JMenuItem("Configure Hotkeys...");
        configureHotkeysItem.setForeground(Color.WHITE);
        configureHotkeysItem.addActionListener(e -> hotkeyManager.showHotkeyConfigDialog());

        JMenuItem showHotkeysItem = new JMenuItem("Show Hotkey List");
        showHotkeysItem.setForeground(Color.WHITE);
        showHotkeysItem.addActionListener(e -> showHotkeyHelp());

        hotkeysMenu.add(configureHotkeysItem);
        hotkeysMenu.add(showHotkeysItem);
        
        
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(cellMenu);
        menuBar.add(kernelMenu);
        menuBar.add(hotkeysMenu);

        JMenu latexMenu = new JMenu("LaTeX");
        JMenuItem saveAllLatexItem = new JMenuItem("Save LaTeX");
        saveAllLatexItem.addActionListener(e -> saveAllLatex());
        JMenuItem loadAllLatexItem = new JMenuItem("Load LaTeX");
        loadAllLatexItem.addActionListener(e -> loadLatexIntoNotebook());
        latexMenu.add(saveAllLatexItem);
        latexMenu.add(loadAllLatexItem);
        menuBar.add(latexMenu);
        
        
        addRightSideButtons(menuBar);
        
        
        setJMenuBar(menuBar);
    }

    private void setupSearchPanel() {
        
        searchPanel = new SearchPanel(this);
        
        
        getContentPane().add(searchPanel, BorderLayout.NORTH);
        searchPanel.setVisible(false);
        
        
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                
                if (e.getID() != KeyEvent.KEY_PRESSED) {
                    return false;
                }
                
                
                if (e.getKeyCode() == KeyEvent.VK_F && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    
                    toggleSearchPanel();
                    
                    
                    e.consume();
                    return true;
                }
                
                
                if (e.getKeyCode() == KeyEvent.VK_F3 && (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
                    if (searchPanel.isVisible()) {
                        clickSearchButton("nextButton");
                    } else {
                        
                        showSearchPanel();
                    }
                    e.consume();
                    return true;
                }
                
                
                if (e.getKeyCode() == KeyEvent.VK_F3 && (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                    if (searchPanel.isVisible()) {
                        clickSearchButton("prevButton");
                    } else {
                        
                        showSearchPanel();
                    }
                    e.consume();
                    return true;
                }
                
                
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && searchPanel.isVisible()) {
                    hideSearchPanel();
                    e.consume();
                    return true;
                }
                
                
                return false;
            }
        });
    }
    
    
    private void toggleSearchPanel() {
        if (searchPanel.isVisible()) {
            hideSearchPanel();
        } else {
            showSearchPanel();
        }
    }
    
    
    private void showSearchPanel() {
        searchPanel.setVisible(true);
        
        
        try {
            java.lang.reflect.Field field = SearchPanel.class.getDeclaredField("searchField");
            field.setAccessible(true);
            JTextField searchField = (JTextField) field.get(searchPanel);
            
            SwingUtilities.invokeLater(() -> {
                searchField.requestFocusInWindow();
                searchField.selectAll();
                
                
                if (!searchField.getText().isEmpty()) {
                    try {
                        java.lang.reflect.Method searchMethod = 
                            SearchPanel.class.getDeclaredMethod("performSearch");
                        searchMethod.setAccessible(true);
                        searchMethod.invoke(searchPanel);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    
    private void hideSearchPanel() {
        
        try {
            java.lang.reflect.Method clearMethod = SearchPanel.class.getDeclaredMethod("clearHighlights");
            clearMethod.setAccessible(true);
            clearMethod.invoke(searchPanel);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        searchPanel.setVisible(false);
        
        
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            SwingUtilities.invokeLater(() -> {
                currentNotebook.requestFocusInWindow();
            });
        }
    }
    
    
    private void clickSearchButton(String buttonName) {
        try {
            java.lang.reflect.Field field = SearchPanel.class.getDeclaredField(buttonName);
            field.setAccessible(true);
            JButton button = (JButton) field.get(searchPanel);
            button.doClick();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void clearAllOutputs() {
        
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            
            boolean clearedAny = clearOutputsInContainer(currentNotebook);
            
            if (clearedAny) {
                statusLabel.setText("All outputs cleared");
            } else {
                statusLabel.setText("No outputs to clear");
            }
        }
    }
    
    private boolean clearOutputsInContainer(Container container) {
        boolean clearedAny = false;
        
        for (Component component : container.getComponents()) {
            if (component instanceof CodeCell) {
                CodeCell cell = (CodeCell) component;
                cell.clearOutput();
                clearedAny = true;
            } else if (component instanceof Container) {
                
                clearedAny = clearOutputsInContainer((Container) component) || clearedAny;
            }
        }
        
        return clearedAny;
    }

    private void interruptKernel() {
        JupyterKernelClient client = getCurrentKernelClient(); 
        if (client != null) {
            statusLabel.setText("Interrupting kernel...");
            kernelStatus.setText("Kernel: Interrupting");
            
            
            for (NotebookPanel notebook : openNotebooks.values()) {
                
                Component[] cells = getCodeCellsFromNotebook(notebook);
                for (Component component : cells) {
                    if (component instanceof CodeCell) {
                        ((CodeCell) component).stopExecution();
                    }
                }
            }
            
            
            boolean success = client.interruptKernel();
            
            if (success) {
                
                Timer timer = new Timer(1000, e -> {
                    statusLabel.setText("Kernel interrupted");
                    kernelStatus.setText("Kernel: Idle");
                });
                timer.setRepeats(false);
                timer.start();
            } else {
                JOptionPane.showMessageDialog(this,
                    "Cannot interrupt kernel. You may need to restart the kernel.",
                    "Interrupt Failed",
                    JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "No kernel running to interrupt",
                "Interrupt Failed",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    
    private void updateAllComponentsToBlueTheme() {
        
        Color darkBlue1 = new Color(10, 10, 35);     
        Color darkBlue2 = new Color(15, 15, 45);     
        Color darkBlue3 = new Color(20, 20, 60);     
        Color darkBlue4 = new Color(30, 30, 80);     
        Color darkBlue5 = new Color(40, 40, 100);    
        
        
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        
        
        defaults.put("Panel.background", darkBlue1);
        defaults.put("OptionPane.background", darkBlue1);
        defaults.put("TextField.inactiveForeground", darkBlue5);
        defaults.put("TextArea.inactiveForeground", darkBlue5);
        defaults.put("ComboBox.disabledForeground", darkBlue5);
        defaults.put("Button.disabledText", darkBlue5);
        defaults.put("MenuItem.disabledForeground", darkBlue5);

        Color blueBackground = new Color(10, 10, 35);
        UIManager.put("Panel.background", blueBackground);
        UIManager.put("RootPane.background", blueBackground);
        UIManager.put("MenuBar.background", blueBackground);
        UIManager.put("OptionPane.background", blueBackground);
        UIManager.put("FileChooser.background", blueBackground);
        UIManager.put("MenuBar.border", BorderFactory.createEmptyBorder());


        
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextField.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextArea.border", BorderFactory.createEmptyBorder());
        UIManager.put("FileChooser.border", BorderFactory.createEmptyBorder());

        UIManager.put("Tree.selectionBackground", new Color(0x3A, 0x75, 0xC4)); 
        UIManager.put("Tree.selectionForeground", Color.WHITE);
        UIManager.put("Tree.background", new Color(13, 13, 35));
        
        
        defaults.put("Separator.foreground", darkBlue3);
        defaults.put("Separator.background", darkBlue1);
        defaults.put("TabbedPane.contentAreaColor", darkBlue1);
        defaults.put("TabbedPane.borderColor", darkBlue3);
        
        
        defaults.put("Button.shadow", darkBlue2);
        defaults.put("Button.darkShadow", darkBlue1);
        defaults.put("Button.light", darkBlue4);
        defaults.put("Button.highlight", darkBlue5);
        
        
        defaults.put("ScrollBar.thumb", darkBlue3);
        defaults.put("ScrollBar.thumbShadow", darkBlue2);
        defaults.put("ScrollBar.thumbHighlight", darkBlue4);
        defaults.put("ScrollBar.track", darkBlue1);
        defaults.put("ScrollBar.trackHighlight", darkBlue2);
        
        
        defaults.put("FileChooser.background", darkBlue1);
        defaults.put("FileChooser.listViewBackground", darkBlue2);
        
        
        defaults.put("Tree.hash", darkBlue3);
        defaults.put("Tree.line", darkBlue3);
        
        
        defaults.put("RSyntaxTextAreaUI.borderColor", darkBlue2);
    }

    private void runCurrentCell() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.runSelectedCell();
        }
    }
    
    private void runAllCells() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            
            currentNotebook.runAllCellsSequentially();
        }
    }

    public NotebookPanel getCurrentNotebook() {
        Component selectedComponent = tabbedPane.getSelectedComponent();
        if (selectedComponent instanceof NotebookPanel) {
            return (NotebookPanel) selectedComponent;
        }
        return null;
    }
    
    private void restartKernel(boolean showProgressDialog) {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook == null) {
            JOptionPane.showMessageDialog(this, "No active notebook to restart kernel for.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String kernelId = notebookToKernelMap.get(currentNotebook);
        if (kernelId == null) {
            
            return;
        }

        System.out.println("Initiating kernel restart for: " + kernelId);

        
        
        KernelRestartWorker worker = new KernelRestartWorker(kernelId);

        if (showProgressDialog) {
            
            worker.execute();
            SwingUtilities.invokeLater(worker::showDialog);
        } else {
            
            worker.execute();
        }
    }
    
    private class KernelRestartWorker extends SwingWorker<Void, String> {
        private JDialog progressDialog;
        private JLabel progressLabel;
        private final String kernelIdToRestart; 

        
        public KernelRestartWorker(String kernelId) {
            this.kernelIdToRestart = kernelId;

            
            progressDialog = new JDialog(JupyterNotebookIDE.this, "Restarting Kernel", true);
            progressLabel = new JLabel("Starting kernel restart...", JLabel.CENTER);
            progressLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            progressLabel.setBackground(new Color(13, 13, 35));
            progressLabel.setForeground(Color.WHITE);
            progressLabel.setOpaque(true);
            
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(new Color(13, 13, 35));
            contentPanel.add(progressLabel, BorderLayout.CENTER);
            contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            progressDialog.getContentPane().add(contentPanel);
            progressDialog.pack();
            progressDialog.setLocationRelativeTo(JupyterNotebookIDE.this);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        }

        public void showDialog() {
            if (SwingUtilities.isEventDispatchThread()) {
                progressDialog.setVisible(true);
            } else {
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));
            }
        }

        @Override
        protected Void doInBackground() throws Exception {
            publish("Shutting down existing kernel: " + kernelIdToRestart);
            
            
            JupyterKernelClient oldClient = kernelClients.remove(kernelIdToRestart);
            if (oldClient != null) {
                oldClient.close();
            }

            publish("Starting new kernel...");
            try {
                
                JupyterKernelClient newClient = usingDirectMethod ?
                    
                    JupyterKernelClient.startPythonKernelDirectly(selectedEnvPath) :
                    JupyterKernelClient.startKernel("python3", selectedEnvPath);
                
                
                kernelClients.put(kernelIdToRestart, newClient);
                publish("Kernel started successfully!");

            } catch (Exception e) {
                e.printStackTrace();
                publish("Failed to start new kernel: " + e.getMessage());
                
                throw e;
            }

            Thread.sleep(500);
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            if (!chunks.isEmpty()) {
                progressLabel.setText(chunks.get(chunks.size() - 1));
                progressDialog.pack();
            }
        }

        
        @Override
        protected void done() {
            progressDialog.setVisible(false);
            progressDialog.dispose();

            try {
                get(); 
                
                
                statusLabel.setText("Kernel " + kernelIdToRestart + " restarted successfully");
                updateStatusForCurrentTab();
                
                
                JupyterKernelClient newClient = kernelClients.get(kernelIdToRestart);
                if (newClient != null) {
                    
                    for (Map.Entry<NotebookPanel, String> entry : notebookToKernelMap.entrySet()) {
                        if (entry.getValue().equals(kernelIdToRestart)) {
                            entry.getKey().setKernelClient(newClient);
                        }
                    }
                }
                

            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Failed to restart kernel: " + e.getMessage());
                
                kernelClients.remove(kernelIdToRestart);
            }
        }
    }
    
    private void openNotebook() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setBackground(new Color(13, 13, 35));
        fileChooser.setForeground(Color.WHITE);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".ipynb");
            }
            
            @Override
            public String getDescription() {
                return "Jupyter Notebook (*.ipynb)";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject notebook = new JSONObject(content);
                
                JupyterKernelClient client = kernelClients.get(SHARED_KERNEL_ID);
                NotebookPanel notebookPanel = new NotebookPanel(client, file.getParentFile());
                notebookPanel.loadFromJson(notebook);
                
                
                notebookPanel.setSavedFile(file);
                
                String title = file.getName();
                tabbedPane.addTab(title, notebookPanel);
                tabbedPane.setSelectedComponent(notebookPanel);
                openNotebooks.put(title, notebookPanel);
                notebookToKernelMap.put(notebookPanel, SHARED_KERNEL_ID);
                
                statusLabel.setText("Opened notebook: " + file.getName());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error opening notebook: " + e.getMessage(), 
                    "Open Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveCurrentNotebook() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.save();
        }
    }
    
    private void saveCurrentNotebookAs() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook != null) {
            currentNotebook.saveAs();
        }
    }
    
    public void createNewNotebook(String kernelId) {
        JupyterKernelClient client = kernelClients.get(kernelId);
        if (client == null) {
            JOptionPane.showMessageDialog(this, "Kernel with ID '" + kernelId + "' not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        NotebookPanel notebook = new NotebookPanel(client, currentDirectory);
        String title = "Untitled-" + (openNotebooks.size() + 1) + ".ipynb";
        if (!kernelId.equals(SHARED_KERNEL_ID)) {
            title += " (" + kernelId + ")"; 
        }

        tabbedPane.addTab(title, notebook);
        tabbedPane.setSelectedComponent(notebook);
        openNotebooks.put(title, notebook);
        notebookToKernelMap.put(notebook, kernelId); 
    }
    
    private void copyAllCodeToClipboard() {
        NotebookPanel currentNotebook = getCurrentNotebook();
        if (currentNotebook == null) {
            JOptionPane.showMessageDialog(this, 
                "No notebook is currently open.", 
                "Copy Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        
        Component[] components = getCodeCellsFromNotebook(currentNotebook);
        
        
        StringBuilder allCode = new StringBuilder();
        
        int cellCount = 0;
        for (Component component : components) {
            if (component instanceof CodeCell) {
                CodeCell cell = (CodeCell) component;
                String codeText = cell.getCodeText();
                
                if (codeText != null && !codeText.trim().isEmpty()) {
                    
                    if (allCode.length() > 0) {
                        allCode.append("\n\n");
                    }
                    
                    
                    allCode.append(codeText);
                    cellCount++;
                }
            }
        }
        
        if (allCode.length() > 0) {
            
            copyToClipboard(allCode.toString());
            statusLabel.setText("Copied code from " + cellCount + " cells to clipboard");
        } else {
            statusLabel.setText("No code to copy");
        }
    }

    public void createNewNotebookWithNewKernel() {
        kernelCounter++;
        String newKernelId = "Kernel " + kernelCounter;
        statusLabel.setText("Starting " + newKernelId + "...");

        
        SwingWorker<JupyterKernelClient, Void> worker = new SwingWorker<JupyterKernelClient, Void>() {
            @Override
            protected JupyterKernelClient doInBackground() throws Exception {
                
                if (usingDirectMethod) {
                    
                    return JupyterKernelClient.startPythonKernelDirectly(selectedEnvPath); 
                } else {
                    return JupyterKernelClient.startKernel("python3", selectedEnvPath);
                }
            }

            @Override
            protected void done() {
                try {
                    JupyterKernelClient newKernel = get();
                    newKernel.setStatusListener(JupyterNotebookIDE.this);
                    kernelClients.put(newKernelId, newKernel);
                    statusLabel.setText(newKernelId + " started.");
                    
                    createNewNotebook(newKernelId);
                } catch (Exception e) {
                    e.printStackTrace();
                    statusLabel.setText("Failed to start new kernel.");
                    JOptionPane.showMessageDialog(JupyterNotebookIDE.this,
                            "Failed to start new kernel: " + e.getMessage(),
                            "Kernel Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void copyToClipboard(String text) {
        try {
            
            java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
            java.awt.datatransfer.Clipboard clipboard = toolkit.getSystemClipboard();
            
            
            java.awt.datatransfer.StringSelection selection = 
                new java.awt.datatransfer.StringSelection(text);
            
            
            clipboard.setContents(selection, null);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error copying to clipboard: " + e.getMessage(), 
                "Clipboard Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addRightSideButtons(JMenuBar menuBar) {
        
        menuBar.add(Box.createHorizontalGlue());
        
        
        JButton copyAllButton = new JButton("Copy All");
        copyAllButton.setForeground(new Color(0x8c, 0xba, 0xfa)); 
        copyAllButton.setBackground(new Color(0x171717));
        copyAllButton.setBorderPainted(false);
        copyAllButton.setFocusPainted(false);
        copyAllButton.setOpaque(true);
        copyAllButton.setToolTipText("Copy all code from all cells to clipboard");
        copyAllButton.addActionListener(e -> copyAllCodeToClipboard());
        menuBar.add(copyAllButton);
        
        
        menuBar.add(Box.createHorizontalStrut(10));
        
        
        JButton runAllButton = new JButton("Run All");
        runAllButton.setForeground(Color.GREEN);
        runAllButton.setBackground(new Color(0x171717));
        runAllButton.setBorderPainted(false);
        runAllButton.setFocusPainted(false);
        runAllButton.setOpaque(true);
        runAllButton.addActionListener(e -> runAllCells());
        menuBar.add(runAllButton);
        
        
        menuBar.add(Box.createHorizontalStrut(10));
        
        
        JButton restartButton = new JButton("Restart Kernel");
        restartButton.setForeground(new Color(0, 120, 255));
        restartButton.setBackground(new Color(0x171717));
        restartButton.setBorderPainted(false);
        restartButton.setFocusPainted(false);
        restartButton.setOpaque(true);
        restartButton.addActionListener(e -> restartKernel(true));
        menuBar.add(restartButton);
        
        
        menuBar.add(Box.createHorizontalStrut(10));
        
        
        JButton interruptButton = new JButton("Interrupt");
        interruptButton.setForeground(Color.RED);
        interruptButton.setBackground(new Color(0x171717));
        interruptButton.setBorderPainted(false);
        interruptButton.setFocusPainted(false);
        interruptButton.setOpaque(true);
        interruptButton.addActionListener(e -> interruptKernel());
        menuBar.add(interruptButton);
        
        
        menuBar.add(Box.createHorizontalStrut(10));
        
        
        JButton clearAllButton = new JButton("Clear All");
        clearAllButton.setForeground(Color.WHITE);
        clearAllButton.setBackground(new Color(0x171717));
        clearAllButton.setBorderPainted(false);
        clearAllButton.setFocusPainted(false);
        clearAllButton.setOpaque(true);
        clearAllButton.addActionListener(e -> clearAllOutputs());
        menuBar.add(clearAllButton);
        
        
        menuBar.add(Box.createHorizontalStrut(20));
    }

    
    private void enableGlobalScrolling() {
        
        
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseWheelEvent) {
                MouseWheelEvent wheelEvent = (MouseWheelEvent) event;
                
                
                if (!wheelEvent.isShiftDown()) {
                    
                    forceVerticalScroll(wheelEvent);
                    wheelEvent.consume();
    
    }
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        
        
        
        SwingUtilities.invokeLater(() -> {
            disableHorizontalWheelScrollingRecursively(this);
        });
        
        
        SwingUtilities.invokeLater(() -> {
            improveScrollbarsInContainer(this);
        });
    }
    
    
    
    private void forceVerticalScroll(MouseWheelEvent e) {
        
        Component source = e.getComponent();
        if (source == null) {
            source = SwingUtilities.getDeepestComponentAt(this, e.getX(), e.getY());
        }
        if (source == null) return;
        
        
        NotebookPanel notebook = getCurrentNotebook();
        if (notebook == null) return;
        
        
        JScrollPane mainScrollPane = findMainScrollPane(notebook);
        if (mainScrollPane == null) return;
        
        
        JScrollBar vBar = mainScrollPane.getVerticalScrollBar();
        if (vBar == null || !vBar.isVisible()) return;
        
        
        int amount = e.getWheelRotation() * 16 * 3;
        
        
        int newValue = Math.max(vBar.getMinimum(), 
                    Math.min(vBar.getMaximum() - vBar.getVisibleAmount(), 
                            vBar.getValue() + amount));
        
        
        vBar.setValue(newValue);
        
        
        mainScrollPane.requestFocus();
    }
    
    
    private JScrollPane findMainScrollPane(Container container) {
        
        for (Component comp : container.getComponents()) {
            if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                if ("mainNotebookScrollPane".equals(scrollPane.getName())) {
                    return scrollPane;
                }
            }
        }
        
        
        for (Component comp : container.getComponents()) {
            if (comp instanceof JScrollPane) {
                return (JScrollPane) comp;
            } else if (comp instanceof Container) {
                JScrollPane found = findMainScrollPane((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    private void disableHorizontalWheelScrollingRecursively(Container container) {
        for (Component comp : container.getComponents()) {
            
            if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                
                
                scrollPane.setWheelScrollingEnabled(false);
                
                
                if (scrollPane.getHorizontalScrollBar() != null) {
                    JScrollBar hBar = scrollPane.getHorizontalScrollBar();
                    
                    
                    hBar.setUI(new BasicScrollBarUI() {
                        @Override
                        protected void configureScrollBarColors() {
                            this.thumbColor = new Color(40, 40, 100);
                            this.trackColor = new Color(20, 20, 50);
                        }
                        
                        @Override
                        protected JButton createDecreaseButton(int orientation) {
                            JButton button = super.createDecreaseButton(orientation);
                            
                            for (MouseWheelListener listener : button.getMouseWheelListeners()) {
                                button.removeMouseWheelListener(listener);
                            }
                            return button;
                        }
                        
                        @Override
                        protected JButton createIncreaseButton(int orientation) {
                            JButton button = super.createIncreaseButton(orientation);
                            
                            for (MouseWheelListener listener : button.getMouseWheelListeners()) {
                                button.removeMouseWheelListener(listener);
                            }
                            return button;
                        }
                    });
                    
                    
                    for (MouseWheelListener listener : hBar.getMouseWheelListeners()) {
                        hBar.removeMouseWheelListener(listener);
                    }
                    
                    
                    hBar.addMouseWheelListener(e -> {
                        if (e.isShiftDown()) {
                            int amount = e.getWheelRotation() * 16 * 3;
                            int newValue = Math.max(hBar.getMinimum(),
                                          Math.min(hBar.getMaximum() - hBar.getVisibleAmount(),
                                                 hBar.getValue() + amount));
                            hBar.setValue(newValue);
                            e.consume();
                        }
                    });
                }
            }
            
            
            if (comp instanceof Container) {
                disableHorizontalWheelScrollingRecursively((Container) comp);
            }
        }
    }

    
    private void improveScrollbarsInContainer(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                
                
                scrollPane.putClientProperty("processed.for.scrolling", Boolean.TRUE);
                
                
                JScrollBar vBar = scrollPane.getVerticalScrollBar();
                if (vBar != null) {
                    vBar.setUnitIncrement(16);
                    vBar.setBlockIncrement(128);
                    
                    
                    vBar.setUI(new BasicScrollBarUI() {
                        @Override
                        protected void configureScrollBarColors() {
                            this.thumbColor = new Color(60, 60, 120); 
                            this.trackColor = new Color(20, 20, 50);
                        }
                    });
                }
                
                
                if (scrollPane.getViewport() != null && 
                    scrollPane.getViewport().getView() instanceof RSyntaxTextArea) {
                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    
                    
                    JScrollBar hBar = scrollPane.getHorizontalScrollBar();
                    if (hBar != null) {
                        hBar.setUnitIncrement(16);
                        hBar.setBlockIncrement(128);
                    }
                }
            }
            
            
            if (comp instanceof Container) {
                improveScrollbarsInContainer((Container) comp);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JupyterNotebookIDE ide = new JupyterNotebookIDE();
            
            ide.createNewNotebook(SHARED_KERNEL_ID); 
            SwingUtilities.updateComponentTreeUI(ide);
            ide.setVisible(true);
        });
    }
    private class ShutdownWorker extends SwingWorker<Void, String> {
        private JDialog progressDialog;
        private JLabel progressLabel;

        public ShutdownWorker() {
            
            progressDialog = new JDialog(JupyterNotebookIDE.this, "Shutting Down", true); 
            progressLabel = new JLabel("Starting shutdown...", JLabel.CENTER);
            progressLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            progressDialog.getContentPane().add(progressLabel);
            progressDialog.pack();
            progressDialog.setLocationRelativeTo(JupyterNotebookIDE.this);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); 
        }

        public void showDialog() {
            
            if (SwingUtilities.isEventDispatchThread()) {
                progressDialog.setVisible(true);
            } else {
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));
            }
        }

        @Override
        protected Void doInBackground() throws Exception {
            publish("Initiating cleanup...");

            
            if (!kernelClients.isEmpty()) {
                publish("Closing " + kernelClients.size() + " kernel(s)...");
                
                Set<String> kernelIds = new HashSet<>(kernelClients.keySet());
                for (String kernelId : kernelIds) {
                    publish("Closing kernel: " + kernelId);
                    JupyterKernelClient clientToClose = kernelClients.get(kernelId);
                    if (clientToClose != null) {
                        try {
                            clientToClose.close();
                            publish("Closed " + kernelId);
                        } catch (Exception e) {
                            publish("Error closing " + kernelId + ": " + e.getMessage());
                        }
                    }
                }
                kernelClients.clear();
            } else {
                publish("No kernel clients to close.");
            }
            

            if (terminalPanel != null) {
                publish("Cleaning up terminal...");
                terminalPanel.cleanup();
            }
            publish("Cleanup complete.");
            Thread.sleep(500);
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            
            if (!chunks.isEmpty()) {
                progressLabel.setText(chunks.get(chunks.size() - 1));
                progressDialog.pack(); 
            }
        }

        @Override
        protected void done() {
            
            progressDialog.setVisible(false);
            progressDialog.dispose();

            
            System.out.println("Shutdown worker finished, disposing IDE window.");
            JupyterNotebookIDE.this.dispose(); 

            
            System.exit(0);
        }
    }

    private class KernelSelectionDialog extends JDialog {
        private static final String PREF_DEFAULT_KERNEL = "default_kernel";
        private static final String PREF_SAVE_AS_DEFAULT = "save_kernel_as_default";
        private static final String PREF_USE_DIRECT_METHOD = "use_direct_method";
        private static final String PREF_PYTHON_ENV_PATH = "python_env_path"; 
        
        private JTable kernelTable;
        private DefaultTableModel tableModel;
        private JCheckBox saveAsDefaultCheckbox;
        private JRadioButton directMethodRadio;
        private JRadioButton standardMethodRadio;
        private JTextField envPathField; 
        private String selectedKernelName;
        private boolean useDirectMethod;
        private boolean dialogCancelled = false;
        private Preferences prefs;
        private String selectedEnvPath; 
        
        public KernelSelectionDialog(Frame parent) {
            super(parent, "Select Jupyter Kernel", true);
            prefs = Preferences.userNodeForPackage(JupyterNotebookIDE.class);
            initializeUI();
            loadSavedPreferences();
            setLocationRelativeTo(parent);
        }
        
        private void initializeUI() {
            setLayout(new BorderLayout(10, 10));
            getContentPane().setBackground(new Color(13, 13, 35));
            
            
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBackground(new Color(13, 13, 35));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            
            JLabel titleLabel = new JLabel("Select a kernel to start:");
            titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            titleLabel.setForeground(Color.WHITE);
            mainPanel.add(titleLabel, BorderLayout.NORTH);
            
            
            JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
            centerPanel.setBackground(new Color(13, 13, 35));
            
            
            String[] columnNames = {"Kernel Name", "Language", "Path"};
            tableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            kernelTable = new JTable(tableModel);
            kernelTable.setBackground(new Color(16, 16, 53));
            kernelTable.setForeground(Color.WHITE);
            kernelTable.setSelectionBackground(new Color(40, 40, 100));
            kernelTable.setSelectionForeground(Color.WHITE);
            kernelTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            kernelTable.setRowHeight(25);
            kernelTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            kernelTable.setShowGrid(false);
            kernelTable.setIntercellSpacing(new Dimension(0, 0));
            
            
            JTableHeader header = kernelTable.getTableHeader();
            header.setBackground(new Color(20, 20, 60));
            header.setForeground(Color.WHITE);
            header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            
            
            JScrollPane scrollPane = new JScrollPane(kernelTable);
            scrollPane.setBackground(new Color(13, 13, 35));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
            scrollPane.setPreferredSize(new Dimension(600, 200));
            
            
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            if (vBar != null) {
                vBar.setBackground(new Color(13, 13, 35));
                vBar.setUI(new BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(40, 40, 100);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
            }
            
            centerPanel.add(scrollPane, BorderLayout.CENTER);
            
            
            JPanel methodPanel = new JPanel(new GridLayout(3, 1, 5, 5)); 
            methodPanel.setBackground(new Color(13, 13, 35));
            methodPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
                "Connection Method",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                Color.WHITE
            ));
            
            ButtonGroup methodGroup = new ButtonGroup();
            
            directMethodRadio = new JRadioButton("Direct Method (Faster, may not work on all systems)");
            directMethodRadio.setBackground(new Color(13, 13, 35));
            directMethodRadio.setForeground(Color.WHITE);
            directMethodRadio.setSelected(true);
            
            standardMethodRadio = new JRadioButton("Standard Method (More compatible)");
            standardMethodRadio.setBackground(new Color(13, 13, 35));
            standardMethodRadio.setForeground(Color.WHITE);
            
            methodGroup.add(directMethodRadio);
            methodGroup.add(standardMethodRadio);
            
            methodPanel.add(directMethodRadio);
            methodPanel.add(standardMethodRadio);
            
            
            JPanel envPanel = new JPanel(new BorderLayout(5, 0));
            envPanel.setBackground(new Color(13, 13, 35));
            
            JLabel envLabel = new JLabel("Python Environment:");
            envLabel.setForeground(Color.WHITE);
            envLabel.setPreferredSize(new Dimension(120, 25));
            
            envPathField = new JTextField();
            envPathField.setBackground(new Color(16, 16, 53));
            envPathField.setForeground(Color.WHITE);
            envPathField.setCaretColor(Color.WHITE);
            
            JButton browseButton = new JButton("Browse...");
            browseButton.setBackground(new Color(0x171717));
            browseButton.setForeground(new Color(0x4EC9B0));
            browseButton.setFocusPainted(false);
            browseButton.addActionListener(e -> browseForEnvironment());
            
            envPanel.add(envLabel, BorderLayout.WEST);
            envPanel.add(envPathField, BorderLayout.CENTER);
            envPanel.add(browseButton, BorderLayout.EAST);
            
            methodPanel.add(envPanel);
            
            
            directMethodRadio.addActionListener(e -> envPanel.setEnabled(true));
            standardMethodRadio.addActionListener(e -> envPanel.setEnabled(false));
            
            centerPanel.add(methodPanel, BorderLayout.SOUTH);
            
            mainPanel.add(centerPanel, BorderLayout.CENTER);
            
            
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.setBackground(new Color(13, 13, 35));
            
            saveAsDefaultCheckbox = new JCheckBox("Save as default kernel");
            saveAsDefaultCheckbox.setBackground(new Color(13, 13, 35));
            saveAsDefaultCheckbox.setForeground(Color.WHITE);
            saveAsDefaultCheckbox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(new Color(13, 13, 35));
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setBackground(new Color(0x171717));
            cancelButton.setForeground(Color.WHITE);
            cancelButton.setFocusPainted(false);
            cancelButton.addActionListener(e -> {
                dialogCancelled = true;
                dispose();
            });
            
            JButton okButton = new JButton("Start");
            okButton.setBackground(new Color(0x171717));
            okButton.setForeground(new Color(0x4EC9B0));
            okButton.setFocusPainted(false);
            okButton.addActionListener(e -> {
                if (kernelTable.getSelectedRow() >= 0) {
                    selectedKernelName = (String) tableModel.getValueAt(kernelTable.getSelectedRow(), 0);
                    useDirectMethod = directMethodRadio.isSelected();
                    selectedEnvPath = envPathField.getText().trim();
                    
                    
                    if (useDirectMethod && selectedKernelName.equals("Python (Direct)")) {
                        if (selectedEnvPath.isEmpty()) {
                            JOptionPane.showMessageDialog(this,
                                "Please select a Python environment path.",
                                "Environment Required",
                                JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        
                        File envDir = new File(selectedEnvPath);
                        if (!envDir.exists() || !new File(envDir, "bin/activate").exists()) {
                            JOptionPane.showMessageDialog(this,
                                "Invalid Python environment. Please select a valid virtual environment directory.",
                                "Invalid Environment",
                                JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    }
                    
                    
                    if (saveAsDefaultCheckbox.isSelected()) {
                        prefs.put(PREF_DEFAULT_KERNEL, selectedKernelName);
                        prefs.putBoolean(PREF_SAVE_AS_DEFAULT, true);
                        prefs.putBoolean(PREF_USE_DIRECT_METHOD, useDirectMethod);
                        prefs.put(PREF_PYTHON_ENV_PATH, selectedEnvPath);
                    }
                    
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Please select a kernel.", 
                        "No Selection", 
                        JOptionPane.WARNING_MESSAGE);
                }
            });
            
            buttonPanel.add(cancelButton);
            buttonPanel.add(okButton);
            
            bottomPanel.add(saveAsDefaultCheckbox, BorderLayout.WEST);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);
            
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);
            
            
            add(mainPanel);
            
            
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            pack();
            setResizable(true);
            
            
            kernelTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && kernelTable.getSelectedRow() >= 0) {
                        okButton.doClick();
                    }
                }
            });
            populateKernelTable();
        }
        
        private void populateKernelTable() {
            
            tableModel.setRowCount(0);
            
            
            tableModel.addRow(new Object[]{
                "Python (Direct)", 
                "Python", 
                "Built-in direct connection"
            });
            
            
            String envPath = envPathField.getText().trim();
            
            
            Map<String, JupyterKernelClient.KernelSpec> kernels = JupyterKernelClient.discoverKernels(envPath);
            
            for (Map.Entry<String, JupyterKernelClient.KernelSpec> entry : kernels.entrySet()) {
                JupyterKernelClient.KernelSpec spec = entry.getValue();
                String kernelName = entry.getKey();
                String displayName = spec.getDisplayName();
                
                
                if (kernelName.toLowerCase().contains("python") || 
                    displayName.toLowerCase().contains("python") || 
                    kernelName.toLowerCase().contains("conda") || 
                    displayName.toLowerCase().contains("conda")) {
                    
                    
                    String resourceDir = spec.getResourceDir();
                    if (resourceDir == null || resourceDir.isEmpty()) {
                        resourceDir = "Default location";
                    }
                    
                    tableModel.addRow(new Object[]{
                        displayName,
                        "Python",  
                        resourceDir
                    });
                }
            }
            
            
            if (tableModel.getRowCount() == 1) {
                tableModel.addRow(new Object[]{
                    "No Python kernels found", 
                    "", 
                    "Install: python -m ipykernel install"
                });
            }
        }
        
        private void browseForEnvironment() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Python Virtual Environment");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            
            
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedDir = fileChooser.getSelectedFile();
                
                File activateScript = new File(selectedDir, "bin/activate");
                File pythonBin = new File(selectedDir, "bin/python");
                File python3Bin = new File(selectedDir, "bin/python3");

                if (activateScript.exists() && (pythonBin.exists() || python3Bin.exists())) {
                    envPathField.setText(selectedDir.getAbsolutePath());
                    populateKernelTable();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "The selected directory does not appear to be a valid Python virtual environment.\n" +
                        "It must contain 'bin/activate' and either 'bin/python' or 'bin/python3'.", 
                        "Invalid Environment",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void loadSavedPreferences() {
            
            String defaultKernel = prefs.get(PREF_DEFAULT_KERNEL, null);
            boolean saveAsDefault = prefs.getBoolean(PREF_SAVE_AS_DEFAULT, false);
            boolean useDirectMethodPref = prefs.getBoolean(PREF_USE_DIRECT_METHOD, true);
            String savedEnvPath = prefs.get(PREF_PYTHON_ENV_PATH, "");
            
            saveAsDefaultCheckbox.setSelected(saveAsDefault);
            
            if (useDirectMethodPref) {
                directMethodRadio.setSelected(true);
            } else {
                standardMethodRadio.setSelected(true);
            }
            
            envPathField.setText(savedEnvPath);
            
            
            if (defaultKernel != null) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (defaultKernel.equals(tableModel.getValueAt(i, 0))) {
                        kernelTable.setRowSelectionInterval(i, i);
                        kernelTable.scrollRectToVisible(kernelTable.getCellRect(i, 0, true));
                        break;
                    }
                }
            } else {
                
                if (tableModel.getRowCount() > 0) {
                    kernelTable.setRowSelectionInterval(0, 0);
                }
            }
        }

        public String getSelectedEnvPath() {
            return selectedEnvPath;
        }
        
        public String getSelectedKernelName() {
            return selectedKernelName;
        }
        
        public boolean useDirectMethod() {
            return useDirectMethod;
        }
        
        public boolean wasDialogCancelled() {
            return dialogCancelled;
        }
    }
}


class NotebookPanel extends JPanel {
    private JPanel cellsContainer;
    private JupyterKernelClient kernelClient;
    private File savedFile;
    private File currentDirectory;
    private Queue<CodeCell> errorCheckQueue = new ConcurrentLinkedQueue<>();
    private boolean isErrorCheckingInProgress = false;

    public NotebookPanel(JupyterKernelClient kernelClient) {
        this(kernelClient, new File(System.getProperty("user.home")));
    }
    public void setErrorCheckingEnabled(boolean enabled) {
        
        for (Component component : cellsContainer.getComponents()) {
            if (component instanceof CodeCell) {
                ((CodeCell) component).setErrorCheckingEnabled(enabled);
            }
        }
    }
    
    public void queueErrorCheck(CodeCell cell) {
        System.out.println("Queueing error check for cell");
        errorCheckQueue.add(cell);
        processErrorCheckQueue();
    }

    private void processErrorCheckQueue() {
        if (isErrorCheckingInProgress || errorCheckQueue.isEmpty()) {
            return;
        }
        
        isErrorCheckingInProgress = true;
        CodeCell cell = errorCheckQueue.poll();
        
        cell.performSequentialErrorCheck(() -> {
            
            Timer delayTimer = new Timer(1000, e -> {
                isErrorCheckingInProgress = false;
                processErrorCheckQueue();
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
        });
    }

    public NotebookPanel(JupyterKernelClient kernelClient, File currentDirectory) {
        this.kernelClient = kernelClient;
        this.currentDirectory = currentDirectory;
        setLayout(new BorderLayout());
        
        cellsContainer = new JPanel();
        cellsContainer.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
        cellsContainer.setBackground(new Color(13, 13, 35));
        
        JScrollPane scrollPane = new JScrollPane(cellsContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBackground(new Color(13, 13, 35));
        
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);  
        scrollPane.getVerticalScrollBar().setBlockIncrement(160); 
        scrollPane.setWheelScrollingEnabled(true); 

        
        scrollPane.setName("mainNotebookScrollPane");
        
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        if (vBar != null) {
            vBar.setBackground(new Color(13, 13, 35));
            vBar.setUnitIncrement(16); 
            vBar.setBlockIncrement(128); 
        }
        
        JScrollBar hBar = scrollPane.getHorizontalScrollBar();
        if (hBar != null) {
            hBar.setBackground(new Color(13, 13, 35));
            hBar.setUnitIncrement(16);
            hBar.setBlockIncrement(128);
        }
        
        add(scrollPane, BorderLayout.CENTER);
        
        addCodeCell();
    }
    
    public void exportWithWrappers() {
        JFileChooser fileChooser = new JFileChooser(currentDirectory);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".ipynb");
            }
            
            @Override
            public String getDescription() {
                return "Jupyter Notebook (*.ipynb)";
            }
        });
        
        
        String suggestedName = "exported_";
        if (savedFile != null) {
            String baseName = savedFile.getName();
            if (baseName.endsWith(".ipynb")) {
                baseName = baseName.substring(0, baseName.length() - 6);
            }
            suggestedName = baseName + "_with_wrappers.ipynb";
        } else {
            suggestedName = "notebook_with_wrappers.ipynb";
        }
        fileChooser.setSelectedFile(new File(currentDirectory, suggestedName));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getPath();
            if (!path.toLowerCase().endsWith(".ipynb")) {
                path += ".ipynb";
                file = new File(path);
            }
            exportToFileWithWrappers(file);
        }
    }

    private void exportToFileWithWrappers(File file) {
        try {
            
            JSONObject notebook = new JSONObject();
            
            
            JSONObject metadata = new JSONObject();
            metadata.put("kernelspec", new JSONObject()
                .put("display_name", "Python 3")
                .put("language", "python")
                .put("name", "python3"));
            notebook.put("metadata", metadata);
            
            
            ImportTracker importTracker = new ImportTracker();
            
            
            JSONArray cells = new JSONArray();
            Component[] components = cellsContainer.getComponents();
            
            
            JSONObject initCell = new JSONObject();
            initCell.put("cell_type", "code");
            initCell.put("execution_count", (Object)null);
            
            
            String initCode = "import os\n" +
                            "import sys\n" +
                            "\n" +
                            "# Set working directory to the notebook's directory\n" +
                            "notebook_dir = os.path.dirname(os.path.abspath('__file__')) if '__file__' in globals() else os.getcwd()\n" +
                            "os.chdir(notebook_dir)\n" +
                            "print(f\"Working directory set to: {notebook_dir}\")\n";
            
            JSONArray initSourceArray = new JSONArray();
            for (String line : initCode.split("\n")) {
                initSourceArray.put(line + "\n");
            }
            initCell.put("source", initSourceArray);
            initCell.put("outputs", new JSONArray());
            
            
            JSONObject initMetadata = new JSONObject();
            initMetadata.put("tags", new JSONArray().put("auto-generated").put("initialization"));
            initCell.put("metadata", initMetadata);
            
            cells.put(initCell);
            
            
            for (Component component : components) {
                if (component instanceof CodeCell) {
                    CodeCell cell = (CodeCell) component;
                    
                    
                    String wrappedCode = getWrappedCodeWithImportTracking(cell, importTracker);
                    
                    
                    JSONObject cellObj = new JSONObject();
                    cellObj.put("cell_type", "code");
                    cellObj.put("execution_count", (Object)null);
                    
                    
                    JSONObject cellMetadata = new JSONObject();
                    cellMetadata.put("language", cell.selectedLanguage);
                    if (!cell.customOutputFilename.isEmpty()) {
                        cellMetadata.put("custom_output_filename", cell.customOutputFilename);
                    }
                    cellObj.put("metadata", cellMetadata);
                    
                    
                    JSONArray sourceArray = new JSONArray();
                    for (String line : wrappedCode.split("\n")) {
                        sourceArray.put(line + "\n");
                    }
                    cellObj.put("source", sourceArray);
                    
                    
                    cellObj.put("outputs", new JSONArray());
                    
                    cells.put(cellObj);
                    
                } else if (component instanceof MarkdownCell) {
                    
                    MarkdownCell cell = (MarkdownCell) component;
                    JSONObject cellObj = new JSONObject();
                    cellObj.put("cell_type", "markdown");
                    
                    String source = cell.getMarkdownText();
                    JSONArray sourceArray = new JSONArray();
                    for (String line : source.split("\n")) {
                        sourceArray.put(line + "\n");
                    }
                    cellObj.put("source", sourceArray);
                    
                    cells.put(cellObj);
                }
            }
            
            notebook.put("cells", cells);
            
            
            notebook.put("nbformat", 4);
            notebook.put("nbformat_minor", 5);
            
            
            Files.write(file.toPath(), notebook.toString(2).getBytes(StandardCharsets.UTF_8));
            
            JOptionPane.showMessageDialog(this, 
                "Notebook exported successfully with code wrappers to:\n" + file.getAbsolutePath(), 
                "Export Successful", 
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error exporting notebook: " + e.getMessage(), 
                "Export Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    
    private String getWrappedCodeWithImportTracking(CodeCell cell, ImportTracker tracker) {
        String language = cell.selectedLanguage;
        String userCode = cell.getCodeText();
        
        switch (language) {
            case "Python":
                return getPythonWrappedCode(userCode, tracker);
                
            case "C++":
                return getCppWrappedCode(userCode, cell.customOutputFilename, tracker);
                
            case "CUDA":
                return getCudaWrappedCode(userCode, cell.customOutputFilename, tracker);
                
            case "Nim":
                return getNimWrappedCode(userCode, cell.customOutputFilename, tracker);
                
            case "Futhark C":
            case "Futhark Multicore":
            case "Futhark CUDA":
            case "Futhark OpenCL":
                String backend = language.substring(8).toLowerCase();
                if (backend.equals("c")) backend = "c";
                return getFutharkWrappedCode(userCode, backend, cell.customOutputFilename, tracker);
                
            case "Java":
                return getJavaWrappedCode(userCode, cell.customOutputFilename, tracker);
                
            case "R":
                return getRWrappedCode(userCode, tracker);
                
            default:
                return userCode;
        }
    }

    
    private String getPythonWrappedCode(String userCode, ImportTracker tracker) {
        StringBuilder code = new StringBuilder();
        
        
        if (!tracker.hasPythonBaseImports) {
            code.append("import os\n");
            code.append("import sys\n");
            code.append("import io\n");
            code.append("import base64\n");
            code.append("from IPython.display import display, HTML\n");
            code.append("\n");
            tracker.hasPythonBaseImports = true;
        }
        
        
        if (!tracker.hasPandasConfig) {
            code.append("# Configure pandas to show nice tables\n");
            code.append("try:\n");
            code.append("    import pandas as pd\n");
            code.append("    pd.set_option('display.max_rows', 30)\n");
            code.append("    pd.set_option('display.max_columns', 20)\n");
            code.append("except ImportError:\n");
            code.append("    pass\n");
            code.append("\n");
            tracker.hasPandasConfig = true;
        }
        
        
        if (!tracker.hasPrintDfFunction) {
            code.append("# Function to print HTML table for DataFrames\n");
            code.append("def print_df(df):\n");
            code.append("    if 'pd' in globals() and isinstance(df, pd.DataFrame):\n");
            code.append("        print(df.to_html(border=1))\n");
            code.append("    else:\n");
            code.append("        print(df)\n");
            code.append("\n");
            tracker.hasPrintDfFunction = true;
        }
        
        
        
        
        
        code.append("# User code\n");
        code.append(userCode);
        
        return code.toString();
    }

    
    private String getCppWrappedCode(String cppCode, String customFilename, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        
        
        if (!tracker.hasCppImports) {
            wrapper.append("import os\n");
            wrapper.append("import subprocess\n");
            wrapper.append("import hashlib\n\n");
            tracker.hasCppImports = true;
        }
        
        
        String filename;
        if (!customFilename.isEmpty()) {
            filename = customFilename;
            if (!filename.endsWith(".cpp")) {
                filename = filename + ".cpp";
            }
            wrapper.append("# Using custom filename\n");
            wrapper.append("cpp_filename = '").append(filename).append("'\n");
        } else {
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(cppCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("cpp_filename = f'cell_{cell_hash}.cpp'\n");
        }
        
        
        wrapper.append("# Use current working directory\n");
        wrapper.append("current_dir = os.getcwd()\n");
        wrapper.append("cpp_file_path = os.path.join(current_dir, cpp_filename)\n");
        wrapper.append("\n");
        
        
        wrapper.append("# Write C++ code to file\n");
        wrapper.append("with open(cpp_file_path, 'w') as cpp_file:\n");
        wrapper.append("    cpp_file.write('''").append(cppCode).append("''')\n");
        wrapper.append("    print(f\"C++ code written to {cpp_file_path}\")\n\n");
        
        wrapper.append("# Compile the C++ code\n");
        wrapper.append("output_path = cpp_file_path.replace('.cpp', '')\n");
        wrapper.append("compile_cmd = ['g++', cpp_file_path, '-o', output_path, '-std=c++17']\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"C++ code compiled successfully to {output_path}\")\n");
        wrapper.append("        print(f\"To run: {output_path}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String getCudaWrappedCode(String cudaCode, String customFilename, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        
        if (!tracker.hasCudaImports) {
            wrapper.append("import os\n");
            wrapper.append("import subprocess\n");
            wrapper.append("import hashlib\n\n");
            tracker.hasCudaImports = true;
        }
        
        
        String filename;
        if (!customFilename.isEmpty()) {
            filename = customFilename;
            if (!filename.endsWith(".cu")) {
                filename = filename + ".cu";
            }
            wrapper.append("# Using custom filename\n");
            wrapper.append("cuda_filename = '").append(filename).append("'\n");
        } else {
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(cudaCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("cuda_filename = f'cell_{cell_hash}.cu'\n");
        }
        
        
        wrapper.append("# Use current working directory\n");
        wrapper.append("current_dir = os.getcwd()\n");
        wrapper.append("cuda_file_path = os.path.join(current_dir, cuda_filename)\n");
        wrapper.append("\n");
        
        
        wrapper.append("# Write CUDA code to file\n");
        wrapper.append("with open(cuda_file_path, 'w') as cuda_file:\n");
        wrapper.append("    cuda_file.write('''").append(cudaCode).append("''')\n");
        wrapper.append("    print(f\"CUDA code written to {cuda_file_path}\")\n\n");
        
        wrapper.append("# Compile the CUDA code\n");
        wrapper.append("output_path = cuda_file_path.replace('.cu', '')\n");
        wrapper.append("compile_cmd = ['nvcc', cuda_file_path, '-o', output_path]\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"CUDA code compiled successfully to {output_path}\")\n");
        wrapper.append("        print(f\"To run: {output_path}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String getNimWrappedCode(String nimCode, String customFilename, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        
        if (!tracker.hasNimImports) {
            wrapper.append("import os\n");
            wrapper.append("import subprocess\n");
            wrapper.append("import hashlib\n");
            wrapper.append("import importlib.util\n");
            wrapper.append("import sys\n");
            wrapper.append("import platform\n\n");
            
            wrapper.append("def get_lib_ext():\n");
            wrapper.append("    \"\"\"Get the correct library extension for the current platform.\"\"\"\n");
            wrapper.append("    system = platform.system().lower()\n");
            wrapper.append("    if system == 'windows':\n");
            wrapper.append("        return 'pyd'\n");
            wrapper.append("    elif system == 'darwin':\n");
            wrapper.append("        return 'dylib'\n");
            wrapper.append("    else:\n");
            wrapper.append("        return 'so'\n\n");
            
            tracker.hasNimImports = true;
        }
        
        
        if (!tracker.hasNimCompileFunction) {
            wrapper.append("def compile_and_import_nim(nim_code, module_name, work_dir):\n");
            wrapper.append("    os.chdir(work_dir)\n");
            wrapper.append("    print(f\"Working directory set to: {work_dir}\")\n\n");
            wrapper.append("    nim_file_path = os.path.join(work_dir, f'{module_name}.nim')\n");
            wrapper.append("    lib_file_path = os.path.join(work_dir, f'{module_name}.{get_lib_ext()}')\n\n");
            wrapper.append("    print(f\"Nim source file: {nim_file_path}\")\n");
            wrapper.append("    print(f\"Output library will be: {lib_file_path}\")\n\n");
            wrapper.append("    with open(nim_file_path, 'w') as nim_file:\n");
            wrapper.append("        nim_file.write(nim_code)\n");
            wrapper.append("    print(f\"Nim code written to {nim_file_path}\")\n\n");
            wrapper.append("    compile_cmd = ['nim', 'c', '-d:release', '--opt:speed', '--app:lib', '--threads:on', f'--out:{lib_file_path}', nim_file_path]\n");
            wrapper.append("    try:\n");
            wrapper.append("        print(\"Compiling Nim code as Python library...\")\n");
            wrapper.append("        compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
            wrapper.append("        if compile_process.returncode != 0:\n");
            wrapper.append("            print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
            wrapper.append("            return None\n");
            wrapper.append("        else:\n");
            wrapper.append("            print(f\"Nim library compiled successfully\")\n");
            wrapper.append("            if compile_process.stderr:\n");
            wrapper.append("                print(f\"Compiler messages:\\n{compile_process.stderr}\")\n\n");
            wrapper.append("        if not os.path.exists(lib_file_path):\n");
            wrapper.append("            print(f\"Error: Compiled library not found at {lib_file_path}\")\n");
            wrapper.append("            return None\n\n");
            wrapper.append("        print(f\"Loading Nim library from {lib_file_path}\")\n\n");
            wrapper.append("        try:\n");
            wrapper.append("            if module_name in sys.modules:\n");
            wrapper.append("                del sys.modules[module_name]\n\n");
            wrapper.append("            spec = importlib.util.spec_from_file_location(module_name, lib_file_path)\n");
            wrapper.append("            if spec is None:\n");
            wrapper.append("                print(f\"Error: Failed to create module spec for {lib_file_path}\")\n");
            wrapper.append("                return None\n\n");
            wrapper.append("            nim_module = importlib.util.module_from_spec(spec)\n");
            wrapper.append("            spec.loader.exec_module(nim_module)\n\n");
            wrapper.append("            print(f\"Successfully imported Nim module '{module_name}'\")\n");
            wrapper.append("            print(f\"Available functions: {[f for f in dir(nim_module) if not f.startswith('_')]}\")\n");
            wrapper.append("            sys.modules[module_name] = nim_module\n");
            wrapper.append("            print(f\"\\nThe module '{module_name}' is now globally available for import in other cells.\")\n");
            wrapper.append("            return nim_module\n");
            wrapper.append("        except Exception as e:\n");
            wrapper.append("            print(f\"Error importing Nim module: {str(e)}\")\n");
            wrapper.append("            import traceback\n");
            wrapper.append("            traceback.print_exc()\n");
            wrapper.append("            return None\n");
            wrapper.append("    except Exception as e:\n");
            wrapper.append("        print(f\"Error: {str(e)}\")\n");
            wrapper.append("        return None\n\n");
            tracker.hasNimCompileFunction = true;
        }
        
        
        if (!customFilename.isEmpty()) {
            String moduleName = customFilename;
            if (moduleName.contains(".")) {
                moduleName = moduleName.substring(0, moduleName.lastIndexOf('.'));
            }
            wrapper.append("# Using custom module name\n");
            wrapper.append("module_name = '").append(moduleName).append("'\n");
        } else {
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(nimCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("module_name = f'nimlib_{cell_hash}'\n");
        }
        
        
        wrapper.append("target_dir = os.getcwd()\n\n");
        
        
        wrapper.append("nim_code = '''\n");
        if (!nimCode.contains("import nimpy")) {
            wrapper.append("import nimpy\n");
        }
        wrapper.append(nimCode).append("\n'''\n\n");
        
        wrapper.append("# Execute the compile and import function\n");
        wrapper.append("nim_module = compile_and_import_nim(nim_code, module_name, target_dir)\n");
        
        return wrapper.toString();
    }

    private String getFutharkWrappedCode(String futharkCode, String backend, String customFilename, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        String safeFutharkCode = futharkCode.replace("\\", "\\\\");
        
        if (!tracker.hasFutharkImports) {
            wrapper.append("import os\n");
            wrapper.append("import subprocess\n");
            wrapper.append("import hashlib\n");
            wrapper.append("import numpy as np\n");
            wrapper.append("import importlib.util\n");
            wrapper.append("import sys\n\n");
            tracker.hasFutharkImports = true;
        }
        
        
        if (!customFilename.isEmpty()) {
            String moduleName = customFilename;
            if (moduleName.contains(".")) {
                moduleName = moduleName.substring(0, moduleName.lastIndexOf('.'));
            }
            wrapper.append("# Using custom module name\n");
            wrapper.append("module_name = '").append(moduleName).append("'\n");
        } else {
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(safeFutharkCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("module_name = f'futhark_cell_{cell_hash}'\n");
        }
        wrapper.append("\n");
        
        
        wrapper.append("# Use current working directory\n");
        wrapper.append("current_dir = os.getcwd()\n");
        wrapper.append("fut_file_path = os.path.join(current_dir, f'{module_name}.fut')\n\n");
        
        
        wrapper.append("# Write Futhark code to file\n");
        wrapper.append("with open(fut_file_path, 'w') as fut_file:\n");
        wrapper.append("    fut_file.write('''").append(safeFutharkCode).append("''')\n");
        wrapper.append("print(f\"Futhark code written to {fut_file_path}\")\n\n");
        
        wrapper.append("# Compile the Futhark code with the ").append(backend).append(" backend\n");
        wrapper.append("compile_cmd = ['futhark', '").append(backend).append("', '--library', fut_file_path]\n");
        wrapper.append("print(f\"Compiling with command: {' '.join(compile_cmd)}\")\n\n");
        
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"Futhark code compiled successfully with ").append(backend).append(" backend\")\n");
        wrapper.append("        \n");
        wrapper.append("        # Build the Python FFI wrapper\n");
        wrapper.append("        build_cmd = ['build_futhark_ffi', module_name]\n");
        wrapper.append("        print(f\"Building FFI wrapper with: {' '.join(build_cmd)}\")\n");
        wrapper.append("        \n");
        wrapper.append("        build_process = subprocess.run(build_cmd, capture_output=True, text=True, cwd=current_dir)\n");
        wrapper.append("        if build_process.returncode != 0:\n");
        wrapper.append("            print(f\"FFI build error:\\n{build_process.stderr}\")\n");
        wrapper.append("        else:\n");
        wrapper.append("            print(f\"FFI wrapper built successfully\")\n");
        wrapper.append("            \n");
        wrapper.append("            # Try to import and use the module\n");
        wrapper.append("            try:\n");
        wrapper.append("                if current_dir not in sys.path:\n");
        wrapper.append("                    sys.path.insert(0, current_dir)\n");
        wrapper.append("                \n");
        wrapper.append("                ffi_module = importlib.import_module(f'_{module_name}')\n");
        wrapper.append("                from futhark_ffi import Futhark\n");
        wrapper.append("                futhark_instance = Futhark(ffi_module)\n");
        wrapper.append("                \n");
        wrapper.append("                print(f\"\\nAvailable Futhark functions:\")\n");
        wrapper.append("                for func_name in dir(futhark_instance):\n");
        wrapper.append("                    if not func_name.startswith('_') and callable(getattr(futhark_instance, func_name)):\n");
        wrapper.append("                        print(f\"  - {func_name}\")\n");
        wrapper.append("                \n");
        wrapper.append("                globals()[module_name] = futhark_instance\n");
        wrapper.append("                print(f\"\\nFuthark instance available as: {module_name}\")\n");
        wrapper.append("                \n");
        wrapper.append("            except ImportError as e:\n");
        wrapper.append("                print(f\"Error importing module: {e}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String getJavaWrappedCode(String javaCode, String customFilename, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        
        if (!tracker.hasJavaImports) {
            wrapper.append("import os\n");
            wrapper.append("import subprocess\n");
            wrapper.append("import hashlib\n");
            wrapper.append("import re\n\n");
            tracker.hasJavaImports = true;
        }
        
        wrapper.append("# Try to extract the class name from the code\n");
        wrapper.append("class_match = re.search(r'public\\s+class\\s+(\\w+)', '''").append(javaCode).append("''')\n");
        
        if (!customFilename.isEmpty()) {
            String className = customFilename;
            if (className.endsWith(".java")) {
                className = className.substring(0, className.lastIndexOf(".java"));
            }
            wrapper.append("# Using custom class name\n");
            wrapper.append("class_name = '").append(className).append("'\n");
        } else {
            wrapper.append("if class_match:\n");
            wrapper.append("    class_name = class_match.group(1)\n");
            wrapper.append("else:\n");
            wrapper.append("    cell_hash = hashlib.md5('''").append(javaCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("    class_name = f'Cell_{cell_hash}'\n");
        }
        
        wrapper.append("    if 'class' not in '''").append(javaCode).append("''':\n");
        wrapper.append("        wrapped_code = f'public class {class_name} {{\\n    public static void main(String[] args) {{\\n        ' + '''").append(javaCode).append("'''.replace('\\n', '\\n        ') + '\\n    }\\n}'\n");
        wrapper.append("    else:\n");
        wrapper.append("        wrapped_code = '''").append(javaCode).append("'''\n");
        
        if (!customFilename.isEmpty()) {
            wrapper.append("wrapped_code = '''").append(javaCode).append("'''\n");
        }
        
        
        wrapper.append("\n# Use current working directory\n");
        wrapper.append("current_dir = os.getcwd()\n");
        wrapper.append("java_file_path = os.path.join(current_dir, f'{class_name}.java')\n");
        
        wrapper.append("# Write Java code to file\n");
        wrapper.append("with open(java_file_path, 'w') as java_file:\n");
        wrapper.append("    java_file.write(wrapped_code)\n");
        wrapper.append("    print(f\"Java code written to {java_file_path}\")\n\n");
        
        wrapper.append("# Compile the Java code\n");
        wrapper.append("compile_cmd = ['javac', java_file_path]\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True, cwd=current_dir)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"Java code compiled successfully\")\n");
        wrapper.append("        \n");
        wrapper.append("        if 'public static void main' in wrapped_code:\n");
        wrapper.append("            print(f\"\\nRunning Java program...\\n\")\n");
        wrapper.append("            run_cmd = ['java', '-cp', current_dir, class_name]\n");
        wrapper.append("            run_process = subprocess.run(run_cmd, capture_output=True, text=True)\n");
        wrapper.append("            if run_process.stdout:\n");
        wrapper.append("                print(run_process.stdout)\n");
        wrapper.append("            if run_process.stderr:\n");
        wrapper.append("                print(\"Errors:\", run_process.stderr)\n");
        wrapper.append("        else:\n");
        wrapper.append("            print(f\"To run: java -cp '{current_dir}' {class_name}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String getRWrappedCode(String rCode, ImportTracker tracker) {
        StringBuilder wrapper = new StringBuilder();
        
        if (!tracker.hasRImports) {
            wrapper.append("import subprocess\n");
            wrapper.append("import tempfile\n");
            wrapper.append("import os\n\n");
            tracker.hasRImports = true;
        }
        
        wrapper.append("# Create a temporary R script file\n");
        wrapper.append("with tempfile.NamedTemporaryFile(mode='w', suffix='.R', delete=False) as f:\n");
        wrapper.append("    f.write('''").append(rCode).append("''')\n");
        wrapper.append("    r_script_path = f.name\n\n");
        
        wrapper.append("try:\n");
        
        wrapper.append("    # Run the R script in current directory\n");
        wrapper.append("    result = subprocess.run(['Rscript', r_script_path], \n");
        wrapper.append("                          capture_output=True, text=True, \n");
        wrapper.append("                          cwd=os.getcwd())\n");
        wrapper.append("    \n");
        wrapper.append("    if result.stdout:\n");
        wrapper.append("        print(result.stdout)\n");
        wrapper.append("    if result.stderr:\n");
        wrapper.append("        stderr_lines = result.stderr.splitlines()\n");
        wrapper.append("        error_lines = []\n");
        wrapper.append("        for line in stderr_lines:\n");
        wrapper.append("            if not (line.startswith('Loading') or \n");
        wrapper.append("                    line.startswith('Attaching') or\n");
        wrapper.append("                    'package' in line and 'was built under' in line):\n");
        wrapper.append("                error_lines.append(line)\n");
        wrapper.append("        if error_lines:\n");
        wrapper.append("            print('\\nWarnings/Errors:')\n");
        wrapper.append("            print('\\n'.join(error_lines))\n");
        wrapper.append("    \n");
        wrapper.append("    if result.returncode != 0:\n");
        wrapper.append("        print(f'\\nR script exited with code {result.returncode}')\n");
        wrapper.append("        \n");
        wrapper.append("finally:\n");
        wrapper.append("    try:\n");
        wrapper.append("        os.unlink(r_script_path)\n");
        wrapper.append("    except:\n");
        wrapper.append("        pass\n");
        
        return wrapper.toString();
    }

    
    private static class ImportTracker {
        boolean hasPythonBaseImports = false;
        boolean hasPandasConfig = false;
        boolean hasPrintDfFunction = false;
        boolean hasCppImports = false;
        boolean hasCudaImports = false;
        boolean hasNimImports = false;
        boolean hasFutharkImports = false;
        boolean hasJavaImports = false;
        boolean hasRImports = false;
        boolean hasNimCompileFunction = false;
    }

    public void setSavedFile(File file) {
        this.savedFile = file;
    }

    
    public void runAllCellsSequentially() {
        
        java.util.List<NotebookCell> cells = new java.util.ArrayList<>();
        
        
        for (Component component : cellsContainer.getComponents()) {
            if (component instanceof NotebookCell) {
                cells.add((NotebookCell) component);
            }
        }
        
        
        if (!cells.isEmpty()) {
            processNextCell(cells, 0);
        }
    }

    
    private void processNextCell(java.util.List<NotebookCell> cells, int index) {
        if (index >= cells.size()) {
            return; 
        }
        
        NotebookCell cell = cells.get(index);
        
        if (cell instanceof CodeCell) {
            
            ((CodeCell) cell).executeWithCallback(() -> {
                
                javax.swing.Timer timer = new javax.swing.Timer(500, e -> {
                    processNextCell(cells, index + 1);
                });
                timer.setRepeats(false);
                timer.start();
            });
        } else {
            
            cell.execute();
            
            
            javax.swing.Timer timer = new javax.swing.Timer(100, e -> {
                processNextCell(cells, index + 1);
            });
            timer.setRepeats(false);
            timer.start();
        }
    }
    
    public void setKernelClient(JupyterKernelClient kernelClient) {
        this.kernelClient = kernelClient;
        
        for (Component component : cellsContainer.getComponents()) {
            if (component instanceof CodeCell) {
                ((CodeCell) component).setKernelClient(kernelClient);
            }
        }
    }
    
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(File currentDirectory) {
        this.currentDirectory = currentDirectory;
        
        for (Component component : cellsContainer.getComponents()) {
            if (component instanceof CodeCell) {
                ((CodeCell) component).setCurrentDirectory(currentDirectory);
            }
        }
    }

    public void addCodeCell() {
        CodeCell cell = new CodeCell(kernelClient, currentDirectory);
        cellsContainer.add(cell);
        cellsContainer.revalidate();
        cellsContainer.repaint();
    }
    
    public void addMarkdownCell() {
        MarkdownCell cell = new MarkdownCell();
        cellsContainer.add(cell);
        cellsContainer.revalidate();
        cellsContainer.repaint();
    }
    
    
    public void navigateToPreviousCell() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        NotebookCell currentCell = null;
        
        
        if (focusOwner != null) {
            
            Container parent = focusOwner instanceof NotebookCell ? 
                              (Container)focusOwner : focusOwner.getParent();
            while (parent != null && !(parent instanceof NotebookCell)) {
                parent = parent.getParent();
            }
            if (parent instanceof NotebookCell) {
                currentCell = (NotebookCell) parent;
            }
        }
        
        
        if (currentCell == null) {
            if (cellsContainer.getComponentCount() > 0) {
                Component lastComponent = cellsContainer.getComponent(cellsContainer.getComponentCount() - 1);
                if (lastComponent instanceof NotebookCell) {
                    ((NotebookCell) lastComponent).requestFocusInEditor();
                    ensureCellVisible((NotebookCell) lastComponent);
                    return;
                }
            }
            return;
        }
        
        
        int currentIndex = -1;
        for (int i = 0; i < cellsContainer.getComponentCount(); i++) {
            if (cellsContainer.getComponent(i) == currentCell) {
                currentIndex = i;
                break;
            }
        }
        
        
        if (currentIndex > 0) {
            Component prevComponent = cellsContainer.getComponent(currentIndex - 1);
            if (prevComponent instanceof NotebookCell) {
                NotebookCell prevCell = (NotebookCell) prevComponent;
                prevCell.requestFocusInEditor();
                ensureCellVisible(prevCell);
            }
        }
    }
    
    public void navigateToNextCell() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        NotebookCell currentCell = null;
        
        
        if (focusOwner != null) {
            
            Container parent = focusOwner instanceof NotebookCell ? 
                              (Container)focusOwner : focusOwner.getParent();
            while (parent != null && !(parent instanceof NotebookCell)) {
                parent = parent.getParent();
            }
            if (parent instanceof NotebookCell) {
                currentCell = (NotebookCell) parent;
            }
        }
        
        
        if (currentCell == null) {
            if (cellsContainer.getComponentCount() > 0) {
                Component firstComponent = cellsContainer.getComponent(0);
                if (firstComponent instanceof NotebookCell) {
                    ((NotebookCell) firstComponent).requestFocusInEditor();
                    ensureCellVisible((NotebookCell) firstComponent);
                    return;
                }
            }
            return;
        }
        
        
        int currentIndex = -1;
        for (int i = 0; i < cellsContainer.getComponentCount(); i++) {
            if (cellsContainer.getComponent(i) == currentCell) {
                currentIndex = i;
                break;
            }
        }
        
        
        if (currentIndex >= 0 && currentIndex < cellsContainer.getComponentCount() - 1) {
            Component nextComponent = cellsContainer.getComponent(currentIndex + 1);
            if (nextComponent instanceof NotebookCell) {
                NotebookCell nextCell = (NotebookCell) nextComponent;
                nextCell.requestFocusInEditor();
                ensureCellVisible(nextCell);
            }
        }
    }
    
    
    private void ensureCellVisible(NotebookCell cell) {
        
        Rectangle cellBounds = cell.getBounds();
        
        
        Point cellLocation = SwingUtilities.convertPoint(
            cellsContainer, cellBounds.x, cellBounds.y, 
            SwingUtilities.getAncestorOfClass(JViewport.class, cellsContainer)
        );
        
        
        Rectangle visibleRect = new Rectangle(
            cellLocation.x, cellLocation.y, 
            cellBounds.width, cellBounds.height
        );
        
        
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
            JScrollPane.class, cellsContainer
        );
        
        if (scrollPane != null) {
            
            scrollPane.getViewport().scrollRectToVisible(visibleRect);
            
            
            scrollPane.revalidate();
            scrollPane.repaint();
        }
    }

    
    public void addCellBelowCurrent() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        NotebookCell currentCell = null;
        
        
        if (focusOwner != null) {
            Container parent = focusOwner.getParent();
            while (parent != null && !(parent instanceof NotebookCell)) {
                parent = parent.getParent();
            }
            if (parent instanceof NotebookCell) {
                currentCell = (NotebookCell) parent;
            }
        }
        
        
        if (currentCell == null) {
            addCodeCell();
            
            if (cellsContainer.getComponentCount() > 0) {
                Component lastComponent = cellsContainer.getComponent(cellsContainer.getComponentCount() - 1);
                if (lastComponent instanceof NotebookCell) {
                    ((NotebookCell) lastComponent).requestFocusInEditor();
                }
            }
            return;
        }
        
        
        int currentIndex = -1;
        for (int i = 0; i < cellsContainer.getComponentCount(); i++) {
            if (cellsContainer.getComponent(i) == currentCell) {
                currentIndex = i;
                break;
            }
        }
        
        
        if (currentIndex >= 0) {
            
            CodeCell newCell = new CodeCell(kernelClient, currentDirectory);
            
            
            cellsContainer.add(newCell, currentIndex + 1);
            cellsContainer.revalidate();
            cellsContainer.repaint();
            
            
            newCell.requestFocusInEditor();
            
            
            Rectangle cellBounds = newCell.getBounds();
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, cellsContainer);
            if (scrollPane != null) {
                scrollPane.getViewport().scrollRectToVisible(cellBounds);
            }
        }
    }

    
    public void deleteSelectedCell() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        NotebookCell currentCell = null;
        
        
        if (focusOwner != null) {
            Container parent = focusOwner.getParent();
            while (parent != null && !(parent instanceof NotebookCell)) {
                parent = parent.getParent();
            }
            if (parent instanceof NotebookCell) {
                currentCell = (NotebookCell) parent;
            }
        }
        
        
        if (currentCell == null) {
            return;
        }
        
        
        int option = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete this cell?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (option != JOptionPane.YES_OPTION) {
            return;  
        }
        
        
        int currentIndex = -1;
        for (int i = 0; i < cellsContainer.getComponentCount(); i++) {
            if (cellsContainer.getComponent(i) == currentCell) {
                currentIndex = i;
                break;
            }
        }
        
        
        if (currentIndex >= 0) {
            
            int nextFocusIndex = -1;
            if (currentIndex < cellsContainer.getComponentCount() - 1) {
                
                nextFocusIndex = currentIndex;
            } else if (currentIndex > 0) {
                
                nextFocusIndex = currentIndex - 1;
            }
            
            
            cellsContainer.remove(currentCell);
            cellsContainer.revalidate();
            cellsContainer.repaint();
            
            
            if (nextFocusIndex >= 0 && nextFocusIndex < cellsContainer.getComponentCount()) {
                Component nextComponent = cellsContainer.getComponent(nextFocusIndex);
                if (nextComponent instanceof NotebookCell) {
                    ((NotebookCell) nextComponent).requestFocusInEditor();
                }
            }
        }
    }

    public void runSelectedCell() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        NotebookCell currentCell = null;
        
        
        if (focusOwner != null) {
            
            if (focusOwner instanceof NotebookCell) {
                currentCell = (NotebookCell) focusOwner;
            } else {
                
                Container parent = focusOwner.getParent();
                while (parent != null && !(parent instanceof NotebookCell)) {
                    parent = parent.getParent();
                }
                if (parent instanceof NotebookCell) {
                    currentCell = (NotebookCell) parent;
                }
            }
        }
        
        
        if (currentCell != null) {
            System.out.println("Executing cell: " + currentCell);
            currentCell.execute();
            return;
        }
        
        
        for (Component component : cellsContainer.getComponents()) {
            if (component instanceof NotebookCell && component.hasFocus()) {
                ((NotebookCell) component).execute();
                return;
            }
        }
        
        
        
        if (focusOwner instanceof RSyntaxTextArea) {
            RSyntaxTextArea editor = (RSyntaxTextArea) focusOwner;
            
            
            Container parent = editor.getParent();
            while (parent != null) {
                if (parent instanceof CodeCell) {
                    ((CodeCell) parent).execute();
                    return;
                }
                parent = parent.getParent();
            }
        }
        
        
        System.out.println("No cell found to execute. Focus owner: " + focusOwner);
    }
    
    public void save() {
        if (savedFile != null) {
            saveToFile(savedFile);
        } else {
            
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            
            
            int option = JOptionPane.showConfirmDialog(
                parentFrame,
                "This notebook hasn't been saved before. Would you like to choose a location?",
                "Save Notebook",
                JOptionPane.YES_NO_OPTION
            );
            
            if (option == JOptionPane.YES_OPTION) {
                saveAs(); 
            } else {
                
                String defaultName = "Untitled-" + System.currentTimeMillis() + ".ipynb";
                savedFile = new File(currentDirectory, defaultName);
                saveToFile(savedFile);
                
                
                Component parent = SwingUtilities.getWindowAncestor(this);
                if (parent instanceof JupyterNotebookIDE) {
                    JupyterNotebookIDE ide = (JupyterNotebookIDE) parent;
                    ide.updateTabTitle(this, defaultName);
                }
            }
        }
    }
    
    public void saveAs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".ipynb");
            }
            
            @Override
            public String getDescription() {
                return "Jupyter Notebook (*.ipynb)";
            }
        });
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getPath();
            if (!path.toLowerCase().endsWith(".ipynb")) {
                path += ".ipynb";
                file = new File(path);
            }
            savedFile = file;
            saveToFile(file);
        }
    }
    
    
    private void saveToFile(File file) {
        try {
            
            JSONObject notebook = new JSONObject();
            
            
            JSONObject metadata = new JSONObject();
            metadata.put("kernelspec", new JSONObject()
                .put("display_name", "Python 3")
                .put("language", "python")
                .put("name", "python3"));
            notebook.put("metadata", metadata);
            
            
            JSONArray cells = new JSONArray();
            Component[] components = cellsContainer.getComponents();
            for (Component component : components) {
                if (component instanceof CodeCell) {
                    CodeCell cell = (CodeCell) component;
                    
                    cells.put(cell.toJSON());
                } else if (component instanceof MarkdownCell) {
                    MarkdownCell cell = (MarkdownCell) component;
                    JSONObject cellObj = new JSONObject();
                    cellObj.put("cell_type", "markdown");
                    
                    
                    String source = cell.getMarkdownText();
                    JSONArray sourceArray = new JSONArray();
                    for (String line : source.split("\n")) {
                        sourceArray.put(line + "\n");
                    }
                    cellObj.put("source", sourceArray);
                    
                    cells.put(cellObj);
                }
            }
            notebook.put("cells", cells);
            
            
            notebook.put("nbformat", 4);
            notebook.put("nbformat_minor", 5);
            
            
            Files.write(file.toPath(), notebook.toString(2).getBytes(StandardCharsets.UTF_8));
            
            
            Component parent = SwingUtilities.getWindowAncestor(this);
            if (parent instanceof JupyterNotebookIDE) {
                JupyterNotebookIDE ide = (JupyterNotebookIDE) parent;
                ide.updateTabTitle(this, file.getName());
            }
            
            
            JOptionPane.showMessageDialog(this, 
                "Notebook saved successfully to:\n" + file.getAbsolutePath(), 
                "Save Successful", 
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error saving notebook: " + e.getMessage(), 
                "Save Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    
    public void loadFromJson(JSONObject notebook) {
        try {
            
            cellsContainer.removeAll();
            
            
            JSONArray cells = notebook.getJSONArray("cells");
            for (int i = 0; i < cells.length(); i++) {
                JSONObject cellObj = cells.getJSONObject(i);
                String cellType = cellObj.getString("cell_type");
                
                if ("code".equals(cellType)) {
                    CodeCell cell = new CodeCell(kernelClient, currentDirectory);
                
                    cell.fromJSON(cellObj);
                    cellsContainer.add(cell);
                } else if ("markdown".equals(cellType)) {
                    MarkdownCell cell = new MarkdownCell();
                    
                    
                    JSONArray sourceArray = cellObj.getJSONArray("source");
                    StringBuilder sourceText = new StringBuilder();
                    for (int j = 0; j < sourceArray.length(); j++) {
                        sourceText.append(sourceArray.getString(j));
                    }
                    
                    cell.setMarkdownText(sourceText.toString());
                    cell.execute();  
                    cellsContainer.add(cell);
                }
            }
            
            
            cellsContainer.revalidate();
            cellsContainer.repaint();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error loading notebook: " + e.getMessage(), 
                "Load Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
}


abstract class NotebookCell extends JPanel {
    protected JPanel toolBar;
    protected JPanel inputArea;
    protected JPanel outputArea;
    
    public NotebookCell() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        
        toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolBar.setBackground(new Color(13, 13, 35));  
        
        
        inputArea = new JPanel(new BorderLayout());
        inputArea.setBackground(new Color(26, 26, 46));  
        
        
        outputArea = new JPanel(new BorderLayout());
        outputArea.setBackground(new Color(26, 26, 46));  
        outputArea.setBorder(BorderFactory.createEmptyBorder(5, 40, 5, 5));
        
        add(toolBar, BorderLayout.NORTH);
        add(inputArea, BorderLayout.CENTER);
        add(outputArea, BorderLayout.SOUTH);
    }
    
    public abstract void execute();
    
    
    public void requestFocusInEditor() {
        
        requestFocus();
    }
}



class CodeCell extends NotebookCell {
    private RSyntaxTextArea codeArea;
    private JTextPane outputTextArea;
    private JupyterKernelClient kernelClient;
    private JComboBox<String> languageCombo; 
    String selectedLanguage = "Python"; 
    private File currentDirectory;
    private LatexEditorPanel latexPanel;
    private JSplitPane contentSplitPane; 
    private ErrorChecker errorChecker;
    private boolean errorCheckingEnabled = true;
    private VisualizationPanel visualPanel;
    private JSplitPane sidebarSplitPane;
    private Timer outputUpdateTimer;
    private boolean hasBeenEdited = true;
    private Timer executionTimer;
    private long startTime;
    private final DecimalFormat timerFormat = new DecimalFormat("0.0");
    private volatile boolean isExecuting = false;
    private JTextField customFilenameField;
    private JButton copyOutputButton;
    private String lastOutputText = ""; 
    String customOutputFilename = ""; 
    
    private boolean isValidFilename(String filename) {
        
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        
        
        
        if (filename.contains("/")) {
            return false;
        }
        
        
        if (filename.contains("\0")) {
            return false;
        }
        
        
        if (filename.equals(".") || filename.equals("..")) {
            return false;
        }
        
        
        
        if (filename.length() > 200) {  
            return false;
        }
        
        
        
        
        
        
        
        
        
        
        
        String shellProblematic = "*?[]{}\\|`\"'<>$&;()#!~";
        for (char c : shellProblematic.toCharArray()) {
            if (filename.indexOf(c) >= 0) {
                customFilenameField.setToolTipText("Warning: Character '" + c + "' may cause issues in shell commands");
                
                
            }
        }
        
        
        
        
        
        
        return true;
    }

    
    private class ColoredArrowComboBoxUI extends BasicComboBoxUI {
        private Color arrowColor;
        
        public ColoredArrowComboBoxUI(Color arrowColor) {
            this.arrowColor = arrowColor;
        }
        
        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton() {
                @Override
                public void paint(Graphics g) {
                    
                    g.setColor(new Color(0x171717)); 
                    g.fillRect(0, 0, getWidth(), getHeight());
                    
                    
                    Graphics2D g2 = (Graphics2D)g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int size = Math.min(w, h) / 3;
                    int x = w / 2 - size / 2;
                    int y = h / 2 - size / 2;
                    
                    g2.setColor(arrowColor);
                    int[] xPoints = {x, x + size, x + size/2};
                    int[] yPoints = {y, y, y + size};
                    g2.fillPolygon(xPoints, yPoints, 3);
                }
                
                @Override
                public void updateUI() {
                    super.updateUI();
                    
                    setBackground(new Color(0x171717));
                    setBorder(BorderFactory.createEmptyBorder());
                    setFocusPainted(false);
                }
            };
            
            button.setBackground(new Color(0x171717));
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setFocusPainted(false);
            return button;
        }
        
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            
            if (c instanceof JComboBox) {
                ((JComboBox<?>)c).setBackground(new Color(0x171717));
            }
        }
        
        public void setArrowColor(Color color) {
            this.arrowColor = color;
            
            if (arrowButton != null) {
                arrowButton.repaint();
            }
        }
    }
    
    
    private void updateComboBoxArrowColor(Color color) {
        ComboBoxUI ui = languageCombo.getUI();
        if (ui instanceof ColoredArrowComboBoxUI) {
            
            ((ColoredArrowComboBoxUI) ui).setArrowColor(color);
        } else {
            
            ColoredArrowComboBoxUI newUI = new ColoredArrowComboBoxUI(color);
            languageCombo.setUI(newUI);
        }
        
        
        languageCombo.setBackground(new Color(0x171717));
        
        
        UIManager.put("ComboBox.background", new Color(0x171717));
        UIManager.put("ComboBox.selectionBackground", new Color(0x272727));
        
        languageCombo.repaint();
    }

    @Override
    public void requestFocusInEditor() {
        
        if (codeArea != null && codeArea.isVisible()) {
            codeArea.requestFocus();
        } else {
            super.requestFocusInEditor();
        }
    }

    
    public void markAsEdited() {
        hasBeenEdited = true;
    }

    
    public void performSequentialErrorCheck(Runnable callback) {
        
        if (!hasBeenEdited) {
            System.out.println("Cell has not been edited, skipping check");
            if (callback != null) {
                callback.run();
            }
            return;
        }
        
        System.out.println("Performing error check on edited cell");
        hasBeenEdited = false; 
        
        
        if (errorChecker != null && errorChecker.parser != null) {
            
            codeArea.removeParser(errorChecker.parser);
            
            codeArea.addParser(errorChecker.parser);
            
            errorChecker.clearErrors();
        }
        
        
        if (errorChecker != null && errorCheckingEnabled) {
            errorChecker.checkErrors();
            codeArea.forceReparsing(errorChecker.parser);
            codeArea.repaint();
        }
        
        
        Timer timer = new Timer(100, e -> {
            if (callback != null) {
                callback.run();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void updateErrorChecker() {
        
        if (errorChecker != null) {
            errorChecker.dispose();
            errorChecker = null;
        }
        
        
        if (errorChecker != null && errorChecker.parser != null) {
            errorChecker.clearErrors();
        }
        
        
        if (errorCheckingEnabled) {
            switch (selectedLanguage) {
                case "Python":
                    errorChecker = new PythonFlake8Checker(codeArea, kernelClient);
                    break;
                case "Nim":
                    errorChecker = new NimChecker(codeArea, kernelClient);
                    break;
                case "Java":
                    errorChecker = new JavaChecker(codeArea, kernelClient);
                    break;
                case "R":
                    errorChecker = new RChecker(codeArea, kernelClient);
                    break;
                case "Futhark C":
                case "Futhark Multicore":
                case "Futhark CUDA":
                case "Futhark OpenCL":
                    errorChecker = new FutharkChecker(codeArea, kernelClient);
                    break;
                default:
                    
                    break;
            }
            
            
            if (errorChecker != null) {
                Timer initialCheckTimer = new Timer(1000, e -> {
                    try {
                        errorChecker.safeCheckErrors();
                    } catch (Exception ex) {
                        System.err.println("Error during initial syntax check: " + ex.getMessage());
                    }
                });
                initialCheckTimer.setRepeats(false);
                initialCheckTimer.start();
            }
        }
    }
    
    
    public void setErrorCheckingEnabled(boolean enabled) {
        this.errorCheckingEnabled = enabled;
        if (errorChecker != null) {
            errorChecker.setCheckingEnabled(enabled);
        }
        
        if (!enabled) {
            
            if (errorChecker != null) {
                errorChecker.clearErrors();
            }
        } else {
            
            updateErrorChecker();
        }
    }
    
    
    private boolean errorCheckQueued = false;

    public void checkErrorsNow() {
        if (errorChecker != null && errorCheckingEnabled && !errorCheckQueued) {
            
            Container parent = getParent();
            while (parent != null && !(parent instanceof NotebookPanel)) {
                parent = parent.getParent();
            }
            
            if (parent instanceof NotebookPanel) {
                
                errorCheckQueued = true;
                
                
                ((NotebookPanel) parent).queueErrorCheck(this);
                
                
                Timer resetTimer = new Timer(1000, e -> errorCheckQueued = false);
                resetTimer.setRepeats(false);
                resetTimer.start();
            } else {
                
                errorChecker.checkErrors();
            }
        }
    }
    
    
    private void addErrorCheckButton() {
        JButton checkErrorsButton = new JButton("Check");
        checkErrorsButton.setBackground(new Color(0x171717));
        checkErrorsButton.setForeground(new Color(0x4080FF)); 
        checkErrorsButton.setToolTipText("Check for errors now");
        checkErrorsButton.addActionListener(e -> checkErrorsNow());
        
        
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(checkErrorsButton);
    }

    public void setSelectedLanguage(String language) {
        selectedLanguage = language;
        languageCombo.setSelectedItem(language);
        updateSyntaxHighlighting();
    }

    public JSONObject toJSON() {
        JSONObject cellObj = new JSONObject();
        cellObj.put("cell_type", "code");
        cellObj.put("execution_count", (Object)null);
        
        
        JSONObject metadata = new JSONObject();
        metadata.put("language", selectedLanguage);
        if (!customOutputFilename.isEmpty()) {
            metadata.put("custom_output_filename", customOutputFilename);
        }
        cellObj.put("metadata", metadata);
        
        
        String source = getCodeText();
        JSONArray sourceArray = new JSONArray();
        for (String line : source.split("\n")) {
            sourceArray.put(line + "\n");
        }
        cellObj.put("source", sourceArray);
        
        
        cellObj.put("outputs", new JSONArray());
        
        return cellObj;
    }

    
    public CodeCell(JupyterKernelClient kernelClient) {
        this(kernelClient, new File(System.getProperty("user.home")));
    }
    
    private void enhanceHorizontalScrolling() {
        
        codeArea.setLineWrap(false);
        
        
        RTextScrollPane scrollPane = (RTextScrollPane) SwingUtilities.getAncestorOfClass(RTextScrollPane.class, codeArea);
        
        if (scrollPane != null) {
            
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            
            
            JViewport viewport = scrollPane.getViewport();
            if (viewport != null) {
                
                viewport.setView(null); 
                
                
                codeArea.setPreferredSize(null); 
                
                
                viewport.setView(codeArea);
            }
            
            
            final JScrollBar hBar = scrollPane.getHorizontalScrollBar();
            if (hBar != null) {
                
                hBar.setUnitIncrement(16);
                hBar.setBlockIncrement(256);
                
                
                hBar.setUI(new BasicScrollBarUI() {
                    @Override
                    public void paint(Graphics g, JComponent c) {
                        
                        boolean scrollNeeded = hBar.getVisibleAmount() < hBar.getMaximum();
                        if (scrollNeeded) {
                            super.paint(g, c);
                        }
                    }
                    
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(60, 60, 120);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
                
                
                codeArea.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) { checkScrollNeeded(); }
                    @Override
                    public void removeUpdate(DocumentEvent e) { checkScrollNeeded(); }
                    @Override
                    public void changedUpdate(DocumentEvent e) { checkScrollNeeded(); }
                    
                    private void checkScrollNeeded() {
                        SwingUtilities.invokeLater(() -> {
                            
                            int textWidth = getActualTextWidth();
                            int viewportWidth = scrollPane.getViewport().getWidth();
                            
                            
                            boolean scrollNeeded = textWidth > viewportWidth;
                            hBar.setVisible(scrollNeeded);
                            
                            
                            if (scrollNeeded) {
                                codeArea.setPreferredSize(new Dimension(Math.max(textWidth, 3000), codeArea.getPreferredSize().height));
                            } else {
                                codeArea.setPreferredSize(new Dimension(viewportWidth, codeArea.getPreferredSize().height));
                            }
                            
                            
                            scrollPane.revalidate();
                            scrollPane.repaint();
                        });
                    }
                    
                    private int getActualTextWidth() {
                        
                        int maxWidth = 0;
                        try {
                            for (int i = 0; i < codeArea.getLineCount(); i++) {
                                int start = codeArea.getLineStartOffset(i);
                                int end = codeArea.getLineEndOffset(i);
                                String lineText = codeArea.getText(start, end - start);
                                
                                
                                FontMetrics fm = codeArea.getFontMetrics(codeArea.getFont());
                                int lineWidth = fm.stringWidth(lineText) + 20; 
                                maxWidth = Math.max(maxWidth, lineWidth);
                            }
                        } catch (Exception ex) {
                            
                            maxWidth = 500;
                        }
                        
                        return maxWidth;
                    }
                });
                
                
                SwingUtilities.invokeLater(() -> {
                    int textWidth = 0;
                    try {
                        for (int i = 0; i < codeArea.getLineCount(); i++) {
                            int start = codeArea.getLineStartOffset(i);
                            int end = codeArea.getLineEndOffset(i);
                            String lineText = codeArea.getText(start, end - start);
                            FontMetrics fm = codeArea.getFontMetrics(codeArea.getFont());
                            int lineWidth = fm.stringWidth(lineText) + 100;
                            textWidth = Math.max(textWidth, lineWidth);
                        }
                    } catch (Exception ex) {
                        textWidth = 500;
                    }
                    
                    int viewportWidth = scrollPane.getViewport().getWidth();
                    boolean scrollNeeded = textWidth > viewportWidth;
                    hBar.setVisible(scrollNeeded);
                    
                    if (scrollNeeded) {
                        codeArea.setPreferredSize(new Dimension(Math.max(textWidth, 3000), codeArea.getPreferredSize().height));
                    } else {
                        codeArea.setPreferredSize(new Dimension(viewportWidth, codeArea.getPreferredSize().height));
                    }
                    
                    scrollPane.revalidate();
                    scrollPane.repaint();
                });
            }
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        
        SwingUtilities.invokeLater(() -> enhanceHorizontalScrolling());
    }

    public CodeCell(JupyterKernelClient kernelClient, File currentDirectory) {
        super();
        this.kernelClient = kernelClient;
        this.currentDirectory = currentDirectory;
        
        
        codeArea = new RSyntaxTextArea();
        codeArea.setLineWrap(false);
        codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON); 
        codeArea.setCodeFoldingEnabled(true);
        codeArea.setAntiAliasingEnabled(true);
        
        codeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markAsEdited();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                markAsEdited();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                markAsEdited();
            }
        });

        
        codeArea.addKeyListener(new KeyAdapter() {
            private Timer debounceTimer = new Timer(1000, e -> checkErrorsNow());
            
            @Override
            public void keyReleased(KeyEvent e) {
                
                if (debounceTimer.isRunning()) {
                    debounceTimer.stop();
                }
                debounceTimer.start();
            }
        });
        
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(
                "/org/fife/ui/rsyntaxtextarea/themes/eclipse.xml"));
            theme.apply(codeArea);
            codeArea.setCurrentLineHighlightColor(new Color(20, 20, 80));
            
            
            applyStandardColorScheme();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        codeArea.setBackground(new Color(13, 13, 35));  
        codeArea.setForeground(Color.WHITE);
        codeArea.setCaretColor(Color.WHITE);
        codeArea.setSelectionColor(new Color(50, 50, 100));
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        
        
        RTextScrollPane codeScrollPane = new RTextScrollPane(codeArea);
        codeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        codeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        codeScrollPane.setBackground(new Color(13, 13, 35));
        
        
        codeScrollPane.getGutter().setBackground(new Color(13, 13, 35));
        codeScrollPane.getGutter().setBorderColor(new Color(13, 13, 35));
        
        
        latexPanel = new LatexEditorPanel(this);
        
        
        visualPanel = new VisualizationPanel();
        
        
        sidebarSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, latexPanel, visualPanel);
        sidebarSplitPane.setResizeWeight(0.5); 
        sidebarSplitPane.setDividerSize(4);
        sidebarSplitPane.setBorder(null);
        sidebarSplitPane.setBackground(new Color(13, 13, 35));
        
        
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codeScrollPane, sidebarSplitPane);
        contentSplitPane.setResizeWeight(0.7); 
        contentSplitPane.setDividerSize(4);
        contentSplitPane.setBorder(null);
        contentSplitPane.setBackground(new Color(13, 13, 35));
    
        
        inputArea.add(contentSplitPane, BorderLayout.CENTER);
        
        
        JScrollBar hBar = codeScrollPane.getHorizontalScrollBar();
        if (hBar != null) {
            hBar.setBackground(new Color(13, 13, 35));
        }
        
        
        int lineHeight = codeArea.getFontMetrics(codeArea.getFont()).getHeight();
        
        int fixedHeight = 13 * lineHeight + 4; 
        
        
        Dimension fixedSize = new Dimension(
            (codeArea.getWidth() > 0 ? codeArea.getWidth() : 600),
            fixedHeight
        );
        
        codeArea.setPreferredSize(fixedSize);
        codeArea.setMinimumSize(new Dimension(0, fixedHeight));
        
        inputArea.setPreferredSize(new Dimension(
            inputArea.getWidth() > 0 ? inputArea.getWidth() : 600,
            fixedHeight
        ));
        inputArea.setMinimumSize(new Dimension(0, fixedHeight));
        
        
        codeArea.revalidate();
        inputArea.revalidate();
        
        
        codeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateHeightWithMinimum(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateHeightWithMinimum(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateHeightWithMinimum(); }
            
            private void updateHeightWithMinimum() {
                
                int lines = codeArea.getLineCount();
                int lineHeight = codeArea.getFontMetrics(codeArea.getFont()).getHeight();
                
                
                lines = Math.max(13, lines);
                
                int exactHeight = lines * lineHeight + 4; 
                
                
                Dimension size = new Dimension(
                    (codeArea.getWidth() > 0 ? codeArea.getWidth() : 600),
                    exactHeight
                );
                
                
                codeArea.setPreferredSize(size);
                codeArea.setMinimumSize(new Dimension(0, exactHeight));
                codeArea.setMaximumSize(new Dimension(Short.MAX_VALUE, exactHeight)); 
                
                
                inputArea.setPreferredSize(new Dimension(
                    inputArea.getWidth() > 0 ? inputArea.getWidth() : 600,
                    exactHeight
                ));
                inputArea.setMinimumSize(new Dimension(0, exactHeight));
                inputArea.setMaximumSize(new Dimension(Short.MAX_VALUE, exactHeight));
                
                
                codeArea.revalidate();
                inputArea.revalidate();
            }
        });
    
        
        JButton runButton = new JButton("Run");
        runButton.setBackground(new Color(0x171717));
        runButton.setForeground(Color.GREEN);
        runButton.addActionListener(e -> {
            
            execute();
        });
        
        
        outputTextArea = new JTextPane();
        outputTextArea.setEditable(false);
        outputTextArea.setBackground(new Color(10, 10, 30));
        outputTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        outputTextArea.setFocusable(true);

        
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputScrollPane.setFocusTraversalKeysEnabled(false);
        outputTextArea.setFocusTraversalKeysEnabled(false);
        outputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        outputScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        outputScrollPane.setBackground(new Color(13, 13, 35));

        
        JScrollBar outHBar = outputScrollPane.getHorizontalScrollBar();
        if (outHBar != null) {
            outHBar.setBackground(new Color(13, 13, 35));
        }

        
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBackground(new Color(10, 10, 30));

        
        copyOutputButton = new JButton("Copy");
        copyOutputButton.setBackground(new Color(0x171717));
        copyOutputButton.setForeground(new Color(0x4EC9B0)); 
        copyOutputButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        copyOutputButton.setBorderPainted(true);
        copyOutputButton.setFocusPainted(false);
        copyOutputButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
            BorderFactory.createEmptyBorder(5, 32, 5, 32) 
        ));
        copyOutputButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyOutputButton.setToolTipText("Copy output to clipboard");
        copyOutputButton.setVisible(false); 
        copyOutputButton.addActionListener(e -> copyOutputToClipboard());

        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        buttonPanel.setOpaque(false);
        buttonPanel.add(copyOutputButton);

        
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(300, 200));
        layeredPane.setLayout(new LayoutManager() {
            @Override
            public void addLayoutComponent(String name, Component comp) {}
            @Override
            public void removeLayoutComponent(Component comp) {}
            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return parent.getSize();
            }
            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(100, 50);
            }
            @Override
            public void layoutContainer(Container parent) {
                Insets insets = parent.getInsets();
                int w = parent.getWidth() - insets.left - insets.right;
                int h = parent.getHeight() - insets.top - insets.bottom;
                
                
                outputScrollPane.setBounds(insets.left, insets.top, w, h);
                
                
                Dimension buttonPanelSize = buttonPanel.getPreferredSize();
                buttonPanel.setBounds(
                    insets.left + w - buttonPanelSize.width - 5,
                    insets.top + 5,
                    buttonPanelSize.width,
                    buttonPanelSize.height
                );
            }
        });

        layeredPane.add(outputScrollPane, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(buttonPanel, JLayeredPane.PALETTE_LAYER);

        
        outputArea.add(layeredPane, BorderLayout.CENTER);
        
        
        String[] cellTypes = {"Code", "Markdown"};
        JComboBox<String> cellTypeCombo = new JComboBox<>(cellTypes);
        cellTypeCombo.setBackground(new Color(0x171717));
        cellTypeCombo.setForeground(Color.WHITE);
        cellTypeCombo.setOpaque(true);
        
        
        cellTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                       boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    c.setBackground(new Color(0x272727));
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(new Color(0x171717));
                    c.setForeground(Color.WHITE);
                }
                return c;
            }
        });
        
        
        String[] languages = {"Python", "C++", "CUDA", "Nim", "Futhark C", "Futhark Multicore", "Futhark CUDA", "Futhark OpenCL", "Java", "R"};
        languageCombo = new JComboBox<>(languages);
        UIManager.put("ComboBox.background", new Color(0x171717));
        UIManager.put("ComboBox.selectionBackground", new Color(0x272727));
        languageCombo.setBackground(new Color(0x171717));
        
        
        Color pythonYellow = new Color(0xFFD700); 
        languageCombo.setForeground(pythonYellow);
        
        
        ColoredArrowComboBoxUI customUI = new ColoredArrowComboBoxUI(pythonYellow);
        languageCombo.setUI(customUI);
        
        
        languageCombo.setBackground(new Color(0x171717));
        
        
        SwingUtilities.invokeLater(() -> {
            updateComboBoxArrowColor(pythonYellow);
        });
        
        languageCombo.setOpaque(true);
        languageCombo.setToolTipText("Select programming language");
        
        
        languageCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                
                label.setOpaque(true);
                
                
                if (isSelected) {
                    label.setBackground(new Color(0x272727));
                } else {
                    label.setBackground(new Color(0x171717));
                }
                
                
                if (value != null) {
                    String langStr = value.toString();
                    
                    
                    if (langStr.startsWith("Futhark")) {
                        String variant = langStr.substring(8); 
                        String variantColor;
                        
                        switch (variant) {
                            case "C":
                                variantColor = "#659AD2"; 
                                break;
                            case "Multicore":
                                variantColor = "#87CEEB"; 
                                break;
                            case "CUDA":
                                variantColor = "#76B900"; 
                                break;
                            case "OpenCL":
                                variantColor = "#FF4444"; 
                                break;
                            default:
                                variantColor = "#A0522D"; 
                        }
                        
                        
                        label.setText("<html><span style='color:#A0522D'>Futhark</span> <span style='color:" + 
                                    variantColor + "'>" + variant + "</span></html>");
                    } else {
                        
                        switch (langStr) {
                            case "Python":
                                label.setForeground(new Color(0xFFD700)); 
                                break;
                            case "C++":
                                label.setForeground(new Color(0x659AD2)); 
                                break;
                            case "CUDA":
                                label.setForeground(new Color(0x76B900)); 
                                break;
                            case "Nim":
                                label.setForeground(new Color(0xB8860B)); 
                                break;
                            case "Java":
                                label.setForeground(new Color(0xF89820)); 
                                break;
                            case "R":
                                label.setForeground(new Color(0x276DC3)); 
                                break;
                            default:
                                label.setForeground(Color.WHITE);
                        }
                    }
                }
                
                return label;
            }
        });
        
        
        languageCombo.addActionListener(e -> {
            selectedLanguage = (String)languageCombo.getSelectedItem();
            updateSyntaxHighlighting();
            
            
            Color languageColor;
            switch (selectedLanguage) {
                case "Python":
                    languageColor = new Color(0xFFD700); 
                    break;
                case "C++":
                    languageColor = new Color(0x659AD2); 
                    break;
                case "CUDA":
                    languageColor = new Color(0x76B900); 
                    break;
                case "Nim":
                    languageColor = new Color(0xB8860B); 
                    break;
                case "Futhark C":
                case "Futhark Multicore":
                case "Futhark CUDA":
                case "Futhark OpenCL":
                    languageColor = new Color(0xA0522D); 
                    break;
                case "Java":
                    languageColor = new Color(0xF89820); 
                    break;
                case "R":
                    languageColor = new Color(0x276DC3); 
                    break;
                default:
                    languageColor = Color.WHITE;
                    break;
            }
            
            
            languageCombo.setForeground(languageColor);
            updateComboBoxArrowColor(languageColor);
            
            
            SwingUtilities.invokeLater(() -> {
                languageCombo.setBackground(new Color(0x171717));
                languageCombo.repaint();
                updateErrorChecker();
            });
        });
        
        
        JLabel filenameLabel = new JLabel("Output:");
        filenameLabel.setForeground(new Color(180, 180, 220));
        customFilenameField = new JTextField(12);
        customFilenameField.setBackground(new Color(0x171717));
        customFilenameField.setForeground(Color.WHITE);
        customFilenameField.setCaretColor(Color.WHITE);
        customFilenameField.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
        customFilenameField.setToolTipText("Custom output filename (leave empty for auto-generated)");

        
        customFilenameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { validateFilename(); }
            @Override
            public void removeUpdate(DocumentEvent e) { validateFilename(); }
            @Override
            public void changedUpdate(DocumentEvent e) { validateFilename(); }
            
            private void validateFilename() {
                String filename = customFilenameField.getText().trim();
                
                if (filename.isEmpty() || isValidFilename(filename)) {
                    customFilenameField.setForeground(Color.WHITE);
                    customOutputFilename = filename;
                } else {
                    customFilenameField.setForeground(new Color(255, 100, 100)); 
                }
            }
        });

        JButton addCellButton = new JButton("+");
        addCellButton.setBackground(new Color(0x171717));
        addCellButton.setForeground(Color.GREEN);
        addCellButton.setToolTipText("Add cell below");
        addCellButton.addActionListener(e -> addCellBelow());
        
        JButton deleteCellButton = new JButton("×");
        deleteCellButton.setBackground(new Color(0x171717));
        deleteCellButton.setForeground(Color.RED);
        deleteCellButton.setToolTipText("Delete cell");
        deleteCellButton.addActionListener(e -> deleteCell());
    
        JButton clearOutputButton = new JButton("Clear");
        clearOutputButton.setBackground(new Color(0x171717));
        clearOutputButton.setForeground(Color.WHITE);
        clearOutputButton.setToolTipText("Clear output");
        clearOutputButton.addActionListener(e -> clearOutput());
        
        toolBar.add(runButton);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(cellTypeCombo);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(addCellButton);
        toolBar.add(deleteCellButton);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(clearOutputButton);
        addErrorCheckButton();

        
        toolBar.add(Box.createHorizontalGlue());
        
        toolBar.add(filenameLabel);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(customFilenameField);
        toolBar.add(Box.createHorizontalStrut(20));

        
        toolBar.add(languageCombo);
        
        
        enhanceHorizontalScrolling();
    }

    private void copyOutputToClipboard() {
        if (lastOutputText != null && !lastOutputText.isEmpty()) {
            try {
                
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Clipboard clipboard = toolkit.getSystemClipboard();
                
                
                StringSelection selection = new StringSelection(lastOutputText);
                
                
                clipboard.setContents(selection, null);
                
                
                String originalText = copyOutputButton.getText();
                copyOutputButton.setText("Copied!");
                copyOutputButton.setForeground(new Color(0x4EC9B0)); 
                
                
                Timer resetTimer = new Timer(1000, e -> {
                    copyOutputButton.setText(originalText);
                    copyOutputButton.setForeground(new Color(0x4EC9B0)); 
                });
                resetTimer.setRepeats(false);
                resetTimer.start();
                
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error copying to clipboard: " + e.getMessage(), 
                    "Copy Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void applyStandardColorScheme() {
        SyntaxScheme scheme = codeArea.getSyntaxScheme();
        
        
        Color purpleKeyword = new Color(0xC586C0);    
        Color tanFunction = new Color(0xDCDCAA);      
        Color blueTeal = new Color(0x4EC9B0);         
        Color lightBlue = new Color(0x9CDCFE);        
        Color salmon = new Color(0xCE9178);           
        Color green = new Color(0x6A9955);            
        Color lightGreen = new Color(0xB5CEA8);       
        Color gray = new Color(0xD4D4D4);             
        
        
        scheme.getStyle(Token.RESERVED_WORD).foreground = purpleKeyword;
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = purpleKeyword;
        scheme.getStyle(Token.FUNCTION).foreground = tanFunction;
        scheme.getStyle(Token.IDENTIFIER).foreground = lightBlue;
        scheme.getStyle(Token.OPERATOR).foreground = gray;
        scheme.getStyle(Token.SEPARATOR).foreground = gray;
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = salmon;
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = lightGreen;
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = lightGreen;
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = green;
        scheme.getStyle(Token.COMMENT_EOL).foreground = green;
        scheme.getStyle(Token.PREPROCESSOR).foreground = new Color(0xD16969);
        scheme.getStyle(Token.VARIABLE).foreground = lightBlue;
        scheme.getStyle(Token.DATA_TYPE).foreground = blueTeal;
        
        
        codeArea.setSyntaxScheme(scheme);
        
        
        codeArea.setBracketMatchingEnabled(true);
        codeArea.setPaintMatchedBracketPair(true);
        codeArea.setMatchedBracketBGColor(new Color(0x2D3147));
        codeArea.setMatchedBracketBorderColor(new Color(0x646464));
    }
    
    
    private void updateSyntaxHighlighting() {
        
        switch (selectedLanguage) {
            case "Python":
                codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                break;
            case "C++":
                codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
                break;
            case "CUDA":
                codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
                break;
            case "Nim":
                codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                break;
            case "Futhark C":
            case "Futhark Multicore":
            case "Futhark CUDA":
            case "Futhark OpenCL":
                
                org.fife.ui.rsyntaxtextarea.TokenMakerFactory tmf = org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance();
                if (tmf instanceof org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory) {
                    ((org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory) tmf)
                        .putMapping("text/futhark", "com.example.FutharkTokenMaker");
                }

                
                codeArea.setSyntaxEditingStyle("text/futhark");

                
                FutharkSyntaxSupport futharkSupport = new FutharkSyntaxSupport(codeArea, selectedLanguage);
                futharkSupport.installHighlighting();
                return;
            case "Java":
                codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
                customizeJavaSyntaxHighlighting();
                return;
            case "R":
                
                org.fife.ui.rsyntaxtextarea.TokenMakerFactory r_tmf = org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance(); 
                if (r_tmf instanceof org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory) { 
                    ((org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory) r_tmf) 
                        .putMapping("text/r", "com.example.RTokenMaker"); 
                } 

                
                codeArea.setSyntaxEditingStyle("text/r"); 

                
                customizeRSyntaxHighlighting();

                
                setupRAutoCompletion(); 

                return;
        }

        
        applyStandardColorScheme();

        codeArea.revalidate();
        codeArea.repaint();
    }

    
    private void customizeJavaSyntaxHighlighting() {
        SyntaxScheme scheme = codeArea.getSyntaxScheme();
        
        
        Color javaKeyword = new Color(0xC586C0);      
        Color javaType = new Color(0x4EC9B0);         
        Color javaMethod = new Color(0xDCDCAA);       
        Color javaAnnotation = new Color(0xDBD7CA);   
        Color javaString = new Color(0xCE9178);       
        Color javaNumber = new Color(0xB5CEA8);       
        Color javaComment = new Color(0x6A9955);      
        Color javaOperator = new Color(0xD4D4D4);     
        Color javaVariable = new Color(0x9CDCFE);     
        Color javaConstant = new Color(0x4FC1FF);     
        
        
        scheme.getStyle(Token.RESERVED_WORD).foreground = javaKeyword;
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = javaKeyword;
        scheme.getStyle(Token.DATA_TYPE).foreground = javaType;
        scheme.getStyle(Token.FUNCTION).foreground = javaMethod;
        scheme.getStyle(Token.IDENTIFIER).foreground = javaVariable;
        scheme.getStyle(Token.OPERATOR).foreground = javaOperator;
        scheme.getStyle(Token.SEPARATOR).foreground = javaOperator;
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = javaString;
        scheme.getStyle(Token.LITERAL_CHAR).foreground = javaString;
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = javaNumber;
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = javaNumber;
        scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL).foreground = javaNumber;
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = javaComment;
        scheme.getStyle(Token.COMMENT_EOL).foreground = javaComment;
        scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = javaComment;
        scheme.getStyle(Token.ANNOTATION).foreground = javaAnnotation;
        scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = javaKeyword;
        
        scheme.getStyle(Token.VARIABLE).foreground = javaConstant;
        scheme.getStyle(Token.PREPROCESSOR).foreground = javaAnnotation;
        scheme.getStyle(Token.ERROR_IDENTIFIER).foreground = Color.RED;
        
        
        codeArea.setSyntaxScheme(scheme);
        
        
        codeArea.setBracketMatchingEnabled(true);
        codeArea.setPaintMatchedBracketPair(true);
        codeArea.setMatchedBracketBGColor(new Color(0x3A3D5C));
        codeArea.setMatchedBracketBorderColor(new Color(0x808080));
    }

    
    private void customizeRSyntaxHighlighting() {
        SyntaxScheme scheme = codeArea.getSyntaxScheme();
        
        
        Color rKeyword = new Color(0xC586C0);        
        Color rFunction = new Color(0xDCDCAA);       
        Color rPackage = new Color(0x4EC9B0);        
        Color rOperator = new Color(0xD4D4D4);       
        Color rAssignment = new Color(0xFF8C00);     
        Color rString = new Color(0xCE9178);         
        Color rNumber = new Color(0xB5CEA8);         
        Color rComment = new Color(0x6A9955);        
        Color rVariable = new Color(0x9CDCFE);       
        Color rDataFrame = new Color(0x4FC1FF);      
        Color rBoolean = new Color(0x569CD6);        
        Color rSpecial = new Color(0xD16969);        
        
        
        scheme.getStyle(Token.RESERVED_WORD).foreground = rKeyword;
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = rKeyword;
        scheme.getStyle(Token.FUNCTION).foreground = rFunction;
        scheme.getStyle(Token.IDENTIFIER).foreground = rVariable;
        scheme.getStyle(Token.OPERATOR).foreground = rOperator;
        scheme.getStyle(Token.SEPARATOR).foreground = rOperator;
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = rString;
        scheme.getStyle(Token.LITERAL_BACKQUOTE).foreground = rString;
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = rNumber;
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = rNumber;
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = rComment;
        scheme.getStyle(Token.COMMENT_EOL).foreground = rComment;
        scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = rComment;
        scheme.getStyle(Token.VARIABLE).foreground = rDataFrame;
        scheme.getStyle(Token.PREPROCESSOR).foreground = rPackage;
        scheme.getStyle(Token.DATA_TYPE).foreground = rPackage;
        scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = rBoolean;
        scheme.getStyle(Token.ANNOTATION).foreground = rSpecial;
        scheme.getStyle(Token.ERROR_IDENTIFIER).foreground = Color.RED;
        scheme.getStyle(Token.MARKUP_TAG_DELIMITER).foreground = rAssignment;
        
        
        codeArea.setSyntaxScheme(scheme);
        
        
        codeArea.setBracketMatchingEnabled(true);
        codeArea.setPaintMatchedBracketPair(true);
        codeArea.setMatchedBracketBGColor(new Color(0x2D4671));
        codeArea.setMatchedBracketBorderColor(new Color(0x7A8A99));
        
        
        codeArea.setAutoIndentEnabled(true);
        codeArea.setCloseCurlyBraces(false); 
        codeArea.setPaintTabLines(true);
    }

    
    private void setupRAutoCompletion() {
        if (selectedLanguage.equals("R")) {
            
            DefaultCompletionProvider provider = new DefaultCompletionProvider();
            
            
            String[][] rCompletions = {
                
                {"data.frame", "data.frame(...) - Create a data frame"},
                {"read.csv", "read.csv(file, header = TRUE, sep = \",\") - Read CSV file"},
                {"write.csv", "write.csv(x, file) - Write CSV file"},
                {"subset", "subset(x, subset, select) - Subset data"},
                {"merge", "merge(x, y, by) - Merge data frames"},
                {"aggregate", "aggregate(x, by, FUN) - Aggregate data"},
                
                
                {"lm", "lm(formula, data) - Linear model"},
                {"glm", "glm(formula, family, data) - Generalized linear model"},
                {"t.test", "t.test(x, y) - t-test"},
                {"cor.test", "cor.test(x, y) - Correlation test"},
                {"anova", "anova(object) - Analysis of variance"},
                
                
                {"plot", "plot(x, y, type) - Basic plot"},
                {"hist", "hist(x, breaks) - Histogram"},
                {"boxplot", "boxplot(x) - Box plot"},
                {"barplot", "barplot(height) - Bar plot"},
                
                
                {"mutate", "mutate(.data, ...) - Add new variables"},
                {"filter", "filter(.data, ...) - Filter rows"},
                {"select", "select(.data, ...) - Select columns"},
                {"arrange", "arrange(.data, ...) - Arrange rows"},
                {"summarise", "summarise(.data, ...) - Summarise data"},
                {"group_by", "group_by(.data, ...) - Group data"},
                
                
                {"ggplot", "ggplot(data, aes()) - Initialize ggplot"},
                {"geom_point", "geom_point() - Add points"},
                {"geom_line", "geom_line() - Add lines"},
                {"geom_bar", "geom_bar() - Add bars"},
                {"geom_histogram", "geom_histogram() - Add histogram"},
                {"facet_wrap", "facet_wrap(~ variable) - Facet wrap"},
                {"theme_minimal", "theme_minimal() - Minimal theme"}
            };
            
            
            for (String[] completion : rCompletions) {
                provider.addCompletion(new BasicCompletion(provider, completion[0], completion[1]));
            }
            
            
            AutoCompletion ac = new AutoCompletion(provider);
            ac.setShowDescWindow(true);
            ac.setParameterAssistanceEnabled(true);
            ac.setAutoActivationEnabled(true);
            ac.setAutoActivationDelay(300);
            ac.install(codeArea);
        }
    }
    
    public void clearOutput() {
        
        stopExecution();
        
        
        lastOutputText = "";
        
        
        if (copyOutputButton != null) {
            copyOutputButton.setVisible(false);
        }
        
        
        setOutputText("", false);
        
        
        if (visualPanel != null) {
            visualPanel.clearVisualization();
        }
    }
    
    public void executeWithCallback(Runnable callback) {
        clearOutput();
        
        
        isExecuting = true;
        
        
        final boolean wasErrorCheckingEnabled = errorChecker != null ? errorChecker.isCheckingEnabled : false;
        if (errorChecker != null) {
            errorChecker.setCheckingEnabled(false);
        }
        
        
        setOutputText("Executing...", false);
        
        
        if (executionTimer != null) {
            executionTimer.stop();
            executionTimer = null;
        }
        
        
        startTime = System.currentTimeMillis();
        executionTimer = new Timer(100, e -> {
            if (isExecuting) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                double seconds = elapsedTime / 1000.0;
                SwingUtilities.invokeLater(() -> {
                    if (isExecuting) {
                        setOutputText("Executing... (" + timerFormat.format(seconds) + "s)", false);
                    }
                });
            } else {
                ((Timer)e.getSource()).stop();
            }
        });
        executionTimer.setRepeats(true);
        executionTimer.start();
        
        if (kernelClient != null) {
            String code = getExecutableCode();
            kernelClient.executeCode(code)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    
                    isExecuting = false;
                    
                    
                    if (executionTimer != null) {
                        executionTimer.stop();
                        executionTimer = null;
                    }
                    long executionTime = System.currentTimeMillis() - startTime;
                    double seconds = executionTime / 1000.0;
                    
                    
                    String resultWithTime = "Execution completed in " + timerFormat.format(seconds) + "s\n\n" + result;
                    setFormattedOutput(resultWithTime);
                    
                    
                    if (errorChecker != null && wasErrorCheckingEnabled) {
                        
                        Timer resumeErrorChecking = new Timer(500, evt -> {
                            errorChecker.setCheckingEnabled(true);
                        });
                        resumeErrorChecking.setRepeats(false);
                        resumeErrorChecking.start();
                    }
                    if (callback != null) {
                        callback.run();
                    }
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        
                        isExecuting = false;
                        
                        
                        if (executionTimer != null) {
                            executionTimer.stop();
                            executionTimer = null;
                        }
                        long executionTime = System.currentTimeMillis() - startTime;
                        double seconds = executionTime / 1000.0;
                        
                        
                        String errorWithTime = "Error after " + timerFormat.format(seconds) + "s: " + ex.getMessage();
                        setFormattedOutput(errorWithTime);
                        
                        
                        if (errorChecker != null && wasErrorCheckingEnabled) {
                            errorChecker.setCheckingEnabled(true);
                        }
                        if (callback != null) {
                            callback.run();
                        }
                    });
                    return null;
                });
        } else {
            
            isExecuting = false;
            
            
            if (executionTimer != null) {
                executionTimer.stop();
                executionTimer = null;
            }
            setOutputText("Error: No kernel client available", false);
            
            if (errorChecker != null && wasErrorCheckingEnabled) {
                errorChecker.setCheckingEnabled(true);
            }
            if (callback != null) {
                callback.run();
            }
        }
    }
    
    public void stopExecution() {
        isExecuting = false;
        if (executionTimer != null) {
            executionTimer.stop();
            executionTimer = null;
        }
        
    }

    
    private String getExecutableCode() {
        String userCode = codeArea.getText();
        
        switch (selectedLanguage) {
            case "Python":
                
                return "import os\n" +
                    "import sys\n" +
                    "import io\n" +
                    "import base64\n" +
                    "from IPython.display import display, HTML\n" +
                    "\n" +
                    "# Configure pandas to show nice tables\n" +
                    "try:\n" +
                    "    import pandas as pd\n" +
                    "    # Set display options for pandas\n" +
                    "    pd.set_option('display.max_rows', 30)\n" +
                    "    pd.set_option('display.max_columns', 20)\n" +
                    "except ImportError:\n" +
                    "    pass\n" +
                    "\n" +
                    "# Function to print HTML table for DataFrames\n" +
                    "def print_df(df):\n" +
                    "    if 'pd' in globals() and isinstance(df, pd.DataFrame):\n" +
                    "        print(df.to_html(border=1))\n" +
                    "    else:\n" +
                    "        print(df)\n" +
                    "\n" +
                    "# Change to the current directory\n" +
                    "os.chdir(r'" + currentDirectory.getAbsolutePath().replace("\\", "\\\\") + "')\n" +
                    "\n" +
                    "# The user's code follows\n" +
                    userCode;
                    
            case "C++":
                return generateCppWrapper(userCode);
                    
            case "CUDA":
                return generateCudaWrapper(userCode);
                
            case "Nim":
                return generateNimWrapper(userCode);
            
            case "Futhark C":
                return generateFutharkFFIWrapper(userCode, "c");
                
            case "Futhark Multicore":
                return generateFutharkFFIWrapper(userCode, "multicore");
                
            case "Futhark CUDA":
                return generateFutharkFFIWrapper(userCode, "cuda");
                
            case "Futhark OpenCL":
                return generateFutharkFFIWrapper(userCode, "opencl");
                
            case "Java":
                return generateJavaWrapper(userCode);
                
            case "R":
                return generateRWrapper(userCode);
                    
            default:
                return userCode;
        }
    }
    
    
    private boolean displayHTMLTable(String result) {
        
        if (result.contains("<table") && result.contains("</table>")) {
            
            StringBuilder html = new StringBuilder();
            html.append("<html><head><style>\n");
            html.append("body { background-color: #101035; color: white; font-family: monospace; }\n");
            html.append("table { border-collapse: collapse; margin: 10px 0; }\n");
            html.append("th { background: #2a2a55; color: white; padding: 6px 10px; text-align: left; border: 1px solid #3a3a75; }\n");
            html.append("td { padding: 6px 10px; border: 1px solid #3a3a75; }\n");
            html.append("tr:nth-child(even) { background-color: #1a1a45; }\n");
            html.append("</style></head><body>\n");
            html.append(result);
            html.append("</body></html>");
            
            
            setOutputText(html.toString(), true);
            
            
            if (visualPanel != null) {
                visualPanel.clearVisualization();
            }
            
            return true; 
        }
        return false; 
    }

    public void setCurrentDirectory(File directory) {
        this.currentDirectory = directory;
    }

    
    private String generateCppWrapper(String cppCode) {
        StringBuilder wrapper = new StringBuilder();
        wrapper.append("import os\n");
        wrapper.append("import subprocess\n");
        wrapper.append("import hashlib\n\n");
        
        
        String filename;
        if (!customOutputFilename.isEmpty()) {
            
            filename = customOutputFilename;
            if (!filename.endsWith(".cpp")) {
                filename = filename + ".cpp";
            }
            wrapper.append("# Using custom filename\n");
            wrapper.append("cpp_filename = '").append(filename).append("'\n");
        } else {
            
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(cppCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("cpp_filename = f'cell_{cell_hash}.cpp'\n");
        }
        
        
        wrapper.append("# Get the current directory path\n");
        wrapper.append("current_dir = r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("'\n");
        wrapper.append("cpp_file_path = os.path.join(current_dir, cpp_filename)\n");
        
        
        wrapper.append("# Write C++ code to file\n");
        wrapper.append("with open(cpp_file_path, 'w') as cpp_file:\n");
        wrapper.append("    cpp_file.write('''").append(cppCode).append("''')\n");
        wrapper.append("    print(f\"C++ code written to {cpp_file_path}\")\n\n");
        
        wrapper.append("# Compile the C++ code\n");
        wrapper.append("output_path = cpp_file_path.replace('.cpp', '')\n");
        wrapper.append("compile_cmd = ['g++', cpp_file_path, '-o', output_path, '-std=c++17']\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"C++ code compiled successfully to {output_path}\")\n");
        wrapper.append("        print(f\"To run: {output_path}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    
    private String generateCudaWrapper(String cudaCode) {
        StringBuilder wrapper = new StringBuilder();
        wrapper.append("import os\n");
        wrapper.append("import subprocess\n");
        wrapper.append("import hashlib\n\n");
        
        
        String filename;
        if (!customOutputFilename.isEmpty()) {
            
            filename = customOutputFilename;
            if (!filename.endsWith(".cu")) {
                filename = filename + ".cu";
            }
            wrapper.append("# Using custom filename\n");
            wrapper.append("cuda_filename = '").append(filename).append("'\n");
        } else {
            
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(cudaCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("cuda_filename = f'cell_{cell_hash}.cu'\n");
        }
        
        
        wrapper.append("# Get the current directory path\n");
        wrapper.append("current_dir = r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("'\n");
        wrapper.append("cuda_file_path = os.path.join(current_dir, cuda_filename)\n");
        
        
        wrapper.append("# Write CUDA code to file\n");
        wrapper.append("with open(cuda_file_path, 'w') as cuda_file:\n");
        wrapper.append("    cuda_file.write('''").append(cudaCode).append("''')\n");
        wrapper.append("    print(f\"CUDA code written to {cuda_file_path}\")\n\n");
        
        wrapper.append("# Compile the CUDA code\n");
        wrapper.append("output_path = cuda_file_path.replace('.cu', '')\n");
        wrapper.append("compile_cmd = ['nvcc', cuda_file_path, '-o', output_path]\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"CUDA code compiled successfully to {output_path}\")\n");
        wrapper.append("        print(f\"To run: {output_path}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String generateNimWrapper(String nimCode) {
        StringBuilder wrapper = new StringBuilder();
        wrapper.append("import os\n");
        wrapper.append("import subprocess\n");
        wrapper.append("import hashlib\n");
        wrapper.append("import importlib.util\n");
        wrapper.append("import sys\n");
        wrapper.append("import platform\n\n");
        
        
        wrapper.append("def get_lib_ext():\n");
        wrapper.append("    \"\"\"Get the correct library extension for the current platform.\"\"\"\n");
        wrapper.append("    system = platform.system().lower()\n");
        wrapper.append("    if system == 'windows':\n");
        wrapper.append("        return 'pyd'\n");
        wrapper.append("    elif system == 'darwin':\n");
        wrapper.append("        return 'dylib'\n");
        wrapper.append("    else:  # Linux and other Unix-like systems\n");
        wrapper.append("        return 'so'\n\n");

        
        wrapper.append("def compile_and_import_nim():\n");
        wrapper.append("    # Get correct working directory from Java\n");
        wrapper.append("    target_dir = r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("'\n");
        wrapper.append("    # Change to that directory first\n");
        wrapper.append("    os.chdir(target_dir)\n");
        wrapper.append("    work_dir = os.getcwd()\n");
        wrapper.append("    print(f\"Working directory set to: {work_dir}\")\n\n");
        
        
        if (!customOutputFilename.isEmpty()) {
            
            String moduleName = customOutputFilename;
            if (moduleName.contains(".")) {
                moduleName = moduleName.substring(0, moduleName.lastIndexOf('.'));
            }
            wrapper.append("    # Using custom module name\n");
            wrapper.append("    module_name = '").append(moduleName).append("'\n");
        } else {
            
            wrapper.append("    # Create a unique identifier based on cell content\n");
            wrapper.append("    cell_hash = hashlib.md5('''").append(nimCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("    module_name = f'nimlib_{cell_hash}'\n");
        }
        wrapper.append("\n");
        
        
        wrapper.append("    # Create full paths for all files\n");
        wrapper.append("    nim_file_path = os.path.join(work_dir, f'{module_name}.nim')\n");
        wrapper.append("    lib_file_path = os.path.join(work_dir, f'{module_name}.{get_lib_ext()}')\n\n");
        
        wrapper.append("    print(f\"Nim source file: {nim_file_path}\")\n");
        wrapper.append("    print(f\"Output library will be: {lib_file_path}\")\n\n");
        
        
        wrapper.append("    # Write Nim code to file with nimpy import\n");
        wrapper.append("    nim_code = '''\n");
        
        
        if (!nimCode.contains("import nimpy")) {
            wrapper.append("import nimpy\n");
        }
        
        
        wrapper.append(nimCode).append("\n'''\n\n");
        
        wrapper.append("    with open(nim_file_path, 'w') as nim_file:\n");
        wrapper.append("        nim_file.write(nim_code)\n");
        wrapper.append("    print(f\"Nim code written to {nim_file_path}\")\n\n");
        
        
        wrapper.append("    # Compile the Nim code as a Python-importable library using absolute paths\n");
        wrapper.append("    compile_cmd = ['nim', 'c', '-d:release', '--opt:speed', '--app:lib', '--threads:on', f'--out:{lib_file_path}', nim_file_path]\n");
        wrapper.append("    try:\n");
        wrapper.append("        print(\"Compiling Nim code as Python library...\")\n");
        wrapper.append("        print(f\"Command: {' '.join(compile_cmd)}\")\n");
        wrapper.append("        compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("        if compile_process.returncode != 0:\n");
        wrapper.append("            print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("            return None\n");
        wrapper.append("        else:\n");
        wrapper.append("            print(f\"Nim library compiled successfully\")\n");
        wrapper.append("            if compile_process.stderr:\n");
        wrapper.append("                print(f\"Compiler messages:\\n{compile_process.stderr}\")\n\n");
        
        
        wrapper.append("        # Verify output file exists\n");
        wrapper.append("        if not os.path.exists(lib_file_path):\n");
        wrapper.append("            print(f\"Error: Compiled library not found at {lib_file_path}\")\n");
        wrapper.append("            print(f\"Directory contents: {os.listdir(work_dir)}\")\n");
        wrapper.append("            return None\n\n");
        
        wrapper.append("        print(f\"Library file exists: {os.path.exists(lib_file_path)}\")\n");
        wrapper.append("        print(f\"Loading Nim library from {lib_file_path}\")\n\n");
        
        wrapper.append("        # Import the module\n");
        wrapper.append("        try:\n");
        wrapper.append("            # Unload existing module if it was previously loaded\n");
        wrapper.append("            if module_name in sys.modules:\n");
        wrapper.append("                del sys.modules[module_name]\n\n");
        
        wrapper.append("            # Load the module\n");
        wrapper.append("            spec = importlib.util.spec_from_file_location(module_name, lib_file_path)\n");
        wrapper.append("            if spec is None:\n");
        wrapper.append("                print(f\"Error: Failed to create module spec for {lib_file_path}\")\n");
        wrapper.append("                return None\n\n");
        
        wrapper.append("            nim_module = importlib.util.module_from_spec(spec)\n");
        wrapper.append("            spec.loader.exec_module(nim_module)\n\n");
        
        wrapper.append("            print(f\"Successfully imported Nim module '{module_name}'\")\n");
        wrapper.append("            print(f\"Available functions: {[f for f in dir(nim_module) if not f.startswith('_')]}\")\n");
        wrapper.append("            print(\"\\nExample usage:\")\n");
        wrapper.append("            print(f\"\\nimport {module_name}\")\n");
        wrapper.append("            print(f\"# Then call your exported functions, e.g.: {module_name}.your_function()\")\n");
        
        wrapper.append("            # Add the module to sys.modules to make it importable by other cells\n");
        wrapper.append("            sys.modules[module_name] = nim_module\n");
        wrapper.append("            print(f\"\\nThe module '{module_name}' is now globally available for import in other cells.\")\n");
        
        wrapper.append("            return nim_module\n");
        wrapper.append("        except Exception as e:\n");
        wrapper.append("            print(f\"Error importing Nim module: {str(e)}\")\n");
        wrapper.append("            import traceback\n");
        wrapper.append("            traceback.print_exc()\n");
        wrapper.append("            return None\n");
        wrapper.append("    except Exception as e:\n");
        wrapper.append("        print(f\"Error: {str(e)}\")\n");
        wrapper.append("        print(\"Make sure Nim and nimpy are installed properly.\")\n");
        wrapper.append("        print(\"Installation instructions:\")\n");
        wrapper.append("        print(\"1. Install Nim: https://nim-lang.org/install.html\")\n");
        wrapper.append("        print(\"2. Install nimpy: nimble install nimpy\")\n");
        wrapper.append("        return None\n\n");
        
        
        wrapper.append("# Execute the compile and import function\n");
        wrapper.append("nim_module = compile_and_import_nim()\n");
        
        return wrapper.toString();
    }

    private String generateFutharkFFIWrapper(String futharkCode, String backend) {
        StringBuilder wrapper = new StringBuilder();
        String safeFutharkCode = futharkCode.replace("\\", "\\\\");

        wrapper.append("import os\n");
        wrapper.append("import subprocess\n");
        wrapper.append("import hashlib\n");
        wrapper.append("import numpy as np\n");
        wrapper.append("import importlib.util\n");
        wrapper.append("import sys\n\n");
        
        
        if (!customOutputFilename.isEmpty()) {
            
            String moduleName = customOutputFilename;
            if (moduleName.contains(".")) {
                moduleName = moduleName.substring(0, moduleName.lastIndexOf('.'));
            }
            wrapper.append("# Using custom module name\n");
            wrapper.append("module_name = '").append(moduleName).append("'\n");
        } else {
            
            wrapper.append("# Create a unique identifier based on cell content\n");
            wrapper.append("cell_hash = hashlib.md5('''").append(safeFutharkCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("module_name = f'futhark_cell_{cell_hash}'\n");
        }
        wrapper.append("\n");
        
        wrapper.append("# Get the current directory path\n");
        wrapper.append("current_dir = r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("'\n");
        wrapper.append("os.chdir(current_dir)\n");
        wrapper.append("fut_file_path = os.path.join(current_dir, f'{module_name}.fut')\n\n");
        
        
        wrapper.append("# Write Futhark code to file\n");
        wrapper.append("with open(fut_file_path, 'w') as fut_file:\n");
        wrapper.append("    fut_file.write('''").append(safeFutharkCode).append("''')\n");
        wrapper.append("print(f\"Futhark code written to {fut_file_path}\")\n\n");
        
        wrapper.append("# Compile the Futhark code with the ").append(backend).append(" backend\n");
        wrapper.append("compile_cmd = ['futhark', '").append(backend).append("', '--library', fut_file_path]\n");
        wrapper.append("print(f\"Compiling with command: {' '.join(compile_cmd)}\")\n\n");
        
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"Futhark code compiled successfully with ").append(backend).append(" backend\")\n");
        wrapper.append("        \n");
        wrapper.append("        # Build the Python FFI wrapper\n");
        wrapper.append("        build_cmd = ['build_futhark_ffi', module_name]\n");
        wrapper.append("        print(f\"Building FFI wrapper with: {' '.join(build_cmd)}\")\n");
        wrapper.append("        \n");
        wrapper.append("        build_process = subprocess.run(build_cmd, capture_output=True, text=True, cwd=current_dir)\n");
        wrapper.append("        if build_process.returncode != 0:\n");
        wrapper.append("            print(f\"FFI build error:\\n{build_process.stderr}\")\n");
        wrapper.append("        else:\n");
        wrapper.append("            print(f\"FFI wrapper built successfully\")\n");
        wrapper.append("            \n");
        wrapper.append("            # Try to import and use the module\n");
        wrapper.append("            try:\n");
        wrapper.append("                # Add current directory to Python path if not already there\n");
        wrapper.append("                if current_dir not in sys.path:\n");
        wrapper.append("                    sys.path.insert(0, current_dir)\n");
        wrapper.append("                \n");
        wrapper.append("                # Import the generated module\n");
        wrapper.append("                ffi_module = importlib.import_module(f'_{module_name}')\n");
        wrapper.append("                \n");
        wrapper.append("                # Import futhark_ffi\n");
        wrapper.append("                from futhark_ffi import Futhark\n");
        wrapper.append("                \n");
        wrapper.append("                # Create Futhark instance\n");
        wrapper.append("                futhark_instance = Futhark(ffi_module)\n");
        wrapper.append("                \n");
        wrapper.append("                # List available functions\n");
        wrapper.append("                print(f\"\\nAvailable Futhark functions:\")\n");
        wrapper.append("                for func_name in dir(futhark_instance):\n");
        wrapper.append("                    if not func_name.startswith('_') and callable(getattr(futhark_instance, func_name)):\n");
        wrapper.append("                        print(f\"  - {func_name}\")\n");
        wrapper.append("                \n");
        wrapper.append("                # Store in global namespace for easy access\n");
        wrapper.append("                globals()[module_name] = futhark_instance\n");
        wrapper.append("                \n");
        wrapper.append("                print(f\"\\nFuthark instance available as: {module_name}\")\n");
        wrapper.append("                print(f\"Example usage:\")\n");
        wrapper.append("                print(f\"  result = {module_name}.your_function(np.array([1, 2, 3]))\")\n");
        wrapper.append("                print(f\"  output = {module_name}.from_futhark(result)\")\n");
        wrapper.append("                \n");
        
        
        if (backend.equals("cuda")) {
            wrapper.append("                print(f\"\\nNote: CUDA backend requires a CUDA-capable GPU\")\n");
        } else if (backend.equals("opencl")) {
            wrapper.append("                print(f\"\\nNote: OpenCL backend requires OpenCL drivers\")\n");
        } else if (backend.equals("multicore")) {
            wrapper.append("                print(f\"\\nNote: Multicore backend will use all available CPU cores\")\n");
        }
        
        wrapper.append("                \n");
        wrapper.append("            except ImportError as e:\n");
        wrapper.append("                print(f\"Error importing module: {e}\")\n");
        wrapper.append("                print(\"Make sure futhark-ffi is installed: pip install futhark-ffi\")\n");
        wrapper.append("            except Exception as e:\n");
        wrapper.append("                print(f\"Error using Futhark module: {e}\")\n");
        wrapper.append("                import traceback\n");
        wrapper.append("                traceback.print_exc()\n");
        wrapper.append("                \n");
        wrapper.append("except subprocess.CalledProcessError as e:\n");
        wrapper.append("    print(f\"Command failed: {e}\")\n");
        wrapper.append("    print(f\"Output: {e.output}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        wrapper.append("    print(\"\\nTroubleshooting:\")\n");
        wrapper.append("    print(\"1. Make sure Futhark is installed and in PATH\")\n");
        wrapper.append("    print(\"2. Install futhark-ffi: pip install futhark-ffi\")\n");
        
        
        if (backend.equals("cuda")) {
            wrapper.append("    print(\"3. For CUDA backend: Ensure CUDA toolkit is installed\")\n");
        } else if (backend.equals("opencl")) {
            wrapper.append("    print(\"3. For OpenCL backend: Ensure OpenCL drivers are installed\")\n");
        }
        
        return wrapper.toString();
    }

    private String generateJavaWrapper(String javaCode) {
        StringBuilder wrapper = new StringBuilder();
        wrapper.append("import os\n");
        wrapper.append("import subprocess\n");
        wrapper.append("import hashlib\n");
        wrapper.append("import re\n\n");
        
        
        wrapper.append("# Try to extract the class name from the code\n");
        wrapper.append("class_match = re.search(r'public\\s+class\\s+(\\w+)', '''").append(javaCode).append("''')\n");
        
        if (!customOutputFilename.isEmpty()) {
            
            String className = customOutputFilename;
            if (className.endsWith(".java")) {
                className = className.substring(0, className.lastIndexOf(".java"));
            }
            wrapper.append("# Using custom class name\n");
            wrapper.append("class_name = '").append(className).append("'\n");
        } else {
            wrapper.append("if class_match:\n");
            wrapper.append("    class_name = class_match.group(1)\n");
            wrapper.append("else:\n");
            wrapper.append("    # Default class name if none found\n");
            wrapper.append("    cell_hash = hashlib.md5('''").append(javaCode).append("'''.encode()).hexdigest()[:8]\n");
            wrapper.append("    class_name = f'Cell_{cell_hash}'\n");
        }
        
        wrapper.append("    # Wrap the code in a class if it doesn't have one\n");
        wrapper.append("    if 'class' not in '''").append(javaCode).append("''':\n");
        wrapper.append("        wrapped_code = f'public class {class_name} {{\\n    public static void main(String[] args) {{\\n        ' + '''").append(javaCode).append("'''.replace('\\n', '\\n        ') + '\\n    }\\n}'\n");
        wrapper.append("    else:\n");
        wrapper.append("        wrapped_code = '''").append(javaCode).append("'''\n");
        
        if (!customOutputFilename.isEmpty()) {
            wrapper.append("wrapped_code = '''").append(javaCode).append("'''\n");
        }
        
        wrapper.append("\n# Get the current directory path\n");
        wrapper.append("current_dir = r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("'\n");
        wrapper.append("java_file_path = os.path.join(current_dir, f'{class_name}.java')\n");
        
        wrapper.append("# Write Java code to file\n");
        wrapper.append("with open(java_file_path, 'w') as java_file:\n");
        wrapper.append("    java_file.write(wrapped_code)\n");
        wrapper.append("    print(f\"Java code written to {java_file_path}\")\n\n");
        
        wrapper.append("# Compile the Java code\n");
        wrapper.append("compile_cmd = ['javac', java_file_path]\n");
        wrapper.append("try:\n");
        wrapper.append("    compile_process = subprocess.run(compile_cmd, capture_output=True, text=True, cwd=current_dir)\n");
        wrapper.append("    if compile_process.returncode != 0:\n");
        wrapper.append("        print(f\"Compilation error:\\n{compile_process.stderr}\")\n");
        wrapper.append("    else:\n");
        wrapper.append("        print(f\"Java code compiled successfully\")\n");
        wrapper.append("        \n");
        wrapper.append("        # Check if there's a main method and run it\n");
        wrapper.append("        if 'public static void main' in wrapped_code:\n");
        wrapper.append("            print(f\"\\nRunning Java program...\\n\")\n");
        wrapper.append("            run_cmd = ['java', '-cp', current_dir, class_name]\n");
        wrapper.append("            run_process = subprocess.run(run_cmd, capture_output=True, text=True)\n");
        wrapper.append("            if run_process.stdout:\n");
        wrapper.append("                print(run_process.stdout)\n");
        wrapper.append("            if run_process.stderr:\n");
        wrapper.append("                print(\"Errors:\", run_process.stderr)\n");
        wrapper.append("        else:\n");
        wrapper.append("            print(f\"To run: java -cp '{current_dir}' {class_name}\")\n");
        wrapper.append("except Exception as e:\n");
        wrapper.append("    print(f\"Error: {str(e)}\")\n");
        
        return wrapper.toString();
    }

    private String generateRWrapper(String rCode) {
        StringBuilder wrapper = new StringBuilder();
        wrapper.append("import subprocess\n");
        wrapper.append("import tempfile\n");
        wrapper.append("import os\n\n");
        
        wrapper.append("# Create a temporary R script file\n");
        wrapper.append("with tempfile.NamedTemporaryFile(mode='w', suffix='.R', delete=False) as f:\n");
        wrapper.append("    f.write('''").append(rCode).append("''')\n");
        wrapper.append("    r_script_path = f.name\n\n");
        
        wrapper.append("try:\n");
        wrapper.append("    # Run the R script\n");
        wrapper.append("    result = subprocess.run(['Rscript', r_script_path], \n");
        wrapper.append("                          capture_output=True, text=True, \n");
        wrapper.append("                          cwd=r'").append(currentDirectory.getAbsolutePath().replace("\\", "\\\\")).append("')\n");
        wrapper.append("    \n");
        wrapper.append("    # Print the output\n");
        wrapper.append("    if result.stdout:\n");
        wrapper.append("        print(result.stdout)\n");
        wrapper.append("    if result.stderr:\n");
        wrapper.append("        # R often outputs normal messages to stderr\n");
        wrapper.append("        # Filter out common non-error messages\n");
        wrapper.append("        stderr_lines = result.stderr.splitlines()\n");
        wrapper.append("        error_lines = []\n");
        wrapper.append("        for line in stderr_lines:\n");
        wrapper.append("            if not (line.startswith('Loading') or \n");
        wrapper.append("                    line.startswith('Attaching') or\n");
        wrapper.append("                    'package' in line and 'was built under' in line):\n");
        wrapper.append("                error_lines.append(line)\n");
        wrapper.append("        if error_lines:\n");
        wrapper.append("            print('\\nWarnings/Errors:')\n");
        wrapper.append("            print('\\n'.join(error_lines))\n");
        wrapper.append("    \n");
        wrapper.append("    if result.returncode != 0:\n");
        wrapper.append("        print(f'\\nR script exited with code {result.returncode}')\n");
        wrapper.append("        \n");
        wrapper.append("finally:\n");
        wrapper.append("    # Clean up the temporary file\n");
        wrapper.append("    try:\n");
        wrapper.append("        os.unlink(r_script_path)\n");
        wrapper.append("    except:\n");
        wrapper.append("        pass\n");
        
        return wrapper.toString();
    }

    class JavaChecker extends ErrorChecker {
        
        public JavaChecker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
            super(editor, kernelClient);
        }
        
        @Override
        public void checkErrors() {
            if (kernelClient == null) return;
            
            String code = editor.getText();
            if (code.trim().isEmpty()) {
                clearErrors();
                return;
            }
            
            clearErrors();
            
            
            String codeEscaped = code.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n");
            
            String checkCode = 
                "import json, tempfile, subprocess, re, os\n" +
                "\n" +
                "def check_java_code(code):\n" +
                "    results = []\n" +
                "    try:\n" +
                "        # Extract or generate class name\n" +
                "        class_match = re.search(r'public\\s+class\\s+(\\w+)', code)\n" +
                "        if class_match:\n" +
                "            class_name = class_match.group(1)\n" +
                "        else:\n" +
                "            class_name = 'TempClass'\n" +
                "            # Wrap code in a class if needed\n" +
                "            if 'class' not in code:\n" +
                "                code = f'public class {class_name} {{\\n{code}\\n}}'\n" +
                "        \n" +
                "        # Write to temp file\n" +
                "        with tempfile.NamedTemporaryFile(suffix='.java', delete=False, mode='w') as f:\n" +
                "            f.write(code)\n" +
                "            temp_path = f.name\n" +
                "        \n" +
                "        # Run javac\n" +
                "        proc = subprocess.run(['javac', '-Xlint:all', temp_path], \n" +
                "                            capture_output=True, text=True)\n" +
                "        \n" +
                "        # Parse javac output\n" +
                "        for line in proc.stderr.splitlines():\n" +
                "            # Pattern: filename:line: error: message\n" +
                "            match = re.match(r'.+?:(\\d+):\\s*(error|warning):\\s*(.+)', line)\n" +
                "            if match:\n" +
                "                line_num = int(match.group(1)) - 1\n" +
                "                err_type = match.group(2)\n" +
                "                message = match.group(3)\n" +
                "                \n" +
                "                results.append({\n" +
                "                    'line': line_num,\n" +
                "                    'column': 0,\n" +
                "                    'length': 1,\n" +
                "                    'message': f'Java {err_type}: {message}',\n" +
                "                    'type': 'error' if err_type == 'error' else 'warning'\n" +
                "                })\n" +
                "    finally:\n" +
                "        try:\n" +
                "            os.unlink(temp_path)\n" +
                "            # Also try to remove .class file\n" +
                "            class_file = temp_path.replace('.java', '.class')\n" +
                "            if os.path.exists(class_file):\n" +
                "                os.unlink(class_file)\n" +
                "        except:\n" +
                "            pass\n" +
                "    \n" +
                "    return results\n" +
                "\n" +
                "results = check_java_code(\"\"\"" + codeEscaped + "\"\"\")\n" +
                "print(json.dumps(results))\n";
            
            kernelClient.executeCode(checkCode)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    try {
                        String jsonStr = result.trim();
                        if (jsonStr.startsWith("[")) {
                            JSONArray issues = new JSONArray(jsonStr);
                            for (int i = 0; i < issues.length(); i++) {
                                JSONObject issue = issues.getJSONObject(i);
                                int line = issue.getInt("line");
                                int col = issue.getInt("column");
                                int length = issue.optInt("length", 1);
                                String message = issue.getString("message");
                                String type = issue.optString("type", "error");
                                
                                SyntaxErrorParser.ErrorLevel level = type.equals("warning") ?
                                    SyntaxErrorParser.ErrorLevel.WARNING : SyntaxErrorParser.ErrorLevel.ERROR;
                                
                                int offset = getLineOffset(line) + col;
                                addError(line, offset, length, message, level);
                            }
                        }
                        editor.forceReparsing(parser);
                        editor.repaint();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
        }
    }

    class RChecker extends ErrorChecker {
        
        public RChecker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
            super(editor, kernelClient);
        }
        
        @Override
        public void checkErrors() {
            if (kernelClient == null) return;
            
            String code = editor.getText();
            if (code.trim().isEmpty()) {
                clearErrors();
                return;
            }
            
            clearErrors();
            
            
            String codeEscaped = code.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n");
            
            
            String checkCode = 
                "import json, tempfile, subprocess, os\n" +
                "\n" +
                "def check_r_code(code):\n" +
                "    results = []\n" +
                "    try:\n" +
                "        # Create R check script\n" +
                "        r_check = '''\n" +
                "tryCatch({\n" +
                "    parse(text = commandArgs(TRUE)[1])\n" +
                "    cat(\"OK\")\n" +
                "}, error = function(e) {\n" +
                "    # Extract line number from error message\n" +
                "    msg <- conditionMessage(e)\n" +
                "    # Try to extract line number\n" +
                "    line_match <- regexpr(\":(\\\\d+):\", msg)\n" +
                "    if (line_match > 0) {\n" +
                "        line_num <- as.integer(sub(\":.*\", \"\", sub(\".*:(\\\\d+):.*\", \"\\\\1\", msg)))\n" +
                "    } else {\n" +
                "        line_num <- 1\n" +
                "    }\n" +
                "    cat(paste(\"ERROR\", line_num, msg, sep=\"|\"))\n" +
                "})\n" +
                "'''\n" +
                "        \n" +
                "        # Save R check script\n" +
                "        with tempfile.NamedTemporaryFile(suffix='.R', delete=False, mode='w') as f:\n" +
                "            f.write(r_check)\n" +
                "            check_script = f.name\n" +
                "        \n" +
                "        # Run R to check syntax\n" +
                "        proc = subprocess.run(['Rscript', check_script, code], \n" +
                "                            capture_output=True, text=True)\n" +
                "        \n" +
                "        output = proc.stdout.strip()\n" +
                "        if output.startswith('ERROR'):\n" +
                "            parts = output.split('|', 2)\n" +
                "            if len(parts) >= 3:\n" +
                "                line_num = int(parts[1]) - 1\n" +
                "                message = parts[2]\n" +
                "                \n" +
                "                results.append({\n" +
                "                    'line': max(0, line_num),\n" +
                "                    'column': 0,\n" +
                "                    'length': 1,\n" +
                "                    'message': f'R syntax error: {message}',\n" +
                "                    'type': 'error'\n" +
                "                })\n" +
                "    finally:\n" +
                "        try:\n" +
                "            if 'check_script' in locals():\n" +
                "                os.unlink(check_script)\n" +
                "        except:\n" +
                "            pass\n" +
                "    \n" +
                "    return results\n" +
                "\n" +
                "results = check_r_code(\"\"\"" + codeEscaped + "\"\"\")\n" +
                "print(json.dumps(results))\n";
            
            kernelClient.executeCode(checkCode)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    try {
                        String jsonStr = result.trim();
                        if (jsonStr.startsWith("[")) {
                            JSONArray issues = new JSONArray(jsonStr);
                            for (int i = 0; i < issues.length(); i++) {
                                JSONObject issue = issues.getJSONObject(i);
                                int line = issue.getInt("line");
                                int col = issue.getInt("column");
                                int length = issue.optInt("length", 1);
                                String message = issue.getString("message");
                                
                                int offset = getLineOffset(line) + col;
                                addError(line, offset, length, message, SyntaxErrorParser.ErrorLevel.ERROR);
                            }
                        }
                        editor.forceReparsing(parser);
                        editor.repaint();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
        }
    }

    private void setOutputText(String text, boolean isHtml) {
        
        if (isHtml) {
            
            lastOutputText = text.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ")
                                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                                .replaceAll("&amp;", "&").replaceAll("&quot;", "\"");
        } else {
            lastOutputText = text;
        }
        
        
        boolean hasOutput = text != null && !text.trim().isEmpty() && 
                        !text.equals("Executing...") && 
                        !text.startsWith("Executing... (");
        copyOutputButton.setVisible(hasOutput);
        
        
        final String content = text;
        final boolean useHtml = isHtml;
        
        
        if (outputUpdateTimer != null && outputUpdateTimer.isRunning()) {
            outputUpdateTimer.stop();
        }
        
        
        try {
            outputTextArea.getDocument().remove(0, outputTextArea.getDocument().getLength());
        } catch (Exception e) {
            
        }
        
        
        outputTextArea.setContentType(useHtml ? "text/html" : "text/plain");
        outputTextArea.setText(content);
        
        
        SwingUtilities.invokeLater(() -> {
            outputTextArea.revalidate();
            outputTextArea.repaint();
            outputArea.revalidate();
            outputArea.repaint();
        });

        if (!useHtml) {
            outputTextArea.putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.FALSE);
            outputTextArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        }

        
        String finalContent = content; 
        if (content != null && !content.trim().isEmpty() && !content.equals("Executing...")) {
            if (useHtml) {
                
                
                if (finalContent.toLowerCase().contains("</body>")) {
                    finalContent = finalContent.replaceAll("(?i)</body>", "<br><br></body>"); 
                } else {
                    finalContent += "<br><br>"; 
                }
            } else {
                
                finalContent += "\n\n"; 
            }
        }
        

        outputTextArea.setText(finalContent); 

        
        outputUpdateTimer = new Timer(100, e -> {
            try {
                
                
                
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        outputUpdateTimer.setRepeats(false);
        outputUpdateTimer.start();
        
        SwingUtilities.invokeLater(() -> {
            outputTextArea.revalidate();
            outputTextArea.repaint();
            outputArea.revalidate();
            outputArea.repaint();
        });
    }
    
    
    private void setFormattedOutput(String result) {
        
        if (displayHTMLTable(result)) {
            return; 
        }
        
        
        if (result.contains("Traceback") || result.contains("Error:")) {
            
            StringBuilder html = new StringBuilder();
            html.append("<html><body style='color:#E6E6E6; font-family:monospace'>");
            
            
            String[] lines = result.split("\n");
            for (String line : lines) {
                
                String escapedLine = line.replace("&", "&amp;")
                                        .replace("<", "&lt;")
                                        .replace(">", "&gt;");
                
                if (line.contains("Error:") || line.contains("Exception:")) {
                    
                    html.append("<div style='color:#FF6B68;'>").append(escapedLine).append("</div>");
                } else if (line.trim().startsWith("File \"") || line.contains("line ")) {
                    
                    html.append("<div style='color:#82AAFF;'>").append(escapedLine).append("</div>");
                } else {
                    
                    html.append("<div>").append(escapedLine).append("</div>");
                }
            }
            
            html.append("</body></html>");
            
            
            setOutputText(html.toString(), true);
            
            
            visualPanel.clearVisualization();
        } else {
            
            boolean foundVisualization = false;
            
            
            Pattern base64Pattern = Pattern.compile("data:image/\\w+;base64,[A-Za-z0-9+/=]+");
            Matcher matcher = base64Pattern.matcher(result);
            
            if (matcher.find()) {
                
                String base64Data = matcher.group(0);
                
                
                visualPanel.displayVisualizationFromBase64(base64Data);
                
                
                result = result.replace(base64Data, "[Image visualization displayed below]");
                foundVisualization = true;
            }
            
            
            if (!foundVisualization) {
                visualPanel.clearVisualization();
            }
            
            
            if (result.contains("<table") && result.contains("</table>")) {
                String html = "<html><head><style>" +
                       "body{background:#101035; color:white; font-family:monospace;}" +
                       "table{border-collapse:collapse; margin:10px 0;}" +
                       "th{background:#2a2a55; color:white; padding:4px 8px; border:1px solid #3a3a75;}" +
                       "td{padding:4px 8px; border:1px solid #3a3a75;}" +
                       "</style></head><body>" + result + "</body></html>";
                setOutputText(html, true);
            } else {
                setOutputText(result, false);
            }
        }
    }
    
    public void setKernelClient(JupyterKernelClient kernelClient) {
        this.kernelClient = kernelClient;
        updateErrorChecker();
    }
    
    public JupyterKernelClient getKernelClient() {
        return kernelClient;
    }

    private void addCellBelow() {
        Container parent = getParent();
        if (parent instanceof JPanel) {
            int index = -1;
            Component[] components = parent.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (components[i] == this) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                CodeCell newCell = new CodeCell(kernelClient, currentDirectory);
            
                parent.add(newCell, index + 1);
                parent.revalidate();
                parent.repaint();
            }
        }
    }
    
    private void deleteCell() {
        Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.revalidate();
            parent.repaint();
        }
    }
    
    @Override
    public void execute() {
        
        clearOutput();
        
        
        isExecuting = true;
        
        
        final boolean wasErrorCheckingEnabled = errorChecker != null ? errorChecker.isCheckingEnabled : false;
        if (errorChecker != null) {
            errorChecker.setCheckingEnabled(false);
        }
        
        
        setOutputText("Executing...", false);
        
        
        if (visualPanel != null) {
            visualPanel.clearVisualization();
        }
        
        
        if (executionTimer != null) {
            executionTimer.stop();
            executionTimer = null;
        }
        
        
        startTime = System.currentTimeMillis();
        executionTimer = new Timer(100, e -> {
            if (isExecuting) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                double seconds = elapsedTime / 1000.0;
                SwingUtilities.invokeLater(() -> {
                    if (isExecuting) { 
                        setOutputText("Executing... (" + timerFormat.format(seconds) + "s)", false);
                    }
                });
            } else {
                
                ((Timer)e.getSource()).stop();
            }
        });
        executionTimer.setRepeats(true);
        executionTimer.start();
        
        if (kernelClient != null) {
            
            String code = getExecutableCode();
            
            
            if (selectedLanguage.equals("Python")) {
                
                if (!code.contains("%matplotlib inline") && !code.contains("matplotlib.use('Agg')")) {
                    
                    code = "import sys\n" +
                        "import io\n" +
                        "import base64\n" +
                        "import matplotlib\n" +
                        "matplotlib.use('Agg')  # Non-interactive backend\n" +
                        "import matplotlib.pyplot as plt\n" +
                        "\n" +
                        "# Store the original stdout\n" +
                        "original_stdout = sys.stdout\n" +
                        "\n" +
                        "# Create a buffer for capturing output\n" +
                        "output_buffer = io.StringIO()\n" +
                        "sys.stdout = output_buffer\n" +
                        "\n" +
                        "try:\n" +
                        "    # Execute the user's code\n" +
                        "    " + code.replace("\n", "\n    ") + "\n" +
                        "    \n" +
                        "    # Check if there are any open figures to save\n" +
                        "    if plt.get_fignums():\n" +
                        "        # Save the figure to a bytes buffer\n" +
                        "        buf = io.BytesIO()\n" +
                        "        plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')\n" +
                        "        buf.seek(0)\n" +
                        "        \n" +
                        "        # Convert to base64 for embedding\n" +
                        "        img_str = base64.b64encode(buf.getvalue()).decode('utf-8')\n" +
                        "        \n" +
                        "        # Print the image as a data URL that our Java code can detect\n" +
                        "        print(f'data:image/png;base64,{img_str}')\n" +
                        "        \n" +
                        "        # Close the figure to prevent it from being displayed again\n" +
                        "        plt.close()\n" +
                        "finally:\n" +
                        "    # Restore stdout\n" +
                        "    sys.stdout = original_stdout\n" +
                        "    # Print the captured output\n" +
                        "    print(output_buffer.getvalue())\n";
                }
            }
            
            kernelClient.executeCode(code)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    
                    isExecuting = false;
                    
                    
                    if (executionTimer != null) {
                        executionTimer.stop();
                        executionTimer = null;
                    }
                    long executionTime = System.currentTimeMillis() - startTime;
                    double seconds = executionTime / 1000.0;
                    
                    
                    String resultWithTime = "Execution completed in " + timerFormat.format(seconds) + "s\n\n" + result;
                    setFormattedOutput(resultWithTime);
                    
                    
                    if (errorChecker != null && wasErrorCheckingEnabled) {
                        
                        Timer resumeErrorChecking = new Timer(500, evt -> {
                            errorChecker.setCheckingEnabled(true);
                        });
                        resumeErrorChecking.setRepeats(false);
                        resumeErrorChecking.start();
                    }
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        
                        isExecuting = false;
                        
                        
                        if (executionTimer != null) {
                            executionTimer.stop();
                            executionTimer = null;
                        }
                        long executionTime = System.currentTimeMillis() - startTime;
                        double seconds = executionTime / 1000.0;
                        
                        
                        String errorWithTime = "Error after " + timerFormat.format(seconds) + "s: " + ex.getMessage();
                        setFormattedOutput(errorWithTime);
                        
                        
                        if (errorChecker != null && wasErrorCheckingEnabled) {
                            errorChecker.setCheckingEnabled(true);
                        }
                    });
                    return null;
                });
        } else {
            
            isExecuting = false;
            
            
            if (executionTimer != null) {
                executionTimer.stop();
                executionTimer = null;
            }
            setOutputText("Error: No kernel client available", false);
            
            if (errorChecker != null && wasErrorCheckingEnabled) {
                errorChecker.setCheckingEnabled(true);
            }
        }
    }
    
    
    public String getCodeText() {
        return codeArea.getText();
    }
    
    public void setCodeText(String code) {
        codeArea.setText(code);
    }
    
    
    public void fromJSON(JSONObject cellObj) {
        
        JSONArray sourceArray = cellObj.getJSONArray("source");
        StringBuilder sourceText = new StringBuilder();
        for (int j = 0; j < sourceArray.length(); j++) {
            sourceText.append(sourceArray.getString(j));
        }
        setCodeText(sourceText.toString());
        
        
        if (cellObj.has("metadata")) {
            JSONObject metadata = cellObj.getJSONObject("metadata");
            if (metadata.has("language")) {
                String language = metadata.getString("language");
                
                if (language.equals("Python") || language.equals("C++") || language.equals("CUDA") || 
                    language.equals("Nim") || language.startsWith("Futhark") || 
                    language.equals("Java") || language.equals("R")) {
                    selectedLanguage = language;
                    languageCombo.setSelectedItem(language);
                    updateSyntaxHighlighting();
                    
                    
                    Color languageColor;
                    switch (language) {
                        case "Python":
                            languageColor = new Color(0xFFD700); 
                            break;
                        case "C++":
                            languageColor = new Color(0x659AD2); 
                            break;
                        case "CUDA":
                            languageColor = new Color(0x76B900); 
                            break;
                        case "Nim":
                            languageColor = new Color(0xB8860B); 
                            break;
                        case "Futhark C":
                        case "Futhark Multicore":
                        case "Futhark CUDA":
                        case "Futhark OpenCL":
                            languageColor = new Color(0xE67E22); 
                            break;
                        case "Java":
                            languageColor = new Color(0xF89820); 
                            break;
                        case "R":
                            languageColor = new Color(0x276DC3); 
                            break;
                        default:
                            languageColor = Color.WHITE;
                            break;
                    }
                    
                    
                    languageCombo.setForeground(languageColor);
                    updateComboBoxArrowColor(languageColor);
                    
                    
                    languageCombo.setBackground(new Color(0x171717));
                    
                    
                    SwingUtilities.invokeLater(() -> {
                        languageCombo.repaint();
                    });
                }
            }
            
            
            if (metadata.has("custom_output_filename")) {
                customOutputFilename = metadata.getString("custom_output_filename");
                customFilenameField.setText(customOutputFilename);
            }
        }
    }
}


class MarkdownCell extends NotebookCell {
    private RSyntaxTextArea markdownArea;
    private JTextPane renderedMarkdown;
    
    public MarkdownCell() {
        super();
        
        
        markdownArea = new RSyntaxTextArea(5, 60);
        markdownArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        markdownArea.setBackground(new Color(16, 16, 53));  
        markdownArea.setForeground(Color.WHITE);
        
        RTextScrollPane scrollPane = new RTextScrollPane(markdownArea);
        
        
        renderedMarkdown = new JTextPane();
        renderedMarkdown.setEditable(false);
        renderedMarkdown.setContentType("text/html");
        renderedMarkdown.setBackground(new Color(16, 16, 53));  
        
        
        JButton renderButton = new JButton("Render");
        renderButton.setBackground(new Color(0x171717));
        renderButton.setForeground(Color.GREEN);
        renderButton.addActionListener(e -> execute());
        
        String[] cellTypes = {"Code", "Markdown"};
        JComboBox<String> cellTypeCombo = new JComboBox<>(cellTypes);
        cellTypeCombo.setBackground(new Color(0x171717));
        cellTypeCombo.setForeground(Color.WHITE);
        cellTypeCombo.setSelectedIndex(1);
        
        
        JButton addCellButton = new JButton("+");
        addCellButton.setBackground(new Color(0x171717));
        addCellButton.setForeground(Color.GREEN);
        addCellButton.setToolTipText("Add cell below");
        addCellButton.addActionListener(e -> addCellBelow());
        
        JButton deleteCellButton = new JButton("×");
        deleteCellButton.setBackground(new Color(0x171717));
        deleteCellButton.setForeground(Color.RED);
        deleteCellButton.setToolTipText("Delete cell");
        deleteCellButton.addActionListener(e -> deleteCell());
        
        toolBar.add(renderButton);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(cellTypeCombo);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(addCellButton);
        toolBar.add(deleteCellButton);
        
        
        inputArea.add(scrollPane, BorderLayout.CENTER);
        outputArea.add(new JScrollPane(renderedMarkdown), BorderLayout.CENTER);
    }
    
    private void addCellBelow() {
        Container parent = getParent();
        if (parent instanceof JPanel) {
            int index = -1;
            Component[] components = parent.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (components[i] == this) {
                    index = i;
                    break;
                }
            }
            
            if (index != -1) {
                MarkdownCell newCell = new MarkdownCell();
                parent.add(newCell, index + 1);
                parent.revalidate();
                parent.repaint();
            }
        }
    }
    
    private void deleteCell() {
        Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.revalidate();
            parent.repaint();
        }
    }
    
    @Override
    public void requestFocusInEditor() {
        
        if (markdownArea != null && markdownArea.isVisible()) {
            markdownArea.requestFocus();
        } else {
            super.requestFocusInEditor();
        }
    }

    @Override
    public void execute() {
        
        
        String markdown = markdownArea.getText();
        String html = "<html><body>" + markdown.replace("\n", "<br>") + "</body></html>";
        renderedMarkdown.setText(html);
    }
    
    public String getMarkdownText() {
        return markdownArea.getText();
    }
    
    public void setMarkdownText(String markdown) {
        markdownArea.setText(markdown);
    }
}



class LatexEditorPanel extends JPanel {
    private RSyntaxTextArea latexEditor;
    private JTextPane previewPane;
    private JButton transpileButton;
    private CodeCell parentCell;
    
    public LatexEditorPanel(CodeCell cell) {
        this.parentCell = cell;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setBackground(new Color(16, 16, 53));
        
        
        latexEditor = new RSyntaxTextArea(5, 30);
        latexEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_LATEX);

        
        SyntaxScheme scheme = latexEditor.getSyntaxScheme();
        
        
        scheme.getStyle(Token.COMMENT_EOL).foreground = new Color(0x6A9955);      
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = new Color(0x6A9955); 
        scheme.getStyle(Token.RESERVED_WORD).foreground = new Color(0xC586C0);    
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = new Color(0xC586C0);  
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = new Color(0xCE9178); 
        scheme.getStyle(Token.SEPARATOR).foreground = new Color(0xD4D4D4);        
        scheme.getStyle(Token.OPERATOR).foreground = new Color(0xD4D4D4);         
        scheme.getStyle(Token.IDENTIFIER).foreground = new Color(0x9CDCFE);       
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = new Color(0xB5CEA8); 
        scheme.getStyle(Token.FUNCTION).foreground = new Color(0xDCDCAA);         
        scheme.getStyle(Token.MARKUP_TAG_DELIMITER).foreground = new Color(0x808080); 
        scheme.getStyle(Token.DATA_TYPE).foreground = new Color(0x4EC9B0);        
        scheme.getStyle(Token.VARIABLE).foreground = new Color(0x9CDCFE);         
      
        
        latexEditor.setSyntaxScheme(scheme);

        
        latexEditor.setBackground(new Color(13, 13, 35));  
        latexEditor.setForeground(Color.WHITE);
        latexEditor.setCaretColor(Color.WHITE);
        latexEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        latexEditor.setCurrentLineHighlightColor(new Color(20, 20, 60)); 

        
        latexEditor.setBracketMatchingEnabled(true);
        latexEditor.setPaintMatchedBracketPair(true);
        latexEditor.setMatchedBracketBGColor(new Color(40, 40, 80));
        latexEditor.setMatchedBracketBorderColor(new Color(80, 80, 140));

        
        latexEditor.repaint();

        
        previewPane = new JTextPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setOpaque(true);
        previewPane.setBackground(new Color(0x101035));        
        previewPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
            "Render",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12),
            Color.WHITE
        ));
        
        
        RTextScrollPane editorScrollPane = new RTextScrollPane(latexEditor);
        
        editorScrollPane.getGutter().setBorder(BorderFactory.createEmptyBorder());
        
        editorScrollPane.getGutter().setBackground(new Color(0x0D0D23));
        
        editorScrollPane.getGutter().setBorderColor(new Color(0x0D0D23));
        
        editorScrollPane.getGutter().setLineNumberColor(new Color(0x808080)); 
        
        editorScrollPane.getGutter().setLineNumberFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        editorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        editorScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        editorScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
            "LaTeX",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12),
            Color.WHITE
        ));

        
        JScrollBar vBar = editorScrollPane.getVerticalScrollBar();
        if (vBar != null) {
            vBar.setBackground(new Color(13, 13, 35));
            vBar.setUI(new BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    this.thumbColor = new Color(40, 40, 100);
                    this.trackColor = new Color(20, 20, 50);
                }
            });
        }

        JScrollBar hBar = editorScrollPane.getHorizontalScrollBar();
        if (hBar != null) {
            hBar.setBackground(new Color(13, 13, 35));
            hBar.setUI(new BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    this.thumbColor = new Color(40, 40, 100);
                    this.trackColor = new Color(20, 20, 50);
                }
            });
        }
        
        
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(new Color(13, 13, 35));
        
        JButton renderButton = new JButton("Render");
        renderButton.setBackground(new Color(0x171717));
        renderButton.setForeground(new Color(0x4EC9B0));  
        renderButton.addActionListener(e -> renderLatex());
        
        transpileButton = new JButton("Transpile to Futhark");
        transpileButton.setBackground(new Color(0x171717));
        transpileButton.setForeground(new Color(0xDCDCAA));  
        transpileButton.addActionListener(e -> transpileToFuthark());
        
        JButton mathEditorButton = new JButton("Math Editor");
        mathEditorButton.setBackground(new Color(0x171717));
        mathEditorButton.setForeground(new Color(0x8c, 0xba, 0xfa));  
        mathEditorButton.setToolTipText("Open LaTeX equation editor");
        mathEditorButton.addActionListener(e -> showMathEditor());

        buttonPanel.add(renderButton);
        buttonPanel.add(transpileButton);
        buttonPanel.add(mathEditorButton);
        
        
        JScrollPane previewScrollPane = new JScrollPane(previewPane);
        previewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        previewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(13, 13, 35));
        topPanel.add(editorScrollPane, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);
        add(previewScrollPane, BorderLayout.CENTER);
                
        
        renderLatex();
    } 

    private void showMathEditor() {
        
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        
        
        MathEquationEditorDialog dialog = new MathEquationEditorDialog(parentFrame, this);
        dialog.setVisible(true);
    }

    public void renderLatex() {
        String latex = latexEditor.getText().trim();
        if (latex.isEmpty()) {
            previewPane.setText("<html><body style='color:gray; background-color:#101035; padding:10px;'>Enter LaTeX equation</body></html>");
            return;
        }
        try {
            
            TeXFormula formula = new TeXFormula(latex);
            formula.setColor(Color.WHITE);
            
            TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);
            icon.setForeground(Color.WHITE);
            System.out.println("Icon dimensions: " + icon.getIconWidth() + " x " + icon.getIconHeight());
    
            
            BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            Color darkBlue = new Color(0x0D0D23); 
            g2.setColor(darkBlue);
            g2.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();
    
            
            File tempImageFile = File.createTempFile("latexPreview", ".png");
            ImageIO.write(image, "png", tempImageFile);
            tempImageFile.deleteOnExit();
    
            
            String fileUrl = "file:///" + tempImageFile.getAbsolutePath().replace("\\", "/");
    
            String html = "<html>" +
                            "<head>" +
                            "<style type='text/css'>" +
                                "html, body { background-color: #0D0D23; margin: 0; padding: 0; }" +
                                "img { display: block; margin: auto; }" +
                            "</style>" +
                            "</head>" +
                            "<body>" +
                            "<img src='" + fileUrl + "'/>" +
                            "</body>" +
                        "</html>";
            previewPane.setContentType("text/html");
            previewPane.setText(html);
            previewPane.revalidate();
            previewPane.repaint();
        } catch (Exception ex) {
            previewPane.setText("<html><body style='color:red; background-color:#101035; padding:10px;'>Error rendering LaTeX: " + ex.getMessage() + "</body></html>");
            ex.printStackTrace();
        }
    }    

private void transpileToFuthark() {
        String latex = latexEditor.getText().trim();
        if (latex.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a LaTeX equation first.", 
                "Empty Equation", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        
        JupyterNotebookIDE ide = (JupyterNotebookIDE) SwingUtilities.getWindowAncestor(this);
        if (ide == null) {
            JOptionPane.showMessageDialog(this, "Could not find the main application window.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String envPath = ide.getSelectedEnvPath();
        String scriptPath = ide.getFutharkTranspilerPath();

        
        if (envPath == null || envPath.trim().isEmpty() || scriptPath == null) {
            JOptionPane.showMessageDialog(this,
                "Python environment or transpiler path not configured. Cannot transpile.",
                "Configuration Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        
        previewPane.setText("<html><body style='color:white; background-color:#1a1a2e; padding:10px;'>" +
                        "Transpiling LaTeX to Futhark code...<br>Please wait." +
                        "</body></html>");
        
        
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    
                    LaTeX2FutharkCaller caller = new LaTeX2FutharkCaller(
                        envPath,    
                        scriptPath  
                    );
                    
                    
                    LaTeX2FutharkCaller.TranspilationResult result = caller.transpileViaTempFile(latex, true);
                    return result.getCompleteProgram();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    return "ERROR: " + e.getMessage();
                }
            }
            
            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result.startsWith("ERROR:")) {
                        
                        JOptionPane.showMessageDialog(LatexEditorPanel.this, 
                            result.substring(6), 
                            "Transpilation Error", 
                            JOptionPane.ERROR_MESSAGE);
                    } else {
                        
                        int option = JOptionPane.showConfirmDialog(LatexEditorPanel.this, 
                            "Futhark code generated successfully! Insert into code cell?", 
                            "Code Generated", 
                            JOptionPane.YES_NO_OPTION);
                            
                        if (option == JOptionPane.YES_OPTION) {
                            parentCell.setCodeText(result);
                            
                            parentCell.setSelectedLanguage("Futhark C");
                        } else {
                            
                            JTextArea textArea = new JTextArea(result);
                            textArea.setEditable(false);
                            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                            JScrollPane scrollPane = new JScrollPane(textArea);
                            scrollPane.setPreferredSize(new Dimension(800, 600));
                            
                            JOptionPane.showMessageDialog(LatexEditorPanel.this, 
                                scrollPane, 
                                "Generated Futhark Code", 
                                JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LatexEditorPanel.this, 
                        "Error retrieving transpilation result: " + e.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    
                    renderLatex();
                }
            }
        };
        
        worker.execute();
    }
    
    public String getLatexText() {
        return latexEditor.getText();
    }
    
    public void setLatexText(String latex) {
        latexEditor.setText(latex);
        renderLatex();
    }

    class EquationEditorDialog extends JDialog {
        private JTextArea latexInput;
        private JTextPane previewPane;
        private JTabbedPane symbolTabs;
        private LatexEditorPanel parentEditor;
        
        public EquationEditorDialog(Frame owner, LatexEditorPanel parent) {
            super(owner, "Math Equation Editor", true);
            this.parentEditor = parent;
            
            setSize(800, 600);
            setLocationRelativeTo(owner);
            
            
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBackground(new Color(13, 13, 35));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.setBackground(new Color(13, 13, 35));
            inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
                "LaTeX",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                Color.WHITE
            ));
            
            latexInput = new JTextArea();
            latexInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
            latexInput.setBackground(new Color(16, 16, 53));
            latexInput.setForeground(Color.WHITE);
            latexInput.setCaretColor(Color.WHITE);
            latexInput.setText(parentEditor.getLatexText());
            
            
            latexInput.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { updatePreview(); }
                public void removeUpdate(DocumentEvent e) { updatePreview(); }
                public void changedUpdate(DocumentEvent e) { updatePreview(); }
            });
            
            JScrollPane inputScroll = new JScrollPane(latexInput);
            inputScroll.setPreferredSize(new Dimension(780, 120));
            customizeScrollBars(inputScroll);
            inputPanel.add(inputScroll, BorderLayout.CENTER);
            
            
            JPanel previewPanel = new JPanel(new BorderLayout());
            previewPanel.setBackground(new Color(13, 13, 35));
            previewPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
                "Preview",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                Color.WHITE
            ));
            
            previewPane = new JTextPane();
            previewPane.setContentType("text/html");
            previewPane.setEditable(false);
            previewPane.setBackground(new Color(16, 16, 53));
            
            JScrollPane previewScroll = new JScrollPane(previewPane);
            customizeScrollBars(previewScroll);
            previewScroll.setPreferredSize(new Dimension(780, 150));
            previewPanel.add(previewScroll, BorderLayout.CENTER);
            
            
            symbolTabs = new JTabbedPane();
            symbolTabs.setBackground(new Color(13, 13, 35));
            symbolTabs.setForeground(Color.WHITE);
            
            
            JPanel basicPanel = createSymbolPanel(new String[][] {
                {"\\alpha", "\\beta", "\\gamma", "\\delta", "\\epsilon", "\\zeta", "\\eta", "\\theta"},
                {"\\iota", "\\kappa", "\\lambda", "\\mu", "\\nu", "\\xi", "\\pi", "\\rho"},
                {"\\sigma", "\\tau", "\\upsilon", "\\phi", "\\chi", "\\psi", "\\omega", "\\Gamma"},
                {"\\Delta", "\\Theta", "\\Lambda", "\\Xi", "\\Pi", "\\Sigma", "\\Upsilon", "\\Phi"},
                {"\\Psi", "\\Omega", "+", "-", "\\times", "\\div", "=", "\\neq"}
            });
            symbolTabs.addTab("Greek & Basic", basicPanel);
            
            
            JPanel operatorsPanel = createSymbolPanel(new String[][] {
                {"\\pm", "\\mp", "\\cdot", "\\ast", "\\star", "\\circ", "\\bullet", "\\oplus"},
                {"\\ominus", "\\otimes", "\\oslash", "\\odot", "\\dagger", "\\ddagger", "\\vee", "\\wedge"},
                {"\\cap", "\\cup", "\\uplus", "\\sqcap", "\\sqcup", "\\triangleleft", "\\triangleright", "\\wr"},
                {"<", ">", "\\leq", "\\geq", "\\equiv", "\\sim", "\\simeq", "\\approx"},
                {"\\subset", "\\supset", "\\subseteq", "\\supseteq", "\\in", "\\ni", "\\notin", "\\propto"}
            });
            symbolTabs.addTab("Operators", operatorsPanel);
            
            
            JPanel arrowsPanel = createSymbolPanel(new String[][] {
                {"\\leftarrow", "\\rightarrow", "\\leftrightarrow", "\\Leftarrow", "\\Rightarrow", "\\Leftrightarrow", "\\uparrow", "\\downarrow"},
                {"\\updownarrow", "\\Uparrow", "\\Downarrow", "\\Updownarrow", "\\mapsto", "\\leadsto", "\\nearrow", "\\searrow"},
                {"\\swarrow", "\\nwarrow", "\\leftharpoonup", "\\leftharpoondown", "\\rightharpoonup", "\\rightharpoondown", "\\hookleftarrow", "\\hookrightarrow"}
            });
            symbolTabs.addTab("Arrows", arrowsPanel);
            
            
            JPanel functionsPanel = createSymbolPanel(new String[][] {
                {"\\sin", "\\cos", "\\tan", "\\cot", "\\sec", "\\csc", "\\arcsin", "\\arccos"},
                {"\\arctan", "\\sinh", "\\cosh", "\\tanh", "\\coth", "\\log", "\\ln", "\\exp"},
                {"\\det", "\\dim", "\\lim", "\\inf", "\\sup", "\\max", "\\min", "\\gcd"},
                {"\\sum", "\\prod", "\\coprod", "\\int", "\\oint", "\\iint", "\\iiint", "\\partial"}
            });
            symbolTabs.addTab("Functions", functionsPanel);
            
            
            JPanel structuresPanel = createSymbolPanel(new String[][] {
                {"\\frac{a}{b}", "\\sqrt{x}", "\\sqrt[n]{x}", "x^{a}", "x_{a}", "\\overline{x}", "\\underline{x}", "\\widehat{x}"},
                {"\\widetilde{x}", "\\overbrace{x}", "\\underbrace{x}", "\\begin{matrix} a & b \\\\ c & d \\end{matrix}", "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", "\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}", "\\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix}", "\\begin{cases} f(x) & x>0 \\\\ g(x) & x<0 \\end{cases}"}
            });
            symbolTabs.addTab("Structures", structuresPanel);
            
            
            JPanel miscPanel = createSymbolPanel(new String[][] {
                {"\\forall", "\\exists", "\\nexists", "\\therefore", "\\because", "\\emptyset", "\\infty", "\\nabla"},
                {"\\Box", "\\Diamond", "\\bot", "\\top", "\\angle", "\\measuredangle", "\\triangle", "\\square"},
                {"\\hbar", "\\imath", "\\jmath", "\\ell", "\\Re", "\\Im", "\\aleph", "\\beth"},
                {"\\wp", "\\neg", "\\lnot", "\\land", "\\lor", "\\to", "\\gets", "\\iff"}
            });
            symbolTabs.addTab("Miscellaneous", miscPanel);
            
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(new Color(13, 13, 35));
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setBackground(new Color(0x171717));
            cancelButton.setForeground(Color.WHITE);
            cancelButton.addActionListener(e -> dispose());
            
            JButton applyButton = new JButton("Apply");
            applyButton.setBackground(new Color(0x171717));
            applyButton.setForeground(new Color(0x4EC9B0));
            applyButton.addActionListener(e -> {
                parentEditor.setLatexText(latexInput.getText());
                dispose();
            });
            
            buttonPanel.add(cancelButton);
            buttonPanel.add(applyButton);
            
            
            mainPanel.add(inputPanel, BorderLayout.NORTH);
            mainPanel.add(previewPanel, BorderLayout.CENTER);
            mainPanel.add(symbolTabs, BorderLayout.SOUTH);
            
            symbolTabs.setPreferredSize(new Dimension(780, 200));
            
            
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(new Color(13, 13, 35));
            contentPanel.add(mainPanel, BorderLayout.CENTER);
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            setContentPane(contentPanel);
            
            
            updatePreview();
        }
    
        private JPanel createSymbolPanel(String[][] symbols) {
            JPanel panel = new JPanel(new GridLayout(symbols.length, symbols[0].length, 2, 2));
            panel.setBackground(new Color(13, 13, 35));
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            for (String[] row : symbols) {
                for (String symbol : row) {
                    JButton button = new JButton(formatButtonLabel(symbol));
                    button.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
                    button.setBackground(new Color(0x171717));
                    button.setForeground(Color.WHITE);
                    button.setToolTipText(symbol);
                    
                    
                    final String symbolText = symbol;
                    button.addActionListener(e -> insertSymbol(symbolText));
                    
                    panel.add(button);
                }
            }
            
            return panel;
        }
        
        private String formatButtonLabel(String symbol) {
            
            if (symbol.length() > 15) {
                
                if (symbol.contains("matrix") || symbol.contains("cases")) {
                    return symbol.substring(0, symbol.indexOf("{")+1) + "..." + symbol.substring(symbol.lastIndexOf("}"));
                }
                return symbol.substring(0, 12) + "...";
            }
            return symbol;
        }
        
        
        private void insertSymbol(String symbol) {
            
            int pos = latexInput.getCaretPosition();
            String text = latexInput.getText();
            
            
            if (symbol.contains("\\frac{a}{b}")) {
                symbol = "\\frac{}{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\frac{".length());
            } 
            else if (symbol.contains("\\sqrt{x}")) {
                symbol = "\\sqrt{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\sqrt{".length());
            }
            else if (symbol.contains("\\sqrt[n]{x}")) {
                symbol = "\\sqrt[]{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\sqrt[".length());
            }
            else if (symbol.contains("x^{a}")) {
                symbol = "^{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "^{".length());
            }
            else if (symbol.contains("x_{a}")) {
                symbol = "_{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "_{".length());
            }
            else if (symbol.contains("\\begin{matrix}")) {
                symbol = "\\begin{matrix}\n  a & b \\\\\n  c & d\n\\end{matrix}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\begin{matrix}\n  ".length());
            }
            else if (symbol.contains("\\begin{pmatrix}")) {
                symbol = "\\begin{pmatrix}\n  a & b \\\\\n  c & d\n\\end{pmatrix}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\begin{pmatrix}\n  ".length());
            }
            else if (symbol.contains("\\begin{bmatrix}")) {
                symbol = "\\begin{bmatrix}\n  a & b \\\\\n  c & d\n\\end{bmatrix}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\begin{bmatrix}\n  ".length());
            }
            else if (symbol.contains("\\begin{vmatrix}")) {
                symbol = "\\begin{vmatrix}\n  a & b \\\\\n  c & d\n\\end{vmatrix}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\begin{vmatrix}\n  ".length());
            }
            else if (symbol.contains("\\begin{cases}")) {
                symbol = "\\begin{cases}\n  f(x) & \\text{if } x > 0 \\\\\n  g(x) & \\text{if } x < 0\n\\end{cases}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + "\\begin{cases}\n  ".length());
            }
            else if (symbol.contains("\\overline") || symbol.contains("\\underline") || 
                     symbol.contains("\\widehat") || symbol.contains("\\widetilde") ||
                     symbol.contains("\\overbrace") || symbol.contains("\\underbrace")) {
                
                String cmd = symbol.substring(0, symbol.indexOf("{"));
                symbol = cmd + "{}";
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + cmd.length() + 1);
            }
            else {
                
                latexInput.setText(text.substring(0, pos) + symbol + text.substring(pos));
                latexInput.setCaretPosition(pos + symbol.length());
            }
            
            
            latexInput.requestFocusInWindow();
            
            
            updatePreview();
        }
        
        private void updatePreview() {
            try {
                
                String latex = latexInput.getText();
                
                
                TeXFormula formula = new TeXFormula(latex);
                formula.setColor(Color.WHITE);
                
                
                TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);
                icon.setForeground(Color.WHITE);
                
                
                BufferedImage image = new BufferedImage(
                        Math.max(1, icon.getIconWidth()), 
                        Math.max(1, icon.getIconHeight()), 
                        BufferedImage.TYPE_INT_ARGB);
                
                Graphics2D g2 = image.createGraphics();
                Color darkBlue = new Color(0x101035);  
                g2.setColor(darkBlue);
                g2.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
                icon.paintIcon(null, g2, 0, 0);
                g2.dispose();
                
                
                String imageData = imageToBase64(image);
                
                
                String html = "<html><body style='background-color:#101035; text-align:center;'>" +
                              "<div style='padding:10px;'>" +
                              "<img src='" + imageData + "' alt='LaTeX Preview'/>" +
                              "</div></body></html>";
                              
                previewPane.setText(html);
                
            } catch (Exception ex) {
                
                previewPane.setText("<html><body style='background-color:#101035; color:#FF6B68; padding:10px;'>" +
                                   "Error rendering LaTeX: " + ex.getMessage() +
                                   "</body></html>");
            }
        }
        
        private String imageToBase64(BufferedImage image) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        }
        
        private void customizeScrollBars(JScrollPane scrollPane) {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            if (vBar != null) {
                vBar.setBackground(new Color(13, 13, 35));
                vBar.setUI(new BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(40, 40, 100);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
            }
            
            JScrollBar hBar = scrollPane.getHorizontalScrollBar();
            if (hBar != null) {
                hBar.setBackground(new Color(13, 13, 35));
                hBar.setUI(new BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(40, 40, 100);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
            }
        }
    }
    
    

    class MathEquationEditorDialog extends JDialog {
        private RSyntaxTextArea latexEditor;
        private JTextPane previewPane;
        private LatexEditorPanel parentPanel;
        private Timer previewUpdateTimer;
        private boolean previewUpdatePending = false;
        
    private boolean isSymbolSupported(String symbol) {
        
        String[] supported = {
            
            "\\alpha", "\\beta", "\\gamma", "\\delta", "\\epsilon", "\\zeta", "\\eta", "\\theta",
            "\\iota", "\\kappa", "\\lambda", "\\mu", "\\nu", "\\xi", "\\pi", "\\rho",
            "\\sigma", "\\tau", "\\upsilon", "\\phi", "\\chi", "\\psi", "\\omega",
            "\\Gamma", "\\Delta", "\\Theta", "\\Lambda", "\\Xi", "\\Pi", "\\Sigma", "\\Phi", "\\Psi", "\\Omega",
            
            
            "+", "-", "*", "\\times", "\\div", "\\pm", "=", "\\neq", "<", ">", "\\leq", "\\geq",
            
            
            "\\sin", "\\cos", "\\tan", "\\cot", "\\sec", "\\csc", "\\arcsin", "\\arccos", "\\arctan",
            "\\sinh", "\\cosh", "\\tanh", "\\log", "\\ln", "\\exp", "\\sqrt", "\\sum", "\\prod",
            "\\int", "\\partial", "\\nabla", "\\infty",
            
            
            "\\det", "det",
            
            
            "\\frac{}{}", "\\sqrt{}", "\\sqrt[3]{}", "^{2}", "_{2}", "^{}", "_{}",
            "\\begin{matrix}", "\\begin{pmatrix}", "\\begin{bmatrix}", "\\begin{vmatrix}"
        };
        
        for (String s : supported) {
            if (s.equals(symbol)) {
                return true;
            }
        }
        return false;
    }

        public MathEquationEditorDialog(Frame owner, LatexEditorPanel parent) {
            super(owner, "Math Equation Editor", true);
            this.parentPanel = parent;
            
            setSize(900, 800); 
            setLocationRelativeTo(owner);
            
            
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBackground(new Color(13, 13, 35));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            
            JPanel editorPanel = new JPanel(new BorderLayout());
            editorPanel.setBackground(new Color(13, 13, 35));
            editorPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
                "LaTeX Editor",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                Color.WHITE
            ));
            
            
            latexEditor = new RSyntaxTextArea(8, 60);
            latexEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_LATEX);
            latexEditor.setBackground(new Color(16, 16, 53));
            latexEditor.setForeground(Color.WHITE);
            latexEditor.setCaretColor(Color.WHITE);
            latexEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
            latexEditor.setText(parent.getLatexText());
            
            
            latexEditor.setCurrentLineHighlightColor(new Color(20, 20, 80)); 
    
            
            applyStandardColorScheme(latexEditor);
            
            
            previewUpdateTimer = new Timer(400, e -> {
                if (previewUpdatePending) {
                    doUpdatePreview();
                    previewUpdatePending = false;
                }
            });
            previewUpdateTimer.setRepeats(false);
            
            
            latexEditor.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { requestPreviewUpdate(); }
                public void removeUpdate(DocumentEvent e) { requestPreviewUpdate(); }
                public void changedUpdate(DocumentEvent e) { requestPreviewUpdate(); }
            });
            
            RTextScrollPane editorScrollPane = new RTextScrollPane(latexEditor);
            editorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            editorScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            editorScrollPane.getGutter().setBackground(new Color(13, 13, 35));
            editorScrollPane.getGutter().setBorderColor(new Color(13, 13, 35));
            customizeScrollBars(editorScrollPane);
            
            editorPanel.add(editorScrollPane, BorderLayout.CENTER);
            
            
            JPanel quickButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            quickButtonsPanel.setBackground(new Color(13, 13, 35));
            
            String[] quickButtons = {
                "Fraction", "\\frac{}{}", 
                "Sqrt", "\\sqrt{}", 
                "Power", "^{}", 
                "Subscript", "_{}", 
                "Matrix", "\\begin{pmatrix}\n  a & b \\\\\n  c & d\n\\end{pmatrix}",
                "Cases", "\\begin{cases}\n  f(x) & x > 0 \\\\\n  g(x) & x < 0\n\\end{cases}"
            };
            
            for (int i = 0; i < quickButtons.length; i += 2) {
                JButton button = new JButton(quickButtons[i]);
                button.setBackground(new Color(0x171717));
                button.setForeground(new Color(0x9CDCFE));  
                final String insertText = quickButtons[i+1];
                button.addActionListener(e -> insertLaTeXTemplate(insertText));
                quickButtonsPanel.add(button);
            }
            
            editorPanel.add(quickButtonsPanel, BorderLayout.SOUTH);
            
            
            JPanel previewPanel = new JPanel(new BorderLayout());
            previewPanel.setBackground(new Color(13, 13, 35));
            previewPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
                "Preview",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 12),
                Color.WHITE
            ));
            
            previewPane = new JTextPane();
            previewPane.setContentType("text/html");
            previewPane.setEditable(false);
            previewPane.setBackground(new Color(16, 16, 53));
            
            JScrollPane previewScrollPane = new JScrollPane(previewPane);
            previewScrollPane.setPreferredSize(new Dimension(500, 300)); 
            previewScrollPane.setBackground(new Color(13, 13, 35));
            customizeScrollBars(previewScrollPane);
            previewPanel.add(previewScrollPane, BorderLayout.CENTER);
            
            
            JTabbedPane symbolTabs = new JTabbedPane();
            symbolTabs.setBackground(new Color(13, 13, 35));
            symbolTabs.setForeground(Color.WHITE);
            
            
            symbolTabs.addTab("Greek & Basic", createSymbolPanel(new String[][] {
                {"α", "\\alpha"}, {"β", "\\beta"}, {"γ", "\\gamma"}, {"δ", "\\delta"}, {"ε", "\\epsilon"}, {"ζ", "\\zeta"}, {"η", "\\eta"}, {"θ", "\\theta"},
                {"ι", "\\iota"}, {"κ", "\\kappa"}, {"λ", "\\lambda"}, {"μ", "\\mu"}, {"ν", "\\nu"}, {"ξ", "\\xi"}, {"π", "\\pi"}, {"ρ", "\\rho"},
                {"σ", "\\sigma"}, {"τ", "\\tau"}, {"υ", "\\upsilon"}, {"φ", "\\phi"}, {"χ", "\\chi"}, {"ψ", "\\psi"}, {"ω", "\\omega"}, {"Γ", "\\Gamma"},
                {"Δ", "\\Delta"}, {"Θ", "\\Theta"}, {"Λ", "\\Lambda"}, {"Ξ", "\\Xi"}, {"Π", "\\Pi"}, {"Σ", "\\Sigma"}, {"Φ", "\\Phi"}, {"Ψ", "\\Psi"},
                {"Ω", "\\Omega"}, {"+", "+"}, {"-", "-"}, {"=", "="}, {"×", "\\times"}, {"÷", "\\div"}, {"±", "\\pm"}, {"≠", "\\neq"}
            }));
            
            
            symbolTabs.addTab("Operators", createSymbolPanel(new String[][] {
                {"∑", "\\sum"}, {"∏", "\\prod"}, {"∫", "\\int"}, {"∮", "\\oint"}, {"∇", "\\nabla"}, {"∂", "\\partial"}, {"≤", "\\leq"}, {"≥", "\\geq"},
                {"≡", "\\equiv"}, {"≈", "\\approx"}, {"∘", "\\circ"}, {"∙", "\\bullet"}, {"⊕", "\\oplus"}, {"⊗", "\\otimes"}, {"∧", "\\wedge"}, {"∨", "\\vee"},
                {"∩", "\\cap"}, {"∪", "\\cup"}, {"⊂", "\\subset"}, {"⊃", "\\supset"}, {"∈", "\\in"}, {"∉", "\\notin"}, {"∞", "\\infty"}, {"∀", "\\forall"},
                {"∃", "\\exists"}, {"∄", "\\nexists"}, {"⇒", "\\Rightarrow"}, {"⇔", "\\Leftrightarrow"}, {"→", "\\rightarrow"}, {"←", "\\leftarrow"}, {"↑", "\\uparrow"}, {"↓", "\\downarrow"}
            }));
            
            
            symbolTabs.addTab("Functions", createSymbolPanel(new String[][] {
                {"sin", "\\sin"}, {"cos", "\\cos"}, {"tan", "\\tan"}, {"cot", "\\cot"}, {"sec", "\\sec"}, {"csc", "\\csc"}, {"arcsin", "\\arcsin"}, {"arccos", "\\arccos"},
                {"arctan", "\\arctan"}, {"sinh", "\\sinh"}, {"cosh", "\\cosh"}, {"tanh", "\\tanh"}, {"log", "\\log"}, {"ln", "\\ln"}, {"exp", "\\exp"}, {"lim", "\\lim"},
                {"max", "\\max"}, {"min", "\\min"}, {"sup", "\\sup"}, {"inf", "\\inf"}, {"det", "\\det"}, {"gcd", "\\gcd"}, {"Pr", "\\Pr"}, {"deg", "\\deg"}
            }));
            
            
            symbolTabs.addTab("Structures", createSymbolPanel(new String[][] {
                {"a/b", "\\frac{}{}"}, {"√", "\\sqrt{}"}, {"∛", "\\sqrt[3]{}"}, {"x²", "^{2}"}, {"x₂", "_{2}"}, {"x̄", "\\bar{x}"}, {"x̂", "\\hat{x}"}, {"x̃", "\\tilde{x}"},
                {"Matrix", "\\begin{matrix}\n  a & b \\\\\n  c & d\n\\end{matrix}"},
                {"Pmatrix", "\\begin{pmatrix}\n  a & b \\\\\n  c & d\n\\end{pmatrix}"},
                {"Bmatrix", "\\begin{bmatrix}\n  a & b \\\\\n  c & d\n\\end{bmatrix}"},
                {"Vmatrix", "\\begin{vmatrix}\n  a & b \\\\\n  c & d\n\\end{vmatrix}"},
                {"Cases", "\\begin{cases}\n  f(x) & x > 0 \\\\\n  g(x) & x < 0\n\\end{cases}"},
                {"Array", "\\begin{array}{cc}\n  a & b \\\\\n  c & d\n\\end{array}"}
            }));
            
            
            symbolTabs.addTab("Advanced", createSymbolPanel(new String[][] {
                {"hat", "\\hat{}"}, {"bar", "\\bar{}"}, {"vec", "\\vec{}"}, {"dot", "\\dot{}"}, {"ddot", "\\ddot{}"}, {"overline", "\\overline{}"}, {"underline", "\\underline{}"}, {"widehat", "\\widehat{}"},
                {"widetilde", "\\widetilde{}"}, {"overbrace", "\\overbrace{}"}, {"underbrace", "\\underbrace{}"}, {"stackrel", "\\stackrel{}{}"}, {"frac", "\\frac{}{}"}, {"sqrt", "\\sqrt[]{}"},
                {"binom", "\\binom{}{}"}, {"left|right|", "\\left| \\right|"}, {"left(right)", "\\left( \\right)"}, {"left[right]", "\\left[ \\right]"}, {"left{right}", "\\left\\{ \\right\\}"}
            }));
            
            
            symbolTabs.addTab("Physics", createSymbolPanel(new String[][] {
                {"hbar", "\\hbar"}, {"ℏ", "\\hslash"}, {"ħ", "\\hbar"}, {"℘", "\\wp"}, {"℮", "\\mathcal{e}"}, {"ℜ", "\\Re"}, {"ℑ", "\\Im"}, {"ð", "\\partial"},
                {"∇", "\\nabla"}, {"∆", "\\Delta"}, {"∑", "\\sum"}, {"∏", "\\prod"}, {"∫", "\\int"}, {"∬", "\\iint"}, {"∭", "\\iiint"}, {"∮", "\\oint"},
                {"∞", "\\infty"}, {"†", "\\dagger"}, {"‡", "\\ddagger"}, {"∝", "\\propto"}, {"≈", "\\approx"}, {"≠", "\\neq"}, {"≡", "\\equiv"}, {"≤", "\\leq"},
                {"≥", "\\geq"}, {"≪", "\\ll"}, {"≫", "\\gg"}, {"∧", "\\wedge"}, {"∨", "\\vee"}, {"∩", "\\cap"}, {"∪", "\\cup"}, {"⊥", "\\perp"}
            }));
            
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(new Color(13, 13, 35));
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setBackground(new Color(0x171717));
            cancelButton.setForeground(Color.WHITE);
            cancelButton.addActionListener(e -> dispose());
            
            JButton applyButton = new JButton("Apply");
            applyButton.setBackground(new Color(0x171717));
            applyButton.setForeground(new Color(0x4EC9B0)); 
            applyButton.addActionListener(e -> {
                parentPanel.setLatexText(latexEditor.getText());
                dispose();
            });
            
            buttonPanel.add(cancelButton);
            buttonPanel.add(applyButton);
            
            
            mainPanel.add(editorPanel, BorderLayout.NORTH);
            mainPanel.add(previewPanel, BorderLayout.CENTER);
            mainPanel.add(symbolTabs, BorderLayout.SOUTH);
            
            
            symbolTabs.setPreferredSize(new Dimension(880, 300));
            
            
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(new Color(13, 13, 35));
            contentPanel.add(mainPanel, BorderLayout.CENTER);
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            setContentPane(contentPanel);
            
            
            requestPreviewUpdate();
        }
        
        private void applyStandardColorScheme(RSyntaxTextArea editor) {
            SyntaxScheme scheme = editor.getSyntaxScheme();
            
            
            Color purpleKeyword = new Color(0xC586C0);    
            Color tanFunction = new Color(0xDCDCAA);      
            Color blueTeal = new Color(0x4EC9B0);         
            Color lightBlue = new Color(0x9CDCFE);        
            Color salmon = new Color(0xCE9178);           
            Color green = new Color(0x6A9955);            
            Color lightGreen = new Color(0xB5CEA8);       
            Color gray = new Color(0xD4D4D4);             
            
            
            scheme.getStyle(Token.RESERVED_WORD).foreground = purpleKeyword;
            scheme.getStyle(Token.RESERVED_WORD_2).foreground = purpleKeyword;
            scheme.getStyle(Token.FUNCTION).foreground = tanFunction;
            scheme.getStyle(Token.IDENTIFIER).foreground = lightBlue;
            scheme.getStyle(Token.OPERATOR).foreground = gray;
            scheme.getStyle(Token.SEPARATOR).foreground = gray;
            scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = salmon;
            scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = lightGreen;
            scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = lightGreen;
            scheme.getStyle(Token.COMMENT_MULTILINE).foreground = green;
            scheme.getStyle(Token.COMMENT_EOL).foreground = green;
            scheme.getStyle(Token.PREPROCESSOR).foreground = new Color(0xD16969);
            scheme.getStyle(Token.VARIABLE).foreground = lightBlue;
            scheme.getStyle(Token.DATA_TYPE).foreground = blueTeal;
            
            
            editor.setSyntaxScheme(scheme);
            
            
            editor.setBracketMatchingEnabled(true);
            editor.setPaintMatchedBracketPair(true);
            editor.setMatchedBracketBGColor(new Color(0x2D3147));
            editor.setMatchedBracketBorderColor(new Color(0x646464));
        }
        
        private void requestPreviewUpdate() {
            previewUpdatePending = true;
            if (!previewUpdateTimer.isRunning()) {
                previewUpdateTimer.start();
            }
        }
        
        private void doUpdatePreview() {
            String latex = latexEditor.getText().trim();
            if (latex.isEmpty()) {
                previewPane.setText("<html><body style='color:gray; background-color:#101035; padding:10px;'>Enter LaTeX equation</body></html>");
                return;
            }
            
            try {
                
                TeXFormula formula = new TeXFormula(latex);
                formula.setColor(Color.WHITE);
                
                
                TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);
                icon.setForeground(Color.WHITE);
                
                
                BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();
                Color darkBlue = new Color(0x0D0D23); 
                g2.setColor(darkBlue);
                g2.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
                icon.paintIcon(null, g2, 0, 0);
                g2.dispose();
                
                
                File tempImageFile = File.createTempFile("latexPreview", ".png");
                ImageIO.write(image, "png", tempImageFile);
                tempImageFile.deleteOnExit();
                
                
                String fileUrl = "file:///" + tempImageFile.getAbsolutePath().replace("\\", "/");
                
                String html = "<html>" +
                              "<head>" +
                              "<style type='text/css'>" +
                                  "html, body { background-color: #0D0D23; margin: 0; padding: 0; }" +
                                  "img { display: block; margin: auto; }" +
                              "</style>" +
                              "</head>" +
                              "<body>" +
                              "<img src='" + fileUrl + "'/>" +
                              "</body>" +
                              "</html>";
                previewPane.setContentType("text/html");
                previewPane.setText(html);
                previewPane.revalidate();
                previewPane.repaint();
            } catch (Exception ex) {
                
                previewPane.setText("<html><body style='color:red; background-color:#101035; padding:10px;'>" +
                                   "Error rendering LaTeX: " + ex.getMessage() + 
                                   "</body></html>");
                ex.printStackTrace();
            }
        }
        
        private JPanel createSymbolPanel(String[][] symbols) {
            
            int totalSymbols = symbols.length;
            int columns = 8;
            int rows = (totalSymbols + columns - 1) / columns; 
            
            JPanel panel = new JPanel(new GridLayout(rows, columns, 5, 5));
            panel.setBackground(new Color(16, 16, 45));
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            for (String[] symbol : symbols) {
                JButton button = new JButton(symbol[0]);
                button.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
                button.setBackground(new Color(0x1a1a2e));
                button.setToolTipText(symbol[1]);
                button.setFocusPainted(false);
                button.setBorderPainted(true);
                button.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100), 1));
                
                
                final String symbolCode = symbol[1];
                
                
                
                if (isSymbolSupported(symbolCode)) {
                    button.setForeground(new Color(0x4EC9B0)); 
                } else {
                    button.setForeground(Color.GRAY); 
                }
                
                
                button.addActionListener(e -> insertSymbol(symbolCode));
                
                panel.add(button);
            }
            
            return panel;
        }
        
        private void insertSymbol(String symbol) {
            int pos = latexEditor.getCaretPosition();
            
            try {
                
                if (symbol.contains("\\frac{}{}")) {
                    latexEditor.getDocument().insertString(pos, "\\frac{}{}", null);
                    latexEditor.setCaretPosition(pos + 6); 
                } 
                else if (symbol.contains("\\sqrt{}")) {
                    latexEditor.getDocument().insertString(pos, "\\sqrt{}", null);
                    latexEditor.setCaretPosition(pos + 6); 
                }
                else if (symbol.contains("\\sqrt[3]{}")) {
                    latexEditor.getDocument().insertString(pos, "\\sqrt[3]{}", null);
                    latexEditor.setCaretPosition(pos + 9); 
                }
                else if (symbol.contains("^{")) {
                    latexEditor.getDocument().insertString(pos, symbol, null);
                    latexEditor.setCaretPosition(pos + symbol.length() - 1); 
                }
                else if (symbol.contains("_{")) {
                    latexEditor.getDocument().insertString(pos, symbol, null);
                    latexEditor.setCaretPosition(pos + symbol.length() - 1); 
                }
                else if (symbol.contains("\\begin{")) {
                    latexEditor.getDocument().insertString(pos, symbol, null);
                    
                    int newPosition = pos + symbol.indexOf("\n") + 3;
                    latexEditor.setCaretPosition(newPosition);
                }
                else if (symbol.contains("\\bar{") || symbol.contains("\\hat{") || 
                         symbol.contains("\\tilde{")) {
                    
                    String modifiedSymbol = symbol.replace("x", ""); 
                    latexEditor.getDocument().insertString(pos, modifiedSymbol, null);
                    latexEditor.setCaretPosition(pos + modifiedSymbol.length() - 1);
                }
                else {
                    
                    latexEditor.getDocument().insertString(pos, symbol, null);
                    latexEditor.setCaretPosition(pos + symbol.length());
                }
                
                
                requestPreviewUpdate();
                
                
                latexEditor.requestFocusInWindow();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        private void insertLaTeXTemplate(String template) {
            int pos = latexEditor.getCaretPosition();
            try {
                if (template.contains("\\frac{}{}")) {
                    latexEditor.getDocument().insertString(pos, template, null);
                    latexEditor.setCaretPosition(pos + 6); 
                } 
                else if (template.contains("\\sqrt{}")) {
                    latexEditor.getDocument().insertString(pos, template, null);
                    latexEditor.setCaretPosition(pos + 6); 
                }
                else if (template.contains("^{}")) {
                    latexEditor.getDocument().insertString(pos, template, null);
                    latexEditor.setCaretPosition(pos + 2); 
                }
                else if (template.contains("_{}")) {
                    latexEditor.getDocument().insertString(pos, template, null);
                    latexEditor.setCaretPosition(pos + 2); 
                }
                else if (template.contains("\\begin{")) {
                    latexEditor.getDocument().insertString(pos, template, null);
                    
                    int newPosition = pos + template.indexOf("\n") + 3;
                    latexEditor.setCaretPosition(newPosition);
                }
                else {
                    
                    latexEditor.getDocument().insertString(pos, template, null);
                    latexEditor.setCaretPosition(pos + template.length());
                }
                
                
                requestPreviewUpdate();
                
                
                latexEditor.requestFocusInWindow();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        private void customizeScrollBars(JScrollPane scrollPane) {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            if (vBar != null) {
                vBar.setBackground(new Color(13, 13, 35));
                vBar.setUI(new BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(40, 40, 100);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
            }
            
            JScrollBar hBar = scrollPane.getHorizontalScrollBar();
            if (hBar != null) {
                hBar.setBackground(new Color(13, 13, 35));
                hBar.setUI(new BasicScrollBarUI() {
                    @Override
                    protected void configureScrollBarColors() {
                        this.thumbColor = new Color(40, 40, 100);
                        this.trackColor = new Color(20, 20, 50);
                    }
                });
            }
        }
    }
}

class SyntaxErrorParser extends AbstractParser {
    private RSyntaxTextArea textArea;
    private List<DefaultParserNotice> notices;
    
    
    public enum ErrorLevel {
        ERROR, WARNING, INFO
    }
    
    public SyntaxErrorParser(RSyntaxTextArea textArea) {
        this.textArea = textArea;
        this.notices = new ArrayList<>();
    }

    public void addNotice(int line, int offset, int length, String message, ErrorLevel level) {
        System.out.println("Adding error notice: " + message + " at line " + line);
        
        
        DefaultParserNotice notice;
        
        if (offset >= 0 && length > 0) {
            try {
                
                Color color = (level == ErrorLevel.ERROR) ? 
                            Color.RED : 
                            (level == ErrorLevel.WARNING ? Color.ORANGE : Color.BLUE);
                            
                
                notice = new DefaultParserNotice(this, message, line, offset, length);
                notice.setColor(color);
                
                
                java.lang.reflect.Field colorField = DefaultParserNotice.class.getDeclaredField("color");
                if (colorField != null) {
                    colorField.setAccessible(true);
                    colorField.set(notice, color);
                }
            } catch (Exception e) {
                
                notice = new DefaultParserNotice(this, message, line);
            }
        } else {
            notice = new DefaultParserNotice(this, message, line);
        }
        
        
        switch (level) {
            case WARNING:
                notice.setLevel(ParserNotice.Level.WARNING);
                break;
            case INFO:
                notice.setLevel(ParserNotice.Level.INFO);
                break;
            case ERROR:
            default:
                notice.setLevel(ParserNotice.Level.ERROR);
                break;
        }
        
        notices.add(notice);
        
        
        textArea.forceReparsing(this);
    }
    
    @Override
    public ParseResult parse(RSyntaxDocument doc, String style) {
        DefaultParseResult result = new DefaultParseResult(this);
        
        
        for (DefaultParserNotice notice : notices) {
            result.addNotice(notice);
        }
        
        return result;
    }
    
    
    public void clearNotices() {
        notices.clear();
        
        
        textArea.forceReparsing(this);
    }
}

abstract class ErrorChecker {
    protected RSyntaxTextArea editor;
    protected JupyterKernelClient kernelClient;
    protected Timer checkTimer;
    protected boolean isCheckingEnabled = true;
    protected SyntaxErrorParser parser;
    
    public ErrorChecker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
        this.editor = editor;
        this.kernelClient = kernelClient;
        
        
        this.parser = new SyntaxErrorParser(editor);
        
        
        editor.addParser(parser);
    }
    
    
    public abstract void checkErrors();
    
    

    public void safeCheckErrors() {
        
        if (!isCheckingEnabled || editor == null || !editor.isShowing()) {
            return;
        }
        
        
        CompletableFuture.runAsync(() -> {
            try {
                checkErrors();
            } catch (Exception e) {
                
                System.err.println("Error during syntax check: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    public void setCheckingEnabled(boolean enabled) {
        this.isCheckingEnabled = enabled;
    }
    
    
    public void dispose() {
        if (checkTimer != null) {
            checkTimer.stop();
            checkTimer = null;
        }
        
        
        if (editor != null && parser != null) {
            editor.removeParser(parser);
        }
    }
    
    
    protected void addError(int line, int offset, int length, String message, SyntaxErrorParser.ErrorLevel level) {
        parser.addNotice(line, offset, length, message, level);
    }
    
    
    protected void clearErrors() {
        parser.clearNotices();
    }
    
    
    protected int getLineOffset(int line) {
        try {
            return editor.getLineStartOffset(line);
        } catch (BadLocationException e) {
            return 0;
        }
    }
}
    class PythonFlake8Checker extends ErrorChecker {
        
        public PythonFlake8Checker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
            super(editor, kernelClient);
        }
        @Override
        public void checkErrors() {
            
            System.out.println("ErrorChecker.checkErrors() called");
            
            
            isCheckingEnabled = true;
            
            
            if (kernelClient == null) {
                return;
            }
            
            String code = editor.getText();
            if (code.trim().isEmpty()) {
                clearErrors();
                return;
            }
            
            
            String codeEscaped = code.replace("\\", "\\\\")
                                     .replace("\"", "\\\"")
                                     .replace("\n", "\\n");
            
            
            
            
            
            String checkCode =
            "import json, ast, tokenize, io, re, tempfile, subprocess, os\n" +
            "\n" +
            "def ast_check(code):\n" +
            "    results = []\n" +
            "    try:\n" +
            "        ast.parse(code)\n" +
            "    except SyntaxError as e:\n" +
            "        line = e.lineno - 1 if e.lineno else 0\n" +
            "        col = e.offset - 1 if e.offset else 0\n" +
            "        results.append({\n" +
            "            'line': line,\n" +
            "            'column': col,\n" +
            "            'length': 1,\n" +
            "            'message': 'SyntaxError: ' + str(e),\n" +
            "            'type': 'error'\n" +
            "        })\n" +
            "    return results\n" +
            "\n" +
            "def flake8_check(code):\n" +
            "    results = []\n" +
            "    try:\n" +
            "        with tempfile.NamedTemporaryFile(suffix='.py', delete=False) as tf:\n" +
            "            tf.write(code.encode('utf-8'))\n" +
            "            temp_path = tf.name\n" +
            "        proc = subprocess.run([\n" +
            "            'flake8', '--format=json', '--max-line-length=120', temp_path\n" +
            "        ], capture_output=True, text=True, timeout=2)\n" +
            "        if proc.stdout:\n" +
            "            try:\n" +
            "                issues = json.loads(proc.stdout)\n" +
            "                for filepath, fileIssues in issues.items():\n" +
            "                    for issue in fileIssues:\n" +
            "                        line_num = issue.get('line_number', 1) - 1\n" +
            "                        col_num = issue.get('column_number', 1) - 1\n" +
            "                        code_val = issue.get('code', '')\n" +
            "                        text_val = issue.get('text', '')\n" +
            "                        results.append({\n" +
            "                            'line': line_num,\n" +
            "                            'column': col_num,\n" +
            "                            'length': 1,\n" +
            "                            'message': code_val + ': ' + text_val,\n" +
            "                            'type': 'warning' if code_val.startswith('W') else 'error'\n" +
            "                        })\n" +
            "            except Exception:\n" +
            "                pass\n" +
            "        os.unlink(temp_path)\n" +
            "    except Exception:\n" +
            "        pass\n" +
            "    return results\n" +
            "\n" +
            "def delimiter_check(code):\n" +
            "    results = []\n" +
            "    try:\n" +
            "        g = tokenize.tokenize(io.BytesIO(code.encode('utf-8')).readline)\n" +
            "        stack = []\n" +
            "        for tok in g:\n" +
            "            if tok.type == tokenize.OP:\n" +
            "                if tok.string in '([{':\n" +
            "                    stack.append((tok.string, tok.start))\n" +
            "                elif tok.string in ')]}':\n" +
            "                    if not stack:\n" +
            "                        results.append({\n" +
            "                            'line': tok.start[0]-1,\n" +
            "                            'column': tok.start[1],\n" +
            "                            'length': len(tok.string),\n" +
            "                            'message': 'Unmatched closing delimiter: ' + tok.string,\n" +
            "                            'type': 'error'\n" +
            "                        })\n" +
            "                    else:\n" +
            "                        opener, start_pos = stack.pop()\n" +
            "                        pairs = {'(': ')', '[': ']', '{': '}'}\n" +
            "                        if pairs.get(opener, '') != tok.string:\n" +
            "                            results.append({\n" +
            "                                'line': tok.start[0]-1,\n" +
            "                                'column': tok.start[1],\n" +
            "                                'length': len(tok.string),\n" +
            "                                'message': 'Mismatched delimiter: expected ' + pairs.get(opener, '?') + ', got ' + tok.string,\n" +
            "                                'type': 'error'\n" +
            "                            })\n" +
            "        for opener, start in stack:\n" +
            "            results.append({\n" +
            "                'line': start[0]-1,\n" +
            "                'column': start[1],\n" +
            "                'length': 1,\n" +
            "                'message': 'Unclosed delimiter: ' + opener,\n" +
            "                'type': 'error'\n" +
            "            })\n" +
            "    except Exception as e:\n" +
            "        pass\n" +
            "    return results\n" +
            "\n" +
            "def parso_check(code):\n" +
            "    import parso\n" +
            "    from parso.python.tree import ErrorLeaf, ErrorNode\n" +
            "    def collect_parso_errors(node, results=None):\n" +
            "        if results is None:\n" +
            "            results = []\n" +
            "        if isinstance(node, (ErrorLeaf, ErrorNode)):\n" +
            "            line, col = node.start_pos\n" +
            "            token_text = node.get_code() if hasattr(node, 'get_code') else \"\"\n" +
            "            length = len(token_text) if token_text else 1\n" +
            "            message = node.get_error_message() if hasattr(node, 'get_error_message') else \"Syntax error detected\"\n" +
            "            results.append({'line': line - 1, 'column': col, 'length': length, 'message': message, 'type': 'error'})\n" +
            "        if hasattr(node, 'children'):\n" +
            "            for child in node.children:\n" +
            "                collect_parso_errors(child, results)\n" +
            "        return results\n" +
            "    module = parso.parse(code, error_recovery=True)\n" +
            "    return collect_parso_errors(module)\n" +
            "\n" +
            "def check_all(code):\n" +
            "    issues = []\n" +
            "    issues.extend(ast_check(code))\n" +
            "    issues.extend(flake8_check(code))\n" +
            "    issues.extend(delimiter_check(code))\n" +
            "    issues.extend(parso_check(code))\n" +
            "    return json.dumps(issues)\n" +
            "\n" +
            "print(check_all(\"\"\"" + codeEscaped + "\"\"\"))\n";
        
            
            kernelClient.executeCode(checkCode)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    try {
                        clearErrors();
                        String jsonStr = result.trim();
                        if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                            JSONArray issues = new JSONArray(jsonStr);
                            
                            for (int i = 0; i < issues.length(); i++) {
                                JSONObject issue = issues.getJSONObject(i);
                                int line = issue.getInt("line");
                                int col = issue.getInt("column");
                                int length = issue.optInt("length", 1);
                                String message = issue.getString("message");
                                String type = issue.optString("type", "error");
                                
                                SyntaxErrorParser.ErrorLevel level = type.equals("warning")
                                        ? SyntaxErrorParser.ErrorLevel.WARNING
                                        : SyntaxErrorParser.ErrorLevel.ERROR;
                                
                                int offset = getLineOffset(line) + col;
                                
                                
                                if (length <= 1) {
                                    try {
                                        String lineText = editor.getText(getLineOffset(line),
                                            editor.getLineEndOffset(line) - getLineOffset(line));
                                        if (col < lineText.length()) {
                                            length = findTokenLength(lineText, col);
                                        }
                                    } catch (BadLocationException e) {
                                        length = 1;
                                    }
                                }
                                
                                addError(line, offset, length, message, level);
                            }
                        }
                        
                        editor.repaint();
                    } catch (Exception e) {
                        System.err.println("Error parsing syntax check result: " + e.getMessage());
                        e.printStackTrace();
                    }
                }))
                .exceptionally(e -> {
                    System.err.println("Error checking Python syntax: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });
                
                if (editor != null) {
                    editor.forceReparsing(parser);
                    editor.repaint();
                }
            }
        
    
    
    private int findTokenLength(String lineText, int column) {
        int end = column;
        
        if (column >= lineText.length()) {
            return 1;
        }
        
        char c = lineText.charAt(column);
        
        
        if (Character.isJavaIdentifierStart(c)) {
            while (end < lineText.length() && Character.isJavaIdentifierPart(lineText.charAt(end))) {
                end++;
            }
        }
        
        else if (c == '"' || c == '\'') {
            char quote = c;
            end++;
            boolean escaped = false;
            while (end < lineText.length()) {
                char curr = lineText.charAt(end);
                if (curr == '\\') {
                    escaped = !escaped;
                } else if (curr == quote && !escaped) {
                    end++;
                    break;
                } else {
                    escaped = false;
                }
                end++;
            }
        }
        
        else if (!Character.isWhitespace(c)) {
            
            if (end+1 < lineText.length()) {
                char next = lineText.charAt(end+1);
                if ((c == '+' && next == '+') || 
                    (c == '-' && next == '-') ||
                    (c == '=' && next == '=') ||
                    (c == '!' && next == '=') ||
                    (c == '<' && next == '=') ||
                    (c == '>' && next == '=') ||
                    (c == '&' && next == '&') ||
                    (c == '|' && next == '|')) {
                    end += 2;
                } else {
                    end++;
                }
            } else {
                end++;
            }
        } else {
            end++;
        }
        
        
        return Math.max(1, end - column);
    }
}

    class NimChecker extends ErrorChecker {

        public NimChecker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
            super(editor, kernelClient);
        }

        
        @Override
        public void checkErrors() {
            
            System.out.println("Running Nim error check");
            
            if (kernelClient == null) {
                System.out.println("No kernel client available for Nim check");
                return;
            }
            
            String code = editor.getText();
            if (code.trim().isEmpty()) {
                clearErrors();
                return;
            }
            
            
            clearErrors();
            
            
            String codeEscaped = code.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n");
            
            
            String checkCode =
                "import json, os, tempfile, subprocess, re\n" +
                "\n" +
                "def check_nim_code(code):\n" +
                "    results = []\n" +
                "    try:\n" +
                "        # Write Nim code to temporary file\n" +
                "        with tempfile.NamedTemporaryFile(suffix='.nim', delete=False, mode='w', encoding='utf-8') as tf:\n" +
                "            tf.write(code)\n" +
                "            temp_path = tf.name\n" +
                "            print(f\"Created temporary Nim file: {temp_path}\")\n" +
                "        \n" +
                "        # Run nim check command with better error handling\n" +
                "        try:\n" +
                "            cmd = ['nim', 'check', '--hints:off', '--colors:off', '--stdout', temp_path]\n" +
                "            print(f\"Running command: {' '.join(cmd)}\")\n" +
                "            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=3)\n" +
                "            \n" +
                "            output = proc.stderr if proc.stderr else proc.stdout\n" +
                "            print(f\"Command output:\\n{output}\")\n" +
                "            \n" +
                "            # Define a regex for Nim errors\n" +
                "            error_regex = re.compile(r'(.+?)\\((\\d+),\\s*(\\d+)\\)\\s+(.+?):\\s+(.+)$')\n" +
                "            \n" +
                "            for line in output.splitlines():\n" +
                "                match = error_regex.match(line)\n" +
                "                if match:\n" +
                "                    file_path, line_num, col_num, err_type, message = match.groups()\n" +
                "                    line_idx = int(line_num) - 1  # Convert to 0-based\n" +
                "                    col = int(col_num) - 1        # Convert to 0-based\n" +
                "                    \n" +
                "                    # Add the error to results\n" +
                "                    results.append({\n" +
                "                        'line': line_idx,\n" +
                "                        'column': col,\n" +
                "                        'length': 1,  # Default length\n" +
                "                        'message': f'Nim {err_type}: {message}',\n" +
                "                        'type': 'error' if err_type.lower() == 'error' else 'warning'\n" +
                "                    })\n" +
                "                    print(f\"Found error: {err_type} at line {line_idx}, col {col}: {message}\")\n" +
                "        except subprocess.TimeoutExpired as te:\n" +
                "            print(f\"Command timed out: {te}\")\n" +
                "        except subprocess.SubprocessError as se:\n" +
                "            print(f\"Subprocess error: {se}\")\n" +
                "        except Exception as e:\n" +
                "            print(f\"Error checking Nim code: {e}\")\n" +
                "    finally:\n" +
                "        # Cleanup\n" +
                "        try:\n" +
                "            if 'temp_path' in locals() and os.path.exists(temp_path):\n" +
                "                os.unlink(temp_path)\n" +
                "                print(f\"Removed temporary file: {temp_path}\")\n" +
                "        except Exception as e:\n" +
                "            print(f\"Error during cleanup: {e}\")\n" +
                "    \n" +
                "    return results\n" +
                "\n" +
                "try:\n" +
                "    results = check_nim_code(\"\"\"" + codeEscaped + "\"\"\")\n" +
                "    print(json.dumps(results))\n" +
                "except Exception as e:\n" +
                "    print(f\"Uncaught error in check_nim_code: {e}\")\n" +
                "    print(json.dumps([]))\n";
            
            System.out.println("Sending Nim check to kernel");
            
            
            kernelClient.executeCode(checkCode)
                .thenAccept(result -> {
                    System.out.println("Nim check result received: " + result);
                    
                    SwingUtilities.invokeLater(() -> {
                        try {
                            
                            String jsonStr = null;
                            for (String line : result.split("\n")) {
                                if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                                    jsonStr = line.trim();
                                    break;
                                }
                            }
                            
                            if (jsonStr != null) {
                                JSONArray issues = new JSONArray(jsonStr);
                                System.out.println("Found " + issues.length() + " Nim issues");
                                
                                for (int i = 0; i < issues.length(); i++) {
                                    JSONObject issue = issues.getJSONObject(i);
                                    int line = issue.getInt("line");
                                    int col = issue.getInt("column");
                                    int length = issue.optInt("length", 1);
                                    String message = issue.getString("message");
                                    String type = issue.optString("type", "error");
                                    
                                    SyntaxErrorParser.ErrorLevel level = type.equals("warning") ? 
                                        SyntaxErrorParser.ErrorLevel.WARNING : SyntaxErrorParser.ErrorLevel.ERROR;
                                    
                                    int offset = getLineOffset(line) + col;
                                    
                                    
                                    if (length <= 1) {
                                        try {
                                            String lineText = editor.getText(getLineOffset(line),
                                                    editor.getLineEndOffset(line) - getLineOffset(line));
                                            if (col < lineText.length()) {
                                                length = findTokenLength(lineText, col);
                                            }
                                        } catch (Exception ex) {
                                            length = 1;
                                        }
                                    }
                                    
                                    System.out.println("Adding Nim error: " + message + " at line " + line);
                                    addError(line, offset, length, message, level);
                                }
                            } else {
                                System.out.println("No JSON output found in Nim result");
                            }
                            
                            
                            if (editor != null) {
                                editor.forceReparsing(parser);
                                editor.repaint();
                            }
                            
                        } catch (Exception e) {
                            System.err.println("Error parsing Nim check result: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                })
                .exceptionally(e -> {
                    System.err.println("Error running Nim check: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });
        }
        
        
        private int findTokenLength(String lineText, int column) {
            int end = column;
            if (column >= lineText.length()) {
                return 1;
            }
            char c = lineText.charAt(column);
            if (Character.isJavaIdentifierStart(c)) {
                while (end < lineText.length() && Character.isJavaIdentifierPart(lineText.charAt(end))) {
                    end++;
                }
            }
            else if (c == '"' || c == '\'') {
                char quote = c;
                end++;
                boolean escaped = false;
                while (end < lineText.length()) {
                    char curr = lineText.charAt(end);
                    if (curr == '\\') {
                        escaped = !escaped;
                    } else if (curr == quote && !escaped) {
                        end++;
                        break;
                    } else {
                        escaped = false;
                    }
                    end++;
                }
            }
            else if (!Character.isWhitespace(c)) {
                
                end++;
            } else {
                end++;
            }
            return Math.max(1, end - column);
        }
    }


    class VerticalFlowLayout implements LayoutManager {
        public static final int TOP = 0;
        public static final int CENTER = 1;
        public static final int BOTTOM = 2;
        public static final int RIGHT = 3;
        
        private int align;
        private int hgap;
        private int vgap;
        private boolean hfill;
        private boolean vfill;
        
        public VerticalFlowLayout() {
            this(TOP, 5, 5, true, false);
        }
        
        public VerticalFlowLayout(int align) {
            this(align, 5, 5, true, false);
        }
        
        public VerticalFlowLayout(int align, int hgap, int vgap) {
            this(align, hgap, vgap, true, false);
        }
        
        public VerticalFlowLayout(int align, int hgap, int vgap, boolean hfill, boolean vfill) {
            this.align = align;
            this.hgap = hgap;
            this.vgap = vgap;
            this.hfill = hfill;
            this.vfill = vfill;
        }
        
        public int getAlignment() { return align; }
        public void setAlignment(int align) { this.align = align; }
        public int getHgap() { return hgap; }
        public void setHgap(int hgap) { this.hgap = hgap; }
        public int getVgap() { return vgap; }
        public void setVgap(int vgap) { this.vgap = vgap; }
        public boolean isHfill() { return hfill; }
        public void setHfill(boolean hfill) { this.hfill = hfill; }
        public boolean isVfill() { return vfill; }
        public void setVfill(boolean vfill) { this.vfill = vfill; }
        
        @Override
        public void addLayoutComponent(String name, Component comp) {}
        
        @Override
        public void removeLayoutComponent(Component comp) {}
        
        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                Dimension dim = new Dimension(0, 0);
                int nmembers = target.getComponentCount();
                
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = m.getPreferredSize();
                        dim.width = Math.max(dim.width, d.width);
                        if (i > 0) {
                            dim.height += vgap;
                        }
                        dim.height += d.height;
                    }
                }
                
                Insets insets = target.getInsets();
                dim.width += insets.left + insets.right + hgap * 2;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
        
        @Override
        public Dimension minimumLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                Dimension dim = new Dimension(0, 0);
                int nmembers = target.getComponentCount();
                
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = m.getMinimumSize();
                        dim.width = Math.max(dim.width, d.width);
                        if (i > 0) {
                            dim.height += vgap;
                        }
                        dim.height += d.height;
                    }
                }
                
                Insets insets = target.getInsets();
                dim.width += insets.left + insets.right + hgap * 2;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
        
        @Override
        public void layoutContainer(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int maxwidth = target.getWidth() - (insets.left + insets.right + hgap * 2);
                int nmembers = target.getComponentCount();
                int y = insets.top + vgap;
                
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = m.getPreferredSize();
                        m.setSize(hfill ? maxwidth : Math.min(d.width, maxwidth), d.height);
                        
                        if (align == CENTER) {
                            m.setLocation(insets.left + hgap + (maxwidth - m.getWidth()) / 2, y);
                        } else if (align == RIGHT) {
                            m.setLocation(insets.left + hgap + (maxwidth - m.getWidth()), y);
                        } else {
                            m.setLocation(insets.left + hgap, y);
                        }
                        
                        y += d.height + vgap;
                    }
                }
            }
        }
    }

    class FutharkChecker extends ErrorChecker {
    
        public FutharkChecker(RSyntaxTextArea editor, JupyterKernelClient kernelClient) {
            super(editor, kernelClient);
        }
        
        @Override
        public void checkErrors() {
            if (kernelClient == null) return;
            
            String code = editor.getText();
            if (code.trim().isEmpty()) {
                clearErrors();
                return;
            }
            
            clearErrors();
            
            
            String codeEscaped = code.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n");
            
            
            String checkCode = 
                "import json, tempfile, subprocess, re, os\n" +
                "\n" +
                "def check_futhark_code(code):\n" +
                "    results = []\n" +
                "    try:\n" +
                "        # Write Futhark code to temporary file\n" +
                "        with tempfile.NamedTemporaryFile(suffix='.fut', delete=False, mode='w') as f:\n" +
                "            f.write(code)\n" +
                "            temp_path = f.name\n" +
                "        \n" +
                "        # Try to check with futhark compiler\n" +
                "        try:\n" +
                "            proc = subprocess.run(['futhark', 'check', temp_path], \n" +
                "                                capture_output=True, text=True, timeout=5)\n" +
                "            \n" +
                "            # Parse futhark check output\n" +
                "            for line in proc.stderr.splitlines():\n" +
                "                # Pattern: filename:line:col-col: error/warning: message\n" +
                "                # or: filename:line:col: error/warning: message\n" +
                "                match = re.match(r'.+?:(\\d+):(\\d+)(?:-(\\d+))?:\\s*(error|warning):\\s*(.+)', line)\n" +
                "                if match:\n" +
                "                    line_num = int(match.group(1)) - 1\n" +
                "                    col_start = int(match.group(2)) - 1\n" +
                "                    col_end = int(match.group(3)) - 1 if match.group(3) else col_start\n" +
                "                    err_type = match.group(4)\n" +
                "                    message = match.group(5)\n" +
                "                    \n" +
                "                    results.append({\n" +
                "                        'line': line_num,\n" +
                "                        'column': col_start,\n" +
                "                        'length': max(1, col_end - col_start + 1),\n" +
                "                        'message': f'Futhark {err_type}: {message}',\n" +
                "                        'type': 'error' if err_type == 'error' else 'warning'\n" +
                "                    })\n" +
                "        except (subprocess.TimeoutExpired, FileNotFoundError):\n" +
                "            # Futhark compiler not available, fall back to basic checks\n" +
                "            results.extend(basic_futhark_check(code))\n" +
                "    finally:\n" +
                "        try:\n" +
                "            os.unlink(temp_path)\n" +
                "        except:\n" +
                "            pass\n" +
                "    \n" +
                "    return results\n" +
                "\n" +
                "def basic_futhark_check(code):\n" +
                "    \"\"\"Basic syntax checking when futhark compiler is not available\"\"\"\n" +
                "    results = []\n" +
                "    lines = code.split('\\n')\n" +
                "    \n" +
                "    # Check for common Futhark syntax patterns\n" +
                "    for i, line in enumerate(lines):\n" +
                "        # Check for unmatched brackets\n" +
                "        if line.count('[') != line.count(']'):\n" +
                "            results.append({\n" +
                "                'line': i,\n" +
                "                'column': 0,\n" +
                "                'length': len(line),\n" +
                "                'message': 'Unmatched brackets',\n" +
                "                'type': 'error'\n" +
                "            })\n" +
                "        \n" +
                "        # Check for common keyword errors\n" +
                "        if re.search(r'\\bdef\\s+def\\b', line):\n" +
                "            results.append({\n" +
                "                'line': i,\n" +
                "                'column': line.find('def def'),\n" +
                "                'length': 7,\n" +
                "                'message': 'Duplicate def keyword',\n" +
                "                'type': 'error'\n" +
                "            })\n" +
                "        \n" +
                "        # Check for missing then in if expressions\n" +
                "        if re.search(r'\\bif\\b.*\\belse\\b', line) and 'then' not in line:\n" +
                "            results.append({\n" +
                "                'line': i,\n" +
                "                'column': line.find('if'),\n" +
                "                'length': 2,\n" +
                "                'message': 'Missing then in if expression',\n" +
                "                'type': 'error'\n" +
                "            })\n" +
                "    \n" +
                "    return results\n" +
                "\n" +
                "results = check_futhark_code(\"\"\"" + codeEscaped + "\"\"\")\n" +
                "print(json.dumps(results))\n";
            
            kernelClient.executeCode(checkCode)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    try {
                        String jsonStr = result.trim();
                        
                        int jsonStart = jsonStr.lastIndexOf("[");
                        int jsonEnd = jsonStr.lastIndexOf("]");
                        if (jsonStart >= 0 && jsonEnd > jsonStart) {
                            jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                            
                            JSONArray issues = new JSONArray(jsonStr);
                            for (int i = 0; i < issues.length(); i++) {
                                JSONObject issue = issues.getJSONObject(i);
                                int line = issue.getInt("line");
                                int col = issue.getInt("column");
                                int length = issue.optInt("length", 1);
                                String message = issue.getString("message");
                                String type = issue.optString("type", "error");
                                
                                SyntaxErrorParser.ErrorLevel level = type.equals("warning") ?
                                    SyntaxErrorParser.ErrorLevel.WARNING : SyntaxErrorParser.ErrorLevel.ERROR;
                                
                                int offset = getLineOffset(line) + col;
                                addError(line, offset, length, message, level);
                            }
                        }
                        editor.forceReparsing(parser);
                        editor.repaint();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
        }
    }

