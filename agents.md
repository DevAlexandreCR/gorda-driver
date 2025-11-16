# Gorda Driver - Project Agents Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Tech Stack](#tech-stack)
5. [Module Breakdown](#module-breakdown)
6. [Data Flow](#data-flow)
7. [Key Features](#key-features)
8. [Build Configuration](#build-configuration)

---

## Project Overview

**Gorda Driver** is an Android application for drivers in the Gorda ride-hailing platform. It enables drivers to:
- Receive and manage ride requests (including those originating from WhatsApp)
- Track trips with real-time GPS location
- Navigate using Google Maps integration
- Manage their driver profile and earnings
- Track trip fees and distance in real-time

**Current Version:** 1.2.2 (Build 65)  
**Minimum SDK:** 24 (Android 7.0)  
**Target SDK:** 35 (Android 15)  
**Language:** Kotlin  
**Build System:** Gradle 8.1.1

---

## Architecture

The application follows a **MVVM (Model-View-ViewModel)** architecture with repository pattern:

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Fragments   │  │  Activities  │  │   Adapters   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  HomeViewModel│  │ProfileViewModel│ │CurrentService│      │
│  └──────────────┘  └──────────────┘  │   ViewModel  │      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     Repository Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ServiceRepo   │  │DriverRepo    │  │ TokenRepo    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   Data & Services Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Firebase   │  │  Retrofit    │  │  Location    │      │
│  │   Services   │  │   Services   │  │   Services   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. Activities
**Location:** `app/src/main/java/gorda/driver/activity/`

#### StartActivity.kt
- **Purpose:** Splash screen and authentication entry point
- **Features:**
  - Checks if user is authenticated
  - Launches Firebase UI authentication
  - Redirects to MainActivity after successful login
- **Launch Mode:** singleInstance
- **Orientation:** Portrait only

#### MainActivity.kt
- **Purpose:** Main container activity with navigation
- **Features:**
  - Navigation drawer with app sections
  - Connection toggle switch for driver availability
  - Location service management
  - Network monitoring
  - Real-time driver updates
  - Service connection handling
- **Launch Mode:** singleInstance
- **Orientation:** Portrait only

---

### 2. Background Services
**Location:** `app/src/main/java/gorda/driver/background/`

#### LocationService.kt
- **Purpose:** Foreground service for continuous location tracking
- **Service Type:** Foreground Service (Location)
- **Features:**
  - Continuous GPS tracking using FusedLocationProviderClient
  - Updates driver location to Firebase every 10 location changes
  - Broadcasts location updates to app components
  - Monitors new service requests
  - Text-to-speech announcements for new services
  - Sound notifications for ride requests
  - Manages service queue listening
- **Notification ID:** 100

#### FeesService.kt
- **Purpose:** Tracks trip distance and calculates fees in real-time
- **Service Type:** Foreground Service (Location)
- **Features:**
  - Real-time distance calculation
  - Fee calculation based on distance and multiplier
  - Trip duration tracking (chronometer)
  - Persists trip state to SharedPreferences
  - Route points collection (ArrayList<LatLng>)
  - Updates every 1 second (UPDATE_INTERVAL = 1000L)
- **Notification ID:** 1
- **Channel ID:** "FeesServiceChannel"

#### NotificationService.kt
- **Purpose:** Firebase Cloud Messaging receiver
- **Extends:** FirebaseMessagingService
- **Features:**
  - Receives push notifications from backend
  - Handles notification types: "notification" and custom messages
  - Creates system notifications with custom sounds
  - Manages notification channels
  - Token registration with Firebase

---

### 3. Models
**Location:** `app/src/main/java/gorda/driver/models/`

#### Service.kt
- **Purpose:** Data class representing a ride service/trip
- **Firebase Path:** `/services/{service_id}`
- **Key Fields:**
  - `id: String` - Unique service identifier
  - `name: String` - Client name
  - `status: String` - Service status (pending/in_progress/terminated/canceled)
  - `start_loc: LocType` - Pickup location
  - `end_loc: LocType?` - Destination location
  - `phone: String` - Client phone number
  - `comment: String?` - Additional instructions
  - `driver_id: String?` - Assigned driver ID
  - `client_id: String?` - Client user ID
  - `wp_client_id: String?` - WhatsApp client identifier
  - `created_at: Long` - Timestamp
  - `metadata: ServiceMetadata` - Trip metadata (fees, distance, times)
- **Methods:**
  - `updateMetadata()` - Updates service metadata in Firebase
  - `terminate(route, distance, fee, multiplier)` - Completes the trip
  - `addApplicant(driver, distance, time, connection)` - Driver applies for service
  - `cancelApplicant(driver)` - Cancel application
  - `onStatusChange(listener)` - Listen to status changes
  - `isInProgress()` - Check if trip is active

#### Driver.kt
- **Purpose:** Data class representing a driver
- **Firebase Path:** `/drivers/{driver_id}`
- **Key Fields:**
  - `id: String` - Unique driver identifier
  - `name: String` - Driver name
  - `email: String` - Email address
  - `phone: String` - Phone number
  - `docType: String` - Document type (ID, Passport, etc.)
  - `document: String` - Document number
  - `photoUrl: String` - Profile photo URL
  - `enabled_at: Int` - Account activation timestamp
  - `created_at: Int` - Registration timestamp
  - `device: Device?` - Device information
  - `vehicle: Vehicle` - Vehicle information
  - `balance: Double` - Earnings balance
- **Methods:**
  - `connect(location)` - Set driver as available/online
  - `disconnect()` - Set driver as unavailable/offline

---

### 4. Repositories
**Location:** `app/src/main/java/gorda/driver/repositories/`

#### ServiceRepository.kt
- **Purpose:** Manages service-related Firebase operations
- **Type:** Singleton object
- **Key Methods:**
  - `getPending(listener)` - Get all pending services
  - `listenNewServices(listener)` - Listen for new service additions
  - `stopListenServices(listener)` - Stop listening to services
  - `startListenNextService(serviceId, listener)` - Monitor specific service
  - `addListenerCurrentService(serviceId, listener)` - Monitor active trip
  - `isThereCurrentService(listener)` - Check if driver has active service
  - `isThereConnectionService(listener)` - Check if driver has accepted service
  - `validateAssignment(serviceId)` - Validate service assignment
  - `addApplicant(serviceId, driverId, distance, time, connection)` - Apply for service
  - `cancelApply(serviceId, driverId)` - Cancel application
  - `updateMetadata(serviceId, metadata, status)` - Update service data
- **Firebase Paths Used:**
  - `/services` - Service records
  - `/drivers_assigned/{driver_id}` - Active service assignments
  - `/service_connections/{driver_id}` - Service connection queue

#### DriverRepository.kt
- **Purpose:** Manages driver-related Firebase operations
- **Type:** Singleton object
- **Key Methods:**
  - `connect(driver, location)` - Mark driver as online
  - `disconnect(driverId)` - Mark driver as offline
  - `updateLocation(driverId, location)` - Update driver GPS position
  - `getDriver(driverId, callback)` - Fetch driver data
  - `updateDriver(driver)` - Update driver information
  - `updateBalance(driverId, balance)` - Update earnings

#### TokenRepository.kt
- **Purpose:** Manages FCM tokens for push notifications
- **Type:** Singleton object
- **Key Methods:**
  - `saveToken(token)` - Store FCM token
  - `getToken(callback)` - Retrieve stored token
  - `updateToken(driverId, token)` - Update token in Firebase

#### SettingsRepository.kt
- **Purpose:** Manages app and ride settings
- **Type:** Singleton object
- **Key Methods:**
  - `getRideFees(callback)` - Fetch current ride pricing
  - `getMultiplier()` - Get pricing multiplier

---

### 5. UI Components
**Location:** `app/src/main/java/gorda/driver/ui/`

#### Fragments Structure:

##### home/HomeFragment.kt
- **Navigation ID:** `nav_home`
- **Purpose:** Main screen showing available services
- **Features:**
  - RecyclerView with ServiceAdapter
  - Displays pending ride requests
  - Shows distance and estimated time
  - Apply button for each service
  - Map preview button
  - Alert notifications for new services

##### service/current/CurrentServiceFragment.kt
- **Navigation ID:** `nav_current_service`
- **Purpose:** Active trip management screen
- **Features:**
  - Trip details display
  - Start/Stop trip buttons
  - Real-time fee calculation
  - Distance tracking
  - Navigation to destination
  - Trip completion functionality

##### service/apply/ApplyFragment.kt
- **Navigation ID:** `nav_apply`
- **Purpose:** Service application screen
- **Features:**
  - Service details display
  - Map with route preview
  - Distance and time estimation
  - Confirm application button
  - Cancel application option

##### service/MapFragment.kt
- **Navigation ID:** `nav_map`
- **Purpose:** Map visualization
- **Features:**
  - Google Maps integration
  - Route display
  - Markers for pickup/destination
  - Distance calculation
  - Navigation integration

##### history/HistoryFragment.kt
- **Navigation ID:** `nav_history`
- **Purpose:** Trip history
- **Features:**
  - List of completed trips
  - Trip details dialog
  - Earnings summary
  - Filtering options

##### profile/ProfileFragment.kt
- **Navigation ID:** `nav_profile`
- **Purpose:** Driver profile management
- **Features:**
  - Profile photo display/update
  - Personal information
  - Vehicle information
  - Balance display
  - Document verification status

##### settings/SettingsFragment.kt
- **Navigation ID:** `nav_settings`
- **Purpose:** App preferences
- **Features:**
  - Notification settings (voice, tone, ringtone)
  - Alert preferences
  - Firebase emulator configuration
  - About information

##### about/AboutFragment.kt
- **Navigation ID:** `nav_about`
- **Purpose:** App information
- **Features:**
  - Version information
  - Build date
  - License information

#### ViewModels:

- **MainViewModel.kt** - Shared state across app
- **HomeViewModel.kt** - Home screen state
- **CurrentServiceViewModel.kt** - Active trip state
- **ApplyViewModel.kt** - Application flow state
- **ProfileViewModel.kt** - Profile data management
- **HistoryViewModel.kt** - Trip history management

#### Adapters:

##### ServiceAdapter.kt
- **Purpose:** RecyclerView adapter for pending services
- **Features:**
  - Service card display
  - Distance calculation
  - Time estimation
  - Click handlers for apply and map

##### HistoryRecyclerViewAdapter.kt
- **Purpose:** RecyclerView adapter for trip history
- **Features:**
  - Trip summary cards
  - Earnings display
  - Date formatting
  - Click handler for details

#### Broadcast Receivers:

##### LocationBroadcastReceiver.kt
- **Purpose:** Receives location updates from LocationService
- **Action:** `gorda.driver.LOCATION_UPDATES`

##### ConnectionBroadcastReceiver.kt
- **Purpose:** Receives driver connection status updates
- **Action:** Connection status changes

---

### 6. Firebase Services
**Location:** `app/src/main/java/gorda/driver/services/firebase/`

#### FirebaseInitializeApp.kt
- **Purpose:** Firebase initialization singleton
- **Configuration:** Supports emulator mode for development

#### Auth.kt
- **Purpose:** Firebase Authentication wrapper
- **Features:**
  - Email/Password authentication via FirebaseUI
  - User session management
  - Auth state listeners
  - Logout functionality
- **Methods:**
  - `getCurrentUserUUID()` - Get current user ID
  - `reloadUser()` - Refresh user data
  - `onAuthChanges(listener)` - Listen to auth changes
  - `launchLogin()` - Create login intent
  - `logOut(context)` - Sign out user

#### Database.kt
- **Purpose:** Firebase Realtime Database wrapper
- **Database Paths:**
  - `/services` - Service records
  - `/drivers_assigned` - Active assignments
  - `/service_connections` - Service queue
  - `/drivers` - Driver profiles
  - `/tokens` - FCM tokens
  - `/online_drivers` - Available drivers
  - `/settings/ride_fees` - Pricing configuration
- **Features:**
  - Automatic data synchronization (keepSynced)
  - Reference caching

#### FirestoreDatabase.kt
- **Purpose:** Cloud Firestore operations
- **Collections:**
  - Driver extended data
  - Transaction history
  - Analytics data

#### Storage.kt
- **Purpose:** Firebase Storage wrapper
- **Features:**
  - Profile photo upload
  - Document uploads
  - Image compression

#### Messaging.kt
- **Purpose:** FCM configuration
- **Features:**
  - Token registration
  - Topic subscriptions

---

### 7. Location Management
**Location:** `app/src/main/java/gorda/driver/location/`

#### LocationHandler.kt
- **Purpose:** Location provider abstraction
- **Features:**
  - Location permission checks
  - FusedLocationProviderClient wrapper
  - LocationRequest configuration
  - Location updates management
  - GPS status monitoring

---

### 8. Maps Integration
**Location:** `app/src/main/java/gorda/driver/maps/`

#### Map.kt
- **Purpose:** Google Maps utilities
- **Static Methods:**
  - `calculateDistance(latLng1, latLng2)` - Distance between points
  - `distanceToString(distance)` - Format distance for display
  - `calculateTime(distance)` - Estimate travel time
  - `getTimeString(time)` - Format time for display
  - `getDirectionURL(origin, dest, secret)` - Build Directions API URL
  - `makePolylineOptions(routes)` - Create polyline from routes
  - `decodePolyline(encoded)` - Decode encoded polyline

#### MapApiService.kt
- **Purpose:** Retrofit interface for Google Directions API
- **Endpoint:** `directions/json`
- **Base URL:** `https://maps.googleapis.com/maps/api/`

#### MapData.kt, Routes.kt, WindowAdapter.kt, InfoWindowData.kt
- **Purpose:** Data classes for map operations and custom info windows

---

### 9. Interfaces
**Location:** `app/src/main/java/gorda/driver/interfaces/`

#### Key Interfaces:

- **DriverInterface** - Driver data contract
- **DeviceInterface** - Device data contract
- **LocInterface** - Location coordinate contract
- **LocType** - Location with address details
- **Vehicle** - Vehicle information
- **Device** - Device information
- **DriverConnected** - Online driver status
- **RideFees** - Pricing structure
- **ServiceMetadata** - Trip metadata
- **LocationUpdateInterface** - Location update callbacks
- **CustomLocationListener** - Location change listener

---

### 10. Utilities
**Location:** `app/src/main/java/gorda/driver/utils/`

#### Constants.kt
- **Purpose:** App-wide constant definitions
- **Categories:**
  - Intent extras
  - Notification channels
  - SharedPreferences keys
  - Action strings
  - Service identifiers

#### ServiceHelper.kt
- **Purpose:** Service-related utility functions
- **Methods:**
  - Service validation
  - Service formatting
  - Service calculations

#### DateHelper.kt
- **Purpose:** Date/time utilities
- **Methods:**
  - Timestamp formatting
  - Duration calculations
  - Date comparisons

#### StringHelper.kt
- **Purpose:** String manipulation
- **Methods:**
  - String formatting
  - Validation
  - Sanitization

#### NumberHelper.kt
- **Purpose:** Number formatting
- **Methods:**
  - Currency formatting
  - Distance formatting
  - Number validation

#### Utils.kt
- **Purpose:** General utility functions
- **Methods:**
  - Permission checks
  - Network status
  - UI helpers
  - Resource access

---

### 11. Serializers
**Location:** `app/src/main/java/gorda/driver/serializers/`

#### RideFeesDeserializer.kt
- **Purpose:** Custom Gson deserializer for RideFees
- **Handles:** Complex fee structure parsing from Firebase

---

### 12. Helpers
**Location:** `app/src/main/java/gorda/driver/helpers/`

#### Helpers.kt
- **Purpose:** Additional helper functions
- **Extensions:**
  - Coroutine helpers (withTimeout)
  - Lifecycle utilities

#### PlaySound.kt
- **Purpose:** Sound playback for notifications
- **Features:**
  - MediaPlayer management
  - Ringtone selection
  - Volume control

---

## Tech Stack

### Core Technologies
- **Language:** Kotlin 1.7.10
- **Build System:** Gradle 8.1.1
- **Min SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35

### Android Jetpack Components
- **AndroidX Core:** 1.10.1
- **AppCompat:** 1.6.1
- **Material Design:** 1.9.0
- **ConstraintLayout:** 2.1.4
- **Navigation:** 2.5.3
  - navigation-fragment-ktx
  - navigation-ui-ktx
- **Lifecycle Components:** 2.6.1
  - lifecycle-livedata-ktx
  - lifecycle-viewmodel-ktx
- **RecyclerView:** 1.3.0
- **Preference:** 1.2.1
- **Legacy Support:** 1.0.0

### Firebase Services
- **Firebase BOM:** 30.1.0
- **Firebase Realtime Database:** 20.2.2
- **Firebase Firestore:** 24.11.1
- **Firebase Authentication:** 22.1.1
- **Firebase Storage:** 20.2.1
- **Firebase Messaging (FCM):** 23.2.1
- **Firebase Core:** 21.1.1
- **FirebaseUI Auth:** 8.0.1

### Google Play Services
- **Location Services:** 21.0.1
- **Google Maps SDK:** 18.1.0

### Networking & Data
- **Retrofit:** 2.9.0
  - Converter Gson
- **Gson:** (via Retrofit)
- **Kotlinx Coroutines Android:** 1.6.4

### Image Loading
- **Glide:** 4.13.2 (with annotation processor)
- **Picasso:** 2.71828

### Error Tracking
- **Sentry Android:** 6.12.1
- **Sentry Gradle Plugin:** 3.4.0

### Testing
- **JUnit:** 4.13.2
- **AndroidX Test Ext:** 1.1.5
- **Espresso Core:** 3.5.1

---

## Module Breakdown

### App Module Structure
```
app/
├── build.gradle              # App-level build configuration
├── google-services.json      # Firebase configuration
├── proguard-rules.pro        # ProGuard rules for release builds
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/gorda/driver/
    │   │   ├── activity/         # Activity classes
    │   │   ├── background/       # Background services
    │   │   ├── helpers/          # Helper utilities
    │   │   ├── interfaces/       # Data interfaces
    │   │   ├── location/         # Location management
    │   │   ├── maps/             # Maps integration
    │   │   ├── models/           # Data models
    │   │   ├── repositories/     # Data repositories
    │   │   ├── serializers/      # Custom serializers
    │   │   ├── services/         # Service wrappers
    │   │   │   ├── firebase/     # Firebase services
    │   │   │   ├── retrofit/     # Retrofit configuration
    │   │   │   └── network/      # Network monitoring
    │   │   ├── ui/               # UI components
    │   │   │   ├── about/
    │   │   │   ├── driver/
    │   │   │   ├── history/
    │   │   │   ├── home/
    │   │   │   ├── profile/
    │   │   │   ├── service/
    │   │   │   │   ├── apply/
    │   │   │   │   ├── current/
    │   │   │   │   └── dataclasses/
    │   │   │   └── settings/
    │   │   └── utils/            # Utility classes
    │   └── res/                  # Resources (layouts, drawables, etc.)
    ├── androidTest/              # Android instrumentation tests
    └── test/                     # Unit tests
```

---

## Data Flow

### 1. Authentication Flow
```
StartActivity
    ↓
Check Firebase Auth
    ↓
┌─ If Authenticated ────→ MainActivity
│
└─ If Not Authenticated → Launch Firebase UI Auth
                              ↓
                         Auth Successful
                              ↓
                         MainActivity
```

### 2. Driver Connection Flow
```
MainActivity
    ↓
User toggles "Connect" switch
    ↓
LocationService starts
    ↓
LocationHandler requests location
    ↓
Driver location updated in Firebase
    ↓
Driver marked as online in /online_drivers
    ↓
LocationService listens for new services
    ↓
New service added to /services
    ↓
LocationService detects new service
    ↓
Sound/Voice notification plays
    ↓
Service appears in HomeFragment RecyclerView
```

### 3. Service Application Flow
```
HomeFragment
    ↓
User clicks "Apply" on service
    ↓
Navigation to ApplyFragment with service data
    ↓
MapFragment shows route from driver to pickup
    ↓
Calculate distance and time
    ↓
User confirms application
    ↓
ServiceRepository.addApplicant()
    ↓
Driver added to /services/{id}/applicants
    ↓
Connection notification sent to driver
    ↓
If accepted by backend:
    ↓
Service ID added to /service_connections/{driver_id}
    ↓
ConnectionServiceDialog appears
    ↓
Driver confirms
    ↓
Navigation to CurrentServiceFragment
```

### 4. Active Trip Flow
```
CurrentServiceFragment
    ↓
User starts trip
    ↓
FeesService starts
    ↓
Service status updated to "in_progress"
    ↓
Service ID added to /drivers_assigned/{driver_id}
    ↓
LocationService tracks route points
    ↓
FeesService calculates distance and fees in real-time
    ↓
Notification shows current fee
    ↓
User arrives at destination
    ↓
User clicks "Complete Trip"
    ↓
Service.terminate() called
    ↓
Metadata updated with route, distance, fee
    ↓
Status changed to "terminated"
    ↓
FeesService stops
    ↓
Driver balance updated
    ↓
Navigation back to HomeFragment
```

### 5. Firebase Data Structure
```
Firebase Realtime Database:
├── services/
│   └── {service_id}/
│       ├── id
│       ├── name
│       ├── status
│       ├── start_loc/
│       ├── end_loc/
│       ├── phone
│       ├── comment
│       ├── driver_id
│       ├── client_id
│       ├── wp_client_id
│       ├── created_at
│       ├── metadata/
│       │   ├── start_trip_at
│       │   ├── end_trip_at
│       │   ├── route
│       │   ├── trip_distance
│       │   ├── trip_fee
│       │   └── trip_multiplier
│       └── applicants/
│           └── {driver_id}/
│               ├── distance
│               ├── time
│               └── connection
├── drivers/
│   └── {driver_id}/
│       ├── id
│       ├── name
│       ├── email
│       ├── phone
│       ├── docType
│       ├── document
│       ├── photoUrl
│       ├── enabled_at
│       ├── created_at
│       ├── device/
│       ├── vehicle/
│       └── balance
├── online_drivers/
│   └── {driver_id}/
│       ├── lat
│       ├── lng
│       └── timestamp
├── drivers_assigned/
│   └── {driver_id}: {service_id}
├── service_connections/
│   └── {driver_id}: {service_id}
├── tokens/
│   └── {driver_id}: {fcm_token}
└── settings/
    └── ride_fees/
        ├── base_fee
        ├── per_km
        └── multiplier
```

---

## Key Features

### 1. Real-Time Location Tracking
- **Implementation:** LocationService (foreground service)
- **Update Frequency:** Every 10 location changes to Firebase
- **Broadcast:** Local broadcasts every location update
- **Permissions Required:**
  - ACCESS_FINE_LOCATION
  - ACCESS_COARSE_LOCATION
  - FOREGROUND_SERVICE
  - FOREGROUND_SERVICE_LOCATION

### 2. WhatsApp Integration
- **Flow:** Client → WhatsApp → Backend → Firebase → Driver App
- **Identifier:** `wp_client_id` field in Service model
- **Backend Process:**
  - Backend monitors WhatsApp messages
  - Creates Service record in Firebase
  - Sets `wp_client_id` instead of `client_id`
  - Driver sees request normally in app

### 3. Service Request System
- **Pending Services:** Query `/services?status=pending`
- **Display:** RecyclerView in HomeFragment
- **Information Shown:**
  - Client name
  - Pickup address
  - Distance from driver
  - Estimated arrival time
- **Actions:**
  - View on map
  - Apply for service

### 4. Trip Meter & Fees
- **Service:** FeesService (foreground service)
- **Features:**
  - Real-time distance calculation
  - Fee calculation: `base_fee + (distance * per_km * multiplier)`
  - Trip duration tracking
  - Route point collection
  - Persistent notification with current fee
- **State Persistence:** SharedPreferences for crash recovery

### 5. Push Notifications
- **Service:** NotificationService (FCM)
- **Notification Types:**
  - New service available
  - Service assigned
  - Service canceled
  - System messages
- **Customization:**
  - Voice announcements (TTS)
  - Custom ringtones
  - Vibration patterns
  - Notification duration

### 6. Google Maps Integration
- **SDK:** Google Maps SDK 18.1.0
- **Features:**
  - Route visualization
  - Distance/time calculations
  - Custom markers
  - Info windows
  - Polyline drawing
- **Directions API:**
  - Retrofit-based service
  - Polyline decoding
  - Route optimization

### 7. Profile Management
- **Data:** Firebase Realtime Database + Firestore
- **Features:**
  - Photo upload to Firebase Storage
  - Personal information editing
  - Vehicle details
  - Earnings/balance display
  - Document status

### 8. Trip History
- **Source:** Firestore (extended data) + Realtime Database
- **Display:** HistoryFragment with RecyclerView
- **Information:**
  - Trip date/time
  - Client name
  - Distance traveled
  - Earnings
  - Route details

### 9. Network Monitoring
- **Component:** NetworkMonitor
- **Purpose:** Detect connectivity changes
- **Actions:**
  - Show/hide connectivity warnings
  - Pause/resume Firebase sync
  - Alert user of offline state

### 10. Error Tracking
- **Tool:** Sentry Android SDK
- **Configuration:**
  - DSN configured via build variables
  - Sample rate: 0.8 (80%)
  - User interaction tracking enabled
- **Captures:**
  - Crashes
  - Uncaught exceptions
  - Handled errors (manual logging)

---

## Build Configuration

### Gradle Configuration

#### Root build.gradle
```groovy
Plugins:
- com.android.application: 8.1.1
- com.android.library: 8.1.1
- org.jetbrains.kotlin.android: 1.7.10
- com.google.android.libraries.mapsplatform.secrets-gradle-plugin: 2.0.0

Classpath:
- com.google.gms:google-services:4.3.15
```

#### App build.gradle
```groovy
Application ID: gorda.driver
Version Code: 65
Version Name: 1.2.2
Min SDK: 24
Target SDK: 35
Compile SDK: 35

Build Features:
- View Binding: enabled
- BuildConfig: enabled

Sentry Plugin: 3.4.0

Build Types:
- release:
  - minifyEnabled: false
  - proguardFiles: configured
```

### Build Variables (local.properties)

Required variables:
- `sdk.dir` - Android SDK path
- `MAPS_API_KEY` - Google Maps API key
- `SENTRY_DSN` - Sentry error tracking DSN
- `MAPS_BASE_URL` - Google Maps API base URL

Optional (for emulator):
- `FIREBASE_USE_EMULATORS` - Enable Firebase emulators
- `FIREBASE_AUTH_HOST` - Auth emulator host
- `FIREBASE_DATABASE_HOST` - Database emulator host
- `FIREBASE_FIRESTORE_HOST` - Firestore emulator host
- `FIREBASE_STORAGE_HOST` - Storage emulator host
- `FIREBASE_AUTH_PORT` - Auth emulator port (9099)
- `FIREBASE_DATABASE_PORT` - Database emulator port (9000)
- `FIREBASE_FIRESTORE_PORT` - Firestore emulator port (8080)
- `FIREBASE_STORAGE_PORT` - Storage emulator port (9199)

### Permissions (AndroidManifest.xml)

```xml
Required Permissions:
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_NETWORK_STATE
- INTERNET
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION
- POST_NOTIFICATIONS
- READ_MEDIA_AUDIO
- READ_EXTERNAL_STORAGE (maxSdkVersion 32)

Application Settings:
- allowBackup: false
- usesCleartextTraffic: true (for emulators)
- supportsRtl: true
```

### ProGuard Configuration
File: `proguard-rules.pro`
- Configured for release builds
- Currently: minifyEnabled = false

---

## Development Workflow

### Setup Steps
1. Clone repository
2. Copy `local.properties.example` → `local.properties`
3. Fill in required API keys and configuration
4. Open project in Android Studio
5. Sync Gradle
6. Connect device/emulator
7. Build and run

### Build Commands
```bash
# Debug build
./gradlew assembleDebug
./gradlew installDebug

# Release build
./gradlew assembleRelease
./gradlew bundleRelease  # App Bundle for Play Store

# Clean build
./gradlew clean

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

### Firebase Emulator Usage
1. Set `FIREBASE_USE_EMULATORS=true` in `local.properties`
2. Configure emulator hosts and ports
3. Start Firebase emulators:
   ```bash
   firebase emulators:start
   ```
4. Run app with emulator endpoints

---

## Version History

### Current: 1.2.2 (Build 65)
- Latest production release

### Recent Releases:
- **1.1.6 (2025-03-19)** - Fixed connection lost errors
- **1.1.5 (2024-08-19)** - Fixed service disappearance errors
- **1.1.2 (2024-08-19)** - Fixed ServiceAdapter and location errors
- **1.1.1 (2024-08-12)** - Fixed LocationService errors
- **1.1.0 (2024-07-29)** - Added meter service (FeesService)

---

## API Integrations

### Firebase Realtime Database
- **Purpose:** Real-time service data, driver status, assignments
- **Endpoints:** See Firebase Data Structure section above

### Firebase Cloud Firestore
- **Purpose:** Extended driver data, transaction history
- **Collections:**
  - `drivers` - Extended driver profiles
  - `transactions` - Payment history

### Firebase Cloud Messaging
- **Purpose:** Push notifications
- **Topics:**
  - Driver-specific topics
  - Broadcast topics

### Firebase Storage
- **Purpose:** File uploads
- **Paths:**
  - `/drivers/{driver_id}/profile.jpg` - Profile photos
  - `/drivers/{driver_id}/documents/` - Document uploads

### Google Maps Directions API
- **Base URL:** `https://maps.googleapis.com/maps/api/`
- **Endpoint:** `directions/json`
- **Parameters:**
  - `origin` - Start coordinates
  - `destination` - End coordinates
  - `mode` - driving
  - `key` - API key
- **Response:** Route data with encoded polyline

---

## Security Considerations

### Authentication
- Firebase Authentication required for all operations
- Email/Password via FirebaseUI
- Session persistence
- Automatic token refresh

### Data Access Rules
- Driver can only access own profile
- Driver can read pending services
- Driver can update own location
- Driver can apply to services
- Service modifications logged

### API Key Protection
- Keys stored in `local.properties` (not in VCS)
- Secrets plugin for build-time injection
- ProGuard rules for release obfuscation

### Network Security
- HTTPS for all Firebase communication
- SSL pinning recommended for production
- Clear text traffic enabled only for emulators

---

## Testing Strategy

### Unit Tests
- Location: `app/src/test/`
- Framework: JUnit 4.13.2
- Focus: Business logic, utilities, calculations

### Instrumentation Tests
- Location: `app/src/androidTest/`
- Framework: AndroidX Test + Espresso
- Focus: UI interactions, navigation, services

### Manual Testing Checklist
- [ ] Authentication flow
- [ ] Driver connection/disconnection
- [ ] Service request reception
- [ ] Service application
- [ ] Trip start/stop
- [ ] Fee calculation accuracy
- [ ] GPS tracking
- [ ] Push notifications
- [ ] Profile updates
- [ ] History display

---

## Troubleshooting

### Common Issues

#### Location not updating
- Check permissions granted
- Verify LocationService is running
- Check GPS is enabled
- Verify Firebase connection

#### Services not appearing
- Check Firebase Database rules
- Verify driver is connected
- Check internet connectivity
- Verify service status is "pending"

#### Push notifications not received
- Check FCM token registered
- Verify notification permissions
- Check notification channels created
- Verify Firebase Cloud Messaging setup

#### Maps not loading
- Verify MAPS_API_KEY is valid
- Check API is enabled in Google Cloud Console
- Verify billing is enabled
- Check network connectivity

#### Build failures
- Sync Gradle files
- Clean and rebuild project
- Check all dependencies are accessible
- Verify SDK versions installed

---

## Contributing Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Add comments for complex logic
- Keep functions focused and small

### Git Workflow
1. Fork repository
2. Create feature branch from `master`
3. Commit with clear messages
4. Test changes thoroughly
5. Open Pull Request with description

### Pull Request Requirements
- [ ] Code compiles without errors
- [ ] No new warnings introduced
- [ ] Existing tests pass
- [ ] New features have tests
- [ ] Documentation updated if needed
- [ ] CHANGELOG.md updated

---

## License
This repository does not contain an explicit license file. All rights are reserved by the original authors.

---

## Contact & Support
For issues, feature requests, or questions:
- GitHub Issues: [DevAlexandreCR/gorda-driver](https://github.com/DevAlexandreCR/gorda-driver)

---

**Document Version:** 1.0  
**Last Updated:** November 16, 2025  
**Project Version:** 1.2.2 (Build 65)

