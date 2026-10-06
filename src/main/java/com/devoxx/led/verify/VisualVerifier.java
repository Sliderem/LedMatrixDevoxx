package com.devoxx.led.verify;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verifies that every generated visual meets the hard contest constraints for a
 * Divoom Pixoo 64: the file must exist, be at most 1 MiB, be exactly 64x64, and
 * (for GIFs) contain more than one frame so the animation actually loops.
 *
 * <p>Results are printed as an aligned PASS/FAIL table (filename, bytes, WxH,
 * frames). Call {@link #verify(List)} and inspect the returned {@link Report};
 * the generator exits non-zero when {@link Report#allPassed()} is {@code false}.
 */
public final class VisualVerifier {

    /** Hard upper bound: 1 MiB. */
    public static final long MAX_BYTES = 1_048_576L;

    /** Soft advisory bound for GIFs: keep them comfortably small. */
    public static final long GIF_SOFT_BYTES = 700_000L;

    /** Per-file verification result. */
    public record Result(
            String filename,
            long bytes,
            int width,
            int height,
            int frames,
            boolean passed,
            String reason) {
    }

    /** Aggregate verification outcome over a set of files. */
    public record Report(List<Result> results, boolean allPassed) {
    }

    /**
     * Verify each file and return the aggregate report.
     *
     * @param files the generated visuals to check (GIF or PNG)
     */
    public Report verify(List<File> files) {
        List<Result> results = new ArrayList<>(files.size());
        boolean all = true;
        for (File file : files) {
            Result r = verifyOne(file);
            results.add(r);
            all &= r.passed();
        }
        return new Report(results, all);
    }

    private Result verifyOne(File file) {
        String name = file.getName();

        if (!file.exists() || !file.isFile()) {
            return new Result(name, 0, 0, 0, 0, false, "file does not exist");
        }

        long bytes = file.length();
        boolean isGif = name.toLowerCase().endsWith(".gif");

        int width = 0;
        int height = 0;
        int frames = 0;
        try {
            int[] dims = new int[2];
            frames = readDimensionsAndFrames(file, dims);
            width = dims[0];
            height = dims[1];
        } catch (IOException e) {
            return new Result(name, bytes, 0, 0, 0, false,
                    "unreadable: " + e.getMessage());
        }

        List<String> problems = new ArrayList<>();
        if (bytes > MAX_BYTES) {
            problems.add("too large (" + bytes + " > " + MAX_BYTES + ")");
        }
        if (width != 64 || height != 64) {
            problems.add("not 64x64 (" + width + "x" + height + ")");
        }
        if (isGif && frames <= 1) {
            problems.add("GIF needs >1 frame (" + frames + ")");
        }

        String note = "";
        if (isGif && bytes > GIF_SOFT_BYTES) {
            note = "GIF over soft limit (" + GIF_SOFT_BYTES + ")";
        }

        boolean passed = problems.isEmpty();
        String reason = passed ? note : String.join("; ", problems);
        return new Result(name, bytes, width, height, frames, passed, reason);
    }

    /**
     * Read a file's dimensions and frame count via {@link ImageReader}.
     *
     * @param dims a length-2 array that receives {width, height}
     * @return the number of image frames (1 for a PNG, &gt;1 for animated GIFs)
     */
    private int readDimensionsAndFrames(File file, int[] dims) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                throw new IOException("no ImageInputStream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("no ImageReader for " + file.getName());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                int count = reader.getNumImages(true);
                dims[0] = reader.getWidth(0);
                dims[1] = reader.getHeight(0);
                return count;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Print the PASS/FAIL table to {@code System.out}. */
    public void printTable(Report report) {
        String header = String.format(
                "%-24s %10s %-9s %-7s %-6s %s",
                "FILENAME", "BYTES", "WxH", "FRAMES", "RESULT", "NOTES");
        System.out.println(header);
        System.out.println("-".repeat(header.length() + 8));
        for (Result r : report.results()) {
            System.out.printf(
                    "%-24s %10d %-9s %-7d %-6s %s%n",
                    r.filename(),
                    r.bytes(),
                    r.width() + "x" + r.height(),
                    r.frames(),
                    r.passed() ? "PASS" : "FAIL",
                    r.reason());
        }
        System.out.println();
        System.out.println(report.allPassed()
                ? "ALL FILES PASSED"
                : "VERIFICATION FAILED");
    }
}
