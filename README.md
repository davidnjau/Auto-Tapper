# Auto Tapper

An Android app that automates tapping on TikTok using the Accessibility Service API. A floating overlay button lets you start/stop tapping without leaving TikTok, and the overlay displays a live tap count and estimated likes in real time.

## Requirements

- Android 7.0+ (API 24)
- TikTok installed (`com.zhiliaoapp.musically` or TikTok Lite)
- Accessibility Service permission
- Draw Over Other Apps permission

## Setup

1. Build and install the app
2. Open Auto Tapper and tap **Enable Accessibility Service** — this opens Android Settings where you enable the service
3. Tap **Grant Overlay Permission** and allow the app to draw over others
4. Return to Auto Tapper and configure your settings

## Settings

### Tap Speed (1–9 taps/sec)
Controls how many gestures are dispatched per second. **7 taps/sec is the recommended setting** — it keeps Android's gesture dispatcher from backing up without meaningfully reducing throughput.

### Taps per Trigger (1× / 2× / 3×)
Number of strokes bundled into each gesture dispatch. Each extra stroke is offset by 60ms so they don't overlap.

- **1×** — default, best reliability
- **2× / 3×** — useful only if session logs show a high cancellation rate at 1×

Recommended combination: **7 taps/sec + 1×**

## Usage

1. Open TikTok and navigate to a video
2. The floating overlay appears in the top-right corner
3. Tap **▶** to start — the button turns green and counters update live
4. Tap **■** to stop — the button turns red and a session summary is logged
5. Tapping stops automatically when you leave TikTok

## Architecture

```
MainActivity.kt       — Configuration UI (speed, multiplier, permissions)
AutoTapperService.kt  — AccessibilityService: floating overlay, gesture loop, foreground guard
LikesCalculator.kt    — Estimates likes from tap counts using measured efficiency constants
```

**Efficiency constants** (measured from real sessions):
- App efficiency: 96.78% (gesture completion rate)
- Like registration rate: 97.8% (completed taps that register as likes)
- Combined: ~94.65%

## Build

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew installDebug        # Install on connected device
./gradlew assembleRelease     # Build release APK
./gradlew clean               # Clean build artifacts
```

## Debugging

Session stats are logged under the `TapperService` tag on stop:

```
Session summary — attempts: 420 | completed: 408 | cancelled: 12 | foreground losses: 0 | success rate: 97%
```

If success rate drops below 85%, reduce tap speed by 1–2 steps.
