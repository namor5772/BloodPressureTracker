# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blood Pressure Tracker — an Android app for manually recording blood pressure readings with timestamps and generating reports. Currently in early development (scaffolded from Android Studio template, no application logic yet).

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "au.roman.bloodpressuretracker.ExampleUnitTest"

# Run instrumented (on-device/emulator) tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean assembleDebug

# Lint check
./gradlew lint
```

## Architecture

- **UI Framework**: Jetpack Compose with Material Design 3
- **Single Activity**: `MainActivity` with Compose content, edge-to-edge layout
- **Theme**: Material 3 with light/dark mode and dynamic color support (Android 12+)
- **Package**: `au.roman.bloodpressuretracker`
- **Min/Target SDK**: 36 | **JVM Target**: Java 11
- **Gradle**: 9.1.0 with Kotlin 2.0.21, AGP 9.0.1
- **Dependency management**: Version catalog (`gradle/libs.versions.toml`)

## Key Paths

- App source: `app/src/main/java/au/roman/bloodpressuretracker/`
- Theme: `app/src/main/java/au/roman/bloodpressuretracker/ui/theme/`
- Unit tests: `app/src/test/java/au/roman/bloodpressuretracker/`
- Instrumented tests: `app/src/androidTest/java/au/roman/bloodpressuretracker/`
- Version catalog: `gradle/libs.versions.toml`
