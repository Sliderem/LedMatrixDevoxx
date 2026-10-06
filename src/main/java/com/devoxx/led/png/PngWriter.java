package com.devoxx.led.png;

import com.devoxx.led.core.Frame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Writes a single {@link Frame} to a PNG file using {@link ImageIO}.
 *
 * <p>Pixels are interpreted as packed ARGB (0xAARRGGBB) and copied into a
 * {@link BufferedImage#TYPE_INT_ARGB} image before encoding.</p>
 */
public final class PngWriter {

    /**
     * Write {@code frame} to {@code file} as a PNG.
     *
     * @param frame the source frame (row-major packed ARGB pixels)
     * @param file  destination file (parent directories must exist)
     */
    public void write(Frame frame, File file) throws IOException {
        int w = frame.width();
        int h = frame.height();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, frame.pixels(), 0, w);
        if (!ImageIO.write(img, "png", file)) {
            throw new IOException("No PNG ImageWriter available");
        }
    }
}
