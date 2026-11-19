# voicejournal Project

voicejournal is an Android app for writing a personal voice-enabled journal, task list, and appointment diary (`minSdk = 34`, `targetSdk = 36`, `compileSdk = 36`).
Each entry (`app/src/main/java/com/example/voicejournal/data/JournalEntry.kt`) has a `UUID` primary key, start/optional stop timestamps, checkable state, image flag, and per-entry IANA timezone (`zoneId`), and can be assigned to one or more categories (`app/src/main/java/com/example/voicejournal/data/Category.kt`, supporting custom colors, sort order, voice aliases, checkable To-Do mode, untruncated double-tap line-deletion shopping-list mode via `deleteLineOnDoubleTap`, and optional date headers).
The main view (`app/src/main/java/com/example/voicejournal/ui/screens/HomeScreen.kt`), hosted by `MainActivity` (with `MainTopAppBar`, `MainFabMenu`, and `AppDrawer`), displays entries grouped by day with Paging 3 (`androidx.paging`), a custom `FastScroller` (`app/src/main/java/com/example/voicejournal/ui/components/FastScroller.kt`), and automatic category switching + scroll-to-entry (`JournalRepository.createdEntryEvents`) whenever a new entry is created.

## Core Features

### Voice Dictation & Smart Categorization
Texts dictated via `SpeechRecognitionManager` (supporting Android `SpeechRecognizer` or Google Cloud Speech-to-Text REST API via type-safe `SpeechService` enum) are saved as `JournalEntry` objects or appended to the selected entry.
When creating a new entry via voice, the **first word** acts as a smart category trigger (`MainViewModel.parseRecognizedTextForCategory`):
- If the first word matches a real category name (`Category.category`) or one of its comma-separated aliases (`Category.aliases`, case-insensitive), the entry is assigned to that category and the trigger word is stripped from the entry text.
- Virtual recycle-bin categories (`"Gelöscht"`, ID `-1`) are excluded from category matching and assignment.
- If no match is found, the entry is assigned to the currently selected category.

### Persistent Voice Recording Notification
A persistent notification (`NotificationHelper`) provides a quick shortcut to trigger speech recognition (`ongoing`, `autoCancel(false)`), reusing `MainActivity` via `Intent.FLAG_ACTIVITY_SINGLE_TOP` and `onNewIntent`.

### Interactive Calendar (Day, 3-Day, Week & Month Views)
`CalendarActivity` (`CalendarViewModel`) provides an interactive schedule across four `CalendarViewMode` modes (`DAY`, `THREE_DAY`, `WEEK`, `MONTH`) backed by a 3-period preload window (`TimeZoneUtils.utcQueryBoundsForLocalDates`) for seamless `HorizontalPager` swiping:
- **Timeline Schedule (`WeekView`)**: Renders 1-, 3-, and 7-day views (`app/src/main/java/com/example/voicejournal/ui/components/WeekView.kt`) with start/stop durations, multi-day splitting, automatic side-by-side overlap columns, and auto-scrolling to the earliest appointment on the focused day.
- **Monthly Grid (`MonthView`)**: Horizontally paged month overview (`app/src/main/java/com/example/voicejournal/ui/components/MonthView.kt`) displaying color-coded appointment chips per day.
- **Day Entries Preview Overlay (`DayEntriesPreviewOverlay`)**: Long-pressing a day opens an animated, cell-anchored preview card (`app/src/main/java/com/example/voicejournal/ui/components/DayEntriesPreviewOverlay.kt`) with dynamic line clamping per entry count and direct navigation to the day view or `EditEntryActivity`.

### Search & External Shortcuts
- **Full-Text Search**: `SearchActivity` (`SearchScreen`, `SearchViewModel`) searches active entries across categories and opens either the entry editor or jumps to the entry's date in `MainActivity`.
- **Google Photos Shortcut**: Long-pressing a date header or tapping an entry's image indicator opens Google Photos at that timestamp.

### Multi-Select, Clipboard & Sharing / Import-Export
- **Multi-Select, Batch Actions & Clipboard**: Long-pressing entries opens a contextual action bar (`MainTopAppBar`) supporting plain-text sharing, `.vj` export via `ShareExportHelper` (using an isolated `FileProvider` cache subdirectory `cacheDir/shared_exports/`), batch timezone updates (`TimeZonePickerDialog`), soft-delete with single-tap Undo, recycle-bin restore/permanent delete, and one-tap clipboard export.
- **Import (`ImportActivity`)**: Accepts `ACTION_VIEW`/`ACTION_SEND` `.vj`/JSON files, validates stream size (max 10 MB via `JournalBackupManager.readBoundedUtf8`) and payload schema in `ImportViewModel`, and imports entries transactionally without overwriting existing category assignments.
- **Full Backup (`JournalBackupManager`)**: Supports V3 JSON export (`"wt"` write+truncate) and transactional V1/V2/V3/legacy text import (max 50 MB) in `JournalBackupManager` (`app/src/main/java/com/example/voicejournal/data/JournalBackupManager.kt`), delegated from `JournalRepository`.

