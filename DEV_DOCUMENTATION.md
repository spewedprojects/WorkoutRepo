# WorkoutRepo Developer Documentation (Detailed Core Mechanics) (v12.2.0)

This document provides an in-depth mapping of the specific classes, methods, functions, and architectural patterns that power the WorkoutRepo Android application.

---

## 1. Architecture Overview & Multi-Provider Sync Model

WorkoutRepo uses a **Unified Archive System** capable of storing, deduplicating, and rendering workout data from both **Strava** and **Intervals.icu**, while also maintaining local routine templates and widget integrations.

```mermaid
graph TD
    UI["UI Layer (MainActivity / ArchiveActivity / ActivityBottomSheet)"]
    SLM["StravaListManager / StravaAdapter"]
    DTU["DateTimeUtils"]
    
    subgraph Data Repositories
        SR["StravaRepository"]
        IR["IntervalsRepository"]
        RR["RoutineRepository"]
    end
    
    subgraph Storage and Security
        AAM["ActivityArchiveManager (activities_cache.json)"]
        ESP["EncryptedSharedPreferences (intervals_secure_prefs)"]
        SP["SharedPreferences (WorkoutRepoAppSettings)"]
    end
    
    subgraph Network APIs
        SS["Strava API (Retrofit)"]
        IS["Intervals.icu API (Retrofit)"]
    end

    UI --> SLM
    SLM --> DTU
    SLM --> SR
    SLM --> IR
    SR --> SS
    IR --> IS
    IR --> ESP
    SR --> SP
    SR --> AAM
    IR --> AAM
    RR --> SP
```

---

## 2. Routine Management & Architecture

WorkoutRepo separates the concept of an **Active Routine** (the one currently being tracked on the main screen) and **Saved Routines** (the library of programs created by the user).

### Core Repository (`RoutineRepository.java`)
Single source of truth for loading and persisting Routines using a JSON file-based approach rather than SQL Room mapping.
- **`getActiveRoutine(Context)`**: Reads `active_routine.json` from `getFilesDir()`. If missing, execution halts and forks to `migrateLegacyData()` to pull data from old `WorkoutPrefs` string mappings.
- **`saveActiveRoutine(Context, Routine)`**: Serializes the routine via Gson's `setPrettyPrinting` and overwrites `active_routine.json`. This acts as the "Active Buffer" that the app is continually operating against.
- **`saveRoutineToLibrary(Context, Routine)`**: Generates a resilient timestamp file via `saved_routines/routine_[UUID].json` for permanent templates.

### UI Editor Synchronization (`WorkoutStorage.java`)
Bridge when the user edits individual values utilizing `EditorBottomSheet.java`.
- **`saveWorkout(Context context, String dayKey, String fieldKey, String value)`**: 
  1. Grabs `RoutineRepository.getActiveRoutine()`.
  2. Synthesizes a local change against the target `DayWorkout` variable (e.g. updating notes).
  3. Executes an immediate save back to the active tracking file.
  4. **Sync Check**: Invokes `isRoutineSaved()` checking if the active ID exists inside `saved_routines/`. If so, it simultaneously commits `RoutineRepository.saveRoutineToLibrary()`.
  5. Forces a UI redraw via `WorkoutsWidgetProvider.Companion.sendRefreshBroadcast()`.

---

## 3. Intervals.icu Integration & Multi-Provider Networking

Due to API subscription requirements for Strava API access, WorkoutRepo natively integrates the **Intervals.icu API** alongside Strava.

### Authentication & Security
- **API Key Storage**: Stored securely using `EncryptedSharedPreferences` (`intervals_secure_prefs`) configured with `AES256_GCM_SPEC` and `MasterKeys`.
- **HTTP Basic Auth Interceptor**: Requests to `https://intervals.icu/api/v1/` automatically attach a Basic Auth header containing `Base64("API_KEY:" + userApiKey)`.
- **Rate Limit Tracking**: Captures `X-RateLimit-Limit` and `X-RateLimit-Remaining` response headers.
- **Credential Validation & Missing Keys UI**: `TokenManager.hasValidCredentials(context)` and `IntervalsRepository.hasValidCredentials(context)` verify that non-empty credentials exist. If missing, `StravaListManager` displays the `text_NoAPIKeys` empty state view ("Setup your API keys in app settings to use this feature.") and suppresses background network fetches.
- **Athlete ID Hooking & Archive Scoping**: When valid keys are entered, `bound_strava_athlete_id` and `bound_intervals_athlete_id` are saved to preferences. Changing credentials to a different athlete prompts a `MaterialAlertDialogBuilder` warning advising the user to export their archive, while preserving previous archives safely on disk as `activities_cache_<athlete_id>.json`.

