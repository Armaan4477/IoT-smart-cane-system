# Drishti Stick: Smart Assistive Navigation and Telemetry System

## Overview

Drishti Stick is an integrated hardware and software assistive ecosystem designed to empower visually impaired individuals with independent mobility, real-time spatial awareness, fall detection, and emergency tracking. Developed for the Smart India Hackathon (SIH 2026), the system fuses edge artificial intelligence, IoT sensor networks, a native Android companion application, and a cloud-based real-time telemetry dashboard.

The solution operates through three interconnected tiers:
1. **Edge Computing and Sensor Fusion Unit (Raspberry Pi 5)**: Captures video streams, executes low-latency local object detection via YOLOv8n, computes directional steering cues, collects multi-sensor telemetry (ultrasonic proximity, 6-axis IMU orientation, and GPS coordinates), and streams control signals over Bluetooth RFCOMM while periodically uploading sensor batches to the cloud.
2. **Mobile Companion Application (Android / Java)**: Operates as an intelligent personal assistant. It receives directional instructions from the stick over offline Bluetooth RFCOMM and delivers immediate voice guidance via Text-to-Speech (TTS). It provides hands-free voice-directed turn-by-turn pedestrian navigation via OpenStreetMap (OSRM), background fall monitoring with emergency audio-visual alerts, and tactile interactions tailored for visually impaired users.
3. **Cloud Telemetry and Monitoring Platform (Next.js / Firebase)**: Provides a central web interface for caretakers and medical responders to monitor real-time device health, track live GPS locations on interactive maps, view historical movement charts, and manage system operations via an administrative kill switch.

---

## System Architecture

```
+-----------------------------------------------------------------------------------+
|                           DRISHTI STICK (HARDWARE / EDGE)                         |
|                                                                                   |
|  +-------------------+  +-------------------+  +-------------------------------+  |
|  |   HC-SR04 Sonic   |  |     MPU-6050      |  |         NEO-6M GPS            |  |
|  | (GPIO 23 / 24)    |  |  (I2C-1, 0x68)    |  |   (UART /dev/serial0, 9600)   |  |
|  +---------+---------+  +---------+---------+  +---------------+---------------+  |
|            |                      |                            |                  |
|            +----------------------+----------------------------+                  |
|                                   |                                               |
|                    +--------------v---------------+                               |
|                    | Sensor Acquisition Thread    |                               |
|                    | - Distance & Orientation     |                               |
|                    | - Periodic API Telemetry     |                               |
|                    +--------------+---------------+                               |
|                                   | (HTTP POST / 7s)                              |
|                                   |                                               |
|  +-------------------+            |                                               |
|  |  Pi Camera Module |            |                                               |
|  |  (picamera2 640p) |            |                                               |
|  +---------+---------+            |                                               |
|            |                      |                                               |
|  +---------v---------+            |                                               |
|  | YOLOv8n Detection |            |                                               |
|  | (CPU Inference)   |            |                                               |
|  +---------+---------+            |                                               |
|            |                      |                                               |
|  +---------v---------+            |                                               |
|  | Detection Logic   |            |                                               |
|  | (STOP/LEFT/RIGHT) |            |                                               |
|  +---------+---------+            |                                               |
|            |                      |                                               |
|  +---------v---------+            |                                               |
|  | Bluetooth RFCOMM  |            |                                               |
|  | (Channel 3)       |            |                                               |
|  +---------+---------+            |                                               |
+------------|----------------------|-----------------------------------------------+
             |                      |
             | (Offline RFCOMM)     | (HTTPS Telemetry)
             |                      |
+------------v---------+   +--------v-----------------------------------------------+
| ANDROID APPLICATION  |   |           CLOUD TELEMETRY BACKEND & DASHBOARD          |
|                      |   |                                                        |
| - Bluetooth Service  |   |  +--------------------------------------------------+  |
| - Text-to-Speech     |   |  | Next.js API Routes (Vercel Serverless)           |  |
| - Voice Destination  |   |  | - POST /api/sensors/data                         |  |
| - Fall Detection     |   |  | - GET  /api/sensors/latest                       |  |
| - OpenStreetMap Nav  |   |  | - POST /api/sensors/health                       |  |
|                      |   |  +-------------------------+------------------------+  |
+----------------------+   |                            |                           |
                           |  +-------------------------v------------------------+  |
                           |  | Firebase Realtime Database                       |  |
                           |  | - sensor_data/ (Indexed by timestamp)            |  |
                           |  | - device_health/esp32_cam                        |  |
                           |  +-------------------------+------------------------+  |
                           |                            |                           |
                           |  +-------------------------v------------------------+  |
                           |  | Next.js React Dashboard (Caregiver Web UI)       |  |
                           |  | - Real-time Sensor Charts (Chart.js)             |  |
                           |  | - Live User Tracking Map (Leaflet / OSM)         |  |
                           |  | - Device Heartbeat & Kill Switch Control         |  |
                           |  +--------------------------------------------------+  |
                           +--------------------------------------------------------+
```

