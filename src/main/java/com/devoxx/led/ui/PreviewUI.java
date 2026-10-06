package com.devoxx.led.ui;

import com.devoxx.led.core.Frame;
import com.devoxx.led.gif.GifEncoder;
import com.devoxx.led.image.ImageAdapter;
import com.devoxx.led.png.PngWriter;
import com.devoxx.led.scene.Scene;
import com.devoxx.led.scene.SceneRegistry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A small Swing desktop app to preview and export 64x64 LED matrix visuals.
 *
 * <p>Two sources feed the same preview + export pipeline:
 * <ol>
 *   <li>Built-in animated {@link Scene}s (devoxx, googlecloud, javacoffee, plasma),
 *       played live and scaled up so the 64x64 pixels are visible.</li>
 *   <li>An uploaded image, resized to 64x64 (letterbox or crop) and optionally
 *       run through a LED-style posterize treatment, shown as a single still.</li>
 * </ol>
 *
 * <p>Export writes a 64x64 animated GIF (for scenes) or PNG (for a still image)
 * into {@code target/visuals/}, ready to upload to the contest.
 *
 * <p>Launch with {@code mvn -q compile exec:java -Dexec.mainClass=com.devoxx.led.ui.PreviewUI}.
 */
public final class PreviewUI {

    private static final int TOTAL_FRAMES = 24;
    private static final int DELAY_MS = 60;
    private static final int SCALE = 8; // 64 * 8 = 512px preview

    private final JFrame frame = new JFrame("Devoxx 2026 LED Matrix - Preview & Export");
    private final MatrixPanel preview = new MatrixPanel();
    private final JLabel status = new JLabel("Pick a scene or upload an image.");

    // Source state: either an animated scene, or a single uploaded still frame.
    private Scene currentScene;
    private Frame uploadedFrame;
    private String currentName = "devoxx";

    // Upload options.
    private final JComboBox<ImageAdapter.FitMode> fitBox =
            new JComboBox<>(ImageAdapter.FitMode.values());
    private final JCheckBox posterizeBox = new JCheckBox("LED posterize", true);
    private final JComboBox<Integer> levelsBox =
            new JComboBox<>(new Integer[]{2, 3, 4, 5, 6, 8});

    // Re-apply options to the last uploaded file.
    private File lastUploadedFile;

    private final Timer timer = new Timer(DELAY_MS, e -> preview.tick());

    private PreviewUI() {
        buildUi();
        selectScene(SceneRegistry.all().get(0));
        timer.start();
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        // ---- Left: scene list ------------------------------------------------
        DefaultListModel<String> model = new DefaultListModel<>();
        for (Scene s : SceneRegistry.all()) {
            model.addElement(s.name());
        }
        JList<String> sceneList = new JList<>(model);
        sceneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sceneList.setSelectedIndex(0);
        sceneList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                int idx = sceneList.getSelectedIndex();
                if (idx >= 0) {
                    selectScene(SceneRegistry.all().get(idx));
                }
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Scenes"));
        left.add(new JScrollPane(sceneList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(140, 0));

        // ---- Center: preview -------------------------------------------------
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createTitledBorder("Preview (64x64 scaled "
                + SCALE + "x)"));
        JPanel previewHolder = new JPanel();
        previewHolder.setBackground(Color.DARK_GRAY);
        previewHolder.add(preview);
        center.add(previewHolder, BorderLayout.CENTER);

        // ---- Right: upload + options + export --------------------------------
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createTitledBorder("Image & Export"));
        right.setPreferredSize(new Dimension(210, 0));

        JButton uploadBtn = new JButton("Upload image...");
        uploadBtn.addActionListener(e -> onUpload());

        JPanel optsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        optsPanel.setBorder(BorderFactory.createTitledBorder("Image options"));
        optsPanel.add(labeled("Fit:", fitBox));
        optsPanel.add(posterizeBox);
        levelsBox.setSelectedItem(4);
        optsPanel.add(labeled("Levels:", levelsBox));
        // Re-apply options when they change (only affects an uploaded image).
        fitBox.addActionListener(e -> reapplyUpload());
        posterizeBox.addActionListener(e -> reapplyUpload());
        levelsBox.addActionListener(e -> reapplyUpload());

        JButton exportBtn = new JButton("Export to target/visuals");
        exportBtn.addActionListener(e -> onExport());

