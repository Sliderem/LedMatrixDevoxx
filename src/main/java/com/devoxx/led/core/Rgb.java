package com.devoxx.led.core;

/**
 * Packed ARGB color helpers. Colors are stored as 0xAARRGGBB ints throughout
 * the rendering pipeline. All channel operations clamp to the 0..255 range.
 */
public final class Rgb {

    private Rgb() {
    }

    /** Pack opaque RGB into 0xFFRRGGBB. */
    public static int argb(int r, int g, int b) {
        return argb(r, g, b, 255);
    }

    /** Pack ARGB into 0xAARRGGBB, clamping each channel to 0..255. */
    public static int argb(int r, int g, int b, int a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int a(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int r(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    public static int g(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    public static int b(int argb) {
        return argb & 0xFF;
    }

    /**
     * Linearly interpolate between two packed ARGB colors.
     *
     * @param t blend factor in [0,1]; 0 returns c1, 1 returns c2.
     */
    public static int lerp(int c1, int c2, double t) {
        double f = t < 0 ? 0 : (t > 1 ? 1 : t);
        int a = (int) Math.round(a(c1) + (a(c2) - a(c1)) * f);
        int r = (int) Math.round(r(c1) + (r(c2) - r(c1)) * f);
        int g = (int) Math.round(g(c1) + (g(c2) - g(c1)) * f);
        int b = (int) Math.round(b(c1) + (b(c2) - b(c1)) * f);
        return argb(r, g, b, a);
    }

    /** Scale the RGB channels of a color by {@code f}, leaving alpha intact. */
    public static int scaleBrightness(int c, double f) {
        int r = (int) Math.round(r(c) * f);
        int g = (int) Math.round(g(c) * f);
        int b = (int) Math.round(b(c) * f);
        return argb(r, g, b, a(c));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
