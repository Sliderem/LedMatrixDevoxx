package com.devoxx.led.scene;

import com.devoxx.led.core.Canvas;
import com.devoxx.led.core.Frame;
import com.devoxx.led.core.PixelFont;
import com.devoxx.led.core.Rgb;

/**
 * A warm stylized coffee cup with looping steam. Steam columns are sine-displaced
 * pixels that scroll upward and wrap modularly in height, and the horizontal
 * sway uses {@code sin(2*PI*phase + ...)}, so the loop is seamless. A subtle
 * "JAVA" label sits across the cup.
 */
public final class JavaCoffeeScene implements Scene {

    private static final int BG = Rgb.argb(18, 10, 6);
    private static final int CUP = Rgb.argb(0xE7, 0x4C, 0x1C);        // warm terracotta
    private static final int CUP_DARK = Rgb.argb(0xA8, 0x33, 0x10);
    private static final int CUP_RIM = Rgb.argb(0xFF, 0x8A, 0x50);
    private static final int COFFEE = Rgb.argb(0x3B, 0x1E, 0x0E);
    private static final int COFFEE_TOP = Rgb.argb(0x6F, 0x3E, 0x1E);
    private static final int STEAM = Rgb.argb(0xFF, 0xE6, 0xC8);
    private static final int LABEL = Rgb.argb(0xFF, 0xD9, 0x8A);

    @Override
    public String name() {
        return "javacoffee";
    }

    @Override
    public int[] palette() {
        return new int[] {
                0x120A06, 0xE74C1C, 0xA83310, 0xFF8A50, 0x3B1E0E,
                0x6F3E1E, 0xFFE6C8, 0xFFD98A, 0x7A6B5A, 0x2A1810,
                0xC0A080, 0xFFF4E6
        };
    }

    @Override
    public Frame render(int frameIndex, int totalFrames) {
        double phase = (double) frameIndex / totalFrames;
        Canvas c = new Canvas();
        c.fill(BG);

        // --- Cup body -------------------------------------------------------
        int cupX = 18;
        int cupY = 30;
        int cupW = 24;
        int cupH = 22;

        // Body with a subtle darker side for shape.
        c.fillRect(cupX, cupY, cupW, cupH, CUP);
        c.fillRect(cupX, cupY, 4, cupH, CUP_DARK);
        c.fillRect(cupX + cupW - 3, cupY, 3, cupH, CUP_DARK);
        // Rim.
        c.fillRect(cupX - 1, cupY - 2, cupW + 2, 3, CUP_RIM);
        // Coffee surface.
        c.fillRect(cupX + 2, cupY, cupW - 4, 2, COFFEE_TOP);
        c.fillRect(cupX + 2, cupY + 2, cupW - 4, 3, COFFEE);

        // Handle on the right.
        c.drawCircle(cupX + cupW + 2, cupY + cupH / 2, 5, CUP);
        c.drawCircle(cupX + cupW + 2, cupY + cupH / 2, 4, CUP);
        c.fillCircle(cupX + cupW + 2, cupY + cupH / 2, 2, BG);

        // Saucer.
        c.fillRect(cupX - 4, cupY + cupH, cupW + 8, 2, CUP_DARK);
        c.fillRect(cupX - 2, cupY + cupH + 2, cupW + 4, 1, CUP_RIM);

        // --- Looping steam --------------------------------------------------
        // Three columns of upward-scrolling sine-displaced pixels. The vertical
        // position wraps over the steam region so the stream looks continuous
        // and returns to its start at phase 1.
        int[] steamBaseX = {cupX + 6, cupX + 12, cupX + 18};
        int topY = 6;
        int bottomY = cupY - 3;
        int span = bottomY - topY;
        for (int col = 0; col < steamBaseX.length; col++) {
            int baseX = steamBaseX[col];
            double colPhaseOffset = col * (2 * Math.PI / 3.0);
            for (int t = 0; t < span; t++) {
                // Scroll upward and wrap within the steam span.
                double scrolled = (t + phase * span);
                int y = bottomY - (int) (scrolled % span);
                double yy = (double) (bottomY - y);
                double amp = 3.0 * (yy / span);  // widen as it rises
                int x = baseX + (int) Math.round(amp
                        * Math.sin(2 * Math.PI * phase + colPhaseOffset + yy * 0.5));
                // Fade with height for a wispy top.
                double fade = 1.0 - (yy / span) * 0.7;
                c.blend(x, y, withAlpha(STEAM, (int) Math.round(200 * fade)));
            }
        }

        // --- JAVA label -----------------------------------------------------
        int spacing = 1;
        int labelW = PixelFont.textWidth("JAVA", spacing);
        int labelX = cupX + (cupW - labelW) / 2;
        PixelFont.drawText(c, "JAVA", labelX, cupY + 10, LABEL, spacing);

        return c.toFrame();
    }

    private static int withAlpha(int color, int alpha) {
        return Rgb.argb(Rgb.r(color), Rgb.g(color), Rgb.b(color), alpha);
    }
}
