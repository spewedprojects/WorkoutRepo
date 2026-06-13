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

---

## 5. Comprehensive Codebase File Map & Class Analysis

This section maps out every source code file in the `WorkoutRepo` project, explaining its exact role, state representation, primary methods, and call relationships.

### 5.1 Core Activities & Controller Layer

#### 5.1.1 [BaseActivity.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/BaseActivity.java)
- **Role**: Base activity class extending `AppCompatActivity`. Centralizes theme application and system insets layout.
- **Key Fields**:
  - `PREFS_NAME` (`"WorkoutRepoAppSettings"`): The name of the Shared Preferences file used globally.
  - `THEME_KEY` (`"SelectedTheme"`): Key representing the chosen night mode.
- **Key Methods**:
  - `onCreate(Bundle)`: Enables edge-to-edge UI configuration, applies themes via `applyTheme()`, and sets layout parameters.
  - `applySystemBarInsets(int)`: Applies padding offsets to a specific view id based on status/navigation system bars.
  - `applyTheme()`: Reads `THEME_KEY` from preferences and calls `AppCompatDelegate.setDefaultNightMode()` dynamically.
  - `setThemeAndSave(String)`: Saves selected theme mode to SharedPreferences, calls `applyTheme()`, and triggers `.recreate()` to force resource reload.

#### 5.1.2 [MainActivity.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/MainActivity.java)
- **Role**: The main launcher activity of the application. Thread-coordinates the main week layout, the Settings carousel, widget callbacks, and theme selection.
- **Key Fields**:
  - `motionLayout`: Constrains the main layout and executes animations for the guide overlay drawer.
  - `prefListener`: Keeps a strong reference to the preference listener.
  - `exportLauncher` & `importLauncher`: Registered for activity results to handle importing/exporting Strava archives.
- **Key Methods**:
  - `onCreate(Bundle)`: Sets up system bar insets, configures buttons, snaps settings scrolls, registers launchers, and loads widget intents.
  - `setupButtons()`: Configures single and long clicks on the Strava icon (`stravaAccess`). Visibly configures `stravaAccess` dynamically based on preferences.
  - `setupThemeButtons()`: Configures click listeners on light, dark, and auto buttons.
  - `setupWeekPager()`: Sets up virtual infinite `ViewPager2` for day swiping, starting in the middle.
  - `onResume()`: Re-registers the listener and refreshes the adapter.
  - `setupOnBackPressed()`: Connects `OnBackPressedCallback` to either collapse the settings menu drawer (`motionLayout.transitionToStart()`) or double-press to exit.

#### 5.1.3 [RoutinesActivity.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/RoutinesActivity.java)
- **Role**: Activity that displays routine template lists, manages routine editing, deleting, importing, and exporting.
- **Key Fields**:
  - `routinesRecycler`: `RecyclerView` styled horizontally with `PagerSnapHelper` to browse routines.
  - `adapter`: Reference to `RoutinesPagerAdapter`.
- **Key Methods**:
  - `onCreate(Bundle)`: Initializes `routinesRecycler` layout, snap configurations, back press handlers, and state restores.
  - `loadData()`: Reads active routine and all library routines, instantiates `RoutinesPagerAdapter`, and scrolls to active routine position.
  - `RoutineActionListener`: Interface callbacks that link UI inputs to `RoutineRepository` operations (`onApply`, `onExport`, `onDelete`, `onAddBlank`, etc.).
  - `autoExportRoutine(Context, Routine)`: Writes routine templates as JSON files to `Documents/RoutineExports` using `MediaStore` on Android 10+ or direct IO on older Android SDKs.
  - `readRoutineFromFile(Uri)`: Imports a routine JSON file, updates its UUID to avoid collisions, maps its suffix to handle duplicate titles, and persists it.

#### 5.1.4 [StravaArchiveActivity.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/StravaArchiveActivity.kt)
- **Role**: Full screen view of Strava activity history.
- **Key Methods**:
  - `onCreate(Bundle)`: Enlists `StravaListManager` with custom fetching configurations to pull the full list via `StravaRepository.getAllActivities()`.

