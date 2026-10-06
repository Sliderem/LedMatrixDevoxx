package com.devoxx.led.gif;

import com.devoxx.led.core.Frame;
import com.devoxx.led.png.PngWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Self-check for the pure-Java GIF89a encoder and the PNG writer: encode a
 * 2-frame 64x64 GIF of two solid colors, write it to {@code target/_selfcheck.gif},
 * and read it back with an ImageIO {@link ImageReader} to confirm the decoded
 * dimensions and frame count. Also verifies the PngWriter emits a 64x64 PNG.
 */
class GifEncoderTest {

    private static final int SIZE = 64;

    private static Frame solid(int argb) {
        int[] px = new int[SIZE * SIZE];
        java.util.Arrays.fill(px, argb);
        return new Frame(px, SIZE, SIZE);
    }

    @Test
    void encodesTwoFrameGifDecodableByImageIO() throws Exception {
        int red = 0xFFFF0000;
        int blue = 0xFF0000FF;
        List<Frame> frames = List.of(solid(red), solid(blue));
        int[] palette = {0xFF0000, 0x0000FF};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new GifEncoder().encode(frames, palette, 60, bos);
        byte[] gif = bos.toByteArray();

        // Also persist to target/ for manual inspection.
        File target = new File("target");
        if (target.isDirectory() || target.mkdirs()) {
            java.nio.file.Files.write(new File(target, "_selfcheck.gif").toPath(), gif);
        }

        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("gif");
        assertTrue(readers.hasNext(), "no GIF ImageReader available");
        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            reader.setInput(iis, false);
            int numImages = reader.getNumImages(true);
            assertEquals(2, numImages, "expected 2 frames");
            assertEquals(SIZE, reader.getWidth(0), "frame width");
            assertEquals(SIZE, reader.getHeight(0), "frame height");

            // Confirm the decoded colors are sane (first frame red-ish, second blue-ish).
            BufferedImage f0 = reader.read(0);
            BufferedImage f1 = reader.read(1);
            int c0 = f0.getRGB(32, 32) & 0xFFFFFF;
            int c1 = f1.getRGB(32, 32) & 0xFFFFFF;
            assertTrue(((c0 >> 16) & 0xFF) > 200 && (c0 & 0xFF) < 50, "frame 0 should be red");
            assertTrue((c1 & 0xFF) > 200 && ((c1 >> 16) & 0xFF) < 50, "frame 1 should be blue");
        } finally {
            reader.dispose();
        }
    }

    @Test
    void pngWriterProduces64x64() throws Exception {
        File target = new File("target");
        assertTrue(target.isDirectory() || target.mkdirs());
        File png = new File(target, "_selfcheck.png");
        new PngWriter().write(solid(0xFF00FF00), png);

        BufferedImage read = ImageIO.read(png);
        assertNotNull(read, "PNG should be readable");
        assertEquals(SIZE, read.getWidth());
        assertEquals(SIZE, read.getHeight());
    }
}
