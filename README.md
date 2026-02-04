# Android-Self-Driving-Sim
Native Android application for Edge AI, demonstrating on-device machine learning inference and optimization using Kotlin and Gradle.

# Android Edge AI: Behavioral Cloning for Autonomous Driving

**An end-to-end learning system that clones human driving behavior, trained in Python and deployed to a native Android device for real-time inference.**

## 📱 Project Overview
This project implements **Behavioral Cloning** (inspired by Bojarski et al., 2016) to create a self-driving car simulation on mobile hardware. 
The system consists of two parts:
1.  **Training (Cloud):** A Convolutional Neural Network (CNN) trained on the "Jungle" dataset (center/left/right camera views) to predict steering angles.
2.  **Inference (Edge):** A native Android app (Kotlin) that processes a live video feed, runs the quantized TFLite model, and visualizes the autonomous steering commands in real-time.

![App Interface](assets1/driving-sim-dash.jpg)

## 🛠️ Tech Stack & Architecture
* **Model Architecture:** Custom CNN (Cropping2D -> 3x Convolutional Blocks -> Dense Regression Head).
* **Training:** Python, TensorFlow/Keras, Google Colab (A100 GPU).
* **Edge Deployment:** Kotlin, TensorFlow Lite (LiteRT), Android NNAPI.
* **Optimization:** Post-training quantization for mobile CPU latency reduction.

## 📊 Key Results
The model was evaluated using the **Autonomy Grade** metric:
$$Autonomy = (1 - \frac{\text{Interventions} \times 6}{\text{Elapsed Time}}) \times 100$$

| Scenario | Duration | Interventions | Autonomy Grade |
| :--- | :--- | :--- | :--- |
| **Daytime 1** | 60s | 0 | **100%** |
| **Nighttime 1** | 60s | 0 | **100%** |
| **Complex Track** | 60s | 0 | **100%** |

[cite_start]*Result: The system achieved 100% autonomy across all test videos, successfully navigating complex foliage and lighting changes without human intervention.* [cite: 81, 98]

## 🔧 Engineering Challenges (The "War Story")
**The Challenge:**
[cite_start]During deployment, the application crashed with a `java.lang.IllegalArgumentException` regarding `builtin opcode 'FULLY_CONNECTED' version '12'`. [cite: 53]

**The Root Cause:**
[cite_start]There was a critical version mismatch between the training environment (TensorFlow 2.16, generating Version 12 operators) and the Android TFLite runtime (supporting only up to Version 11). [cite: 56, 57]

**The Solution:**
[cite_start]Refactored the model architecture and downgraded specific operator requirements to ensure compatibility with the stable Android TFLite schema, proving the importance of matching training/inference runtime versions in Edge AI pipelines. [cite: 101]

## 📂 Repository Structure
* **`app/`**: Native Android source code (Kotlin).
* **`app/src/main/assets/`**: Contains the quantized `final_model.tflite` and test video files.
* **`notebooks/`**: (Optional) The Jupyter Notebook used for training the CNN.

## 🚀 How to Run
1.  Clone the repo.
2.  Open the folder in **Android Studio**.
3.  Sync Gradle files.
4.  Connect an Android device (Developer Mode enabled) or use an Emulator.
5.  Run `Run 'app'`.

## 👤 Author
**Brandyn Ewanek**
* [LinkedIn](https://www.linkedin.com/in/brandyn-ewanek/)
* [Portfolio](https://brandynewanek.github.io/)
