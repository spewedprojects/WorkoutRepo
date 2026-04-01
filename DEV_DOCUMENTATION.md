# WorkoutRepo Developer Documentation (Detailed Core Mechanics)

This document provides an in-depth mapping of the specific classes, methods, and functions that power the features within the WorkoutRepo Android application, designed for precise maintainability.

---

## 1. Routine Management & Architecture

WorkoutRepo separates the concept of an **Active Routine** (the one currently being tracked on the main screen) and **Saved Routines** (the library of programs the user has created).

### Core Repository (`RoutineRepository.java`)
This acts as the single source of truth for loading and persisting Routines using a JSON file-based approach rather than SQL Room mapping.
- **`getActiveRoutine(Context)`**: Reads `active_routine.json` from `getFilesDir()`. If missing, execution halts and it forks to `migrateLegacyData()` to pull data from old `WorkoutPrefs` string mappings.
- **`saveActiveRoutine(Context, Routine)`**: Serializes the routine via Gson's `setPrettyPrinting` and overwrites `active_routine.json`. This acts as the "Active Buffer" that the app is continually operating the week against.
- **`saveRoutineToLibrary(Context, Routine)`**: Generates a resilient timestamp file via `saved_routines/routine_[UUID].json` for permanent templates.

### UI Editor Synchronization (`WorkoutStorage.java`)
Acts as a bridge when the user edits individual values utilizing `EditorBottomSheet.java`.
- **`saveWorkout(Context context, String dayKey, String fieldKey, String value)`**: 
  1. Grabs `RoutineRepository.getActiveRoutine()`.
  2. Synthesizes a local change against the target `DayWorkout` variable (e.g. updating the "notes" String).
  3. Executes an immediate save back to the active tracking file.
  4. **Crucial Sync Check**: Invokes `isRoutineSaved()` checking if the active ID also currently exists inside the `saved_routines` directory. If so, it simultaneously commits `RoutineRepository.saveRoutineToLibrary()`.
  5. Forces a UI redraw via `WorkoutsWidgetProvider.Companion.sendRefreshBroadcast()`.

### Routine Views (`RoutinesActivity.java`)
Manages the template Library cards.
- **`loadData()`**: Parses routines inside the `routinesRecycler` (`RecyclerView` mimicking ViewPager mechanics via `PagerSnapHelper`). Reads all dates and runs a mathematical sort loop `Long.compare(r1.timestamp, r2.timestamp)` prior to instantiating the adapter.
- **Action Integrations (`RoutineActionListener`)**:
  - **`onApply(Routine)`**: Promotes a library card to be the immediate active template mapping via `RoutineRepository.saveActiveRoutine()`.
  - **`onEditRoutineMeta()`**: Opens the metadata text injector via `EditorBottomSheet`.
  - **`autoExportRoutine()`**: Creates a timestamped `.json` file (`routine_TITLE_DATE.json`) written directly to the external `Documents/RoutineExports` path utilizing `MediaStore` APIs on Android 10+.
  - **`readRoutineFromFile(Uri)`**: Parses custom JSON intents. Force-assigns a new `UUID.randomUUID()` to imported arrays to strictly avoid UUID overwrite collisions against user templates. 

---

## 2. Strava API & Deep Sync Networking

WorkoutRepo syncs with the Strava API, heavily prioritizing high-speed IO operations while fetching large historical caches. 

### Sync Execution (`StravaRepository.kt`)
- **Pagination**: Accomplished within `StravaService.kt` defining `@Query("page")` and `@Query("per_page")`.
- **Coroutines Concurrency (`fetchAndSaveActivities()`)**: 
  - To accelerate fetching 800+ activities, the engine leverages `async(Dispatchers.IO)` launching four concurrent HTTP connections utilizing the mapped token.
  - Generates `deferredPages` pulling pages 1...4 mapping 200 items each, invoking `awaitAll().flatten()` before depositing into memory.
- **Auto vs. Manual Syncs**:
  - The repository utilizes variables loaded from `WorkoutRepoAppSettings`.
  - **Auto Sync**: Controlled by boolean `EnableAutoRefresh` against the `CacheDurationHours` math threshold. Auto triggers `isDeepSync = false`, pulling only 1 page (`pagesToFetch = 1..1`) to remain highly power-efficient and silent in the background. Does not invoke UI block.
  - **Manual Sync**: Invoked strictly by the user swiping "Pull to refresh" or explicitly loading activities. Bypasses threshold duration, triggers `isDeepSync = true`, fires all 4 backend endpoints (`pagesToFetch = 1..4`), recalculates the massive dictionary array, and commits `saveToDisk()`.

