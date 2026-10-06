package com.devoxx.led.scene;

import com.devoxx.led.core.Canvas;
import com.devoxx.led.core.Frame;
import com.devoxx.led.core.Rgb;

/**
 * A high-energy plasma field whose sine arguments all include {@code 2*PI*phase}
 * so the whole field returns to its start at phase 1 (seamless loop). The scalar
 * field is mapped through a bold color ramp built from the four Google colors,
 * color-cycling with phase. An explicit capped palette keeps the GIF small.
 */
public final class PlasmaScene implements Scene {

    private static final int BLUE = Rgb.argb(0x42, 0x85, 0xF4);
    private static final int RED = Rgb.argb(0xEA, 0x43, 0x35);
    private static final int YELLOW = Rgb.argb(0xFB, 0xBC, 0x05);
    private static final int GREEN = Rgb.argb(0x34, 0xA8, 0x53);

    // Four anchor colors cycled around a ring.
    private static final int[] RAMP = {BLUE, RED, YELLOW, GREEN};

    // Explicit capped palette: a smooth 48-stop ring through the Google colors.
    private final int[] palette = buildPalette();

    @Override
    public String name() {
        return "plasma";
    }

    @Override
    public int[] palette() {
        return palette.clone();
    }

    @Override
    public Frame render(int frameIndex, int totalFrames) {
        double phase = (double) frameIndex / totalFrames;
        Canvas c = new Canvas();

        double tp = 2 * Math.PI * phase;
        double cx = Canvas.SIZE / 2.0;
        double cy = Canvas.SIZE / 2.0;

        for (int y = 0; y < Canvas.SIZE; y++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double dx = x - cx;
                double dy = y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);

                // All phase terms are multiples of 2*PI*phase -> loops cleanly.
                double v = Math.sin(x * 0.18 + tp)
                        + Math.sin(y * 0.16 - tp)
                        + Math.sin((x + y) * 0.12 + tp)
                        + Math.sin(dist * 0.25 - tp);

                // Normalize roughly from [-4,4] to [0,1).
                double norm = (v + 4.0) / 8.0;
                // Color-cycle the ramp lookup with phase.
                double t = (norm + phase) % 1.0;
                c.setPixel(x, y, sampleRamp(t));
            }
        }

        return c.toFrame();
    }

    /** Sample the four-color ring at t in [0,1). */
    private static int sampleRamp(double t) {
        double scaled = ((t % 1.0) + 1.0) % 1.0 * RAMP.length;
        int i = (int) scaled;
        double frac = scaled - i;
        int a = RAMP[i % RAMP.length];
        int b = RAMP[(i + 1) % RAMP.length];
        return Rgb.lerp(a, b, frac);
    }

    private static int[] buildPalette() {
        int stops = 48;
        int[] p = new int[stops];
        for (int i = 0; i < stops; i++) {
            double t = (double) i / stops;
            int argb = sampleRamp(t);
            p[i] = argb & 0xFFFFFF; // packed 0xRRGGBB for the encoder
        }
        return p;
    }
}