#### 5.1.5 [StravaBottomSheet.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/StravaBottomSheet.kt)
- **Role**: Bottom sheet display showing Strava activities filtered by a specific day of the week.
- **Key Methods**:
  - `onViewCreated(View, Bundle)`: Instantiates `StravaListManager` to query specific days via `StravaRepository.getActivitiesForDay()`. Sets custom bottom sheet background transparency and removes clipping.

#### 5.1.6 [EditorBottomSheet.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/EditorBottomSheet.java)
- **Role**: Configures the textual content editor. Contains specialized dismiss overrides to prevent unsaved data loss.
- **Key Methods**:
  - `onCreateView()`: Configures buttons and registers listeners.
  - `attemptExit()`: Compares current text to original. If modified, launches `ConfirmationDialogHelper`.
  - `onDismiss(DialogInterface)`: Re-instantiates bottom sheet dialog with cached unsaved text if user accidentally swiped to close.
  - `clearFocusOnKeyboardHide(EditText, View)`: Registers window insets callback to clear focus when keyboard is hidden.

---

### 5.2 Adapters & Pagers Layer

#### 5.2.1 [WeekPagerAdapter.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/adapters/WeekPagerAdapter.java)
- **Role**: Pager adapter backing the virtual infinite week loop on the main layout.
- **Key Fields**:
  - `VIRTUAL_COUNT`: Set to `Integer.MAX_VALUE` to support infinite swiping.
  - `weekDays`, `workoutTypes`, `majors`, `minors`: Array of default fallback items.
- **Key Methods**:
  - `onBindViewHolder(WeekViewHolder, int)`: Resolves day of the week via `position % 7`, queries values from `WorkoutStorage`, formats texts, applies notes state, and binds long-click actions.
  - `setupEditor(...)`: Attaches long-click listeners that resolve raw data values and open `EditorBottomSheet`.

#### 5.2.2 [RoutinesPagerAdapter.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/adapters/RoutinesPagerAdapter.java)
- **Role**: Manages routine library views and provides an "Add New" template slot at the end.
- **Key Fields**:
  - `routines`: List of saved routine templates.
  - `editingRoutineId`: Stores ID of routine currently inside editing state.
- **Key Methods**:
  - `getItemViewType(int)`: Returns `TYPE_ADD_NEW` if `position == routines.size()`, otherwise `TYPE_ROUTINE`.
  - `onCreateViewHolder(...)`: Sets layout params width to `screenWidth * 0.65` for the Add card to peek, and full width for normal routines.
  - `RoutineViewHolder.bind(...)`: Binds details, configures click listeners for Edit/Delete/Apply actions, and shows/hides options depending on active status.

#### 5.2.3 [RoutineDayAdapter.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/adapters/RoutineDayAdapter.java)
- **Role**: Recycled list binding days inside individual routine cards in `RoutinesActivity`.
- **Key Methods**:
  - `onBindViewHolder(DayViewHolder, int)`: Populates day titles, labels, workout types, major, minor, and notes details. Attaches click listener field triggers if edit mode evaluates to true.

#### 5.2.4 [StarvaAdapter.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/adapters/StarvaAdapter.kt)
- **Role**: Handles formatting and item binding for Strava activities list.
- **Key Fields**:
  - `loadingActivityId`: Holds ID of activity that is currently fetching details.
- **Key Methods**:
  - `updateList(List<StravaActivity>)`: Leverages `DiffUtil` calculations to animate additions/removals smoothly.
  - `onBindViewHolder(ViewHolder, int)`: Binds click callbacks. Formats distance to kilometers, moving time to hours/minutes, average power, heart rate, and elevation. Computes dates and dynamically swaps workout icons.

