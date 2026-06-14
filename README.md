# iKoo — On-Device AI Co-Pilot for Android

> Your phone already sees the message that says "dinner Friday 8 PM". iKoo is the layer that notices, understands, and offers to handle it — without ever sending that message anywhere.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Privacy](https://img.shields.io/badge/AI-100%25%20On--Device-FF6F00)
![Package](https://img.shields.io/badge/package-com.sudocode.ikoo-informational)

---

## What is iKoo?

iKoo is a native Android app that runs quietly in the background, watches your screen and notifications (with your permission), and uses fully offline on-device AI to turn scattered chats into useful actions — calendar events, reminders, voice answers, and searchable memories.

No cloud. No server. Not a single byte of your data leaves your phone.

---

## The Problem

Every day, plans and deadlines arrive buried in WhatsApp chats, Gmail threads, Teams messages, and SMS notifications:

- *"Dinner Friday 8 PM at Olive"*
- *"Final exam Monday 9 AM in Hall B"*
- *"Pay home loan EMI by 5 PM"*

Nothing on Android today reads that text and quietly does the obvious next thing. Cloud assistants like Siri or Google Assistant cannot see inside third-party apps, require an internet connection, and send everything to a server — a non-starter for privacy-conscious users. Photo galleries balloon into unsearchable archives. Screen-time tools feel like yet another app to babysit.

iKoo fixes all of this in one place.

---

## Core Features

### 1. Smart Intent Detection and Auto Calendar
The Accessibility Service continuously scans on-screen text from supported apps. A fast heuristic classifier (IntentDetector) flags calendar-worthy content. The on-device Gemma 3n model then extracts a clean title, date, time, and location. A floating card offers one-tap "Add to Calendar" — inserted straight into the native Android Calendar app.

### 2. "Hey iKoo" Wake Word and Voice Assistant
A lightweight foreground service listens for the "Hey iKoo" wake phrase using Android's on-device SpeechRecognizer. Activating it opens an animated voice overlay cycling through Waking, Listening, Thinking, and Responding states for hands-free commands and questions.

### 3. AssistiveTouch Floating Bubble
A draggable, always-on-top overlay bubble accessible from any app. Scan the current screen, ask iKoo a question, or capture a screenshot to search for a product — all without leaving whatever you are doing.

### 4. Notification Intelligence
A NotificationListenerService inspects incoming notifications even when the screen is locked. Calendar-worthy content from supported apps is scored and refined by the offline LLM, so a meeting invite that arrives while your phone is locked still produces an "Add to Calendar" prompt.

### 5. AI Photo Gallery ("Memories")
Natural-language search over your device's entire photo library — entirely offline. Queries like "beach photos from Goa last week" or "temple photos in Mysore last month" are parsed using place recognition, GPS/EXIF metadata, relative date parsing, and ML Kit vision (OCR, image labeling, barcode scanning). No upload required.

### 6. Digital Wellbeing and App Usage Limiter
Set daily time limits for any app. iKoo tracks real usage via UsageStatsManager and enforces limits through the Accessibility Service — a built-in, no-subscription screen-time guardrail.

### 7. Activity History and Live Dashboard
Every detection is logged to a local Room database with timestamp, source app, and confidence score. The History tab shows a searchable timeline; the Live Dashboard shows a real-time feed of detections, current app usage, and quick actions.

---

## How It Works — The Detection Pipeline

Every screen read and notification flows through one shared pipeline (`IKooPipeline`):

```
1. Capture      AccessibilityService reads on-screen text  /  NotificationListenerService reads incoming notifications
2. Fast Classify IntentDetector scores text as CALENDAR_EVENT / REMINDER / TASK / NONE — in microseconds
3. AI Refine    On-device LLM (Gemma 3n E2B INT4 or Qwen2.5-0.5B) extracts: title, date, time, location
4. Normalize    EventCandidateNormalizer filters false positives (UPI strings, navigation text, account numbers)
5. Suggest      OverlaySuggestionManager shows a de-duplicated floating "Add to Calendar" card
6. Act & Log    CalendarActionManager inserts the event; HistoryRepository logs the detection to Room
```

The heavy AI step only runs after the fast heuristic pass confirms a promising signal, and only for messages from a vetted allow-list of apps. This keeps battery usage and latency low.

**Supported apps:** WhatsApp, WhatsApp Business, Gmail, Microsoft Teams, Google Messages (SMS), Telegram, Slack

---

## Technology Stack

### Language, UI and Architecture

| Layer | Technology |
|---|---|
| Language | Kotlin (with a small amount of Java for Room entities/DAOs) |
| UI Toolkit | Jetpack Compose + Material 3, custom dark "glass" theme |
| App Structure | Single-activity (ComponentActivity) with bottom-nav Compose shell |
| Concurrency | Kotlin Coroutines and Flow |
| Image Loading | Coil (AsyncImage) |

### On-Device AI and Machine Learning

| Layer | Technology |
|---|---|
| LLM Runtime | Google MediaPipe LLM Inference API (LiteRT), fully offline on-CPU |
| Text Models | Gemma 3n E2B Instruct (INT4, LiteRT) and Qwen2.5-0.5B-Instruct via AIEngine interface |
| Vision Models | GemmaLiteRtVisionEngine behind a VisionEngine abstraction |
| Computer Vision | Google ML Kit: OCR, Image Labeling, Barcode/QR Scanning |
| NLU Heuristics | Custom offline regex/keyword engines (IntentDetector, SmartSearchParser) |

### Data and Persistence

| Layer | Technology |
|---|---|
| Local Database | Room (SQLite) — HistoryDao, HistoryEventEntity, IKooDatabase |
| Media Metadata | Android MediaStore + ExifInterface |
| Preferences | SharedPreferences |

### Android System Integrations

| Layer | Technology |
|---|---|
| Screen Reading | AccessibilityService |
| Notifications | NotificationListenerService |
| Voice | SpeechRecognizer (on-device) via foreground Service |
| Calendar | CalendarContract + ACTION_INSERT intents |
| Overlays | WindowManager (SYSTEM_ALERT_WINDOW) |
| Digital Wellbeing | UsageStatsManager |
| Boot Persistence | BootReceiver |

---

## Privacy

All AI inference runs locally via Google MediaPipe LiteRT.

- No message text leaves the device
- No screen content leaves the device
- No photos leave the device
- No server component exists for any AI feature

Privacy is the architecture, not a policy.

---

## Who It Is For

- **Busy professionals and students** who receive plans and deadlines across WhatsApp, email, and Teams and do not want to manually transcribe them into a calendar
- **Privacy-conscious Android users** who want assistant-style convenience without sending personal messages or photos to the cloud
- **Anyone with an unsearchable photo gallery** — years of photos that are easy to take and impossible to find again
- **Parents and self-improvers** looking for a built-in, no-subscription way to manage time in distracting apps

---

## Project Status

The codebase implements the full Compose UI shell (Home, Live/Dashboard, History, Memories), the Accessibility and Notification Listener services, the shared detection pipeline, both Gemma and Qwen on-device engines behind a common interface, the wake-word service with boot persistence, the AssistiveTouch overlay and calendar suggestion overlay, the Room-backed history store, and the app-usage limiter.

Approximately 14,000 lines of Kotlin across the project.

---

## License

This project is proprietary software. All rights reserved.

---

*iKoo — com.sudocode.ikoo. An Android app that reads the room, thinks on-device, and quietly takes care of the small things.*
