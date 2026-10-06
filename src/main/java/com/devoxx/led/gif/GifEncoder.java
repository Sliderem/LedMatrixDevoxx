package com.devoxx.led.gif;

import com.devoxx.led.core.Frame;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Pure-Java GIF89a animated encoder with no external dependencies.
 *
 * <p>Writes the {@code GIF89a} signature, a Logical Screen Descriptor, a global
 * color table (the palette padded to a power of two, 2..256 entries), the
 * {@code NETSCAPE2.0} Application Extension for infinite looping, and for each
 * frame a Graphic Control Extension (delay in centiseconds), an Image Descriptor,
 * and LZW-compressed indexed image data flushed as sub-blocks of at most 255
 * bytes. Finishes with the {@code 0x3B} trailer.</p>
 *
 * <p>Palettes are packed {@code 0xRRGGBB} ints. If the caller passes {@code null}
 * for the palette, one is derived from the frames via {@link MedianCutQuantizer}.
 * Each frame's ARGB pixels are mapped to palette indices by nearest color.</p>
 */
public final class GifEncoder {

    /**
     * Encode the given frames as an infinitely-looping animated GIF.
     *
     * @param frames  list of equal-sized frames (width/height taken from the first)
     * @param palette packed 0xRRGGBB entries (2..256), or {@code null} to auto-quantize
     * @param delayMs per-frame display duration in milliseconds (converted to centiseconds)
     * @param out     destination stream (not closed by this method)
     */
    public void encode(List<Frame> frames, int[] palette, int delayMs, OutputStream out)
            throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }

        Frame first = frames.get(0);
        int width = first.width();
        int height = first.height();

        // Resolve the palette.
        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] rgbPalette;
        if (palette == null) {
            rgbPalette = quantizer.buildPalette(frames, 256);
        } else {
            if (palette.length < 1 || palette.length > 256) {
                throw new IllegalArgumentException("palette must have 1..256 entries");
            }
            rgbPalette = palette.clone();
            // Mask to RGB so nearest-color math is consistent.
            for (int i = 0; i < rgbPalette.length; i++) {
                rgbPalette[i] &= 0xFFFFFF;
            }
            // Prime the quantizer with this exact palette for nearest-color mapping.
            quantizer.setPalette(rgbPalette);
        }

        // Palette size must be a power of two (2..256) for the color-table flag.
        int actualColors = Math.max(2, rgbPalette.length);
        int tableSize = 2;
        int bitsPerPixel = 1;
        while (tableSize < actualColors) {
            tableSize <<= 1;
            bitsPerPixel++;
        }
        // Minimum LZW code size must be at least 2 per the GIF spec.
        int minCodeSize = Math.max(2, bitsPerPixel);

        // Header.
        writeBytes(out, "GIF89a");

        // Logical Screen Descriptor.
        writeShort(out, width);
        writeShort(out, height);
        // Packed field: global color table flag (1), color resolution (111),
        // sort flag (0), size of global color table (bitsPerPixel-1).
        int gctSizeField = bitsPerPixel - 1;
        int packed = 0x80 | 0x70 | gctSizeField;
        out.write(packed);
        out.write(0); // background color index
        out.write(0); // pixel aspect ratio

        // Global Color Table (padded to tableSize entries).
        for (int i = 0; i < tableSize; i++) {
            int c = (i < rgbPalette.length) ? rgbPalette[i] : 0;
            out.write((c >> 16) & 0xFF);
            out.write((c >> 8) & 0xFF);
            out.write(c & 0xFF);
        }

        // NETSCAPE2.0 Application Extension for infinite looping.
        out.write(0x21);            // extension introducer
        out.write(0xFF);            // application extension label
        out.write(11);              // block size
        writeBytes(out, "NETSCAPE2.0");
        out.write(3);               // sub-block size
        out.write(1);               // sub-block id
        writeShort(out, 0);         // loop count: 0 = forever
        out.write(0);               // block terminator

        int delayCentis = Math.round(delayMs / 10.0f);

        for (Frame frame : frames) {
            // Graphic Control Extension.
            out.write(0x21);        // extension introducer
            out.write(0xF9);        // graphic control label
            out.write(4);           // block size
            // Packed: reserved(000) disposal(001 = do not dispose) userInput(0) transparent(0)
            out.write(0x04);
            writeShort(out, delayCentis);
            out.write(0);           // transparent color index (unused)
            out.write(0);           // block terminator

            // Image Descriptor.
            out.write(0x2C);        // image separator
            writeShort(out, 0);     // image left
            writeShort(out, 0);     // image top
            writeShort(out, width);
            writeShort(out, height);
            out.write(0);           // packed: no local color table, not interlaced

            // Map pixels to palette indices.
            int[] pixels = frame.pixels();
            byte[] indices = new byte[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                indices[i] = (byte) quantizer.indexOf(pixels[i]);
            }

            // LZW-compressed image data.
            out.write(minCodeSize);
            lzwEncode(out, indices, minCodeSize);
            out.write(0);           // block terminator (zero-length sub-block)
        }

        out.write(0x3B);            // trailer
        out.flush();
    }

    // ---- LZW compression with GIF sub-block framing --------------------------

    private static void lzwEncode(OutputStream out, byte[] indices, int minCodeSize)
            throws IOException {
        final int clearCode = 1 << minCodeSize;
        final int endCode = clearCode + 1;

        BitWriter bits = new BitWriter(out);
        int codeSize = minCodeSize + 1;
        int nextCode = endCode + 1;

        // Dictionary: maps (prefix << 8 | nextByte) -> code. 0..clearCode-1 are roots.
        java.util.HashMap<Integer, Integer> dict = new java.util.HashMap<>();

        bits.write(clearCode, codeSize);

        if (indices.length == 0) {
            bits.write(endCode, codeSize);
            bits.flush();
            return;
        }

        int prefix = indices[0] & 0xFF;
        for (int i = 1; i < indices.length; i++) {
            int k = indices[i] & 0xFF;
            int key = (prefix << 8) | k;
            Integer existing = dict.get(key);
            if (existing != null) {
                prefix = existing;
            } else {
                bits.write(prefix, codeSize);
                if (nextCode < 4096) {
                    dict.put(key, nextCode++);
                    if (nextCode > (1 << codeSize) && codeSize < 12) {
                        codeSize++;
                    }
                } else {
                    // Dictionary full: emit clear and reset.
                    bits.write(clearCode, codeSize);
                    dict.clear();
                    codeSize = minCodeSize + 1;
                    nextCode = endCode + 1;
                }
                prefix = k;
            }
        }

        bits.write(prefix, codeSize);
        bits.write(endCode, codeSize);
        bits.flush();
    }

    /**
     * Accumulates variable-width LZW codes LSB-first into bytes, buffering them
     * into GIF data sub-blocks of at most 255 bytes.
     */
    private static final class BitWriter {
        private final OutputStream out;
        private final byte[] block = new byte[255];
        private int blockLen = 0;
        private int bitBuffer = 0;
        private int bitCount = 0;

        BitWriter(OutputStream out) {
            this.out = out;
        }

        void write(int code, int codeSize) throws IOException {
            bitBuffer |= (code << bitCount);
            bitCount += codeSize;
            while (bitCount >= 8) {
                writeByte(bitBuffer & 0xFF);
                bitBuffer >>>= 8;
                bitCount -= 8;
            }
        }

        void flush() throws IOException {
            if (bitCount > 0) {
                writeByte(bitBuffer & 0xFF);
                bitBuffer = 0;
                bitCount = 0;
            }
            flushBlock();
        }

        private void writeByte(int b) throws IOException {
            block[blockLen++] = (byte) b;
            if (blockLen == 255) {
                flushBlock();
            }
        }

        private void flushBlock() throws IOException {
            if (blockLen > 0) {
                out.write(blockLen);
                out.write(block, 0, blockLen);
                blockLen = 0;
            }
        }
    }

    // ---- little helpers ------------------------------------------------------

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeBytes(OutputStream out, String ascii) throws IOException {
        for (int i = 0; i < ascii.length(); i++) {
            out.write(ascii.charAt(i) & 0xFF);
        }
    }
}
