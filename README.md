# Voice Journal

Voice Journal is a comprehensive Android application designed to capture your thoughts, tasks, and memories effortlessly using voice commands. It combines powerful speech-to-text capabilities with location tracking and smart organization.

## Features

### 🎙️ Advanced Speech Recognition
*   **Dual Engine Support:** Choose between:
    *   **Android Built-in SpeechRecognizer:** For offline capabilities (device dependent) and zero configuration.
    *   **Google Cloud Speech-to-Text API:** For higher accuracy and advanced recognition models (requires API Key).
*   **Automatic Categorization:** Start your sentence with a keyword (e.g., "Todo", "Kaufen", "Journal") to automatically file the entry into the correct category.

### 📍 GPS & Location Tracking
*   **Background Tracking:** Optional GPS tracking records your path throughout the day.
*   **Map Visualization:** View your route for specific days directly on Google Maps.
*   **Configurable Interval:** Adjust how often the app updates your location to balance accuracy and battery life via Settings.

### 📝 Entry Management
*   **Smart Organization:** Entries are grouped by date and filtered by category.
*   **Full Control:** Edit entries to fix typos or delete unwanted recordings.
*   **Undo Functionality:** Accidentally deleted an entry? Use the "Undo" button to restore it immediately.
*   **Google Photos Integration:** Quickly jump to your Google Photos timeline for the specific date of an entry to relive the visual memories.

### ⚙️ Customization & Data
*   **Settings Menu:** Customize the number of days shown, GPS settings, and API configuration.
*   **Data Portability:** Export your entire journal to JSON for backup and Import it back whenever needed.
*   **Category Management:** Add, remove, or reorder categories to fit your workflow.

## Getting Started

### Prerequisites
The app requires the following permissions to function fully:
*   **Microphone:** For recording audio.
*   **Location (Fine/Coarse & Background):** For GPS tracking features.
*   **Notifications:** To allow foreground services for stable recording and tracking.
*   **Internet:** Required if using the Google Cloud Speech API.

### Setting up Google Cloud Speech-to-Text (Optional)
To use the high-accuracy Google Cloud engine:
1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
2.  Create a new project or select an existing one.
3.  **Enable Billing:** Ensure a billing account is linked to your project (required for API usage).
4.  **Enable API:** Go to "APIs & Services" > "Library" and search for **"Cloud Speech-to-Text API"**, then enable it.
5.  **Create Credentials:**
    *   Go to "APIs & Services" > "Credentials".
    *   Click "Create Credentials" > "API Key".
    *   (Optional but recommended) Restrict the key to "Android apps" (using your package name `com.example.voicejournal` and SHA-1 fingerprint) and restrict the API usage to "Cloud Speech-to-Text API".
6.  **In the App:** Open the Settings menu, select "Google Cloud Speech API", and paste your API Key.

## Usage

1.  **Launch the App:** Grant necessary permissions upon first launch.
2.  **Record:** Tap the **+** FAB (Floating Action Button).
3.  **Speak:** Say your command.
    *   *Example:* "Todo Milch kaufen" -> Creates an entry in the "Todo" category with content "Milch kaufen".
    *   *Example:* "Journal Heute war ein schöner Tag" -> Creates an entry in "Journal".
4.  **Review:** See your entries appear instantly in the list.

## License

Voice Journal is licensed under the MIT License. See the `LICENSE` file for more information.