### Resilient Entry, Category & Settings Management (MVVM & State Management)
`EditEntryActivity` (`EditEntryViewModel`), `EditCategoryActivity` (`EditCategoryViewModel`), `CategoryManagerActivity` (`CategoryManagerViewModel`), and `SettingsActivity` (`SettingsViewModel`) manage state in `StateFlow`, preserve cursor/selection state via `TextFieldValue`, survive configuration changes, and await `NonCancellable` database persistence before navigating back.
User preferences, GPS worker scheduling, and encrypted Google Cloud API key access are centralized in `SettingsRepository` (`app/src/main/java/com/example/voicejournal/data/SettingsRepository.kt`).

### Background GPS Track Logging
Optional periodic GPS track logging via `WorkManager` (`LocationWorker`, minimum 15-minute `PeriodicWorkRequest` interval, restored after reboot by `BootReceiver`) using `FusedLocationProviderClient` and Google Maps route preview (with automatic waypoint downsampling to respect Google Maps URL limits).

## Project Context: Frameworks and Libraries

### Core Architecture & Dependency Injection
- **Build & Language:** **AGP `9.5.x`**, **Kotlin `2.2.x`** (Coroutines, `StateFlow`/`Flow`), **KSP** (`app/schemas/` Room schema export).
- **Architecture Pattern:** **MVVM** with Android Jetpack ViewModels (`MainViewModel`, `SettingsViewModel`, `CategoryManagerViewModel`, `EditEntryViewModel`, `EditCategoryViewModel`, `ImportViewModel`, `SearchViewModel`, `CalendarViewModel`).
- **Dependency Injection:** **Manual DI** via the singleton service locator `Injector` (`app/src/main/java/com/example/voicejournal/di/Injector.kt`) and typed `ViewModelProvider.Factory` classes (Hilt is **not** used).

### User Interface (UI)
- **UI Toolkit:** **Jetpack Compose** with **Material 3** (`1.4.0`) and **Paging 3 Compose**.
- **Formatting, Timezones & Localization:** Shared thread-safe `DateTimeFormatter` instances in `DateFormatters` (`app/src/main/java/com/example/voicejournal/util/DateFormatters.kt`), timezone resolution and offset-aware formatting in `TimeZoneUtils` (`app/src/main/java/com/example/voicejournal/util/TimeZoneUtils.kt`), and localized string resources in `res/values/strings.xml` and `res/values-de/strings.xml`.

### Data Persistence & Security
- **Local Database:** **Room** (`2.8.x`, `AppDatabase`, schema version `19`, schemas exported to `app/schemas/`).
- **Zero Data Loss Policy:** Explicit `Migration` chain (`MIGRATION_1_2` through `MIGRATION_18_19`) — `fallbackToDestructiveMigration` is strictly forbidden.
  - Uses **Soft Delete** (`deletedAt: Long?`). Active views filter `deletedAt IS NULL`; the virtual read-only category `"Gelöscht"` (ID `-1`) serves as the recycle bin, where entries can be restored or permanently deleted after confirmation.
  - Stores per-entry IANA timezone ID (`zoneId: String?` on `JournalEntry`, falling back to `ZoneId.systemDefault()` when `null`) so entries retain their authentic creation local date/time across travel timezones while keeping chronological UTC ordering (`start_datetime`).
- **Credential Storage:** `SecureApiKeyStore` encrypts the Google Cloud Speech API key using Android Keystore `AES/GCM/NoPadding` (256-bit) inside excluded `SharedPreferences` (`voice_journal_secure_prefs`), excluded from cloud backup and device transfer in `backup_rules.xml` and `data_extraction_rules.xml`.

### Networking & Local REST API Server
- **Embedded REST API Server:** **Ktor Server (`Netty`, port `8080`)** in `WebServer` (`app/src/main/java/com/example/voicejournal/WebServer.kt`), hosted by `WebServerService` (`specialUse` Foreground Service), exposing a CORS-enabled JSON REST API (`/categories`, `/journalentries`, `/journalentries/category/{id}`, `/journalentries/{id}`) with soft-delete and live timeline scroll notification support.
- **External Speech API:** Direct HTTPS requests via `HttpURLConnection` (with explicit connect/read timeouts) to `speech.googleapis.com` using **kotlinx.serialization** for JSON handling (Retrofit/OkHttp are **not** used).

### Reference Documentation & Tests
- **Project Reference Docs:**
  - `DATABASE.html`: Interactive Room v18 entity-relationship and schema diagram.
  - `GLOSSARY.html`: UI & domain glossary defining canonical German/English component terms for prompts and UI consistency.
- **Unit Tests (`app/src/test/`):** JUnit 4 suites covering voice category parsing & target category resolution (`ExampleUnitTest.kt`), travel timezone utilities (`TimeZoneSupportTest.kt`), shopping-list double-tap line deletion & LIFO undo (`LineDeleteAndUndoTest.kt`), week timeline overlap/scroll behavior (`WeekViewBugFixTest.kt`), and month grid / day preview overlay logic (`MonthViewTest.kt`).