#### 5.2.5 [GuideAdapter.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/adapters/GuideAdapter.java)
- **Role**: Adapts the Settings & Guide carousel inside `MainActivity`.
- **Key Methods**:
  - `onBindViewHolder(ViewHolder, int)`: Wire switches, check boxes, text inputs, and buttons.
  - `showKeywordDialog(SettingsViewHolder, SharedPreferences)`: Instantiates keyword validation dialog (`"Hobdy"`) before enabling the Strava toggle switch.

---

### 5.3 Storage & Data Layer

#### 5.3.1 [RoutineRepository.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/data/RoutineRepository.java)
- **Role**: The core single source of truth for saving/loading routines via local JSON documents.
- **Key Methods**:
  - `getActiveRoutine(Context)`: Reads active routine from `active_routine.json`. If missing, runs `migrateLegacyData()`.
  - `migrateLegacyData(Context)`: Pulls legacy values stored in `WorkoutPrefs` and transforms them into standard `Routine` format.
  - `getAllSavedRoutines(Context)`: Parses files under `saved_routines/` and sorts them chronologically by creation timestamp.
  - `saveRoutineToLibrary(Context, Routine)`: Writes routine template to disk with prefix `routine_[ID].json`.

#### 5.3.2 [WorkoutStorage.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/storage/WorkoutStorage.java)
- **Role**: Transaction layer between UI editor interactions and data persistence.
- **Key Methods**:
  - `saveWorkout(Context, String, String, String)`: Modifies active routine buffer, calls `saveActiveRoutine()`, syncs changes to libraries if routine exists, and triggers widget updates.

#### 5.3.3 [Routine.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/model/Routine.java)
- **Role**: Plain data class annotated with `@Keep` representing routine structures. Holds `id`, `title`, `notes`, `timestamp`, and days list.

#### 5.3.4 [DayWorkout.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/routine/model/DayWorkout.java)
- **Role**: Data class representing individual days. Maps `dayName`, `workoutType`, `majorWorkouts`, `minorWorkouts`, `majorLabel`, `minorLabel`, and `notes`.

---

### 5.4 Strava Networking, API & Logic Layer

#### 5.4.1 [StravaModels.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/data/StravaModels.kt)
- **Role**: Kotlin representation of Strava payloads: `StravaActivity` (with elevation, heart rate, power, workout type indices, descriptions), `TokenResponse` (OAuth metadata), and `StravaAthlete` (profile attributes).

#### 5.4.2 [StravaService.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/network/StravaService.kt)
- **Role**: Retrofit REST endpoints interface mapping token refreshes, list queries, details, and athlete profile requests.

#### 5.4.3 [StravaRepository.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/repository/StravaRepository.kt)
- **Role**: Centralized coordinate engine for the Strava API and local cache persistence.
- **Key Methods**:
  - `getActivitiesForDay(Context, String, Boolean)`: Performs smart cache/network checks and filters by day name.
  - `fetchAndSaveActivities(Context, Boolean)`: Uses Kotlin Coroutines `async` to pull multiple API pages in parallel (pages 1..4 for manual refresh, page 1 for auto refresh), merges them with old items in memory, sorts, and serializes to `strava_activities_cache.json`.
  - `mergeActivities(List, List)`: Implements conflict resolution by preferring items containing descriptions and comparing `lastModifiedLocal` timestamps.
  - `importArchive(Context, String)` / `getExportData(Context)`: Encapsulates serialization processes for data transfers.

#### 5.4.4 [TokenManager.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/repository/TokenManager.kt)
- **Role**: Stores credentials (API client secrets, client ID, access keyword, temporary tokens).

#### 5.4.5 [FilterEngine.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/utils/FilterEngine.kt)
- **Role**: Generic lambda-based search filter that returns filtered subsets based on query match lists and types.

