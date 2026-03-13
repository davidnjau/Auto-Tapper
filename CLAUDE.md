# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Auto-Tapper is an Android accessibility service app that automates tapping on TikTok (packages `com.zhiliaoapp.musically` and `com.zhiliaoapp.musically.go`) via a floating overlay button. It simulates touch gestures using the Android `GestureDescription` API and estimates engagement (likes) based on tap counts.

## Build & Run Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests on device
./gradlew clean                  # Clean build artifacts
```

## Architecture

The app has three core classes in `app/src/main/java/com/dave/autotapper/`:

**`MainActivity.kt`** — Configuration UI. Manages the tap speed SeekBar (range 1–9, displayed as 1–10 taps/sec), checks Accessibility Service and `SYSTEM_ALERT_WINDOW` permissions, and persists the speed setting via SharedPreferences. Reads `AutoTapperService.instance` on resume to show live service status.

**`AutoTapperService.kt`** — Android AccessibilityService that does all the work. On connect, inflates `floating_button.xml` as a `TYPE_ACCESSIBILITY_OVERLAY` window anchored to the top-right. A Kotlin coroutine loop fires `GestureDescription.StrokeDescription` (10ms duration) at the screen center, at the configured rate (1–20 taps/sec). Exposes a companion `instance` singleton so `MainActivity` can read state. Tap speed can be updated dynamically without restarting the service.

**`LikesCalculator.kt`** — Singleton object with pre-measured efficiency constants (`APP_EFFICIENCY = 96.78%`, `REGISTRATION_RATE = 97.8%`, `COMBINED_EFFICIENCY = 94.65%`). Provides forward/reverse calculations between time+speed and expected likes, plus breakdown and reference-table helpers.

## Key Configuration

- **Min SDK:** 24 | **Target/Compile SDK:** 36 | **JVM target:** Java 11
- **Gradle version:** 8.13.2 (Kotlin DSL throughout)
- **Version catalog:** `gradle/libs.versions.toml`
- **Accessibility service config:** `app/src/main/res/xml/accessibility_service_config.xml`
- **Floating overlay layout:** `app/src/main/res/layout/floating_button.xml` — contains `tvCounter` (tap count) and `tvCounterLikes` (estimated likes) TextViews flanking the 64×64dp toggle button.

## Permissions Required at Runtime

1. Accessibility Service — user must enable in Android Settings
2. `SYSTEM_ALERT_WINDOW` (draw over other apps) — requested via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