---

## Repository Structure

```
.
|-- android/                         # Native Android companion application
|   |-- app/
|   |   |-- src/main/
|   |   |   |-- java/com/an/wrist/
|   |   |   |   |-- api/             # Retrofit client and API endpoints
|   |   |   |   |-- models/          # Sensor and navigation data models
|   |   |   |   |-- repository/      # Data repositories for API synchronization
|   |   |   |   |-- service/         # Background Bluetooth & Accelerometer monitor services
|   |   |   |   |-- viewmodel/       # MVVM ViewModel architectures
|   |   |   |   |-- MainActivity.java
|   |   |   |   |-- MapActivity.java
|   |   |   |   `-- SensorDetailActivity.java
|   |   |   |-- res/                 # Layouts, drawables, strings, and navigation assets
|   |   |   `-- AndroidManifest.xml  # Permissions and service declarations
|   |   `-- build.gradle.kts         # App dependencies, compileSdk 36, minSdk 30
|   |-- gradle/libs.versions.toml    # Dependency version catalog
|   `-- build.gradle.kts             # Project build configuration
|
|-- nextjs_api/                      # Cloud backend and real-time caregiver dashboard
|   |-- src/
|   |   |-- components/              # UI widgets (Charts, Maps, Data tables, Kill Switch)
|   |   |-- context/                 # Application contexts (KillSwitchContext)
|   |   |-- lib/                     # Firebase Admin, Firebase client, Health, Kill switch
|   |   |-- pages/
|   |   |   |-- api/sensors/         # REST API endpoints (data.js, latest.js, health.js)
|   |   |   |-- _app.js              # Global styles and context wrapper
|   |   |   `-- index.js             # Real-time monitoring dashboard
|   |   `-- styles/                  # Tailwind CSS styling
|   |-- firebase.database.rules.json # Production RTDB security rules and indexes
|   |-- package.json                 # Next.js 16, React 19, Firebase Admin, Chart.js
|   `-- next.config.js               # Next.js deployment configuration
|
|-- rpi-codes/                       # Raspberry Pi embedded edge computing programs
|   |-- main.py                      # Master multi-threaded application entry point
|   |-- vision_main.py               # Standalone vision and Bluetooth pipeline
|   |-- camera.py                    # Picamera2 hardware capture wrapper
|   |-- yolo.py                      # YOLOv8n object detection and spatial classification
|   |-- detection.py                 # Obstacle filtering and priority decision engine
|   |-- bluetooth.py                 # Native Linux RFCOMM Bluetooth socket server
|   |-- ultrasonic.py                # HC-SR04 ultrasonic distance driver (gpiozero)
|   |-- mpu6050.py                   # MPU-6050 6-axis IMU driver (smbus2)
|   `-- gps.py                       # NEO-6M NMEA serial parsing driver
|
|-- .gitignore                       # Git exclusion rules
`-- README.md                        # Master project documentation
```

---

## Hardware Specifications and Pinout

The physical stick utilizes a Raspberry Pi 5 as the central processing unit, connected to a sensory cluster:

| Component | Interface | Raspberry Pi Pin / Port | Configuration Details |
| :--- | :--- | :--- | :--- |
| **Raspberry Pi Camera Module** | CSI Ribbon Cable | Dedicated CAM/DISP Port | 640x480 RGB888 capture via `picamera2` |
| **HC-SR04 Ultrasonic Sensor** | GPIO | Trigger: GPIO 23 (Pin 16)<br>Echo: GPIO 24 (Pin 18) | Voltage divider used on Echo line (5V to 3.3V logic) |
| **MPU-6050 6-Axis IMU** | I2C | SDA: GPIO 2 (Pin 3)<br>SCL: GPIO 3 (Pin 5)<br>VCC: 3.3V, GND: Pin 6 | Bus 1, Address `0x68`, 16-bit Accelerometer + Gyroscope |
| **NEO-6M GPS Module** | UART Serial | TX (Module) to RXD: GPIO 15 (Pin 10)<br>RX (Module) to TXD: GPIO 14 (Pin 8) | Port: `/dev/serial0`, Baud Rate: 9600 bps |
| **Bluetooth Transceiver** | Onboard SoC | Integrated Cypress CYW43455 | RFCOMM Channel 3, Profile: Serial Port Profile (SPP) |

---

## Subsystem Details

### 1. Edge Computing and Vision Subsystem (`rpi-codes/`)

The Raspberry Pi software stack coordinates concurrent sensor polling, computer vision inference, and inter-device communication.

#### Concurrent Threading Model (`main.py`)
- **Sensor Loop Thread**: Every 7 seconds, samples the ultrasonic sensor (distance in cm), MPU-6050 (3-axis acceleration, 3-axis angular velocity, total g-force), and NEO-6M GPS (latitude and longitude). Builds a JSON telemetry payload and posts it over HTTPS to the Next.js cloud endpoint.
- **Camera Capture Thread**: Continuously captures live video frames via `picamera2` at 640x480 resolution, pushing frames into a bounded queue (`maxsize=2`). Older frames are dropped if unprocessed to guarantee zero latency.
- **YOLO Detection Thread**: Pulls frames from the queue and executes YOLOv8n CPU inference. Detections are evaluated for bounding box centroids:
  - Horizontal position $< 0.33 \times \text{width}$: Classified as `LEFT`.
  - Horizontal position $> 0.67 \times \text{width}$: Classified as `RIGHT`.
  - Between $0.33$ and $0.67 \times \text{width}$: Classified as `CENTER`.
- **Navigation Decision Engine (`detection.py`)**: Filters detections against an obstacle taxonomy (vehicles, people, animals, furniture, barriers). Evaluates direction using a strict safety priority:
  1. `STOP`: When an obstacle is detected in the `CENTER` zone.
  2. `LEFT` or `RIGHT`: Steers the user away from side obstacles based on the highest detection confidence.
  3. `FORWARD`: Default state when no relevant obstacles are detected within the threshold.
- **Bluetooth RFCOMM Server (`bluetooth.py`)**: Implements raw Linux socket communication (`AF_BLUETOOTH`, `SOCK_STREAM`, `BTPROTO_RFCOMM`) on Channel 3. Broadcasts newline-delimited JSON messages (`{"command": "LEFT"}\n`) to the paired Android phone without third-party external dependencies.

---

### 2. Android Mobile Application (`android/`)

The companion Android application (`com.an.wrist`) acts as the user's primary interface, voice navigator, and emergency watchdog.

#### Core Capabilities
- **Bluetooth Background Service (`BluetoothService.java`)**:
  - Runs as an Android Foreground Service with type `connectedDevice`.
  - Automatically establishes and maintains an RFCOMM socket connection to `DrishtiStick` on channel 3.
  - Features an exponential reconnect loop to recover from signal loss.
  - Broadcasts `ACTION_COMMAND_RECEIVED` intents to the application layer whenever a new spatial command is received.
- **Text-to-Speech Guidance**:
  - `MainActivity` consumes broadcast commands and verbalizes instructions ("STOP", "LEFT", "RIGHT", "FORWARD") using native Android `TextToSpeech` with debouncing to prevent repetitive audio fatigue.
- **Accessibility Gestures & Voice Destination**:
  - Visually impaired users can place a thumb or finger anywhere on the screen for 4 seconds (`DASHBOARD_HOLD_DURATION_MS`).
  - Haptic countdown pulses fire every second (4, 3, 2, 1) via the device vibrator.
  - Once triggered, the app activates the Android Speech Recognizer, prompting: *"Where do you want to go?"*
  - The spoken destination is parsed and passed directly to the navigation module.
- **Pedestrian Navigation (`MapActivity.java`)**:
  - Built with OpenStreetMap (OSMDroid) and OSRMRoadManager.
  - Computes footpaths and walking routes from current GPS coordinates to the requested destination.
  - Tracks distance to consecutive waypoints, triggering auditory turn cues and updating dynamically if the user deviates from the path.
- **Fall Detection Service (`AccelerometerMonitorService.java`)**:
  - Operates as a persistent foreground service (`dataSync`).
  - Continuously monitors MPU-6050 accelerometer vector magnitudes:
    $$\text{Magnitude} = \sqrt{a_x^2 + a_y^2 + a_z^2}$$
  - Flags anomalies when delta spikes exceed the safety threshold (`SPIKE_THRESHOLD = 6500.0`), immediately dispatching high-priority audio-visual notifications.
- **Telemetry Dashboard & Historical Visualizations**:
  - Real-time visualization using `MPAndroidChart` for Accelerometer (X, Y, Z), Gyroscope (X, Y, Z), and Ultrasonic range metrics.
  - Synchronized auto-polling loop matching the 7-second hardware transmission window.

---

### 3. Cloud Telemetry and Web Dashboard (`nextjs_api/`)

A centralized monitoring platform built on Next.js 16 and Firebase Realtime Database.

#### API Reference

##### 1. Ingest Sensor Telemetry
- **Endpoint**: `POST /api/sensors/data`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "source": "rpi-drishti",
  "distance_cm": 42.5,
  "mpu6050": {
    "accel": { "x": 0.05, "y": 0.12, "z": 0.98 },
    "gyro": { "x": 0.2, "y": -0.1, "z": 0.05 },
    "total_accel": 0.99
  },
  "gps": {
    "lat": 28.6139,
    "lon": 77.2090
  }
}
```
- **Response** (`201 Created`):
```json
{
  "message": "Sensor data stored successfully",
  "id": "-NxAbC12345XYZ",
  "timestamp": "2026-11-18T04:31:18.000Z"
}
```

