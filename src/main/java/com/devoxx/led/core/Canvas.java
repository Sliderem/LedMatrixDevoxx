package com.devoxx.led.core;

import java.util.Arrays;

/**
 * A fixed 64x64 ARGB drawing surface backed by a flat {@code int[4096]}
 * (0xAARRGGBB, row-major). Scenes draw into a Canvas and then call
 * {@link #toFrame()} to produce an aliasing-free {@link Frame} snapshot.
 */
public final class Canvas {

    public static final int SIZE = 64;

    private final int[] pixels = new int[SIZE * SIZE];

    public int width() {
        return SIZE;
    }

    public int height() {
        return SIZE;
    }

    /** Direct read access to the backing ARGB value at (x,y); 0 if out of bounds. */
    public int getPixel(int x, int y) {
        if (!inBounds(x, y)) {
            return 0;
        }
        return pixels[y * SIZE + x];
    }

    /** Set a pixel, ignoring coordinates outside the 64x64 surface. */
    public void setPixel(int x, int y, int argb) {
        if (inBounds(x, y)) {
            pixels[y * SIZE + x] = argb;
        }
    }

    /** Flood the whole surface with a single color. */
    public void fill(int argb) {
        Arrays.fill(pixels, argb);
    }

    /** Bresenham line between two points inclusive. */
    public void drawLine(int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            setPixel(x, y, argb);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** Outline of an axis-aligned rectangle with top-left (x,y). */
    public void drawRect(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int x1 = x + w - 1;
        int y1 = y + h - 1;
        drawLine(x, y, x1, y, argb);
        drawLine(x, y1, x1, y1, argb);
        drawLine(x, y, x, y1, argb);
        drawLine(x1, y, x1, y1, argb);
    }

    /** Filled axis-aligned rectangle with top-left (x,y). */
    public void fillRect(int x, int y, int w, int h, int argb) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                setPixel(x + i, y + j, argb);
            }
        }
    }

    /** Midpoint circle outline centred on (cx,cy). */
    public void drawCircle(int cx, int cy, int r, int argb) {
        if (r < 0) {
            return;
        }
        int x = r;
        int y = 0;
        int err = 1 - r;
        while (x >= y) {
            plotCircleOctants(cx, cy, x, y, argb);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    /** Filled disc centred on (cx,cy) using a squared-radius test. */
    public void fillCircle(int cx, int cy, int r, int argb) {
        if (r < 0) {
            return;
        }
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    setPixel(cx + dx, cy + dy, argb);
                }
            }
        }
    }

    /** Alpha-over composite {@code argb} onto the existing pixel at (x,y). */
    public void blend(int x, int y, int argb) {
        if (!inBounds(x, y)) {
            return;
        }
        int sa = Rgb.a(argb);
        if (sa == 0) {
            return;
        }
        if (sa == 255) {
            pixels[y * SIZE + x] = argb;
            return;
        }
        int dst = pixels[y * SIZE + x];
        int da = Rgb.a(dst);
        double saf = sa / 255.0;
        double daf = da / 255.0;
        double outAf = saf + daf * (1 - saf);
        if (outAf <= 0) {
            pixels[y * SIZE + x] = 0;
            return;
        }
        int outR = (int) Math.round((Rgb.r(argb) * saf + Rgb.r(dst) * daf * (1 - saf)) / outAf);
        int outG = (int) Math.round((Rgb.g(argb) * saf + Rgb.g(dst) * daf * (1 - saf)) / outAf);
        int outB = (int) Math.round((Rgb.b(argb) * saf + Rgb.b(dst) * daf * (1 - saf)) / outAf);
        int outA = (int) Math.round(outAf * 255);
        pixels[y * SIZE + x] = Rgb.argb(outR, outG, outB, outA);
    }

    /** Produce an independent Frame; the backing array is copied so scenes cannot alias. */
    public Frame toFrame() {
        return new Frame(Arrays.copyOf(pixels, pixels.length), SIZE, SIZE);
    }

    private void plotCircleOctants(int cx, int cy, int x, int y, int argb) {
        setPixel(cx + x, cy + y, argb);
        setPixel(cx - x, cy + y, argb);
        setPixel(cx + x, cy - y, argb);
        setPixel(cx - x, cy - y, argb);
        setPixel(cx + y, cy + x, argb);
        setPixel(cx - y, cy + x, argb);
        setPixel(cx + y, cy - x, argb);
        setPixel(cx - y, cy - x, argb);
    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }
}
