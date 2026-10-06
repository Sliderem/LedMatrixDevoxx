package com.devoxx.led.scene;

import com.devoxx.led.core.Frame;

/**
 * A seamless-looping 64x64 visual. Implementations must drive all motion from
 * {@code phase = (double) frameIndex / totalFrames} in [0,1) so that rendering
 * {@code frameIndex == totalFrames} reproduces {@code frameIndex == 0}; this
 * guarantees the animated GIF loops without a visible seam.
 *
 * <p>Sealed to the four themed Devoxx 2026 scenes.
 */
public sealed interface Scene
        permits DevoxxScene, GoogleCloudScene, JavaCoffeeScene, PlasmaScene {

    /** Stable, lowercase identifier used for the output file name. */
    String name();

    /** Render a single frame. {@code frameIndex} in [0,totalFrames). */
    Frame render(int frameIndex, int totalFrames);

    /**
     * An optional curated palette of packed 0xRRGGBB entries (<=64 recommended)
     * handed straight to the GIF encoder for crisp LED colors. Returning
     * {@code null} lets the encoder quantize the frames instead.
     */
    default int[] palette() {
        return null;
    }
}
