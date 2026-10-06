package com.devoxx.led.scene;

import com.devoxx.led.core.Canvas;
import com.devoxx.led.core.Frame;
import com.devoxx.led.core.Rgb;

/**
 * Four Google-colored dots orbit a stylized central cloud. Each dot angle is
 * {@code 2*PI*phase + perDotOffset}, so after one full phase cycle every dot
 * returns to its start. The cloud gently breathes via {@code sin(2*PI*phase)}.
 */
public final class GoogleCloudScene implements Scene {

    private static final int BG = Rgb.argb(10, 12, 20);
    private static final int BLUE = Rgb.argb(0x42, 0x85, 0xF4);
    private static final int RED = Rgb.argb(0xEA, 0x43, 0x35);
    private static final int YELLOW = Rgb.argb(0xFB, 0xBC, 0x05);
    private static final int GREEN = Rgb.argb(0x34, 0xA8, 0x53);
    private static final int CLOUD = Rgb.argb(0xF1, 0xF3, 0xF4);
    private static final int CLOUD_SHADE = Rgb.argb(0xBD, 0xC1, 0xC6);

    private static final int[] DOT_COLORS = {BLUE, RED, YELLOW, GREEN};

    @Override
    public String name() {
        return "googlecloud";
    }

    @Override
    public int[] palette() {
        return new int[] {
                0x0A0C14, 0x4285F4, 0xEA4335, 0xFBBC05, 0x34A853,
                0xF1F3F4, 0xBDC1C6, 0x1A2A4A, 0x6AA0F7, 0xEF6A5F,
                0xFCCB3A, 0x5CBE76, 0x303030, 0x9AA0A6
        };
    }

    @Override
    public Frame render(int frameIndex, int totalFrames) {
        double phase = (double) frameIndex / totalFrames;
        Canvas c = new Canvas();
        c.fill(BG);

        int cx = Canvas.SIZE / 2;
        int cy = Canvas.SIZE / 2;

        // Breathing cloud radius returns to its start at phase 1.
        double breathe = Math.sin(2 * Math.PI * phase);
        int base = 8 + (int) Math.round(breathe * 1.5);
        drawCloud(c, cx, cy, base);

        // Orbiting dots.
        double orbit = 22.0;
        for (int i = 0; i < DOT_COLORS.length; i++) {
            double angle = 2 * Math.PI * phase + i * (Math.PI / 2);
            int dx = (int) Math.round(Math.cos(angle) * orbit);
            int dy = (int) Math.round(Math.sin(angle) * (orbit * 0.6));
            int px = cx + dx;
            int py = cy + dy;
            // Soft glow underneath then the solid dot on top.
            c.fillCircle(px, py, 4, Rgb.scaleBrightness(DOT_COLORS[i], 0.35));
            c.fillCircle(px, py, 3, DOT_COLORS[i]);
            c.setPixel(px, py, WHITEN(DOT_COLORS[i]));
        }

        return c.toFrame();
    }

    private static int WHITEN(int color) {
        return Rgb.lerp(color, Rgb.argb(255, 255, 255), 0.5);
    }

    /** A simple three-lobe cloud silhouette centered on (cx,cy). */
    private static void drawCloud(Canvas c, int cx, int cy, int r) {
        // Soft shaded base lobes.
        c.fillCircle(cx - r, cy + 1, r - 1, CLOUD_SHADE);
        c.fillCircle(cx + r, cy + 1, r - 1, CLOUD_SHADE);
        c.fillCircle(cx, cy - 2, r, CLOUD_SHADE);
        // Bright highlight lobes slightly offset up.
        c.fillCircle(cx - r, cy, r - 2, CLOUD);
        c.fillCircle(cx + r, cy, r - 2, CLOUD);
        c.fillCircle(cx, cy - 3, r - 1, CLOUD);
        // Flat base row to read as a cloud rather than loose blobs.
        c.fillRect(cx - r, cy, 2 * r, r - 1, CLOUD);
    }
}
