package com.devoxx.led.ui;

import com.devoxx.led.ai.DotEnv;
import com.devoxx.led.ai.OpenAiImageClient;
import com.devoxx.led.core.Frame;
import com.devoxx.led.device.PixooClient;
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
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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

    /** Folder where generated / adapted stills are saved and listed from. */
    private static final Path GENERATED_DIR = Path.of("generated");

    private final JFrame frame = new JFrame("Devoxx 2026 LED Matrix - Preview & Export");
    private final MatrixPanel preview = new MatrixPanel();
    private final JLabel status = new JLabel("Pick a scene or upload an image.");

    // Saved generated images (file names under generated/).
    private final DefaultListModel<String> generatedModel = new DefaultListModel<>();
    private final JList<String> generatedList = new JList<>(generatedModel);

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

    // AI prompt -> image.
    private final DotEnv env = DotEnv.load();
    private final OpenAiImageClient aiClient = new OpenAiImageClient(env);
    private final JTextField promptField = new JTextField();
    private final JButton generateBtn = new JButton("Generate");

    // Pixoo 64 device.
    private final JTextField hostField =
            new JTextField(DotEnv.load().getOrDefault("PIXOO_HOST", ""), 12);
    private final JButton sendBtn = new JButton("Send to Pixoo");

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
        JPanel scenesPanel = new JPanel(new BorderLayout());
        scenesPanel.setBorder(BorderFactory.createTitledBorder("Scenes"));
        scenesPanel.add(new JScrollPane(sceneList), BorderLayout.CENTER);

        // Generated / saved images list (below the scenes).
        generatedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        generatedList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                String fileName = generatedList.getSelectedValue();
                if (fileName != null) {
                    loadGenerated(fileName);
                }
            }
        });
        JPanel generatedPanel = new JPanel(new BorderLayout());
        generatedPanel.setBorder(BorderFactory.createTitledBorder("Generated"));
        generatedPanel.add(new JScrollPane(generatedList), BorderLayout.CENTER);

        // Clear the scene selection's visual highlight when a generated item is picked
        // (and vice-versa) so it is obvious which source is active.
        generatedList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting() && generatedList.getSelectedValue() != null) {
                sceneList.clearSelection();
            }
        });
        sceneList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting() && sceneList.getSelectedValue() != null) {
                generatedList.clearSelection();
            }
        });

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 6));
        left.add(scenesPanel);
        left.add(generatedPanel);
        left.setPreferredSize(new Dimension(150, 0));

        // Populate the generated list from disk.
        refreshGeneratedList();

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

        JPanel devicePanel = new JPanel(new GridLayout(0, 1, 0, 4));
        devicePanel.setBorder(BorderFactory.createTitledBorder("Pixoo 64 device"));
        devicePanel.add(labeled("IP:", hostField));
        sendBtn.addActionListener(e -> onSendToPixoo());
        devicePanel.add(sendBtn);

        right.add(optsPanel);
        right.add(Box.createVerticalStrut(8));
        right.add(exportBtn);
        right.add(Box.createVerticalStrut(8));
        right.add(devicePanel);
        right.add(Box.createVerticalGlue());

        // ---- Bottom: upload button + AI prompt chat box + status ------------
        JButton uploadBtn = new JButton("Upload image...");
        uploadBtn.addActionListener(e -> onUpload());

        // Left cluster: upload button then "Prompt:" label.
        JPanel chatLeft = new JPanel(new BorderLayout(6, 0));
        chatLeft.add(uploadBtn, BorderLayout.WEST);
        chatLeft.add(new JLabel("  Prompt:"), BorderLayout.EAST);

        JPanel chat = new JPanel(new BorderLayout(6, 0));
        chat.setBorder(BorderFactory.createTitledBorder("Upload an image, or describe one (AI)"));
        chat.add(chatLeft, BorderLayout.WEST);
        chat.add(promptField, BorderLayout.CENTER);
        chat.add(generateBtn, BorderLayout.EAST);
        promptField.addActionListener(e -> onGenerate());
        generateBtn.addActionListener(e -> onGenerate());
        if (!aiClient.isConfigured()) {
            promptField.setEnabled(false);
            generateBtn.setEnabled(false);
            promptField.setToolTipText("Set CHATGPT_API_KEY in .env to enable AI generation.");
        }

        JPanel south = new JPanel(new BorderLayout());
        south.add(chat, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        frame.add(left, BorderLayout.WEST);
        frame.add(center, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.add(south, BorderLayout.SOUTH);

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

    // ---- AI prompt -> image ---------------------------------------------------

    private void onGenerate() {
        final String prompt = promptField.getText() == null ? "" : promptField.getText().trim();
        if (prompt.isEmpty()) {
            status.setText("Type a prompt first.");
            return;
        }
        generateBtn.setEnabled(false);
        promptField.setEnabled(false);
        status.setText("Generating image for: \"" + prompt + "\" ...");

        // Current image options drive how the big AI image is reduced to 64x64.
        final ImageAdapter.FitMode fit = (ImageAdapter.FitMode) fitBox.getSelectedItem();
        final boolean poster = posterizeBox.isSelected();
        final int levels = (Integer) levelsBox.getSelectedItem();

        new SwingWorker<Frame, Void>() {
            @Override
            protected Frame doInBackground() throws Exception {
                BufferedImage big = aiClient.generate(prompt);
                // Reduce the 1024x1024 result to the 64x64 LED matrix.
                return ImageAdapter.adapt(big, fit, poster, levels);
            }

            @Override
            protected void done() {
                try {
                    Frame f = get();
                    uploadedFrame = f;
                    currentScene = null;
                    lastUploadedFile = null;
                    currentName = "ai-" + baseName(prompt);
                    preview.resetAnimation();

                    // Save the generated still locally and show it in the list.
                    String saved = saveGenerated(f, currentName);
                    status.setText("AI image saved: generated/" + saved + "  [" + fit
                            + (poster ? ", posterize x" + levels : "") + "]  (still 64x64)");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    error("Generation failed: " + cause.getMessage());
                } finally {
                    generateBtn.setEnabled(true);
                    promptField.setEnabled(true);
                }
            }
        }.execute();
    }

    // ---- Generated image library (saved under generated/) ---------------------

    /**
     * Write {@code frame} to {@code generated/<stem>.png}, de-duplicating the name
     * with a numeric suffix, refresh the list, and select it. Returns the file name.
     */
    private String saveGenerated(Frame frame, String stem) throws IOException {
        Files.createDirectories(GENERATED_DIR);
        String name = stem + ".png";
        Path target = GENERATED_DIR.resolve(name);
        int n = 2;
        while (Files.exists(target)) {
            name = stem + "-" + n++ + ".png";
            target = GENERATED_DIR.resolve(name);
        }
        new PngWriter().write(frame, target.toFile());
        refreshGeneratedList();
        generatedList.setSelectedValue(name, true);
        return name;
    }

    /** Rebuild the generated list from PNG files on disk, sorted by name. */
    private void refreshGeneratedList() {
        generatedModel.clear();
        if (Files.isDirectory(GENERATED_DIR)) {
            try (var stream = Files.list(GENERATED_DIR)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(generatedModel::addElement);
            } catch (IOException ignored) {
                // Leave the list empty if the folder cannot be read.
            }
        }
    }

    /** Load a saved generated PNG into the preview as a still. */
    private void loadGenerated(String fileName) {
        try {
            File file = GENERATED_DIR.resolve(fileName).toFile();
            // No resizing needed: these are already 64x64. Letterbox keeps them intact.
            Frame f = ImageAdapter.adapt(file, ImageAdapter.FitMode.LETTERBOX, false, 4);
            this.uploadedFrame = f;
            this.currentScene = null;
            this.lastUploadedFile = null;
            this.currentName = baseName(fileName);
            preview.resetAnimation();
            status.setText("Generated: " + fileName + "  (still 64x64)");
        } catch (IOException ex) {
            error("Could not load generated image: " + ex.getMessage());
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

    // ---- Send to Pixoo 64 -----------------------------------------------------

    private void onSendToPixoo() {
        final String host = hostField.getText() == null ? "" : hostField.getText().trim();
        if (host.isEmpty()) {
            error("Enter the Pixoo 64 IP address first (e.g. 192.168.1.100).");
            return;
        }

        // Snapshot what to send on the UI thread.
        final Scene scene = currentScene;
        final Frame still = uploadedFrame;
        if (scene == null && still == null) {
            status.setText("Nothing to send.");
            return;
        }

        sendBtn.setEnabled(false);
        status.setText("Sending to Pixoo at " + host + " ...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                PixooClient device = new PixooClient(host);
                if (scene != null) {
                    List<Frame> frames = new ArrayList<>(TOTAL_FRAMES);
                    for (int i = 0; i < TOTAL_FRAMES; i++) {
                        frames.add(scene.render(i, TOTAL_FRAMES));
                    }
                    device.sendAnimation(frames, DELAY_MS);
                } else {
                    device.sendStill(still);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Sent to Pixoo at " + host
                            + (scene != null ? " (animation)" : " (still)") + ".");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    error("Send failed: " + cause.getMessage()
                            + "  (device on same WiFi? IP correct?)");
                } finally {
                    sendBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void error(String msg) {
        status.setText(msg);
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        // Sanitize to a safe lowercase file stem, collapsing runs of separators.
        base = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (base.length() > 32) {
            base = base.substring(0, 32).replaceAll("-+$", "");
        }
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
