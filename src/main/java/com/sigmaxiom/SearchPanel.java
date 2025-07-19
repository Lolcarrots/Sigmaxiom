package com.sigmaxiom;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.swing.Timer;


public class SearchPanel extends JPanel {
    private JTextField searchField;
    private JButton prevButton;
    private JButton nextButton;
    private JButton closeButton;
    private JLabel matchesLabel;
    private JCheckBox caseSensitiveCheck;
    
    private JupyterNotebookIDE ide;
    private List<SearchMatch> matches = new ArrayList<>();
    private int currentMatchIndex = -1;
    
    
    private Map<JTextComponent, List<Object>> highlightsByTextArea = new HashMap<>();

    private Object currentMatchHighlight = null;
    private JTextComponent currentMatchComponent = null;
    private static final Color CURRENT_MATCH_COLOR = new Color(255, 120, 0, 250);
    private static final Color OTHER_MATCH_COLOR = new Color(255, 95, 59, 80);
    
    
    public SearchPanel(JupyterNotebookIDE ide) {
        this.ide = ide;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 100)));
        setBackground(new Color(16, 16, 45)); 
        
        
        searchField = new JTextField(20);
        searchField.setBackground(new Color(20, 20, 60));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(40, 40, 100)),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        
        
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    
                    searchField.requestFocusInWindow();
                });
            }
        });
        
        prevButton = new JButton("▲");  
        styleButton(prevButton);
        prevButton.setToolTipText("Previous match (Shift+Enter)");
        
        nextButton = new JButton("▼");  
        styleButton(nextButton);
        nextButton.setToolTipText("Next match (Enter)");
        
        closeButton = new JButton("✕");
        styleButton(closeButton);
        closeButton.setToolTipText("Close search (Escape)");
        
        matchesLabel = new JLabel("No matches");
        matchesLabel.setForeground(Color.WHITE);
        matchesLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        matchesLabel.setPreferredSize(new Dimension(100, 25)); 
        
        caseSensitiveCheck = new JCheckBox("Match case");
        caseSensitiveCheck.setBackground(new Color(16, 16, 45));
        caseSensitiveCheck.setForeground(Color.WHITE);
        caseSensitiveCheck.setFocusable(false);
        
        
        JPanel searchContainer = new JPanel();
        searchContainer.setLayout(new BoxLayout(searchContainer, BoxLayout.X_AXIS));
        searchContainer.setBackground(new Color(16, 16, 45));
        searchContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setForeground(Color.WHITE);
        
        
        searchContainer.add(matchesLabel);  
        searchContainer.add(Box.createRigidArea(new Dimension(5, 0)));
        searchContainer.add(searchIcon);
        searchContainer.add(Box.createRigidArea(new Dimension(5, 0)));
        searchContainer.add(searchField);
        searchContainer.add(Box.createRigidArea(new Dimension(5, 0)));
        searchContainer.add(prevButton);
        searchContainer.add(nextButton);
        searchContainer.add(Box.createRigidArea(new Dimension(10, 0)));
        searchContainer.add(caseSensitiveCheck);
        searchContainer.add(Box.createRigidArea(new Dimension(10, 0)));
        searchContainer.add(closeButton);
        
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(new Color(16, 16, 45));
        rightPanel.add(searchContainer);
        
        add(rightPanel, BorderLayout.EAST);
        
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { 
                
                clearHighlights();
                performSearch(); 
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) { 
                
                clearHighlights();
                performSearch(); 
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) { 
                
                clearHighlights();
                performSearch(); 
            }
        });
        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                
                forceClearAllHighlights();
            }
        });

        
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    
                    forceRemoveAllHighlights();
                    
                    
                    return false;
                }
                
                
                if (isVisible() && searchField.hasFocus() && e.getID() == KeyEvent.KEY_PRESSED) {
                    int keyCode = e.getKeyCode();
                    
                    if (keyCode == KeyEvent.VK_ENTER) {
                        
                        if (e.isShiftDown()) {
                            navigateToPrevious();
                        } else {
                            navigateToNext();
                        }
                        e.consume();
                        return true;
                    }
                    else if (keyCode == KeyEvent.VK_ESCAPE) {
                        closeSearch();
                        e.consume();
                        return true;
                    }
                    else if (keyCode == KeyEvent.VK_TAB) {
                        
                        if (e.isShiftDown()) {
                            caseSensitiveCheck.requestFocusInWindow();
                        } else {
                            prevButton.requestFocusInWindow();
                        }
                        e.consume();
                        return true;
                    }
                }
                return false;
            }
        });
        
        prevButton.addActionListener(e -> navigateToPrevious());
        nextButton.addActionListener(e -> navigateToNext());
        closeButton.addActionListener(e -> closeSearch());
        caseSensitiveCheck.addActionListener(e -> performSearch());
        
        
        setVisible(false);
    }
    
    private void styleButton(JButton button) {
        button.setBackground(new Color(26, 26, 46));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 100)));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(28, 28));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    }
    
    
    public void showSearch() {
        
        forceRemoveAllHighlights();
        
        setVisible(true);
        
        
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
        
        
        if (!searchField.getText().isEmpty()) {
            performSearch();
        }
    }
    
    
    public void toggleSearch() {
        if (isVisible()) {
            closeSearch();
        } else {
            showSearch();
        }
    }
    
    
    private void closeSearch() {
        
        forceRemoveAllHighlights();
        
        setVisible(false);
        
        
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook != null) {
            SwingUtilities.invokeLater(() -> {
                currentNotebook.requestFocusInWindow();
            });
        }
    }
    
    
    private void performSearch() {
        
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;
        
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            matchesLabel.setText("No matches");
            return;
        }
        
        boolean isCaseSensitive = caseSensitiveCheck.isSelected();
        
        
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook == null) return;
        
        
        List<JTextComponent> textComponents = getAllTextComponents(currentNotebook);
        int matchCount = 0;
        
        for (JTextComponent textComponent : textComponents) {
            String text = textComponent.getText();
            String searchFor = searchText;
            
            if (!isCaseSensitive) {
                text = text.toLowerCase();
                searchFor = searchText.toLowerCase();
            }
            
            int index = 0;
            while ((index = text.indexOf(searchFor, index)) != -1) {
                matches.add(new SearchMatch(textComponent, index, index + searchFor.length()));
                matchCount++;
                
                
                try {
                    highlightMatch(textComponent, index, index + searchFor.length());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
                
                index += searchFor.length();
            }
        }
        
        
        if (matches.isEmpty()) {
            matchesLabel.setText("No matches");
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
        } else {
            matchesLabel.setText("0 of " + matchCount);
            prevButton.setEnabled(true);
            nextButton.setEnabled(true);
            
            
            currentMatchIndex = 0;
            navigateToMatch(currentMatchIndex);
        }
    }
    
    
    private void highlightMatch(JTextComponent textComponent, int start, int end) throws BadLocationException {
        
        Highlighter highlighter = textComponent.getHighlighter();
        Object highlight = highlighter.addHighlight(start, end, 
            new DefaultHighlighter.DefaultHighlightPainter(OTHER_MATCH_COLOR));
        
        
        highlightsByTextArea.computeIfAbsent(textComponent, k -> new ArrayList<>())
                           .add(highlight);
    }
    
    
    private void navigateToNext() {
        if (matches.isEmpty()) return;
        
        try {
            currentMatchIndex = (currentMatchIndex + 1) % matches.size();
            navigateToMatch(currentMatchIndex);
        } finally {
            
            SwingUtilities.invokeLater(() -> {
                searchField.requestFocusInWindow();
            });
        }
    }
    
    
    private void navigateToPrevious() {
        if (matches.isEmpty()) return;
        
        try {
            currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
            navigateToMatch(currentMatchIndex);
        } finally {
            
            SwingUtilities.invokeLater(() -> {
                searchField.requestFocusInWindow();
            });
        }
    }
    
    
    


    

    private void navigateToMatch(int index) {
        if (index < 0 || index >= matches.size()) return;
    
        
        if (currentMatchHighlight != null && currentMatchComponent != null) {
            try {
                currentMatchComponent.getHighlighter().removeHighlight(currentMatchHighlight);
            } catch (Exception e) {
                
                System.err.println("Failed to remove previous highlight: " + e.getMessage());
            }
            currentMatchHighlight = null;
            currentMatchComponent = null;
        }

        
        SearchMatch match = matches.get(index);
        JTextComponent textComponent = match.getTextComponent();

        
        matchesLabel.setText((index + 1) + " of " + matches.size());

        
        Component cell = findParentCell(textComponent);
        if (cell == null) {
            System.err.println("SearchPanel: Could not find parent cell for text component.");
            return; 
        }
        JScrollPane mainScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, cell);
        if (mainScrollPane == null) {
            System.err.println("SearchPanel: Could not find main scroll pane.");
            return; 
        }
        JViewport viewport = mainScrollPane.getViewport();
        Component view = viewport.getView(); 

        
        Rectangle cellBounds = cell.getBounds();
        
        Rectangle cellBoundsInView = SwingUtilities.convertRectangle(
            cell.getParent(), cellBounds, view);

        
        
        viewport.scrollRectToVisible(cellBoundsInView);

        
        
        
        Timer fineTuneTimer = new Timer(50, e -> { 
            try {
                
                Highlighter highlighter = textComponent.getHighlighter();
                currentMatchHighlight = highlighter.addHighlight(
                        match.getStartOffset(),
                        match.getEndOffset(),
                        new DefaultHighlighter.DefaultHighlightPainter(CURRENT_MATCH_COLOR)
                );
                currentMatchComponent = textComponent;

                
                textComponent.setSelectionStart(match.getStartOffset());
                textComponent.setSelectionEnd(match.getEndOffset());

                
                Rectangle matchRect = textComponent.modelToView(match.getStartOffset());

                if (matchRect != null) {
                    
                    Rectangle matchRectInView = SwingUtilities.convertRectangle(
                        textComponent, matchRect, view);

                    
                    int targetY = Math.max(0, matchRectInView.y - (viewport.getExtentSize().height / 3));

                    
                    animateVerticalScroll(mainScrollPane, targetY);

                    
                    
                    Timer innerScrollTimer = new Timer(100, evt -> { 
                        try {
                            
                            Rectangle finalMatchRect = textComponent.modelToView(match.getStartOffset());
                            if (finalMatchRect != null) {
                                
                                Rectangle visibleRect = new Rectangle(
                                    finalMatchRect.x,
                                    Math.max(0, finalMatchRect.y - 30), 
                                    finalMatchRect.width,
                                    finalMatchRect.height + 60); 
                                textComponent.scrollRectToVisible(visibleRect);
                            }
                        } catch (BadLocationException ble) {
                            
                        } finally {
                            
                            searchField.requestFocusInWindow();
                        }
                    });
                    innerScrollTimer.setRepeats(false);
                    innerScrollTimer.start();

                } else {
                    
                    System.err.println("SearchPanel: Could not get modelToView for match offset.");
                    
                    
                    searchField.requestFocusInWindow();
                }

            } catch (BadLocationException ble) {
                System.err.println("SearchPanel: Error highlighting or selecting match: " + ble.getMessage());
                
                searchField.requestFocusInWindow();
            } catch (Exception ex) {
                System.err.println("SearchPanel: Unexpected error during fine-tune scrolling:");
                ex.printStackTrace();
                
                searchField.requestFocusInWindow();
            }
        });
        fineTuneTimer.setRepeats(false); 
        fineTuneTimer.start();
    }
       
      

    
    private Component findParentCell(Component comp) {
        Container parent = comp.getParent();
        while (parent != null && !(parent instanceof NotebookCell)) {
            parent = parent.getParent();
        }
        return parent;
    }
    
    
    private void animateVerticalScroll(JScrollPane scrollPane, int targetY) {
        final JScrollBar vBar = scrollPane.getVerticalScrollBar();
        final int initial = vBar.getValue();
        final int distance = targetY - initial;
        
        
        if (Math.abs(distance) < 10) {
            vBar.setValue(targetY);
            return;
        }
        
        
        final int duration = 250;  
        final int delay = 10;      
        final int steps = duration / delay;
        final double stepIncrement = (double) distance / steps;
        
        Timer timer = new Timer(delay, null);
        timer.addActionListener(new ActionListener() {
            int currentStep = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                currentStep++;
                int newValue = (int) (initial + stepIncrement * currentStep);
                vBar.setValue(newValue);
                if (currentStep >= steps) {
                    
                    vBar.setValue(targetY);
                    timer.stop();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }


    
    private void clearHighlights() {
        
        if (currentMatchHighlight != null && currentMatchComponent != null) {
            Highlighter highlighter = currentMatchComponent.getHighlighter();
            highlighter.removeHighlight(currentMatchHighlight);
            currentMatchHighlight = null;
            currentMatchComponent = null;
        }
        
        
        for (Map.Entry<JTextComponent, List<Object>> entry : highlightsByTextArea.entrySet()) {
            JTextComponent textComponent = entry.getKey();
            Highlighter highlighter = textComponent.getHighlighter();
            
            for (Object highlight : entry.getValue()) {
                highlighter.removeHighlight(highlight);
            }
        }
        
        highlightsByTextArea.clear();
    }
    
    
    private List<JTextComponent> getAllTextComponents(NotebookPanel notebook) {
        List<JTextComponent> textComponents = new ArrayList<>();
        
        
        Container cellsContainer = getCellsContainer(notebook);
        if (cellsContainer != null) {
            for (Component comp : cellsContainer.getComponents()) {
                if (comp instanceof CodeCell) {
                    CodeCell cell = (CodeCell) comp;
                    
                    
                    RSyntaxTextArea codeArea = getCodeArea(cell);
                    if (codeArea != null) {
                        textComponents.add(codeArea);
                    }
                    
                    
                    RSyntaxTextArea latexEditor = getLatexEditor(cell);
                    if (latexEditor != null) {
                        textComponents.add(latexEditor);
                    }
                } else if (comp instanceof MarkdownCell) {
                    
                    MarkdownCell markdownCell = (MarkdownCell) comp;
                    RSyntaxTextArea markdownEditor = getMarkdownEditor(markdownCell);
                    if (markdownEditor != null) {
                        textComponents.add(markdownEditor);
                    }
                }
            }
        }
        
        return textComponents;
    }
    
    
    private Container getCellsContainer(NotebookPanel notebook) {
        try {
            Field field = NotebookPanel.class.getDeclaredField("cellsContainer");
            field.setAccessible(true);
            return (Container) field.get(notebook);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    private RSyntaxTextArea getCodeArea(CodeCell cell) {
        try {
            Field field = CodeCell.class.getDeclaredField("codeArea");
            field.setAccessible(true);
            return (RSyntaxTextArea) field.get(cell);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    private RSyntaxTextArea getLatexEditor(CodeCell cell) {
        try {
            
            Field panelField = CodeCell.class.getDeclaredField("latexPanel");
            panelField.setAccessible(true);
            LatexEditorPanel latexPanel = (LatexEditorPanel) panelField.get(cell);
            
            if (latexPanel != null) {
                
                Field editorField = LatexEditorPanel.class.getDeclaredField("latexEditor");
                editorField.setAccessible(true);
                return (RSyntaxTextArea) editorField.get(latexPanel);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    
    private RSyntaxTextArea getMarkdownEditor(MarkdownCell cell) {
        try {
            Field field = MarkdownCell.class.getDeclaredField("markdownArea");
            field.setAccessible(true);
            return (RSyntaxTextArea) field.get(cell);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void forceClearAllHighlights() {
        
        if (currentMatchHighlight != null && currentMatchComponent != null) {
            try {
                Highlighter highlighter = currentMatchComponent.getHighlighter();
                highlighter.removeHighlight(currentMatchHighlight);
            } catch (Exception e) {
                
            }
            currentMatchHighlight = null;
            currentMatchComponent = null;
        }
        
        
        for (Map.Entry<JTextComponent, List<Object>> entry : highlightsByTextArea.entrySet()) {
            JTextComponent textComponent = entry.getKey();
            if (textComponent != null) {
                try {
                    Highlighter highlighter = textComponent.getHighlighter();
                    
                    
                    for (Object highlight : entry.getValue()) {
                        try {
                            highlighter.removeHighlight(highlight);
                        } catch (Exception e) {
                            
                        }
                    }
                    
                    
                    
                    Highlighter.Highlight[] allHighlights = highlighter.getHighlights();
                    for (Highlighter.Highlight h : allHighlights) {
                        if (h.getPainter() instanceof DefaultHighlighter.DefaultHighlightPainter) {
                            DefaultHighlighter.DefaultHighlightPainter painter = 
                                (DefaultHighlighter.DefaultHighlightPainter) h.getPainter();
                            Color color = painter.getColor();
                            
                            
                            if (color.equals(CURRENT_MATCH_COLOR) || color.equals(OTHER_MATCH_COLOR)) {
                                try {
                                    highlighter.removeHighlight(h);
                                } catch (Exception e) {
                                    
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    
                }
            }
        }
        
        
        highlightsByTextArea.clear();
        
        
        matches.clear();
        currentMatchIndex = -1;
        matchesLabel.setText("No matches");
    }

    private void forceRemoveAllHighlights() {
        
        NotebookPanel currentNotebook = ide.getCurrentNotebook();
        if (currentNotebook == null) return;
        
        
        List<JTextComponent> textComponents = getAllTextComponents(currentNotebook);
        
        
        currentMatchHighlight = null;
        currentMatchComponent = null;
        highlightsByTextArea.clear();
        
        
        for (JTextComponent textComponent : textComponents) {
            if (textComponent == null) continue;
            
            try {
                Highlighter highlighter = textComponent.getHighlighter();
                if (highlighter == null) continue;
                
                
                Highlighter.Highlight[] highlights = highlighter.getHighlights();
                if (highlights == null) continue;
                
                
                for (Highlighter.Highlight h : highlights) {
                    try {
                        
                        highlighter.removeHighlight(h);
                    } catch (Exception e) {
                        
                    }
                }
            } catch (Exception e) {
                
            }
        }
    }


    
    private static class SearchMatch {
        private JTextComponent textComponent;
        private int startOffset;
        private int endOffset;
        
        public SearchMatch(JTextComponent textComponent, int startOffset, int endOffset) {
            this.textComponent = textComponent;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
        
        public JTextComponent getTextComponent() {
            return textComponent;
        }
        
        public int getStartOffset() {
            return startOffset;
        }
        
        public int getEndOffset() {
            return endOffset;
        }
    }
}
