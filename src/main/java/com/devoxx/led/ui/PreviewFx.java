package com.devoxx.led.ui;

import com.devoxx.led.ai.DotEnv;
import com.devoxx.led.ai.OpenAiImageClient;
import com.devoxx.led.core.Frame;
import com.devoxx.led.device.PixooClient;
import com.devoxx.led.gif.GifEncoder;
import com.devoxx.led.image.ImageAdapter;
import com.devoxx.led.png.PngWriter;
import com.devoxx.led.scene.SceneRegistry;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX edition of the LED matrix preview tool. Mirrors {@link PreviewUI}
 * (the Swing version) feature-for-feature:
 *
 * <ul>
 *   <li>Scenes list (animated, played live and scaled up).</li>
 *   <li>Generated list of saved 64x64 stills under {@code generated/}.</li>
 *   <li>Upload an image + AI prompt generation (bottom bar).</li>
 *   <li>Image fit / LED-posterize options, export to {@code target/visuals/}.</li>
 *   <li>Send the current preview to a Divoom Pixoo 64 over the local network.</li>
 * </ul>
 *
 * <p>All backend logic (scenes, GIF/PNG encoders, image adapter, OpenAI client,
 * Pixoo client) is shared with the Swing version. Run via {@code mvn javafx:run}.
 */
public final class PreviewFx extends Application {

    private static final int TOTAL_FRAMES = 24;
    private static final int DELAY_MS = 60;
    private static final int SCALE = 6;            // 64 * 6 = 384 px preview
    private static final int MATRIX = 64;
    private static final Path GENERATED_DIR = Path.of("generated");

    // Shared backend.
    private final DotEnv env = DotEnv.load();
    private final OpenAiImageClient aiClient = new OpenAiImageClient(env);

    // Current source: either an animated scene or a single still frame.
    private com.devoxx.led.scene.Scene currentScene;
    private Frame uploadedFrame;
    private String currentName = "devoxx";
    private File lastUploadedFile;

    // UI widgets referenced across handlers.
    private final Canvas canvas = new Canvas(MATRIX * SCALE, MATRIX * SCALE);
    private final WritableImage image = new WritableImage(MATRIX, MATRIX);
    private final Label status = new Label("Pick a scene or upload an image.");

    private final ListView<String> sceneList = new ListView<>();
    private final ListView<String> generatedList = new ListView<>();
    private final ObservableList<String> generatedItems = FXCollections.observableArrayList();

    private final ComboBox<ImageAdapter.FitMode> fitBox =
            new ComboBox<>(FXCollections.observableArrayList(ImageAdapter.FitMode.values()));
    private final CheckBox posterizeBox = new CheckBox("LED posterize");
    private final ComboBox<Integer> levelsBox =
            new ComboBox<>(FXCollections.observableArrayList(2, 3, 4, 5, 6, 8));

    private final TextField promptField = new TextField();
    private final Button generateBtn = new Button("Generate");

    private final TextField hostField = new TextField(env.getOrDefault("PIXOO_HOST", ""));
    private final Button sendBtn = new Button("Send to Pixoo");

