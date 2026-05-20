# FlightStats Android

FlightStats is a native Android application designed as a personal logbook, interactive flight map, and comprehensive statistics dashboard. Built entirely using modern Material 3 guidelines and native Android architecture, the application enables travelers to digitize, analyze, and visualize their flight history completely offline.

## Core Features

### On-Device AI Travel Summaries
- Powered by the official Google ML Kit GenAI Prompt API for on-device Gemini Nano text generation.
- Generates high-quality, grounded summaries of yearly and all-time travel metrics directly on the device.
- Uses strict prompt context and grounded metrics (chronological flights, country lists, and total distance) to eliminate model hallucination and produce precise, fluff-free travel summaries.
- Features a collapsible travel summary interface with a custom, programmatic color fade matching the Material 3 surface theme.

### Intelligent Boarding Pass Scanner
- Instantly parses standard IATA BCBP (Bar Coded Boarding Pass) barcodes and QR codes from physical passes or gallery screenshots.
- Utilizes Google ML Kit Barcode Scanning and CameraX for reliable, low-latency character extraction.
- Automatically populates origin, destination, flight number, airline, seat, and class metrics.

### Interactive Flight Mapping
- Leverages OpenStreetMap (osmdroid) to project high-fidelity flight routes and lines.
- Visualizes flight paths with thematic polyline coordinates.
- Integrates interactive airport markers, dynamic map overlays, and robust performance optimization to support large flight histories.

### Advanced Visual Analytics
- Bespoke custom layouts including a modern, flush `EdgeToEdgePieView` for country-level statistics.
- Seamlessly integrates MPAndroidChart widgets to depict monthly, daily, and yearly flight distributions.
- Calculates detailed comparisons such as circumnavigation metrics, distance to the moon, hours aloft, and longest flights.

### Material 3 Expressive UI
- Implements comprehensive Edge-to-Edge window insets for seamless modern layout navigation.
- Features an expressive settings control center supporting dynamic light/dark mode switching and custom Material 3 color templates.
- Built-in automatic navigation transitions and elegant cards.

### High-Performance Local Storage
- Backed by an offline-first Room Database architecture.
- Full transactional safety and query indexing to ensure near-zero database latency.
- Completely functional without any network access or server reliance.

## Technical Specifications

- **Build Pipeline**: Gradle Version Catalogs (Kotlin DSL).
- **Minimum SDK**: 31 (Android 12)
- **Target SDK**: 36 (Android 15 QPR3)
- **UI Framework**: XML layout binding with Material Components for Android (v1.12.0) and Material Design 3 tokens.
- **Database Engine**: Room Persistence Library.
- **AI Engine**: Google ML Kit GenAI Prompt SDK (Gemini Nano).
- **Vision APIs**: CameraX and Google ML Kit Barcode Scanning SDK.
- **Mapping & Charts**: Osmdroid, MPAndroidChart, and custom Canvas graphics libraries.

## Getting Started

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- Java Development Kit (JDK) 17 or higher
- Android SDK Build-Tools matching the configured API compile target

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/JurianOnderwater/FlightStatsAndroid.git
   ```
2. Open the cloned folder in Android Studio.
3. Sync the project with the Gradle build files.
4. Build and deploy the application to a connected device or emulator running Android 12 (API 31) or newer.

## Releases and Distribution

Pre-compiled binary assets are available for testing. You can download the latest signed debug or release APK directly from the [Releases page](https://github.com/JurianOnderwater/FlightStatsAndroid/releases).
