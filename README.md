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

A desktop app lets you preview scenes live, upload or AI-generate an image, adapt
it to the 64x64 matrix, save/export it, and optionally push it to a Divoom Pixoo 64
on your network. There are two editions with the same features:

**JavaFX edition** (default, `com.devoxx.led.ui.PreviewFx`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"; mvn -q javafx:run
```

**Swing edition** (zero extra deps, `com.devoxx.led.ui.PreviewUI`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;C:\Users\emili\tools\apache-maven-3.9.9\bin;$env:PATH"; mvn -q compile exec:java -Dexec.mainClass=com.devoxx.led.ui.PreviewUI
```

- **Scenes list** — play any built-in scene live, scaled 8x (512x512) with
  nearest-neighbor upscaling so individual LEDs stay crisp.
- **Generated list** — every AI-generated still is saved as a 64x64 PNG under
  `generated/` and listed here; click one to reload it. The folder is loaded on
  startup, so your library persists across runs (`generated/` is git-ignored).
- **Upload image** (bottom bar) — load a PNG/JPG/BMP/GIF; it is resized to 64x64
  with a **letterbox** or **crop-to-square** fit, and an optional **LED posterize**
  treatment (2–8 levels per channel) so photos read as deliberate LED pixel art.
- **Describe an image (AI)** (bottom bar) — type a prompt and the app calls the
  OpenAI image API, downscales the result to 64x64 through the same adapter, shows
  it in the preview, and saves it to `generated/`. Requires `CHATGPT_API_KEY` (see
  below); if no key is configured the prompt box is disabled.
- **Export** — writes a 64x64 looping GIF (scenes) or a PNG (still) into
  `target/visuals/`, ready to submit.
- **Send to Pixoo** — pushes the current preview to a Divoom Pixoo 64 over the
  local network (see **Device compatibility** below).

## Configuration (`.env`)

AI generation and the optional default device IP are read from a local `.env`
file (never committed). Copy [`.env.example`](.env.example) to `.env` and fill in:

```
CHATGPT_API_KEY=sk-...        # enables the "Describe an image (AI)" box
# OPENAI_IMAGE_MODEL=gpt-image-1
# PIXOO_HOST=192.168.1.100    # prefills the Pixoo IP field
```

Real OS environment variables take precedence over `.env`. **Never commit `.env`
or any file containing a real key** — `.env` and `config.txt` are git-ignored.

## Device compatibility

This project is first and foremost a **64x64 visual generator**. The output files
(GIF/PNG) can be used with *any* display or app that imports images. Only the
direct **Send to Pixoo** button talks to hardware, and it is specific to one
device family.

| Device | Direct "Send" from this app? | How to use |
| --- | --- | --- |
| **Divoom Pixoo 64** (64x64, Wi-Fi) | ✅ Yes | Enter the device IP; the app POSTs frames to its local HTTP API (`Draw/SendHttpGif`). |
| Other Wi-Fi Divoom with the same HTTP API (e.g. Pixoo-64 variants) | ⚠️ Likely, untested | Same IP + HTTP path; frame size must be 64x64. |
| **Bluetooth LED matrices** (e.g. a 32x32 IDC / generic BLE panel) | ❌ No | Export a file and import it with the vendor's own Bluetooth app. See notes below. |
| Any other display | ❌ No direct send | Export a 64x64 GIF/PNG and load it however that display accepts images. |

### Why a 32x32 Bluetooth panel does not work with "Send to Pixoo"

Two independent reasons:

1. **Transport.** "Send to Pixoo" uses **Wi-Fi / HTTP** — it needs an IP address and
   sends JSON over your local network. A Bluetooth panel speaks **BLE**, a different
   transport with no IP. The JDK has no built-in Bluetooth stack, so direct control
   would require a third-party BLE library *and* the panel's (usually undocumented,
   vendor-specific) GATT command protocol — effectively a separate device driver.
2. **Resolution.** The pipeline is fixed at **64x64** (`Canvas.SIZE = 64`, and the
   Pixoo payload is exactly `64*64*3` RGB bytes). A **32x32** panel needs a 32x32
   canvas and a different frame payload.

**Practical path for a 32x32 Bluetooth panel today:** use this app to design/preview
a visual, **Export** it, then down-size to 32x32 and send it through the app that
came with your panel (most BLE LED-matrix/badge apps import PNG or GIF). The
generator stays useful; only the on-the-wire delivery changes.

**If you want native support** for a specific Bluetooth 32x32 panel, it would be a
new, separate module: a configurable matrix size, a 32x32 render/export path, and a
BLE `DeviceClient` built on a Java Bluetooth library (e.g. BlueCove/TinyB/Bluez via
JNI) targeting that panel's protocol. Open an issue with the exact model and we can
scope it.

## Project layout

```
pom.xml
README.md
LICENSE                         Apache-2.0
.gitignore
.env.example                    template for CHATGPT_API_KEY / PIXOO_HOST
gallery/                        committed final GIFs + PNGs
generated/                      AI/saved stills (git-ignored, created at runtime)
src/main/java/com/devoxx/led/
  core/     Rgb, Frame, Canvas, PixelFont   drawing primitives
  gif/      GifEncoder, MedianCutQuantizer  pure-Java GIF89a + quantizer
  png/      PngWriter                       ImageIO PNG writer
  image/    ImageAdapter                    resize + LED-posterize to 64x64
  ai/       DotEnv, OpenAiImageClient       .env loader + OpenAI image generation
  device/   PixooClient                     Divoom Pixoo 64 local HTTP client
  scene/    Scene + the four themed scenes
  ui/       PreviewFx (JavaFX), PreviewUI (Swing)   preview + upload + AI + send
  verify/   VisualVerifier                  64x64 / <=1MB / frames>1 checks
  Main.java                                 render -> verify -> publish pipeline
```

## Credits & inspiration

Inspired by [`glaforge/jixoo`](https://github.com/glaforge/jixoo), a modern Java
client library for creating and processing 64x64 RGB LED matrix visuals for the
Divoom Pixoo 64. This project is an independent, dependency-free generator and does
not use the jixoo artifact at runtime.

The optional **Send to Pixoo** feature uses the Divoom Pixoo 64 local HTTP API
(`Draw/ResetHttpGifId` / `Draw/SendHttpGif`), the same documented protocol used by
the broader Pixoo community tooling. AI image generation uses the OpenAI image API.

## License

Licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for the full text.