### Key API Endpoints (`IntervalsService.kt`)
1. **`GET athlete/0/activities`**: Fetches activity summaries filtered by `oldest` and `newest` ISO date strings.
2. **`GET activity/{id}`**: Fetches detailed single activity JSON (including description/notes edited directly on Intervals.icu).
3. **`GET activity/{id}/messages`**: Fetches user comments and chat thread messages for lazy loading.
4. **`GET athlete/0/wellness`**: Fetches athlete fitness and fatigue metrics (`ctl`, `atl`, `tsb`).

### Unified Archive Model (`ArchiveActivity.kt`)
The archive data model unifies both providers:
```kotlin
data class ArchiveActivity(
    val id: String = UUID.randomUUID().toString(),
    val stravaActivityId: Long? = null,
    val intervalsActivityId: String? = null,
    val source: SourceProvider = SourceProvider.STRAVA,
    val name: String,
    val distance: Float,
    val movingTime: Int,
    val startDateLocal: String,
    val averageWatts: Float? = null,
    val averageHeartrate: Float? = null,
    val totalElevationGain: Float? = null,
    val type: String,
    val workoutType: Int? = null,
    val description: String? = null,
    val lastModifiedLocal: Long = System.currentTimeMillis()
)
```

---

## 4. Sync Strategy, Deduplication & Date-Time Precision

### Incremental Sync Boundary Calculation
To prevent burning API rate limits while respecting preloaded/imported historical archives, `IntervalsRepository.syncActivities()` calculates `oldest` dynamically:

```mermaid
flowchart TD
    Start["Start Intervals.icu Sync"] --> CheckArchive{"Is Archive Empty?"}
    
    CheckArchive -- Yes --> UsePref["Read 'IntervalsDurationYears' (Default: 2)<br/>Set oldest = now - durationYears"]
    
    CheckArchive -- No --> CheckForce{"Is Force Deep Sync?"}
    
    CheckForce -- No --> CalcLatest["Find max startDateLocal in Archive<br/>Set oldest = latestDate - 1 day<br/>(Discard Duration Pref)"]
    
    CheckForce -- Yes --> CompareDates{"Is configuredStartDate < earliestArchiveDate?"}
    CompareDates -- Yes --> UseConfigured["Set oldest = configuredStartDate<br/>(Backfill missing history)"]
    CompareDates -- No --> CalcLatest
    
    UsePref --> FetchAPI["Call GET athlete/0/activities(oldest)"]
    CalcLatest --> FetchAPI
    UseConfigured --> FetchAPI
    
    FetchAPI --> MergeLoop["Deduplicate & Merge into Archive"]
    MergeLoop --> SaveDisk["Atomically Save to activities_cache.json"]
```

### Heuristic Deduplication (`ActivityArchiveManager.findExistingMatch`)
When syncing activities from Intervals.icu into an archive that contains existing Strava activities, `findExistingMatch` merges duplicate entries:

1. **Strict ID Check**: Matches if `candidate.stravaActivityId == existing.stravaActivityId` or `candidate.intervalsActivityId == existing.intervalsActivityId`.
2. **Sport Type Matching**: Mapped via `SportTypeMapper`. Supports `"Unknown"` as a wildcard matching key.
3. **Robust ISO Date Parsing**: Converts `startDateLocal` via `DateTimeUtils.parseToLocalDateTime()` to handle ISO format variances (with/without `Z` or UTC offsets). Matches if within a **12-hour temporal window**.
4. **Moving Time Tolerance**: Compares `movingTime` with a **~5% tolerance window**.

When a match occurs, the existing entry is updated to store both `stravaActivityId` and `intervalsActivityId`, preserving descriptions and local edits.

