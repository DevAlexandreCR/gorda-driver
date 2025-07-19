# Gorda Driver

## Project Overview
Gorda Driver is the Android application for drivers in the Gorda ride platform. It lets drivers receive ride requests, track trips and manage their profile. The app uses Firebase for authentication, real-time updates and push notifications. Location is tracked in the background and Google Maps is used to navigate from pick up to destination. Ride requests can originate from WhatsApp and appear in the driver's queue via the backend.

## Tech Stack
- Kotlin
- Android Jetpack (Navigation, ViewModel, LiveData)
- Firebase (Auth, Realtime Database, Firestore, Storage, Messaging)
- Google Play Services and Google Maps SDK
- Retrofit and Gson
- Glide / Picasso for images
- Sentry for crash reporting

## Getting Started

### Requirements
- Android Studio with Android SDK 24 or newer
- Google Maps API key and Firebase project
- Sentry DSN (optional)

### Local setup
1. Clone the repository.
2. Copy `local.properties.example` to `local.properties` and fill the required keys. The template contains the variables used by the build such as `MAPS_API_KEY` and Firebase emulator hosts.
3. Open the project in Android Studio and let it download the Gradle dependencies.
4. Connect an Android device or start an emulator.
5. Build and run from Android Studio or execute `./gradlew installDebug`.

### Production build
Run `./gradlew assembleRelease` to generate a release APK or App Bundle. Configure signing information in your Gradle properties or via Android Studio.

## API Usage
The app reads and writes data to Firebase Realtime Database and Firestore. It also uses the Google Maps Directions API through `maps/MapApiService.kt`. Provide your own credentials in `local.properties`.

## Components
- **activity** – Application entry points (`StartActivity`, `MainActivity`).
- **background** – Foreground services for location, fees meter and notifications.
- **location** – Location provider helpers.
- **maps** – Google Maps helpers and Retrofit API for directions.
- **models** – Kotlin data classes such as `Driver` and `Service`.
- **repositories** – Interaction layer with Firebase.
- **services** – Firebase initialization and network utilities.
- **ui** – Fragments and ViewModels for the different screens.

## WhatsApp Integration Flow
Clients can request a ride through WhatsApp. The backend processes the message and creates a `Service` record in Firebase, referencing the client via `wp_client_id`. Drivers receive a push notification and can accept or reject the request directly from the app.

## Contributing
1. Fork the project and create your feature branch from `master`.
2. Commit your changes with clear messages.
3. Open a pull request describing your changes.

## License
This repository does not contain an explicit license file. All rights are reserved by the original authors.
