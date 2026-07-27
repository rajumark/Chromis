<p align="center">
  <img src="docs/icon.png" alt="Chromis Logo" width="120"/>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
</p>

# Chromis

**AI-Powered Photo Colorizer — 100% On-Device**

Chromis brings black & white photos back to life using a deep neural network running entirely on your Android device. No cloud. No API. No data leaving your phone.

## Features

- **One-tap colorization** — pick any B&W or color image from your gallery
- **Touch-to-compare** — hold the screen to reveal the original, release to see the colorized result
- **Persistent gallery** — home screen shows all your colorized images in a Pinterest-style grid (survives app restarts)
- **Long-press delete** — remove any image from your history
- **Save & Share** — download to Downloads folder or share directly to any app
- **100% offline** — no INTERNET permission, no analytics, no tracking

## Showcase

| Original | Colorized |
|----------|-----------|
| ![Original](docs/showcase/photo1_original.png) | ![Colorized](docs/showcase/photo1_colorized.png) |
| ![Original](docs/showcase/photo2_original.png) | ![Colorized](docs/showcase/photo2_colorized.png) |
| ![Original](docs/showcase/photo3_original.png) | ![Colorized](docs/showcase/photo3_colorized.png) |

## App Screenshots

| Home | Processing | Result |
|------|-----------|--------|
| ![Home](docs/screenshots/home_screen.png) | ![Processing](docs/screenshots/processing_screen.png) | ![Result](docs/screenshots/result_screen.png) |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Model | DDColor-Tiny (FP16 ONNX, 129 MB) |
| Inference | ONNX Runtime 1.19.2 |
| Image Processing | OpenCV 4.5.3 |
| UI | Jetpack Compose + Material Design 3 |
| Architecture | Single-Activity, Coroutines |

## How It Works

1. **Pick Photo** — Gallery intent decodes image to Bitmap
2. **Lab Conversion** — OpenCV converts RGB → CIE Lab, extracts L channel
3. **ONNX Inference** — DDColor-Tiny predicts a,b chrominance at 512×512
4. **Upscale & Merge** — Chrominance upscaled to original resolution, merged with L
5. **Colour Output** — Lab → RGB conversion, displayed with touch-to-compare

## Requirements

- Android 8.0 (API 26) or higher
- ~200 MB RAM for model inference

## Build

```bash
# Clone
git clone https://github.com/rajumark/Chromis.git
cd Chromis

# Model is tracked via Git LFS — ensure you have it installed:
git lfs install
git lfs pull

# Build
./gradlew assembleDebug
```

## Project Structure

```
app/src/main/java/raju/shingadiya/chromis/
├── MainActivity.kt          # Two-screen UI (Home + Image)
├── engine/
│   └── DDColorEngine.kt     # ONNX inference pipeline
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

## Privacy

The APK has **no INTERNET permission**. No analytics SDK. No crash reporting. No tracking. This isn't a policy — it's architecture. Verify it yourself by inspecting the AndroidManifest.

## Credits

Built by **Raju Shingadiya** — Edge AI Project

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.