##### 2. Retrieve Latest Sensor Records
- **Endpoint**: `GET /api/sensors/latest?limit=10`
- **Query Parameters**:
  - `limit` (optional, default: `10`): Number of latest telemetry records to retrieve.
- **Response** (`200 OK`):
```json
{
  "data": [
    {
      "id": "-NxAbC12345XYZ",
      "source": "rpi-drishti",
      "distance_cm": 42.5,
      "mpu6050": { ... },
      "gps": { ... },
      "timestamp": 1731904278000,
      "serverTimestamp": "2026-11-18T04:31:18.000Z",
      "isOnline": true,
      "healthAgeMs": 2400
    }
  ],
  "health": {
    "isOnline": true,
    "lastPingAt": 1731904278000,
    "lastPingIso": "2026-11-18T04:31:18.000Z",
    "timeoutMs": 15000,
    "healthAgeMs": 2400
  }
}
```

##### 3. Device Health Ping
- **Endpoint**: `POST /api/sensors/health`
- **Request Body**: `{"source": "rpi-drishti"}`
- **Response** (`200 OK`):
```json
{
  "message": "Health ping recorded",
  "health": {
    "lastPingAt": 1731904278000,
    "lastPingIso": "2026-11-18T04:31:18.000Z",
    "source": "rpi-drishti",
    "pingIntervalMs": 15000,
    "timeoutMs": 15000,
    "updatedAt": 1731904278000,
    "isOnline": true
  }
}
```

