package com.devoxx.led;

import com.devoxx.led.core.Frame;
import com.devoxx.led.gif.GifEncoder;
import com.devoxx.led.png.PngWriter;
import com.devoxx.led.scene.Scene;
import com.devoxx.led.scene.SceneRegistry;
import com.devoxx.led.verify.VisualVerifier;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Devoxx Belgium 2026 LED matrix visuals.
 *
 * <p>For every scene in {@link SceneRegistry}, this renders {@link #TOTAL_FRAMES}
 * frames into an animated GIF ({@code target/visuals/<name>.gif}) plus a key
 * still PNG ({@code target/visuals/<name>.png}). It then runs the
 * {@link VisualVerifier}; if every file passes the 64x64 / &le;1 MiB / frames&gt;1
 * contract the passing files are copied into {@code gallery/} at the repo root.
 * Any failure prints a summary and exits non-zero.
 */
public final class Main {

    /** Frames per animated GIF (seamless loop; frame TOTAL_FRAMES equals frame 0). */
    private static final int TOTAL_FRAMES = 24;

    /** Per-frame delay in milliseconds (~16.6 fps). */
    private static final int DELAY_MS = 60;

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            System.err.println("Generation failed: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() throws IOException {
        Path visualsDir = Path.of("target", "visuals");
        Files.createDirectories(visualsDir);

        GifEncoder gif = new GifEncoder();
        PngWriter png = new PngWriter();

        List<File> generated = new ArrayList<>();

        System.out.println("Rendering " + SceneRegistry.all().size()
                + " scenes (" + TOTAL_FRAMES + " frames @ " + DELAY_MS + "ms)...");
        System.out.println();

        for (Scene scene : SceneRegistry.all()) {
            String name = scene.name();

            List<Frame> frames = new ArrayList<>(TOTAL_FRAMES);
            for (int i = 0; i < TOTAL_FRAMES; i++) {
                frames.add(scene.render(i, TOTAL_FRAMES));
            }

            File gifFile = visualsDir.resolve(name + ".gif").toFile();
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(gifFile.toPath()))) {
                gif.encode(frames, scene.palette(), DELAY_MS, out);
            }
            generated.add(gifFile);
            System.out.printf("  %-14s -> %-28s %8d bytes%n",
                    name, gifFile.getPath(), gifFile.length());

            // Key still: a representative mid-animation frame.
            File pngFile = visualsDir.resolve(name + ".png").toFile();
            png.write(frames.get(TOTAL_FRAMES / 2), pngFile);
            generated.add(pngFile);
            System.out.printf("  %-14s -> %-28s %8d bytes%n",
                    name, pngFile.getPath(), pngFile.length());
        }

        System.out.println();

        VisualVerifier verifier = new VisualVerifier();
        VisualVerifier.Report report = verifier.verify(generated);
        verifier.printTable(report);

        if (!report.allPassed()) {
            System.err.println();
            System.err.println("One or more visuals failed verification. "
                    + "Tune the generator (smaller palette / fewer frames / tighter "
                    + "encoding) and regenerate. gallery/ was NOT updated.");
            System.exit(1);
        }

        // Copy passing files into the committed gallery.
        Path galleryDir = Path.of("gallery");
        Files.createDirectories(galleryDir);
        System.out.println();
        System.out.println("Copying passing visuals to gallery/ ...");
        for (VisualVerifier.Result r : report.results()) {
            Path src = visualsDir.resolve(r.filename());
            Path dst = galleryDir.resolve(r.filename());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  gallery/" + r.filename());
        }

        System.out.println();
        System.out.println("Done. " + report.results().size()
                + " files generated, verified, and copied to gallery/.");
    }
}
