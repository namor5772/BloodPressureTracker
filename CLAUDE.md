# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blood Pressure Tracker — an Android app for manually recording blood pressure readings (systolic, diastolic, pulse) with timestamps, viewing history, deleting records, exporting data as CSV, grouped report, or daily averages via Android's share sheet, importing CSV files to restore/transfer readings, and AI-powered reading explanations via the Anthropic API (Claude Haiku 4.5). Exports are ordered oldest first.

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
- **Navigation**: Enum-based (`Screen.Record`, `Screen.History`) with bottom nav bar — no Jetpack Navigation
- **Theme**: Material 3 with light/dark mode and dynamic color support (Android 12+)
- **Database**: Room (`blood_pressure.db`, version 2) with `BloodPressureDao`, `BloodPressureRecord` entity, migration 1→2 (adds pulse column)
- **Package**: `au.roman.bloodpressuretracker`
- **Min/Target/Compile SDK**: 36 | **JVM Target**: Java 11
- **Gradle**: 9.3.1 with Kotlin 2.2.10, AGP 9.1.0, KSP 2.3.2
- **AI**: Anthropic Messages API (`claude-haiku-4-5-20251001`) via `HttpURLConnection` + `org.json` — API key and custom instructions stored in SharedPreferences (`"settings"` → `"anthropic_api_key"`, `"custom_instructions"`); custom instructions are appended to the user message on each API call
- **Dependency management**: Version catalog (`gradle/libs.versions.toml`)

## Key Paths

- App source: `app/src/main/java/au/roman/bloodpressuretracker/`
- Data layer (Room): `app/src/main/java/au/roman/bloodpressuretracker/data/` — `AppDatabase`, `BloodPressureDao`, `BloodPressureRecord`
- AI integration: `app/src/main/java/au/roman/bloodpressuretracker/AnthropicApi.kt`
- Theme: `app/src/main/java/au/roman/bloodpressuretracker/ui/theme/`
- Unit tests: `app/src/test/java/au/roman/bloodpressuretracker/`
- Instrumented tests: `app/src/androidTest/java/au/roman/bloodpressuretracker/`
- Version catalog: `gradle/libs.versions.toml`