### Centralized Date & Time Processing (`DateTimeUtils.kt`)
To resolve timezone shifts and misleading `00:00` display values across all activity list views:
- **`parseToLocalDateTime(dateStr)`**: `startDateLocal` fields are recorded in local wall-clock time at the activity location. Strips trailing `Z` and offset strings (`+05:30`) so evening workouts (e.g. 19:30) remain at 19:30 local wall-clock time instead of being shifted past `00:00` into the next day by device timezone offsets.
- **`formatActivityDate(dateStr)`**: Checks if a time component is present (`DATE_TIME_FORMATTER` -> `"6 Mar 2026, Fri ৹ 06:09"`). If date-only or `00:00:00`, formats as `"6 Mar 2026, Fri"` using `DATE_ONLY_FORMATTER` to eliminate misleading `00:00` rendering.

---

## 5. Lazy Loading & Smart Description Fallback

To minimize network overhead, detailed workout notes and chat threads are lazily fetched when the user taps an activity card.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Adapter as StravaAdapter / StravaListManager
    participant StravaRepo as StravaRepository
    participant IntervalsRepo as IntervalsRepository
    participant StravaAPI as Strava Service
    participant IntervalsAPI as Intervals.icu Service
    participant Archive as ActivityArchiveManager

    User->>Adapter: Tap Activity Card
    Adapter->>StravaRepo: getActivityDetails(context, archiveActivity)
    
    alt Active Source is INTERVALS_ICU or intervalsActivityId != null
        StravaRepo->>IntervalsRepo: getActivityDetailsWithDescription(...)
        IntervalsRepo->>IntervalsAPI: GET activity/{id} & GET activity/{id}/messages
        IntervalsAPI-->>IntervalsRepo: Return updated description & chat messages
        IntervalsRepo->>Archive: Save updated ArchiveActivity
        IntervalsRepo-->>Adapter: Return detailed ArchiveActivity
    else Active Source is STRAVA
        StravaRepo->>StravaAPI: GET /activities/{id}
        alt Strava API Succeeds
            StravaAPI-->>StravaRepo: Return activity details
            StravaRepo->>Archive: Save updated ArchiveActivity
            StravaRepo-->>Adapter: Return detailed ArchiveActivity
        else Strava API Fails (e.g. HTTP 403 / Unauthenticated)
            StravaAPI-->>StravaRepo: HTTP 403 Forbidden / Error
            Note over StravaRepo, IntervalsRepo: Trigger Smart Fallback to Intervals.icu
            StravaRepo->>IntervalsRepo: getActivityDetailsWithDescription(...)
            
            alt intervalsActivityId is null
                IntervalsRepo->>IntervalsAPI: GET athlete/0/activities (around date window)
                IntervalsAPI-->>IntervalsRepo: Return activities on date
                IntervalsRepo->>IntervalsRepo: Match via findExistingMatch & link intervalsActivityId
            end
            
            IntervalsRepo->>IntervalsAPI: GET activity/{id} & GET activity/{id}/messages
            IntervalsAPI-->>IntervalsRepo: Return description
            IntervalsRepo->>Archive: Save linked ArchiveActivity
            IntervalsRepo-->>Adapter: Return detailed ArchiveActivity
        end
    end
```

---

## 6. UI Control Flow & Layout Mechanics

### Responsive Multi-Column Activity Grid (`StravaListManager.kt` & `StarvaAdapter.kt`)
- **Dynamic Span Calculation**: `StravaListManager.kt` evaluates display metrics at runtime:
  $$\text{spanCount} = \max\left(1, \left\lfloor \frac{\text{screenWidthDp}}{410} \right\rfloor\right)$$
- **Layout Manager**: Configures `recyclerView.layoutManager` with `GridLayoutManager(context, spanCount)`.
- **Dynamic Item Margins (`StarvaAdapter.kt`)**: Adjusts `ViewGroup.MarginLayoutParams` per item based on `position % spanCount`:
  - Single column (`spanCount == 1`): Sets start and end margins to `12dp`.
  - Multi-column (`spanCount > 1`): First column gets `12dp` start and `1dp` end margin; second column gets `1dp` start and `12dp` end margin.

### Dynamic Provider Tinting for Import/Merge Dialog (`MainActivity.java`)
- When importing JSON archives via `importLauncher` in `MainActivity.java`, `R.layout.dialog_import_option` is displayed.
- The `btnMergeArchive` button background tint dynamically matches `ActiveSyncSource`:
  - `STRAVA`: Tinted with `#fc4c02` (`@color/strava_color`).
  - `INTERVALS_ICU`: Tinted with `#dc0746` (`@color/intervals_icu_color`).

