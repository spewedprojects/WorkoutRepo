# WorkoutRepo Developer Documentation

This document outlines the purpose, features, and technical implementation of the WorkoutRepo Android application.

## Overview
WorkoutRepo is a "no-frills" but feature-rich workout tracking application designed for power users who want to manage routines, track daily progress, and integrate their Strava activity history.

## What the App Does

### 1. Workout & Routine Management
- **Active Tracking**: The main screen acts as a "buffer" for the current active routine. Users can edit types, major/minor exercises, and notes for each day of the week.
- **Routine Library**: Users can save their active routines to a library, allowing for multiple workout programs to be stored and swapped in as needed.
- **Rich Text Editing**: Supports custom formatting (bullets, bolding) within workout notes and exercise lists.

### 2. External Integrations
- **Strava Sync**: Connects to the Strava API to fetch athlete activities.
- **Activity Archive**: Provides a dedicated activity to browse and archive Strava workout history.

### 3. Home Screen Visibility
- **Weekly Workouts Widget**: A home screen widget that displays the current day's workout from the active routine for at-a-glance access.

---

## How It Works (Technical Implementation)

### Tech Stack
- **Languages**: Java (Core Logic & UI) and Kotlin (Modern features & Integrations).
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 15)
- **Networking**: [Retrofit 2](https://square.github.io/retrofit/) with Gson converter and OkHttp logging.
- **Serialization**: [Gson](https://github.com/google/gson) for JSON processing.

### Architecture: Repository Pattern
The app uses a **Repository Pattern** to decouple UI components from data persistence.
- **`RoutineRepository`**: Manages the loading, saving, and migration of routine data. It handles both "Active" (temp buffer) and "Library" (persistent files) routines.
- **`WorkoutStorage`**: A bridge between UI actions (saving an edit) and the repository, which also handles triggering UI/Widget refreshes.

### Data Persistence
Instead of a heavyweight database like Room, WorkoutRepo uses **File-based JSON Storage**:
- Data is stored in the app's internal files directory (`context.getFilesDir()`).
- **Active Routine**: `active_routine.json`
- **Library**: `saved_routines/routine_[id].json`
- **Migration**: Includes logic to migrate legacy data from `SharedPreferences` (`WorkoutPrefs`) to the new JSON-based system.

### Networking & Strava Integration
- Uses **OAuth 2.0** for Strava authentication.
- **`StravaService`**: A Retrofit interface defining endpoints for athlete activities and token refreshing.
- **`StravaListManager`**: Handles the business logic of managing and filtering retrieved Strava activities.

### UI & UX Components
- **Modular Activities**: Logic is split across `MainActivity` (tracking), `RoutinesActivity` (library management), and `StravaArchiveActivity`.
- **Bottom Sheets**: Uses `BottomSheetDialogFragment` (`EditorBottomSheet`, `StravaBottomSheet`) for a modern, non-intrusive editing experience.
- **Text Rendering**: `TextFormatUtils` provides a custom engine for rendering bullets and bold text using Android `Spannables`. It includes a custom `LeadingMarginSpan` (`TextBulletSpan`) for high-quality indentation that standard Android components often lack.

### App Widget Implementation
- **Provider**: `WorkoutsWidgetProvider` manages the lifecycle and broadcast updates.
- **Data Loading**: `WorkoutsRemoteViewsService` acts as a `RemoteViewsFactory` to populate the widget's grid/list with workout data.
- **Communication**: Uses local broadcasts to trigger the widget to refresh whenever the underlying data changes in `WorkoutStorage`.
