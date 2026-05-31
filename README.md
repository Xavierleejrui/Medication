# 💊 약-up! Smart Medication Adherence Tracker

> A mobile Android application that uses on-device deep learning to visually verify pill intake, schedule smart reminders, and track medication adherence.

---

## 📱 Overview

**약-up!** addresses medication non-adherence — a critical healthcare problem where up to 50% of patients fail to take medications correctly. Unlike existing apps (Medisafe, MyTherapy) that rely solely on self-reporting, 약-up! uses **computer vision and on-device DNN inference** to visually verify that the correct pill was taken.

The app uses a **few-shot similarity matching** approach: users register their own pills with 3–5 photos, and the app learns their visual fingerprint without requiring a pre-trained pill database.

---

## ✨ Key Features

- **📷 Visual Pill Registration** — Register any medication with 3–5 photos using a guide box for consistent framing
- **🔍 Dual-Gate Verification** — Verify pills using both shape (MobileNetV3) and color (HSV histogram) gates independently
- **🔔 Smart Reminders** — Daily alarms at scheduled times; missed doses require camera verification for the next dose
- **📊 Adherence Statistics** — Track adherence percentage with per-medication bar charts
- **🧠 On-Device ML** — All inference runs locally; no internet required for verification
- **⚡ Optimized Model** — 70.5% size reduction via dynamic range quantization with only 0.2% accuracy drop

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 36 |
| Build System | Gradle 8.9 + AGP 8.7.3 |
| Camera | CameraX 1.3.4 |
| ML Inference | TensorFlow Lite 2.13.0 |
| Database | Room 2.6.1 |
| Charts | MPAndroidChart |
| Async | Kotlin Coroutines + Flow |
| Serialization | Gson |
| Test Device | Samsung Galaxy S24+ |

---

## 🧠 ML Architecture

### Feature Extraction Pipeline

```
Camera Image (full resolution)
         │
         ▼
  1. Center Square Crop       ← removes background
         │
         ▼
  2. Resize to 224×224
         │
         ▼
  3. Grey World Normalization  ← removes lighting effects
         │
         ├──────────────────────────────────────┐
         ▼                                      ▼
 MobileNetV3Small (TFLite)           HSV Color Histogram
 576-dimensional feature vector      Top 40% saturated pixels
                                     256-dimensional histogram
         │                                      │
         └──────────────┬───────────────────────┘
                        ▼
           832-dimensional Combined Feature Vector
           (stored as JSON in Room database)
```

### Dual-Gate Verification

```
Verification Image
         │
         ▼
  Extract 832-dim features
         │
    ┌────┴─────┐
    ▼           ▼
Gate 1        Gate 2
Shape/Texture  Color
MobileNetV3    HSV Histogram
≥ 0.72         ≥ 0.70
(cosine sim)   (cosine sim)
    │           │
    └────┬──────┘
         ▼
    Both pass? → ✅ VERIFIED
    Either fail? → ❌ MISMATCH
```

---

## 📐 Model Optimization

| Metric | Float32 (Baseline) | Optimized (Dynamic Quantization) |
|--------|--------------------|----------------------------------|
| **Size** | 3,635.1 KB | 1,073.8 KB |
| **Accuracy** | 88.8% | 88.6% |
| **Size Reduction** | — | **70.5%** |
| **Accuracy Drop** | — | **0.2%** |
| **Method** | — | Dynamic Range Quantization (INT8 weights) |

Quantization converts model weights from 32-bit float → 8-bit integer while keeping activations in float32, ensuring compatibility with Android's XNNPACK delegate.

---

## 📦 Dataset

| Attribute | Detail |
|-----------|--------|
| Pill types | 6 (Calcium-D, Charcoal, Codipront, Loperamide, Multivitamin, Omega-3 Fish Oil) |
| Total images | ~200 |
| Images per type | ~33 |
| Background | White paper |
| Collection device | Samsung Galaxy S24+ |

---

## 🏗️ Project Structure

```
MedicationTracker/
├── app/src/main/
│   ├── assets/
│   │   └── mobilenet_v3_feature_extractor.tflite   ← Optimized model
│   └── java/com/example/medicationtracker/
│       ├── MainActivity.kt
│       ├── data/
│       │   ├── local/
│       │   │   ├── dao/
│       │   │   │   ├── AdherenceDao.kt
│       │   │   │   └── MedicationDao.kt
│       │   │   ├── entities/
│       │   │   │   ├── AdherenceLog.kt
│       │   │   │   └── Medication.kt
│       │   │   └── MedicationDatabase.kt
│       │   └── repository/
│       │       ├── AdherenceRepository.kt
│       │       └── MedicationRepository.kt
│       ├── ml/
│       │   ├── ColorFeatureExtractor.kt            ← HSV histogram (256-dim)
│       │   ├── PillFeatureExtractor.kt             ← MobileNetV3 + preprocessing
│       │   └── PillVerifier.kt                     ← Cosine similarity + dual-gate
│       ├── receiver/
│       │   ├── AlarmReceiver.kt                    ← Fires at medication time
│       │   └── NotificationActionReceiver.kt       ← Handles Taken/Not Yet buttons
│       ├── ui/
│       │   ├── add/
│       │   │   ├── AddMedicationActivity.kt
│       │   │   └── ThumbnailAdapter.kt
│       │   ├── main/
│       │   │   ├── CameraTestActivity.kt
│       │   │   └── MedicationListActivity.kt
│       │   ├── stats/
│       │   │   └── StatisticsActivity.kt
│       │   └── verify/
│       │       └── VerificationActivity.kt
│       └── utils/
│           ├── AlarmScheduler.kt                   ← Schedules exact daily alarms
│           ├── CameraHelper.kt                     ← CameraX wrapper with zoom
│           ├── MissedDosePrefs.kt                  ← Tracks missed dose state
│           ├── NotificationHelper.kt               ← Builds/shows notifications
│           └── PermissionHelper.kt
```

