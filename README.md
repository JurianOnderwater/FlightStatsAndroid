# FlightStats Android

FlightStats is a beautifully crafted, native Android application that serves as a personal logbook and statistics dashboard for all your flights. Designed with Android's modern Material 3 guidelines and a focus on sleek, dynamic aesthetics, this app tracks where you've been and visualizes your travel footprint.

## ✨ Features

- **Comprehensive Dashboard**: View in-depth statistics about your flights, including total distance traveled, longest flights, and time spent aloft.
- **Smart Boarding Pass Scanner**: Instantly add new flights by scanning the barcode or QR code on your boarding pass (or uploading a screenshot from your gallery). Powered by Google ML Kit, the app automatically extracts origin, destination, flight number, airline, seat, and class directly from standard IATA BCBP formats.
- **Global Footprint**: A custom-built, full-bleed, edge-to-edge "Donut Chart" widget visually represents the distribution of countries you've visited, mapping your personal footprint across the globe.
- **Interactive Map**: View your flights plotted on a beautiful world map featuring custom geometric (9-sided scallop) markers.
- **Material You Dynamic Theming**: Fully supports Android 12+ wallpaper-based dynamic coloring. The entire app seamlessly adapts to your system theme.
- **Fully Offline**: Powered locally via a Room Database, so you can always view and log your flights, even at 35,000 feet.

## 🛠 Tech Stack

- **UI & Layouts**: XML layouts leveraging the `Material 3` (v1.12.0) components and tokens.
- **Charting**: A hybrid of [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) and bespoke custom `View` renderers (e.g., `EdgeToEdgePieView`) to ensure charts look sharp and flush within Material cards.
- **Scanner Integration**: `CameraX` and `Google ML Kit Barcode Scanning`.
- **Local Persistence**: `Room Database`.

## 🚀 Getting Started

### Prerequisites
- Android Studio (Koala or newer recommended)
- Minimum SDK: 26 (Android 8.0 Oreo)
- Target SDK: 34 (Android 14)

### Installation
1. Clone this repository:
   ```bash
   git clone https://github.com/JurianOnderwater/FlightStatsAndroid.git
   ```
2. Open the project in Android Studio.
3. Sync project with Gradle files.
4. Run the app on a connected device or emulator.

## 📦 Releases

You can download the latest pre-compiled debug APK from the [Releases page](https://github.com/JurianOnderwater/FlightStatsAndroid/releases).