#### Database Architecture and Security (`firebase.database.rules.json`)
The Realtime Database enforces strict indexing and write restrictions:
- `sensor_data`: Indexed on `.indexOn: ["timestamp", "serverTimestamp"]` to enable fast descending window queries. Client writes are locked; only the backend Firebase Admin SDK can insert telemetry records.
- `device_health/esp32_cam`: Tracks the most recent heartbeat. If `now - lastPingAt > 15000 ms`, the system marks the physical stick as offline.

#### Global Kill Switch (`src/lib/kill-switch.js`)
An administrative control mechanism designed to protect cloud quotas and stop unexpected outbound egress:
- `KILL_SWITCH_ENABLED`: When set to `true`, halts all Firebase incoming writes and client subscriptions immediately, returning `503 Service Unavailable`.

---

## Installation and Setup Guide

### 1. Prerequisites
- **Raspberry Pi 5 / 4B** running Raspberry Pi OS (Bookworm, 64-bit).
- **Android Smartphone** running Android 11.0 (API Level 30) or higher with Bluetooth enabled.
- **Node.js** v20.x or later and `npm`.
- **Firebase Project** with Realtime Database activated.
- **Arduino IDE** (optional, for ESP32 auxiliary units if deployed).

---

### 2. Raspberry Pi Setup

