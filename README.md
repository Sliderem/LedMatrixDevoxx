# Devoxx 2026 LED Matrix Visuals

Polished 64x64 animated visuals for the **Google Cloud Raffle at Devoxx Belgium 2026**.
Each visual is authored for the [Divoom Pixoo 64](https://divoom.com) — a 64x64 RGB
LED matrix display — and exported as a seamlessly looping animated GIF plus a key
still PNG, ready to upload with a contest submission.

This is a standalone **Java 21 + Maven** command-line generator with **zero runtime
dependencies**: a hand-written GIF89a encoder, a median-cut quantizer, a PNG writer,
and four themed scenes all live in pure JDK code.

## What it generates

Running the generator produces, for every scene, a `<name>.gif` (24 frames @ 60ms,
infinitely looping) and a representative `<name>.png` still:

| Visual | Description |
| --- | --- |
| **devoxx** | A bold pulsing `DEVOXX 2026` wordmark with a scrolling Devoxx-orange accent bar and a dimmed Belgian-flag (black / yellow / red) band sweeping across a dark background. |
| **googlecloud** | Four dots in the Google brand colors (blue, red, yellow, green) orbiting a gently breathing central cloud. The orbit angle is driven by the loop phase so motion returns seamlessly to its start. |
| **javacoffee** | A stylized warm coffee cup with three upward-scrolling, sine-swayed steam columns and a subtle `JAVA` label — a nod to the language that powers Devoxx. |
| **plasma** | A high-energy color-cycling plasma field built from overlapping sine waves, mapped through a bold LED-friendly palette derived from the four Google colors. |

Every motion uses `phase = frameIndex / totalFrames` so frame `totalFrames` equals
frame `0` — the GIFs loop without a visible seam.

## Constraints

All output is built to satisfy the hard contest / Pixoo 64 constraints, which the
generator verifies automatically before publishing to `gallery/`:

- **Exactly 64x64 pixels.**
- **At most 1 MiB (1,048,576 bytes)** per file — GIFs are kept comfortably under 700 KB.
- **GIFs are animated** (more than one frame).

A built-in `VisualVerifier` reads each file back with `javax.imageio` and prints a
PASS/FAIL table (filename, bytes, WxH, frames). The generator exits non-zero if any
file fails, and only copies passing files into `gallery/`.

## Requirements

- **JDK 21** (tested with Eclipse Temurin 21.0.12).
- **Apache Maven 3.9+**.

On the development machine, Java and Maven are not on `PATH`, so every command below
sets `JAVA_HOME` and `PATH` inline first. This is PowerShell; the statement separator
is `;` (never `&&`). Adjust the two paths if your toolchain lives elsewhere.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"
```

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"; mvn -q clean package
```

This compiles the project and produces a runnable fat jar at
`target/led-matrix-visuals.jar`.

## Generate all visuals

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"; mvn -q compile exec:java
```

The generator renders every scene to `target/visuals/<name>.gif` and `<name>.png`,
prints each output path and byte size, runs the verifier, and copies the passing
files into `gallery/` at the repo root. You can also run the fat jar directly:

```powershell
java -jar target/led-matrix-visuals.jar
```

`target/` is build output and is git-ignored. The curated final visuals live in
`gallery/` and are committed.

## Interactive preview UI

A small Swing desktop app lets you preview the scenes live and adapt an uploaded
image to the 64x64 matrix, then export — no device required.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"; mvn -q compile exec:java -Dexec.mainClass=com.devoxx.led.ui.PreviewUI
```

- **Scenes list** — play any built-in scene live, scaled 8x (512x512) with
  nearest-neighbor upscaling so individual LEDs stay crisp.
- **Upload image** — load a PNG/JPG/BMP/GIF; it is resized to 64x64 with a
  **letterbox** or **crop-to-square** fit, and an optional **LED posterize**
  treatment (2–8 levels per channel) so photos read as deliberate LED pixel art.
- **Export** — writes a 64x64 looping GIF (scenes) or a PNG (uploaded still) into
  `target/visuals/`, ready to submit.

## Project layout

```
pom.xml
README.md
LICENSE                         Apache-2.0
.gitignore
gallery/                        committed final GIFs + PNGs
src/main/java/com/devoxx/led/
  core/     Rgb, Frame, Canvas, PixelFont   drawing primitives
  gif/      GifEncoder, MedianCutQuantizer  pure-Java GIF89a + quantizer
  png/      PngWriter                       ImageIO PNG writer
  image/    ImageAdapter                    resize + LED-posterize uploads to 64x64
  scene/    Scene + the four themed scenes
  ui/       PreviewUI                       Swing live preview + upload + export
  verify/   VisualVerifier                  64x64 / <=1MB / frames>1 checks
  Main.java                                 render -> verify -> publish pipeline
```

## Credits & inspiration

Inspired by [`glaforge/jixoo`](https://github.com/glaforge/jixoo), a modern Java
client library for creating and processing 64x64 RGB LED matrix visuals for the
Divoom Pixoo 64. This project is an independent, dependency-free generator and does
not use the jixoo artifact at runtime.

## License

Licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for the full text.
