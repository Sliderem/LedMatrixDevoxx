package com.devoxx.led.scene;

import com.devoxx.led.core.Canvas;
import com.devoxx.led.core.Frame;
import com.devoxx.led.core.PixelFont;
import com.devoxx.led.core.Rgb;

/**
 * "DEVOXX" over "2026" wordmark that pulses in brightness, with a Devoxx-orange
 * accent bar scrolling horizontally and a Belgian-flag (black / yellow / red)
 * band sweeping behind the text. Every motion is driven by
 * {@code phase = frameIndex/totalFrames} and wraps modularly for a seamless loop.
 */
public final class DevoxxScene implements Scene {

    private static final int BG = Rgb.argb(8, 8, 12);
    private static final int ORANGE = Rgb.argb(0xF4, 0x72, 0x16);
    private static final int WHITE = Rgb.argb(255, 255, 255);

    // Belgian flag colors.
    private static final int BELGE_BLACK = Rgb.argb(20, 20, 24);
    private static final int BELGE_YELLOW = Rgb.argb(0xFF, 0xD9, 0x0F);
    private static final int BELGE_RED = Rgb.argb(0xEF, 0x33, 0x40);

    @Override
    public String name() {
        return "devoxx";
    }

    @Override
    public int[] palette() {
        return new int[] {
                0x08080C, 0x141418, 0xF47216, 0xFFFFFF,
                0xFFD90F, 0xEF3340, 0x7A3A0B, 0x804A08,
                0x771A20, 0x808080, 0x3C3C3C, 0xC05912
        };
    }

    @Override
    public Frame render(int frameIndex, int totalFrames) {
        double phase = (double) frameIndex / totalFrames;
        Canvas c = new Canvas();
        c.fill(BG);

        // Belgian-flag vertical bands sweeping left across the full width,
        // wrapping modularly so phase 1 == phase 0.
        int bandW = Canvas.SIZE / 3;
        int shift = (int) Math.round(phase * Canvas.SIZE) % Canvas.SIZE;
        for (int x = 0; x < Canvas.SIZE; x++) {
            int sx = ((x + shift) % Canvas.SIZE + Canvas.SIZE) % Canvas.SIZE;
            int band = sx / bandW;
            int col = switch (band) {
                case 0 -> BELGE_BLACK;
                case 1 -> BELGE_YELLOW;
                default -> BELGE_RED;
            };
            // Dim the bands so the wordmark stays dominant.
            int dim = Rgb.scaleBrightness(col, 0.35);
            for (int y = 0; y < Canvas.SIZE; y++) {
                c.setPixel(x, y, Rgb.lerp(BG, dim, 0.7));
            }
        }

        // Brightness pulse that returns to its start at phase 1.
        double pulse = 0.6 + 0.4 * (0.5 + 0.5 * Math.sin(2 * Math.PI * phase));

        // Scrolling orange accent bar, wraps modularly.
        int barH = 3;
        int barY = (int) Math.round(phase * (Canvas.SIZE + barH)) - barH;
        for (int dy = 0; dy < barH; dy++) {
            int y = ((barY + dy) % Canvas.SIZE + Canvas.SIZE) % Canvas.SIZE;
            c.fillRect(0, y, Canvas.SIZE, 1, Rgb.scaleBrightness(ORANGE, pulse));
        }

        // Wordmark: "DEVOXX" on one line, "2026" below, both centered.
        int spacing = 1;
        int devoxxW = PixelFont.textWidth("DEVOXX", spacing);
        int yearW = PixelFont.textWidth("2026", spacing);
        int devoxxX = (Canvas.SIZE - devoxxW) / 2;
        int yearX = (Canvas.SIZE - yearW) / 2;
        int line1Y = 18;
        int line2Y = 36;

        int textColor = Rgb.scaleBrightness(WHITE, pulse);
        int yearColor = Rgb.scaleBrightness(ORANGE, 0.6 + 0.4 * pulse);

        // Drop a dark outline for legibility against the bands.
        drawOutlined(c, "DEVOXX", devoxxX, line1Y, textColor, spacing);
        drawOutlined(c, "2026", yearX, line2Y, yearColor, spacing);

        return c.toFrame();
    }

    private static void drawOutlined(Canvas c, String s, int x, int y, int color, int spacing) {
        int shadow = Rgb.argb(0, 0, 0);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) {
                    PixelFont.drawText(c, s, x + ox, y + oy, shadow, spacing);
                }
            }
        }
        PixelFont.drawText(c, s, x, y, color, spacing);
    }
}
