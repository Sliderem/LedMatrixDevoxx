package com.devoxx.led.core;

/**
 * An immutable snapshot of a rendered surface. {@code pixels} holds
 * {@code width * height} packed ARGB (0xAARRGGBB) ints in row-major order.
 * For LED matrix output width and height are expected to be 64.
 */
public record Frame(int[] pixels, int width, int height) {

    /** Allocate a blank (fully transparent/black) frame of the given size. */
    public static Frame of(int w, int h) {
        return new Frame(new int[w * h], w, h);
    }
}