---

## 🚀 Setup & Installation

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android device running API 26+ (or emulator)

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Xavierleejrui/Medication.git
   cd MedicationTracker
   ```

2. **Open in Android Studio**
   - File → Open → Select the `MedicationTracker` folder
   - Wait for Gradle sync to complete (~2 minutes first time)

3. **Verify the TFLite model is present**
   ```
   app/src/main/assets/mobilenet_v3_feature_extractor.tflite
   ```
   If missing, see [Model Setup](#model-setup) below.

4. **Build and run**
   - Connect your Android device via USB
   - Enable USB debugging (Settings → Developer Options)
   - Click ▶ Run in Android Studio

### Model Setup

If the `.tflite` model is not included, generate it using Google Colab:

```python
import tensorflow as tf

base_model = tf.keras.applications.MobileNetV3Small(
    input_shape=(224, 224, 3),
    include_top=False,
    weights='imagenet',
    pooling='avg',
    include_preprocessing=False
)

converter = tf.lite.TFLiteConverter.from_keras_model(base_model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open('mobilenet_v3_feature_extractor.tflite', 'wb') as f:
    f.write(tflite_model)
```

Place the generated file at `app/src/main/assets/mobilenet_v3_feature_extractor.tflite`.

---

## 📋 Permissions Required

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Pill registration and verification |
| `POST_NOTIFICATIONS` | Medication reminders (Android 13+) |
| `SCHEDULE_EXACT_ALARM` | Precise alarm timing |
| `USE_EXACT_ALARM` | Precise alarm timing (Android 12+) |
| `INTERNET` | ML Kit OCR (optional feature) |

---

## 📱 How to Use

### 1. Register a Medication
1. Tap **➕ Register Medication** from the home screen
2. Point camera at your pill — fill the guide box
3. Capture **3–5 photos** from different angles
4. Enter medication name, dosage
5. Add reminder times using the time picker
6. Tap **SAVE MEDICATION**

> 💡 **Tip:** Use the same zoom level during registration and verification for best accuracy

### 2. Verify a Pill
1. Tap **🔍 Verify My Pill**
2. Place your pill inside the guide box
3. Tap **VERIFY PILL**
4. Result shows Shape and Color confidence separately:
   - ✅ Both gates pass → Correct medication logged
   - ❌ Either gate fails → Mismatch shown with which gate failed

### 3. Medication Reminders
- Notifications fire at scheduled times
- **✅ I've taken it** → Self-report, no camera required
- **❌ Not yet** → Sets missed flag; next dose requires camera verification
- ⚠️ Home screen banner appears when verification is required

### 4. View Statistics
- Tap **📊 View Statistics** to see 7-day adherence percentage and per-medication bar charts

---

## ⚙️ Configuration

### Verification Thresholds

In `ml/PillVerifier.kt`:
```kotlin
const val SIMILARITY_THRESHOLD = 0.72f  // MobileNetV3 (shape/texture)
```

In `ui/verify/VerificationActivity.kt`:
```kotlin
private const val MOBILENET_THRESHOLD = 0.72f  // Shape gate
private const val COLOR_THRESHOLD = 0.70f       // Color gate
```

Increase thresholds for stricter verification (more false negatives).
Decrease for more lenient verification (more false positives).

### Color Feature Weight

In `ml/ColorFeatureExtractor.kt`:
```kotlin
fun combineFeatures(
    mobilenetFeatures: FloatArray,
    colorHistogram: FloatArray,
    colorWeight: Float = 5.0f   // Increase to weight color more heavily
)
```

---

## ⚠️ Known Limitations

1. **Visually similar pills** — Pills of the same shape and similar color (e.g., two different small white tablets) may be confused. This is a fundamental limitation of using a general-purpose ImageNet feature extractor for specialized pill recognition.

2. **Lighting sensitivity** — While Grey World normalization reduces lighting effects, very dark or very bright environments may affect accuracy. Good consistent lighting is recommended.

3. **Minimum focus distance** — Very small pills may cause autofocus issues. Use the zoom controls (+ button) to magnify small pills before capturing.

4. **Registration consistency** — The same zoom level and distance should be used during both registration and verification for best results.

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| Feature extraction time | ~120ms |
| Model loading time | ~10ms |
| Notification latency | <1 second from scheduled time |
| Model size (optimized) | 1,073.8 KB |
| Feature vector dimensions | 832 (576 MobileNet + 256 Color) |
| Min registration photos | 3 |
| Max registration photos | 5 |
| Max reminder times/day | 4 |

---

## 🗓️ Project Timeline

| Week | Dates | Milestone |
|------|-------|-----------|
| 1 | May 5–11 | Proposal, dataset collection (6 pill types, ~200 images) |
| 2 | May 12–18 | Android project setup, Room database, CameraX integration |
| 3 | May 19–25 | TFLite integration, model quantization, ML pipeline |
| 4 | May 26–Jun 1 | Dual-gate verification, notifications, accuracy tuning, UI polish |

---

## 📚 References

- Howard, A. et al. "Searching for MobileNetV3." ICCV 2019.
- TensorFlow Lite: https://www.tensorflow.org/lite
- Android CameraX: https://developer.android.com/training/camerax
- Room Persistence Library: https://developer.android.com/training/data-storage/room
- MPAndroidChart: https://github.com/PhilJay/MPAndroidChart

---

## 📄 License

This project was developed as part of the Mobile Computing and Applications course (Spring 2026).

---

<div align="center">
  <sub>Built with ❤️ on Samsung Galaxy S24+ | MobileNetV3 + CameraX + Room</sub>
</div>