### Visualizing & Filtering (`StravaListManager.kt`)
UI delegation object attached to both the bottom sheet and archive full screen.
- **Generic Engine (`FilterEngine.kt.filterItems`)**: Evaluates search variables via mapped Type lambdas. It cascades against Text Fields first (ignoring regex case), and evaluates Type IDs secondarily.
- **Data Rendering**: `applyFilters()` operates dynamically, rebuilding and passing the new chunk layout instantly into `bindList()` via `StravaAdapter` allowing near 60fps sorting of the data stack.

---

## 3. SharedPreferences, Context & Threading 

### Base Setup (`BaseActivity.java`)
Serves as the central superclass extending `AppCompatActivity` threading rules across `MainActivity`, `RoutinesActivity`, and `StravaArchiveActivity`.
- Ensures mathematical uniformity for all UI padding by dynamically extending layouts behind the device navigation cutouts natively utilizing `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`.
- It executes `applyTheme()` instantly ahead of `super.onCreate()` ensuring no UI 'flash-loading' artifacts occur at runtime instantiation.

### Global Theme Routing
- Governed entirely inside `BaseActivity.applyTheme()`. It extracts `THEME_KEY` mapping from `PREFS_NAME`.
- Assigns internal overrides forcing device layout modifications explicitly utilizing `AppCompatDelegate.setDefaultNightMode()`. 
- Valid states: `MODE_NIGHT_NO` (light), `MODE_NIGHT_YES` (dark), `MODE_NIGHT_FOLLOW_SYSTEM` (auto).
- Execution to change state via `setThemeAndSave(theme)` commits the string to SharedPreferences and invokes `.recreate()`, wiping the task stack view structure and injecting the new core app resource values.

### Shared Preference Lifecycle Integration
- Stored into the native Android filesystem mapped securely under string `WorkoutRepoAppSettings`. 
- **Active Threading (`MainActivity.java`)**: Instead of re-reading memory sequentially, the `MainActivity` maps a dedicated pointer memory event `SharedPreferences.OnSharedPreferenceChangeListener prefListener`. When settings (like user preference `"EnableStravaFeature"`) manipulate from outside activities (e.g. guide pages), this background event directly intercepts the configuration change flag, instantly transitioning the physical `stravaAccess` UI element visibility to `View.GONE` / `View.VISIBLE`. 

---

## 4. UI Control Flow & Components

### Main Home UI (`MainActivity.java`)
- **Long Clicks vs. Short Clicks (`setupButtons()`)**:
    - Focuses entirely on the `stravaAccess` button binding dual actions dynamically depending on `StravaButtonLongClickAction` boolean configurations.
    - If `TRUE`: Standard user Short Click directly launches the full-screen `StravaArchiveActivity`. The alternate Long Press launches the fast `StravaBottomSheet`, explicitly pulling subsets by reading `dayOfWeek`.
    - If `FALSE`: The logic exactly inverts, executing the sheet normally and pushing the deep historical view exclusively towards explicit long presses. 
- **MotionLayout View Logic**:
    - Deployed surrounding the `R.id.guide_btn` UI element to avoid using basic standard Fragment animations. 
    - Activating the guide button prompts XML instructions within `motionLayout.transitionToEnd()`, gracefully executing scaling modifications to layer layouts dynamically bringing in the instruction view element stack overtop the page structure. 
    - `OnBackPressedCallback` physically intercepts internal hardware inputs. Instead of closing the entire application container, the function overrides standard `finish()` explicitly passing `motionLayout.transitionToStart()` sliding the guide layers out elegantly if the view status boolean evaluates to `R.id.end_visible`.

### Text Typography System (`TextFormatUtils.java`)
WorkoutRepo parses markdown strings natively bypassing browser web engines.
- **`formatNotesForDisplay(String raw)`**: Computes and dissects regex lines `^\\s*(\\d+)[.)]\\s*` injecting standard integers. Computes trailing spaces defining hierarchical loops returning depth integer `MAIN_BULLET_INDENT` or `SUB_BULLET_INDENT` modifying the X coordinate pixel render array margin layout.
- **`TextBulletSpan` (Inner Class)**: Injects `LeadingMarginSpan`. This class overrides system layout rendering strings physically forcing `Canvas.drawText()` execution drawing thick Unicode glyph shapes (`\u2022`, `\u09F9`) precisely offset independent of text wrapping logic to generate completely flawless document alignment geometry.