        right.add(uploadBtn);
        right.add(Box.createVerticalStrut(8));
        right.add(optsPanel);
        right.add(Box.createVerticalStrut(8));
        right.add(exportBtn);
        right.add(Box.createVerticalGlue());

        frame.add(left, BorderLayout.WEST);
        frame.add(center, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel labeled(String text, java.awt.Component c) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(new JLabel(text), BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // ---- Source selection ----------------------------------------------------

    private void selectScene(Scene scene) {
        this.currentScene = scene;
        this.uploadedFrame = null;
        this.lastUploadedFile = null;
        this.currentName = scene.name();
        preview.resetAnimation();
        status.setText("Scene: " + scene.name() + " (animated, " + TOTAL_FRAMES + " frames)");
    }

    private void onUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (png, jpg, jpeg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastUploadedFile = chooser.getSelectedFile();
        reapplyUpload();
    }

    /** (Re)load the last uploaded file applying the current fit/posterize options. */
    private void reapplyUpload() {
        if (lastUploadedFile == null) {
            return;
        }
        try {
            ImageAdapter.FitMode fit = (ImageAdapter.FitMode) fitBox.getSelectedItem();
            boolean poster = posterizeBox.isSelected();
            int levels = (Integer) levelsBox.getSelectedItem();
            Frame f = ImageAdapter.adapt(lastUploadedFile, fit, poster, levels);
            this.uploadedFrame = f;
            this.currentScene = null;
            this.currentName = baseName(lastUploadedFile.getName());
            preview.resetAnimation();
            status.setText("Image: " + lastUploadedFile.getName()
                    + "  [" + fit + (poster ? ", posterize x" + levels : "") + "]  (still 64x64)");
        } catch (IOException ex) {
            error("Could not load image: " + ex.getMessage());
        }
    }

    // ---- Export ---------------------------------------------------------------

    private void onExport() {
        try {
            Path dir = Path.of("target", "visuals");
            Files.createDirectories(dir);

            if (uploadedFrame != null) {
                // Still image -> PNG.
                File out = dir.resolve(currentName + ".png").toFile();
                new PngWriter().write(uploadedFrame, out);
                status.setText("Exported " + out.getPath() + "  (" + out.length() + " bytes)");
            } else if (currentScene != null) {
                // Animated scene -> GIF (plus a mid-frame PNG still).
                List<Frame> frames = new ArrayList<>(TOTAL_FRAMES);
                for (int i = 0; i < TOTAL_FRAMES; i++) {
                    frames.add(currentScene.render(i, TOTAL_FRAMES));
                }
                File gifOut = dir.resolve(currentName + ".gif").toFile();
                try (OutputStream os = new BufferedOutputStream(
                        Files.newOutputStream(gifOut.toPath()))) {
                    new GifEncoder().encode(frames, currentScene.palette(), DELAY_MS, os);
                }
                File pngOut = dir.resolve(currentName + ".png").toFile();
                new PngWriter().write(frames.get(TOTAL_FRAMES / 2), pngOut);
                status.setText("Exported " + gifOut.getPath() + "  ("
                        + gifOut.length() + " bytes) + still PNG");
            }
        } catch (IOException ex) {
            error("Export failed: " + ex.getMessage());
        }
    }

    private void error(String msg) {
        status.setText(msg);
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        // Sanitize to a safe lowercase file stem.
        base = base.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return base.isEmpty() ? "upload" : base;
    }

    // ---- The scaled matrix rendering panel ------------------------------------

    private final class MatrixPanel extends JPanel {
        private int animFrame = 0;
        private final BufferedImage buffer =
                new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        MatrixPanel() {
            setPreferredSize(new Dimension(64 * SCALE, 64 * SCALE));
        }

        void resetAnimation() {
            animFrame = 0;
            repaint();
        }

        /** Advance the animation one frame (driven by the Swing timer). */
        void tick() {
            if (currentScene != null) {
                animFrame = (animFrame + 1) % TOTAL_FRAMES;
                repaint();
            }
            // A still uploaded image does not animate.
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Frame f;
            if (uploadedFrame != null) {
                f = uploadedFrame;
            } else if (currentScene != null) {
                f = currentScene.render(animFrame, TOTAL_FRAMES);
            } else {
                return;
            }

            buffer.setRGB(0, 0, 64, 64, f.pixels(), 0, 64);
            // Nearest-neighbor upscale so individual LEDs stay crisp.
            g.drawImage(buffer, 0, 0, 64 * SCALE, 64 * SCALE, null);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PreviewUI::new);
    }
}
