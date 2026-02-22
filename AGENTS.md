# voicejournal Project
voicejournal is an Android app for writing your journal/diary.
Each entry (app\src\main\java\com\example\voicejournal\data\JournalEntry) can be assigned to one or more
categories (app\src\main\java\com\example\voicejournal\data\Category.kt).
The main view is a large list of entries (app\src\main\java\com\example\voicejournal\ui\screens\HomeScreen.kt).
It is managed by the \app\src\main\java\com\example\voicejournal\MainActivity.
The list has infinite scrolling with bi-directional paging using the
Paging3 library.
There is also a FastScroller (app\src\main\java\com\example\voicejournal\ui\components\FastScroller.kt).
The entries are grouped by day.

## Core Features

### Voice Dictation & Smart Categorization
Texts dictated via the `SpeechRecognitionManager` are saved as new `JournalEntry` objects. The app uses the **first word** of the recognized text as a smart category trigger:
- If this word matches a category name (`Category.category`) or one of its comma-separated aliases (`Category.aliases`, case-insensitive), the new entry is automatically assigned to that specific category instead of the active one.
- The trigger word is automatically removed from the final entry content.
- If no match is found, the entry is assigned to the currently selected category.

### Persistent Voice Recording Notification
To allow quick access to voice dictation, the app provides a persistent notification acting as a shortcut to trigger speech recognition. 
- The notification is marked as `ongoing` and `autoCancel(false)`, keeping it permanently in the notification drawer while the app is active, without being swipeable or dismissed upon click.
- Clicking the notification uses `Intent.FLAG_ACTIVITY_SINGLE_TOP` to cleanly reuse the existing `MainActivity` via `onNewIntent`.

## Project Context: Frameworks and Libraries

### Core Architecture
- **Language:** **Kotlin** (idiomatic, null-safe, coroutine-based).
- **Architecture Pattern:** **MVVM** (Model-View-ViewModel) in combination with the Android Jetpack components.
- **Asynchronous Programming:** **Kotlin Coroutines** and **Flow** for managing background threads and reactive data streams.

### User Interface (UI)
- **UI Toolkit:** **Jetpack Compose** for building the entire user interface in a declarative way.
- **Design System:** **Material 3** to use modern and consistent UI components.
- **Navigation:** **Compose Navigation** for managing navigation between the different screens (composable functions).

### Data Persistence
- **Local Database:** **Room Persistence Library** for storing `JournalEntry` and `Category` objects in a local SQLite database.
- **Zero Data Loss Policy:** Version Increment, Mandatory Migration Classes. 
  - Uses **Soft Delete** for Journal Entries (`deletedAt: Long?`). Hard deletions are avoided.
  - A virtual, hardcoded read-only category ("Gelöscht", ID `-1`) acts as a recycle bin showing all soft-deleted entries.

### Dependency Injection (DI)
- **DI Framework:** **Hilt** to simplify dependency injection throughout the project.

### Network & APIs
- **HTTP Client:** **Retrofit** and **OkHttp** for communication with external APIs (e.g., for AI services).
- **JSON Serialization:** **kotlinx.serialization** for converting JSON responses into Kotlin data classes.