### Direct Provider Toggle (Keyword Dialog Removal)
- Selecting Strava in Settings (`GuideAdapter.java`) directly executes `setSyncSource(sHolder, prefs, SourceProvider.STRAVA.name())`.
- Removed keyword dialog requirements (`showKeywordDialog`) and deleted `dialog_stravakeyword.xml`.

### IME-Gated Settings UI (`GuideAdapter.java` & `settings_app.xml`)
- **Duration Input (`id/tilDuration_icu` & `id/etDuration_icu`)**: Allows the user to enter custom initial fetch duration (in years).
- **Save API Details Button (`id/saveAPIDetails`)**:
  - Hidden by default (`View.GONE`).
  - Listens to soft keyboard insets via `ViewCompat.setOnApplyWindowInsetsListener` and focus changes on inputs.
  - Becomes `VISIBLE` **only** when the IME (keyboard) is visible **AND** an input field is focused.
  - Tapping Save persists credentials, clears focus, hides keyboard, and sets button visibility to `View.GONE`.

### Native State Selectors for Source Selection Buttons
The `selectStrava` and `selectICU` buttons use Android state selectors (`button_background_selector_strava.xml` & `icu.xml`):
- **Selected State (`android:state_selected="true"`)**: Displays a 2dp outer border layer-list with visible gap (`rounded_corners_strava_border` / `intervalsicu_border`).
- **Unselected State (Default)**: Displays a 0dp outer border layer-list (`rounded_corners_button_border_disabled_strava` / `icu`).
- **Code Control**: Managed in `GuideAdapter.java` via `sHolder.btnSelectStrava.setSelected(isStrava)` and `sHolder.btnSelectICU.setSelected(isICU)`.

---

## 7. Complete Codebase File Index