    private int animFrame = 0;
    private long lastTick = 0;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));
        root.setLeft(buildLeft());
        root.setCenter(buildCenter());
        root.setRight(buildRight());
        root.setBottom(buildBottom());

        // Animation loop (~16 fps) advancing scene frames.
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (currentScene == null) {
                    return;
                }
                if (now - lastTick >= DELAY_MS * 1_000_000L) {
                    lastTick = now;
                    animFrame = (animFrame + 1) % TOTAL_FRAMES;
                    redraw();
                }
            }
        }.start();

        selectScene(SceneRegistry.all().get(0));

        Scene scene = new Scene(root);
        primaryStage.setTitle("Devoxx 2026 LED Matrix - Preview & Export (JavaFX)");
        primaryStage.setScene(scene);

        // Fit the window to the available screen: prefer a compact size, but
        // never exceed ~90% of the usable screen area.
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.min(900, vb.getWidth() * 0.9);
        double h = Math.min(640, vb.getHeight() * 0.9);
        primaryStage.setWidth(w);
        primaryStage.setHeight(h);
        primaryStage.setMaxWidth(vb.getWidth());
        primaryStage.setMaxHeight(vb.getHeight());
        primaryStage.setX(vb.getMinX() + (vb.getWidth() - w) / 2);
        primaryStage.setY(vb.getMinY() + (vb.getHeight() - h) / 2);
        primaryStage.show();
    }

    // ---- Layout ---------------------------------------------------------------

    private Region buildLeft() {
        for (com.devoxx.led.scene.Scene s : SceneRegistry.all()) {
            sceneList.getItems().add(s.name());
        }
        sceneList.getSelectionModel().select(0);
        sceneList.getSelectionModel().selectedItemProperty().addListener((obs, old, name) -> {
            if (name != null) {
                int idx = sceneList.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    generatedList.getSelectionModel().clearSelection();
                    selectScene(SceneRegistry.all().get(idx));
                }
            }
        });

        generatedList.setItems(generatedItems);
        generatedList.getSelectionModel().selectedItemProperty().addListener((obs, old, name) -> {
            if (name != null) {
                sceneList.getSelectionModel().clearSelection();
                loadGenerated(name);
            }
        });
        refreshGeneratedList();

        TitledPane scenes = new TitledPane("Scenes", sceneList);
        scenes.setCollapsible(false);
        TitledPane generated = new TitledPane("Generated", generatedList);
        generated.setCollapsible(false);
        VBox.setVgrow(scenes, Priority.ALWAYS);
        VBox.setVgrow(generated, Priority.ALWAYS);

        VBox left = new VBox(6, scenes, generated);
        left.setPrefWidth(160);
        return left;
    }

    private Region buildCenter() {
        VBox holder = new VBox(canvas);
        holder.setStyle("-fx-background-color: #333;");
        holder.setPadding(new Insets(8));
        holder.setAlignment(javafx.geometry.Pos.CENTER);
        TitledPane pane = new TitledPane("Preview (64x64 scaled " + SCALE + "x)", holder);
        pane.setCollapsible(false);
        return pane;
    }

    private Region buildRight() {
        fitBox.getSelectionModel().select(ImageAdapter.FitMode.LETTERBOX);
        posterizeBox.setSelected(true);
        levelsBox.getSelectionModel().select(Integer.valueOf(4));
        fitBox.setMaxWidth(Double.MAX_VALUE);
        levelsBox.setMaxWidth(Double.MAX_VALUE);

        fitBox.setOnAction(e -> reapplyUpload());
        posterizeBox.setOnAction(e -> reapplyUpload());
        levelsBox.setOnAction(e -> reapplyUpload());

        VBox opts = new VBox(6,
                labeled("Fit:", fitBox),
                posterizeBox,
                labeled("Levels:", levelsBox));
        opts.setPadding(new Insets(6));
        TitledPane optsPane = new TitledPane("Image options", opts);
        optsPane.setCollapsible(false);

        Button exportBtn = new Button("Export to target/visuals");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setOnAction(e -> onExport());

        hostField.setPromptText("192.168.1.100");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setOnAction(e -> onSendToPixoo());
        VBox device = new VBox(6, labeled("IP:", hostField), sendBtn);
        device.setPadding(new Insets(6));
        TitledPane devicePane = new TitledPane("Pixoo 64 device", device);
        devicePane.setCollapsible(false);

        VBox right = new VBox(8, optsPane, exportBtn, devicePane);
        right.setPadding(new Insets(0, 0, 0, 8));
        right.setPrefWidth(220);
        return right;
    }

    private Region buildBottom() {
        Button uploadBtn = new Button("Upload image...");
        uploadBtn.setOnAction(e -> onUpload());

        promptField.setPromptText("Describe an image, e.g. \"duke mascot, bold pixel art\"");
        HBox.setHgrow(promptField, Priority.ALWAYS);
        promptField.setOnAction(e -> onGenerate());
        generateBtn.setOnAction(e -> onGenerate());
        if (!aiClient.isConfigured()) {
            promptField.setDisable(true);
            generateBtn.setDisable(true);
            promptField.setTooltip(new Tooltip("Set CHATGPT_API_KEY in .env to enable AI generation."));
        }

        HBox bar = new HBox(8, uploadBtn, new Label("Prompt:"), promptField, generateBtn);
        bar.setPadding(new Insets(6));
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        TitledPane pane = new TitledPane("Upload an image, or describe one (AI)", bar);
        pane.setCollapsible(false);

        status.setPadding(new Insets(4, 8, 4, 8));
        VBox bottom = new VBox(pane, status);
        bottom.setPadding(new Insets(8, 0, 0, 0));
        return bottom;
    }

    private static HBox labeled(String text, Region control) {
        Label l = new Label(text);
        l.setMinWidth(44);
        HBox.setHgrow(control, Priority.ALWAYS);
        HBox box = new HBox(6, l, control);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    // ---- Source selection -----------------------------------------------------

    private void selectScene(com.devoxx.led.scene.Scene scene) {
        this.currentScene = scene;
        this.uploadedFrame = null;
        this.lastUploadedFile = null;
        this.currentName = scene.name();
        this.animFrame = 0;
        redraw();
        status.setText("Scene: " + scene.name() + " (animated, " + TOTAL_FRAMES + " frames)");
    }

    private void onUpload() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        lastUploadedFile = file;
        reapplyUpload();
    }

    private void reapplyUpload() {
        if (lastUploadedFile == null) {
            return;
        }
        try {
            ImageAdapter.FitMode fit = fitBox.getValue();
            boolean poster = posterizeBox.isSelected();
            int levels = levelsBox.getValue();
            Frame f = ImageAdapter.adapt(lastUploadedFile, fit, poster, levels);
            this.uploadedFrame = f;
            this.currentScene = null;
            this.currentName = baseName(lastUploadedFile.getName());
            redraw();
            status.setText("Image: " + lastUploadedFile.getName()
                    + "  [" + fit + (poster ? ", posterize x" + levels : "") + "]  (still 64x64)");
        } catch (Exception ex) {
            error("Could not load image: " + ex.getMessage());
        }
    }

    // ---- AI prompt -> image ----------------------------------------------------

    private void onGenerate() {
        final String prompt = promptField.getText() == null ? "" : promptField.getText().trim();
        if (prompt.isEmpty()) {
            status.setText("Type a prompt first.");
            return;
        }
        generateBtn.setDisable(true);
        promptField.setDisable(true);
        status.setText("Generating image for: \"" + prompt + "\" ...");

        final ImageAdapter.FitMode fit = fitBox.getValue();
        final boolean poster = posterizeBox.isSelected();
        final int levels = levelsBox.getValue();

        Task<Frame> task = new Task<>() {
            @Override
            protected Frame call() throws Exception {
                BufferedImage big = aiClient.generate(prompt);
                return ImageAdapter.adapt(big, fit, poster, levels);
            }
        };
        task.setOnSucceeded(ev -> {
            try {
                Frame f = task.getValue();
                uploadedFrame = f;
                currentScene = null;
                lastUploadedFile = null;
                currentName = "ai-" + baseName(prompt);
                redraw();
                String saved = saveGenerated(f, currentName);
                status.setText("AI image saved: generated/" + saved + "  [" + fit
                        + (poster ? ", posterize x" + levels : "") + "]  (still 64x64)");
            } catch (Exception ex) {
                error("Saving failed: " + ex.getMessage());
            } finally {
                generateBtn.setDisable(false);
                promptField.setDisable(false);
            }
        });
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            error("Generation failed: " + (ex != null ? ex.getMessage() : "unknown error"));
            generateBtn.setDisable(false);
            promptField.setDisable(false);
        });
        Thread t = new Thread(task, "ai-generate");
        t.setDaemon(true);
        t.start();
    }

    // ---- Generated library -----------------------------------------------------

    private String saveGenerated(Frame frame, String stem) throws Exception {
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
        generatedList.getSelectionModel().select(name);
        return name;
    }

    private void refreshGeneratedList() {
        generatedItems.clear();
        if (Files.isDirectory(GENERATED_DIR)) {
            try (var stream = Files.list(GENERATED_DIR)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(generatedItems::add);
            } catch (Exception ignored) {
                // leave empty
            }
        }
    }

    private void loadGenerated(String fileName) {
        try {
            File file = GENERATED_DIR.resolve(fileName).toFile();
            Frame f = ImageAdapter.adapt(file, ImageAdapter.FitMode.LETTERBOX, false, 4);
            this.uploadedFrame = f;
            this.currentScene = null;
            this.lastUploadedFile = null;
            this.currentName = baseName(fileName);
            redraw();
            status.setText("Generated: " + fileName + "  (still 64x64)");
        } catch (Exception ex) {
            error("Could not load generated image: " + ex.getMessage());
        }
    }

    // ---- Export ----------------------------------------------------------------

    private void onExport() {
        try {
            Path dir = Path.of("target", "visuals");
            Files.createDirectories(dir);

            if (uploadedFrame != null) {
                File out = dir.resolve(currentName + ".png").toFile();
                new PngWriter().write(uploadedFrame, out);
                status.setText("Exported " + out.getPath() + "  (" + out.length() + " bytes)");
            } else if (currentScene != null) {
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
        } catch (Exception ex) {
            error("Export failed: " + ex.getMessage());
        }
    }

    // ---- Send to Pixoo ---------------------------------------------------------

    private void onSendToPixoo() {
        final String host = hostField.getText() == null ? "" : hostField.getText().trim();
        if (host.isEmpty()) {
            error("Enter the Pixoo 64 IP address first (e.g. 192.168.1.100).");
            return;
        }
        final com.devoxx.led.scene.Scene scene = currentScene;
        final Frame still = uploadedFrame;
        if (scene == null && still == null) {
            status.setText("Nothing to send.");
            return;
        }

        sendBtn.setDisable(true);
        status.setText("Sending to Pixoo at " + host + " ...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
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
        };
        task.setOnSucceeded(ev -> {
            status.setText("Sent to Pixoo at " + host
                    + (scene != null ? " (animation)" : " (still)") + ".");
            sendBtn.setDisable(false);
        });
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            error("Send failed: " + (ex != null ? ex.getMessage() : "unknown")
                    + "  (device on same WiFi? IP correct?)");
            sendBtn.setDisable(false);
        });
        Thread t = new Thread(task, "pixoo-send");
        t.setDaemon(true);
        t.start();
    }

    // ---- Rendering -------------------------------------------------------------

    /** Draw the active frame into the WritableImage and scale it onto the canvas. */
    private void redraw() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::redraw);
            return;
        }
        Frame f;
        if (uploadedFrame != null) {
            f = uploadedFrame;
        } else if (currentScene != null) {
            f = currentScene.render(animFrame, TOTAL_FRAMES);
        } else {
            return;
        }
        PixelWriter pw = image.getPixelWriter();
        int[] px = f.pixels();
        for (int y = 0; y < MATRIX; y++) {
            for (int x = 0; x < MATRIX; x++) {
                pw.setArgb(x, y, px[y * MATRIX + x]);
            }
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        // Nearest-neighbor scale: disable smoothing so LEDs stay crisp.
        g.setImageSmoothing(false);
        g.drawImage(image, 0, 0, MATRIX * SCALE, MATRIX * SCALE);
    }

    private void error(String msg) {
        status.setText(msg);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        base = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (base.length() > 32) {
            base = base.substring(0, 32).replaceAll("-+$", "");
        }
        return base.isEmpty() ? "upload" : base;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
