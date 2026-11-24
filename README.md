# 💬 HeyChat - Modern Realtime Android Messaging App

[<img src="https://img.shields.io/badge/Download_APK-HeyChat_v3.0.128-00A884?style=for-the-badge&logo=android&logoColor=white" height="42">](https://github.com/soron01-bit/HeyChat/releases/latest/download/app-debug.apk)

**HeyChat** is a high-performance, feature-packed WhatsApp-style Android messaging application built natively using **Kotlin**, **Jetpack Compose**, **Supabase Realtime**, **CameraX**, and **Room SQLite Database**.

---

## ✨ Features & Highlights

### 💬 Realtime Messaging & Offline Caching
* **Sub-10ms Instant Messaging:** Powered by Supabase Realtime WebSocket Channels and Direct Broadcasts.
* **Offline-First Architecture:** Messages, profiles, and media are cached locally in **Room DB**, rendering instantly in 0ms when offline.
* **WhatsApp Read Receipts:** Real-time message status indicators (**Sent 🗸**, **Delivered ◐**, **Seen ●**).
* **Live Online / Offline Presence:** Live presence tracking with instant "Online" indicator and "Last Seen" timestamps.

### 🎙️ Voice Messaging System
* **Native AAC Recording:** Record high-quality, lightweight voice notes (`.m4a`).
* **Live Recording Control Bar:** Red pulsing indicator 🔴, live timer (`00:15`), cancel 🗑️, and send 📤.
* **WhatsApp-Style Audio Player:** In-bubble audio player with Play/Pause (▶️/⏸️), progress slider, and duration badge.

### 📷 Camera & QR Code System
* **Personal QR Code:** Every user has a unique QR code in Settings for quick profile sharing.
* **Live Camera QR Scanner:** Embedded CameraX QR scanner in the top bar to scan and start chatting with users instantly.
* **Smart Empty Chat Filtering:** Scanning a QR code opens a chat room immediately. If no message is sent, empty chats automatically vanish from the chat list!

### ⭐ Favourites & Chat Filtering
* **Horizontal Filter Chips Row:** Filter chats by **All**, **Unread [Count]**, **Favourites**, and **Groups**.
* **Favourites System:** Long-press any chat or select multiple chats to bulk-add them to **Favourites (⭐)** in 0ms!
* **Multi-Select Bulk Actions:** Multi-select chats for bulk favourite addition or permanent deletion.

### ⭕ Status Stories & Expiry
* **24-Hour Expiry Statuses:** Post photo statuses that automatically expire after 24 hours.
* **Pre-Publish Preview & 90° Rotation:** Rotate and preview status photos before publishing.
* **Quick Reactions & Direct Replies:** Reply directly to statuses with text or quick emoji reactions (👍 ❤️ 😂 😮 😢 🙏).

---

## 🛠️ Tech Stack & Architecture

| Layer | Technology Used |
|---|---|
| **Language** | 100% Kotlin |
| **UI Framework** | Jetpack Compose (Material3) |
| **Backend & Database** | Supabase (PostgreSQL, Gotrue Auth, Realtime WebSockets, Storage) |
| **Local Database** | Android Jetpack Room SQLite Database |
| **Camera & QR** | CameraX 1.3.2 + Google ZXing 3.5.3 |
| **Asynchronous Programming** | Kotlin Coroutines, StateFlow, ChannelFlow |
| **Image Loading** | Coil Compose |
| **Dependency Injection & Design** | Repository Pattern, Clean MVVM Architecture |

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Jellyfish / Ladybug or newer
* JDK 17
* Android Device / Emulator (API Level 24 / Android 7.0 or higher)

### Setup Instructions
1. **Clone the Repository:**
   ```bash
   git clone https://github.com/soron01-bit/HeyChat.git
   ```
2. **Open in Android Studio:**
   * Open Android Studio and select **Open Project**.
   * Navigate to the cloned `HeyChat` folder.
3. **Gradle Sync & Build:**
   * Allow Android Studio to complete the Gradle sync.
   * Connect an Android device or launch an emulator.
   * Click **Run `app`** (`Shift + F10`).

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