#### 5.4.6 [StravaListManager.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/utils/StravaListManager.kt)
- **Role**: Handles UI orchestration for Strava archives.
- **Key Methods**:
  - `setup()`: Configures query listeners, text watchers, custom Glide loads, filter buttons, and click handlers.
  - `showDatePicker()`: Builds `MaterialDatePicker.Builder.dateRangePicker()` with constrained UTC bounds.
  - `showDurationDialog(LocalDate)`: Opens multi-option dialogs mapping single days to custom period structures (week, month, year).
  - `onActivityClick(Long)`: Lazily fetches detailed activity statistics on user selection.
  - `applyFilters(...)`: Combines all state queries to update lists and stats blocks.

#### 5.4.7 [StravaStatsManager.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/utils/StravaStatsManager.kt)
- **Role**: Handles UI parsing of summary statistics.
- **Key Methods**:
  - `updateStats(List, String, LocalDate?, LocalDate?)`: Calculates unique days active, total durations (hours & minutes), distances in kilometers, and consistency scores.

#### 5.4.8 [StravaArchiveManager.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/strava/utils/StravaArchiveManager.java)
- **Role**: Directs JSON archive data transfers in worker threads.
- **Key Methods**:
  - `exportData(Context, Uri)`: Resolves pretty-printed cache arrays and writes them to targeted URIs.
  - `importData(Context, Uri)`: Reads file inputs in background threads and passes them to `StravaRepository.importArchive()` for merging.

---

### 5.5 Helper & Typography Layer

#### 5.5.1 [TextFormatUtils.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/utils/TextFormatUtils.java)
- **Role**: Dissects markdown strings to render formatted bullet items and styles.
- **Key Methods**:
  - `formatBulletsForDisplay(String)`: Strip markers, applies `TextBulletSpan`.
  - `formatBulletsForWidget(String)`: Formats elements with `LeadingMarginSpan.Standard` compatible with RemoteViews.
  - `formatNotesForDisplay(String)`: Matches number formats and dashed sub-bullets, yielding indents.
  - `applyBoldFormatting(SpannableStringBuilder)`: Resolves `**` tags and sets bold style spans.
  - `TextBulletSpan` (Inner Class): Injects margins and overrides `drawLeadingMargin` to customize bullet draw dimensions.

#### 5.5.2 [ExpandableNoteHelper.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/utils/ExpandableNoteHelper.java)
- **Role**: Animates note sections in the UI.
- **Key Methods**:
  - `setupNoteState(...)`: Measures text count. Collapses content using `TextFormatUtils.getCollapsedNotes` if line lengths exceed threshold metrics.
  - `toggleNote(...)`: Animates layout transition sizes with `AutoTransition` and rotates toggle indicators.

#### 5.5.3 [ConfirmationDialogHelper.java](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/utils/ConfirmationDialogHelper.java)
- **Role**: Triggers custom validation dialog views.

---

### 5.6 App Widget Core Components

#### 5.6.1 [WorkoutsWidgetProvider.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/widgets/WorkoutsWidgetProvider.kt)
- **Role**: Broadcast receiver managing widget states.
- **Key Methods**:
  - `onReceive(Context, Intent)`: Captures calendar date and timezone changes, calling `sendRefreshBroadcast()`.
  - `updateAppWidget(...)`: Extracts active routine info, determines day indices, populates text fields, binds click template intents, and registers adapters.
  - `sendRefreshBroadcast(Context)`: Calls `notifyAppWidgetViewDataChanged` and updates list layouts.

#### 5.6.2 [WorkoutsRemoteViewsService.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/widgets/WorkoutsRemoteViewsService.kt)
- **Role**: Binds `WorkoutsRemoteViewsFactory` views.

#### 5.6.3 [WorkoutsRemoteViewsFactory.kt](file:///c:/Users/rkhar/Documents/ANDROID%20APPS/WorkoutRepo/app/src/main/java/com/gratus/workoutrepo/widgets/WorkoutsRemoteViewsFactory.kt)
- **Role**: Adapter factory that binds day elements inside widget collection lists.
- **Key Methods**:
  - `onDataSetChanged()`: Pulls active day workouts and parses major/minor elements.
  - `getViewAt(int)`: Inflates RemoteViews using `formatBulletsForWidget` and registers fill-in clicks.