#### System Dependencies
Enable I2C, Serial UART, and Camera interfaces via raspi-config:
```bash
sudo raspi-config
# Navigate to Interface Options:
# Enable I2C -> Yes
# Enable Serial Port -> Shell Access: No, Serial Hardware: Yes
# Enable Camera -> Yes
sudo reboot
```

Configure Bluetooth aliases and discoverability:
```bash
sudo bluetoothctl system-alias DrishtiStick
sudo hciconfig hci0 piscan
```

#### Python Environment Setup
```bash
# Clone the repository
git clone https://github.com/your-org/SIH_2026.git
cd SIH_2026/rpi-codes

# Create a virtual environment with system site-packages for picamera2
python3 -m venv --system-site-packages ~/sih/venv
source ~/sih/venv/bin/activate

# Install required Python packages
pip install --upgrade pip
pip install ultralytics opencv-python-headless requests smbus2 gpiozero pyserial numpy
```

#### Running the Software
To execute all subsystems simultaneously (Sensors, Vision, YOLO, Bluetooth, and API telemetry):
```bash
~/sih/venv/bin/python main.py
```

To run only the Vision and Bluetooth RFCOMM loop independently:
```bash
~/sih/venv/bin/python vision_main.py
```

---

### 3. Android Application Setup

1. Open Android Studio (Ladybug or later recommended).
2. Select **Open** and choose the `android/` directory.
3. Allow Gradle to synchronize dependencies.
4. Verify the API base URL in `android/app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://dhrististick.vercel.app/\"")
   ```
5. Ensure the Android device is paired to the Raspberry Pi:
   - Go to **Android Settings** > **Bluetooth**.
   - Pair with device named **DrishtiStick**.
6. Build and deploy the application to your physical device:
   ```bash
   ./gradlew installDebug
   ```
7. Grant required runtime permissions when prompted:
   - Nearby Devices / Bluetooth Connect
   - Location (Fine and Coarse)
   - Microphone / Audio Recording
   - Notifications

