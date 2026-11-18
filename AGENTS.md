# voicejournal Project

voicejournal is an Android app for writing a personal voice-enabled journal/diary (`minSdk = 34`, `targetSdk = 36`).
Each entry (`app/src/main/java/com/example/voicejournal/data/JournalEntry.kt`) can be assigned to one or more categories (`app/src/main/java/com/example/voicejournal/data/Category.kt`).
The main view (`app/src/main/java/com/example/voicejournal/ui/screens/HomeScreen.kt`), hosted by `MainActivity`, displays entries grouped by day with Paging 3 (`androidx.paging`) and a custom `FastScroller` (`app/src/main/java/com/example/voicejournal/ui/components/FastScroller.kt`).

## Core Features

### Voice Dictation & Smart Categorization
Texts dictated via `SpeechRecognitionManager` (supporting Android `SpeechRecognizer` or Google Cloud Speech-to-Text REST API) are saved as `JournalEntry` objects or appended to the selected entry.
When creating a new entry via voice, the **first word** acts as a smart category trigger:
- If the first word matches a real category name (`Category.category`) or one of its comma-separated aliases (`Category.aliases`, case-insensitive), the entry is assigned to that category and the trigger word is stripped from the entry text.
- Virtual recycle-bin categories (`"Gelöscht"`, ID `-1`) are excluded from category matching and assignment.
- If no match is found, the entry is assigned to the currently selected category.

### Persistent Voice Recording Notification
A persistent notification (`NotificationHelper`) provides a quick shortcut to trigger speech recognition (`ongoing`, `autoCancel(false)`), reusing `MainActivity` via `Intent.FLAG_ACTIVITY_SINGLE_TOP` and `onNewIntent`.

### Multi-Select, Clipboard & Sharing / Import-Export
- **Multi-Select & Sharing**: Long-pressing entries opens a contextual action bar supporting plain-text sharing and `.vj` export (via an isolated `FileProvider` cache subdirectory `cacheDir/shared_exports/`).
- **Import (`ImportActivity`)**: Accepts `ACTION_VIEW`/`ACTION_SEND` `.vj`/JSON files, validates stream size (max 10 MB) and payload schema in `ImportViewModel`, and imports entries transactionally without overwriting existing category assignments.
- **Full Backup**: Supports V3 JSON export (`"wt"` write+truncate) and transactional V1/V2/V3/legacy text import in `JournalRepository`.

### Resilient Entry & Category Editing (MVVM & State Management)
`EditEntryActivity` (`EditEntryViewModel`) and `EditCategoryActivity` (`EditCategoryViewModel`) manage all editable state in `StateFlow`, preserve cursor/selection state via `TextFieldValue`, survive configuration changes, and await `NonCancellable` database persistence before navigating back.

### Background GPS Track Logging
Optional periodic GPS track logging via `WorkManager` (`LocationWorker`, minimum 15-minute `PeriodicWorkRequest` interval) using `FusedLocationProviderClient` and Google Maps route preview (with automatic waypoint downsampling to respect Google Maps URL limits).

## Project Context: Frameworks and Libraries

### Core Architecture & Dependency Injection
- **Language:** **Kotlin** (`2.2.x`, Coroutines, `StateFlow`/`Flow`).
- **Architecture Pattern:** **MVVM** with Android Jetpack ViewModels (`MainViewModel`, `EditEntryViewModel`, `EditCategoryViewModel`, `ImportViewModel`, `SearchViewModel`, `CalendarViewModel`).
- **Dependency Injection:** **Manual DI** via the singleton service locator `Injector` (`app/src/main/java/com/example/voicejournal/di/Injector.kt`) and typed `ViewModelProvider.Factory` classes (Hilt is **not** used).

### User Interface (UI)
- **UI Toolkit:** **Jetpack Compose** with **Material 3** (`1.4.0`), **Paging 3 Compose**, and **Compose Navigation**.

### Data Persistence & Security
- **Local Database:** **Room** (`AppDatabase`, schema version `18`, schemas exported to `app/schemas/`).
- **Zero Data Loss Policy:** Explicit `Migration` chain (`MIGRATION_1_2` through `MIGRATION_17_18`) — `fallbackToDestructiveMigration` is strictly forbidden.
  - Uses **Soft Delete** (`deletedAt: Long?`). Active views filter `deletedAt IS NULL`; the virtual read-only category `"Gelöscht"` (ID `-1`) serves as the recycle bin, where entries can be restored or permanently deleted after confirmation.
  - Stores per-entry IANA timezone ID (`zoneId: String?` on `JournalEntry`, falling back to `ZoneId.systemDefault()` when `null`) so entries retain their authentic creation local date/time across travel timezones while keeping chronological UTC ordering (`start_datetime`).
- **Credential Storage:** `SecureApiKeyStore` encrypts the Google Cloud Speech API key using Android Keystore `AES/GCM/NoPadding` (256-bit) inside excluded `SharedPreferences` (`voice_journal_secure_prefs`), excluded from cloud backup and device transfer in `backup_rules.xml` and `data_extraction_rules.xml`.

### Networking & Local Web Server
- **Embedded Web Server:** **Ktor Server (`Netty`)** hosted in `WebServerService` (`specialUse` Foreground Service) serving a local web interface for browsing/editing journal entries on the local network.
- **External Speech API:** Direct HTTPS requests via `HttpURLConnection` (with explicit connect/read timeouts) to `speech.googleapis.com` using **kotlinx.serialization** for JSON handling (Retrofit/OkHttp are **not** used).
