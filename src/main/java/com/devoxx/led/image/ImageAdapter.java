package com.devoxx.led.image;

import com.devoxx.led.core.Canvas;
import com.devoxx.led.core.Frame;
import com.devoxx.led.core.Rgb;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Adapts an arbitrary source image into a 64x64 {@link Frame} suitable for the
 * LED matrix, with two optional treatments:
 *
 * <ul>
 *   <li><b>Fit mode</b>: letterbox (preserve aspect ratio, pad with black) or
 *       crop-to-square (fill the whole 64x64 canvas, cropping the long edge).</li>
 *   <li><b>LED-style posterize</b>: quantize each RGB channel to a small number
 *       of levels so a photo reads like deliberate LED pixel art rather than a
 *       muddy thumbnail. Disabled leaves the resized pixels untouched.</li>
 * </ul>
 *
 * <p>Only the first frame of an animated GIF is used; the result is a single
 * still {@link Frame}. All processing is done with the JDK (ImageIO + AWT),
 * no external dependencies.</p>
 */
public final class ImageAdapter {

    private ImageAdapter() {
    }

    /** How the source image is mapped onto the square 64x64 canvas. */
    public enum FitMode {
        /** Preserve aspect ratio; pad the short dimension with black bars. */
        LETTERBOX,
        /** Fill the canvas; crop the long dimension to a centered square. */
        CROP
    }

    /**
     * Load {@code file} and adapt it to a 64x64 {@link Frame}.
     *
     * @param file          source image (PNG, JPG, BMP, GIF - first frame)
     * @param fit           letterbox or crop-to-square
     * @param ledPosterize  if {@code true}, apply LED-style channel posterization
     * @param levels        posterization levels per channel (used only when
     *                      {@code ledPosterize}); clamped to 2..8
     * @throws IOException if the file cannot be read as an image
     */
    public static Frame adapt(File file, FitMode fit, boolean ledPosterize, int levels)
            throws IOException {
        BufferedImage src = ImageIO.read(file);
        if (src == null) {
            throw new IOException("Unsupported or unreadable image: " + file);
        }
        return adapt(src, fit, ledPosterize, levels);
    }

    /** Adapt an in-memory image (used for GIF frames or already-loaded images). */
    public static Frame adapt(BufferedImage src, FitMode fit, boolean ledPosterize, int levels) {
        int size = Canvas.SIZE;
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        // Opaque black background so letterbox bars and transparency read as black.
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, size, size);

        int sw = src.getWidth();
        int sh = src.getHeight();

        if (fit == FitMode.CROP) {
            // Scale so the SHORT edge fills 64, then center-crop the long edge.
            double scale = Math.max((double) size / sw, (double) size / sh);
            int dw = (int) Math.round(sw * scale);
            int dh = (int) Math.round(sh * scale);
            int dx = (size - dw) / 2;
            int dy = (size - dh) / 2;
            g.drawImage(src, dx, dy, dw, dh, null);
        } else {
            // Scale so the LONG edge fits 64, centered, with black bars.
            double scale = Math.min((double) size / sw, (double) size / sh);
            int dw = (int) Math.round(sw * scale);
            int dh = (int) Math.round(sh * scale);
            int dx = (size - dw) / 2;
            int dy = (size - dh) / 2;
            g.drawImage(src, dx, dy, dw, dh, null);
        }
        g.dispose();

        int[] pixels = new int[size * size];
        square.getRGB(0, 0, size, size, pixels, 0, size);

        if (ledPosterize) {
            int lv = Math.max(2, Math.min(8, levels));
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = posterize(pixels[i], lv);
            }
        } else {
            // Force opaque so exported GIF/PNG have no stray alpha.
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0xFF000000 | (pixels[i] & 0xFFFFFF);
            }
        }

        return new Frame(pixels, size, size);
    }

    /** Quantize each RGB channel to {@code levels} evenly-spaced steps. */
    private static int posterize(int argb, int levels) {
        int r = quantizeChannel(Rgb.r(argb), levels);
        int g = quantizeChannel(Rgb.g(argb), levels);
        int b = quantizeChannel(Rgb.b(argb), levels);
        return Rgb.argb(r, g, b, 255);
    }

    private static int quantizeChannel(int v, int levels) {
        int step = 255 / (levels - 1);
        int q = Math.round((float) v / step) * step;
        return Math.max(0, Math.min(255, q));
    }
}