---

### 4. Cloud Telemetry Dashboard Setup (Next.js)

#### Environment Configuration
Navigate to `nextjs_api/` and create `.env.local`:
```bash
cd nextjs_api
cp .env.local.example .env.local
```

Populate `.env.local` with your Firebase project credentials:
```ini
# Firebase Client Configuration
NEXT_PUBLIC_FIREBASE_API_KEY=your_api_key
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=your_project_id.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=your_project_id
NEXT_PUBLIC_FIREBASE_DATABASE_URL=https://your_project_id-default-rtdb.firebaseio.com
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=your_project_id.appspot.com
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your_messaging_sender_id
NEXT_PUBLIC_FIREBASE_APP_ID=your_app_id
NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID=your_measurement_id
NEXT_PUBLIC_SENSOR_WINDOW=20

# Firebase Admin SDK Configuration (Server-Side)
FIREBASE_PROJECT_ID=your_project_id
FIREBASE_DATABASE_URL=https://your_project_id-default-rtdb.firebaseio.com
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxxxx@your_project_id.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYOUR_KEY_HERE\n-----END PRIVATE KEY-----\n"
```

#### Local Development Server
```bash
npm install
npm run dev
```
Open `http://localhost:3000` in your web browser.

#### Deploying to Vercel
1. Install Vercel CLI:
   ```bash
   npm install -g vercel
   ```
2. Deploy the project:
   ```bash
   vercel --prod
   ```
3. Set all environment variables defined in `.env.local` within the Vercel Project Dashboard (**Settings** > **Environment Variables**).

---

## Troubleshooting Guide

| Issue | Probable Cause | Corrective Action |
| :--- | :--- | :--- |
| **Bluetooth connection failed on Android** | Raspberry Pi not discoverable or channel mismatch | Run `sudo hciconfig hci0 piscan` on the Pi. Ensure both `bluetooth.py` and `BluetoothService.java` use RFCOMM Channel 3. Confirm phone is paired in Android Settings. |
| **YOLO model inference lagging** | Frame queue saturation or thermal throttling | Check Raspberry Pi cooling. Verify `FRAME_QUEUE_MAX = 2` so that outdated frames are dropped automatically. |
| **GPS returns null coordinates** | No satellite fix indoors | Test the module outdoors with a direct line of sight to the sky. Check connections to `/dev/serial0` and verify baud rate is 9600. |
| **MPU-6050 reading 0x00 / error** | Loose I2C wiring or incorrect address | Verify connections on GPIO 2 (SDA) and GPIO 3 (SCL). Run `i2cdetect -y 1` to ensure address `0x68` is present. |
| **API returns 503 on sensor upload** | Kill switch enabled or missing Firebase keys | Verify that `KILL_SWITCH_ENABLED` is `false` in `nextjs_api/src/lib/kill-switch.js` and that `FIREBASE_*` environment variables are properly set. |
| **Dashboard shows device offline** | Heartbeat timestamp older than 15 seconds | Check internet connectivity on the Raspberry Pi and ensure `main.py` is actively posting to `/api/sensors/data`. |

---

## Technical Stack Summary

- **Embedded / Hardware**: Raspberry Pi 5, Pi Camera v2, HC-SR04, MPU-6050, NEO-6M GPS, Linux RFCOMM BlueZ.
- **Edge AI / Vision**: Python 3, YOLOv8n (Ultralytics), OpenCV, Picamera2.
- **Mobile Platform**: Android 14/15, Java 17, MVVM, Retrofit 2, OkHttp 3, OSMDroid, OSRM Routing, MPAndroidChart.
- **Cloud & Dashboard**: Next.js 16, React 19, Tailwind CSS v4, Chart.js, Firebase Admin SDK v14, Firebase Realtime Database, Vercel Serverless.

---

## License

This project is licensed under the MIT License. Details are available in the LICENSE file.
