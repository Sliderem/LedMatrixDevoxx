package com.devoxx.led.gif;

import com.devoxx.led.core.Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a shared palette of at most 256 colors from one or more {@link Frame}s
 * using the median-cut algorithm, then maps arbitrary ARGB colors to the nearest
 * palette entry by squared RGB distance.
 *
 * <p>Used as the fallback when a scene supplies no explicit palette (or supplies
 * one exceeding 256 colors). Colors are compared on their RGB channels only;
 * alpha is ignored for quantization (the GIF path treats fully transparent
 * pixels via a separate transparent index).</p>
 */
public final class MedianCutQuantizer {

    /** A color box: a list of 0xRRGGBB colors plus its bounding ranges. */
    private static final class Box {
        final int[] colors; // packed 0xRRGGBB
        int rMin, rMax, gMin, gMax, bMin, bMax;

        Box(int[] colors) {
            this.colors = colors;
            shrink();
        }

        void shrink() {
            rMin = gMin = bMin = 255;
            rMax = gMax = bMax = 0;
            for (int c : colors) {
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                if (r < rMin) rMin = r;
                if (r > rMax) rMax = r;
                if (g < gMin) gMin = g;
                if (g > gMax) gMax = g;
                if (b < bMin) bMin = b;
                if (b > bMax) bMax = b;
            }
        }

        int longestAxis() {
            int rr = rMax - rMin;
            int gg = gMax - gMin;
            int bb = bMax - bMin;
            if (rr >= gg && rr >= bb) return 0;
            if (gg >= bb) return 1;
            return 2;
        }

        int volume() {
            return (rMax - rMin) * (gMax - gMin) * (bMax - bMin);
        }
    }

    private int[] palette = new int[0]; // packed 0xRRGGBB entries

    /**
     * Build a palette of at most {@code maxColors} entries from the given frames.
     *
     * @param frames    source frames (pixels are packed ARGB; alpha is ignored)
     * @param maxColors maximum palette size (clamped to 1..256)
     * @return packed 0xRRGGBB palette entries (length 1..maxColors)
     */
    public int[] buildPalette(List<Frame> frames, int maxColors) {
        if (maxColors < 1) maxColors = 1;
        if (maxColors > 256) maxColors = 256;

        // Collect unique RGB colors (dedupe to keep boxes small).
        // Use a boolean occupancy over 0xRRGGBB space would be 16M bits (2MB) -
        // acceptable, but a HashSet keeps it simple and small for LED visuals.
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (Frame f : frames) {
            for (int argb : f.pixels()) {
                seen.add(argb & 0xFFFFFF);
            }
        }

        if (seen.isEmpty()) {
            palette = new int[]{0};
            return palette.clone();
        }

        int[] colors = new int[seen.size()];
        int i = 0;
        for (int c : seen) {
            colors[i++] = c;
        }

        // If already within budget, use the colors directly.
        if (colors.length <= maxColors) {
            palette = colors.clone();
            return palette.clone();
        }

        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(colors));

        // Repeatedly split the box with the largest volume until we reach maxColors.
        while (boxes.size() < maxColors) {
            Box target = null;
            int targetIdx = -1;
            int bestVol = -1;
            for (int b = 0; b < boxes.size(); b++) {
                Box box = boxes.get(b);
                if (box.colors.length < 2) continue;
                int v = box.volume();
                if (v > bestVol) {
                    bestVol = v;
                    target = box;
                    targetIdx = b;
                }
            }
            if (target == null) break; // no box can be split further

            Box[] split = splitBox(target);
            boxes.set(targetIdx, split[0]);
            boxes.add(split[1]);
        }

        palette = new int[boxes.size()];
        for (int b = 0; b < boxes.size(); b++) {
            palette[b] = averageColor(boxes.get(b).colors);
        }
        return palette.clone();
    }

    private static Box[] splitBox(Box box) {
        int axis = box.longestAxis();
        final int shift = switch (axis) {
            case 0 -> 16; // red
            case 1 -> 8;  // green
            default -> 0; // blue
        };
        // Sort by the chosen axis channel. Box key the channel in the high byte so
        // a plain ascending int sort orders by that channel first, with the full
        // original color preserved in the low 24 bits for exact reconstruction.
        long[] keyed = new long[box.colors.length];
        for (int i = 0; i < keyed.length; i++) {
            int c = box.colors[i];
            int ch = (c >> shift) & 0xFF;
            keyed[i] = ((long) ch << 24) | (c & 0xFFFFFF);
        }
        java.util.Arrays.sort(keyed);
        int[] colors = new int[keyed.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (int) (keyed[i] & 0xFFFFFF);
        }
        int mid = colors.length / 2;
        int[] left = java.util.Arrays.copyOfRange(colors, 0, mid);
        int[] right = java.util.Arrays.copyOfRange(colors, mid, colors.length);
        return new Box[]{new Box(left), new Box(right)};
    }

    private static int averageColor(int[] colors) {
        long r = 0, g = 0, b = 0;
        for (int c : colors) {
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int n = colors.length;
        int ri = (int) (r / n);
        int gi = (int) (g / n);
        int bi = (int) (b / n);
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Return the index of the palette entry nearest to {@code argb} by squared
     * RGB distance. {@link #buildPalette} must be called first.
     *
     * @param argb a packed ARGB color (alpha ignored)
     * @return palette index in {@code [0, paletteSize)}
     */
    public int indexOf(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int p = palette[i];
            int dr = r - ((p >> 16) & 0xFF);
            int dg = g - ((p >> 8) & 0xFF);
            int db = b - (p & 0xFF);
            long dist = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
                if (dist == 0) break;
            }
        }
        return best;
    }

    /**
     * Install an explicit palette (packed 0xRRGGBB) for nearest-color mapping,
     * bypassing {@link #buildPalette}. The RGB channels are retained as-is.
     *
     * @param rgbPalette packed 0xRRGGBB entries (1..256)
     */
    public void setPalette(int[] rgbPalette) {
        int[] copy = rgbPalette.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] &= 0xFFFFFF;
        }
        this.palette = copy;
    }

    /** The current palette (packed 0xRRGGBB), or empty before build. */
    public int[] palette() {
        return palette.clone();
    }
}
