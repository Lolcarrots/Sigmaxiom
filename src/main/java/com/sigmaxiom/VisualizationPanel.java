package com.sigmaxiom;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;

public class VisualizationPanel extends JPanel {
    private BufferedImage originalImage;
    private JLabel visualizationLabel;
    private JLabel placeholderLabel;

    public VisualizationPanel() {
        
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(40, 40, 100), 1),
            "Visualization",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12),
            Color.WHITE
        ));
        setBackground(new Color(13, 13, 35));

        
        placeholderLabel = new JLabel("No visualization available.", SwingConstants.CENTER);
        placeholderLabel.setForeground(new Color(150, 150, 200));
        placeholderLabel.setBackground(new Color(16, 16, 45));
        placeholderLabel.setOpaque(true);

        
        visualizationLabel = new JLabel();
        visualizationLabel.setHorizontalAlignment(JLabel.CENTER);
        visualizationLabel.setBackground(new Color(16, 16, 45));
        visualizationLabel.setOpaque(true);

        
        add(placeholderLabel, BorderLayout.CENTER);

        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (originalImage != null) {
                    int maxWidth = getWidth() - 20;
                    int maxHeight = getHeight() - 20;
                    double scale = Math.min((double) maxWidth / originalImage.getWidth(),
                                            (double) maxHeight / originalImage.getHeight());
                    int newWidth = (int) (originalImage.getWidth() * scale);
                    int newHeight = (int) (originalImage.getHeight() * scale);

                    BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = scaledImage.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
                    g2.dispose();

                    visualizationLabel.setIcon(new ImageIcon(scaledImage));
                    revalidate();
                    repaint();
                }
            }
        });
    }

    
    public void displayVisualization(BufferedImage image) {
        if (image != null) {
            originalImage = image;  

            
            int maxWidth = getWidth() - 20;
            int maxHeight = getHeight() - 20;
            double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
            int newWidth = (int) (image.getWidth() * scale);
            int newHeight = (int) (image.getHeight() * scale);
            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaledImage.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, 0, 0, newWidth, newHeight, null);
            g2.dispose();

            visualizationLabel.setIcon(new ImageIcon(scaledImage));
            remove(placeholderLabel);
            if (!isAncestorOf(visualizationLabel)) {
                add(visualizationLabel, BorderLayout.CENTER);
            }
            revalidate();
            repaint();
        } else {
            clearVisualization();
        }
    }

    public void displayVisualizationFromBase64(String base64Data) {
    try {
        
        if (base64Data.startsWith("data:image/")) {
            base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
        }
        
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        BufferedImage image = ImageIO.read(bis);
        bis.close();

        
        displayVisualization(image);
    } catch (IOException e) {
        e.printStackTrace();
        clearVisualization();
    }
}


    public void clearVisualization() {
        originalImage = null;
        remove(visualizationLabel);
        visualizationLabel.setIcon(null);
        if (!isAncestorOf(placeholderLabel)) {
            add(placeholderLabel, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }
}