| Directory / File                                                                                                                  | Description & Functionality                                                                                                                              |
|:----------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`com.gratus.workoutrepo`**                                                                                                      |                                                                                                                                                          |
| [BaseActivity.java](app/src/main/java/com/gratus/workoutrepo/BaseActivity.java)                                                   | Base activity class handling edge-to-edge system insets, night mode theme routing (`applyTheme`), and preference keys.                                   |
| [MainActivity.java](app/src/main/java/com/gratus/workoutrepo/MainActivity.java)                                                   | Main entry controller managing infinite week swipe pager, MotionLayout drawer, provider-tinted import dialogs, and settings change listeners.            |
| [RoutinesActivity.java](app/src/main/java/com/gratus/workoutrepo/RoutinesActivity.java)                                           | Routine library browser with horizontal `PagerSnapHelper`, template import/export, and routine lifecycle management.                                     |
| [ArchiveActivity.kt](app/src/main/java/com/gratus/workoutrepo/ArchiveActivity.kt)                                                 | Full-screen history viewer. Displays activity grid list and populates athlete wellness metrics (`fitnessRow`) when Intervals.icu is active.              |
| [ActivityBottomSheet.kt](app/src/main/java/com/gratus/workoutrepo/ActivityBottomSheet.kt)                                         | Quick-access bottom sheet filtering workout history by day of the week.                                                                                  |
| [EditorBottomSheet.java](app/src/main/java/com/gratus/workoutrepo/EditorBottomSheet.java)                                         | Textual content editor for routine workouts with unsaved text confirmation protection.                                                                   |
| **`com.gratus.workoutrepo.archive`**                                                                                              |                                                                                                                                                          |
| [ArchiveActivity.kt](app/src/main/java/com/gratus/workoutrepo/archive/model/ArchiveActivity.kt)                                   | Core unified activity data model class supporting Strava and Intervals.icu IDs and metrics.                                                              |
| [SourceProvider.kt](app/src/main/java/com/gratus/workoutrepo/archive/model/SourceProvider.kt)                                     | Enum defining active data sync source providers (`STRAVA` vs `INTERVALS_ICU`).                                                                           |
| [ActivityArchiveManager.kt](app/src/main/java/com/gratus/workoutrepo/archive/data/ActivityArchiveManager.kt)                      | Atomic disk persistence manager (`activities_cache.json`), legacy data migrator, wellness cache manager, and heuristic deduplication engine.             |
| [DateTimeUtils.kt](app/src/main/java/com/gratus/workoutrepo/archive/utils/DateTimeUtils.kt)                                       | Centralized date-time parsing and formatting utility. Strips UTC offset flags to preserve wall-clock time and format UI date strings cleanly.            |
| [SportTypeMapper.kt](app/src/main/java/com/gratus/workoutrepo/archive/utils/SportTypeMapper.kt)                                   | Maps provider-specific sport type strings to standardized UI activity categories.                                                                        |
| **`com.gratus.workoutrepo.intervalsICU`**                                                                                         |                                                                                                                                                          |
| [IntervalsService.kt](app/src/main/java/com/gratus/workoutrepo/intervalsICU/network/IntervalsService.kt)                          | Retrofit REST interface for Intervals.icu (`activities`, `activity/{id}`, `messages`, `wellness`).                                                       |
| [IntervalsRepository.kt](app/src/main/java/com/gratus/workoutrepo/intervalsICU/repository/IntervalsRepository.kt)                 | Encrypted API key manager, credential validator, incremental sync coordinator, single activity detail fetcher, and wellness metric provider.             |
| [IntervalsCalendarRepository.kt](app/src/main/java/com/gratus/workoutrepo/intervalsICU/repository/IntervalsCalendarRepository.kt) | Repository for fetching and caching Intervals.icu calendar events and planned workouts.                                                                  |
| [IntervalsModels.kt](app/src/main/java/com/gratus/workoutrepo/intervalsICU/data/IntervalsModels.kt)                               | Data models for Intervals.icu responses (`IntervalsActivity`, `IntervalsMessage`, `IntervalsWellness`).                                                  |
| **`com.gratus.workoutrepo.strava`**                                                                                               |                                                                                                                                                          |
| [StravaRepository.kt](app/src/main/java/com/gratus/workoutrepo/strava/repository/StravaRepository.kt)                             | Strava API repository featuring parallel page fetching, token management, and smart fallback routing to Intervals.icu.                                   |
| [TokenManager.kt](app/src/main/java/com/gratus/workoutrepo/strava/repository/TokenManager.kt)                                     | Credentials & token manager for Strava OAuth API access with validity checks.                                                                            |
| [StarvaAdapter.kt](app/src/main/java/com/gratus/workoutrepo/strava/adapters/StarvaAdapter.kt)                                     | `RecyclerView.Adapter` with `DiffUtil` support, multi-column margin adjustment, lazy loading indicators, and dynamic icon rendering.                     |
| [StravaListManager.kt](app/src/main/java/com/gratus/workoutrepo/strava/utils/StravaListManager.kt)                                | UI controller for `GridLayoutManager` span calculations, search filters, date range pickers, missing keys empty state, and detail fetching.              |
| **`com.gratus.workoutrepo.adapters`**                                                                                             |                                                                                                                                                          |
| [GuideAdapter.java](app/src/main/java/com/gratus/workoutrepo/adapters/GuideAdapter.java)                                          | Settings page adapter managing direct source selection (`setSelected`), IME-gated save button, athlete change alert dialogs, and preference persistence. |
| [WeekPagerAdapter.java](app/src/main/java/com/gratus/workoutrepo/adapters/WeekPagerAdapter.java)                                  | Virtual infinite pager adapter for main week view.                                                                                                       |
| **`com.gratus.workoutrepo.widgets`**                                                                                              |                                                                                                                                                          |
| [WorkoutsWidgetProvider.kt](app/src/main/java/com/gratus/workoutrepo/widgets/WorkoutsWidgetProvider.kt)                           | App widget receiver handling date updates, active routine binding, and RemoteViews refreshes.                                                            |
