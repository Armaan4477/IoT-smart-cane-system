#!/usr/bin/env python3
"""
main.py — Drishti Stick unified entry point.

Starts all subsystems with a single command:
    ~/sih/venv/bin/python ~/sih/main.py

Subsystems launched concurrently:
  1. Sensor loop  — reads Ultrasonic / MPU6050 / GPS, uploads to API every 7 s
  2. Camera       — captures frames from Raspberry Pi Camera (picamera2)
  3. YOLO         — runs YOLOv8n object detection on each frame
  4. Bluetooth    — sends detection commands to the Android app over RFCOMM
"""

import queue
import signal
import sys
import threading
import time

import requests

from ultrasonic import UltrasonicSensor
from mpu6050   import MPU6050Sensor
from gps       import GPSSensor
from camera    import CameraCapture
from yolo      import YOLODetector
from detection import DetectionProcessor
from bluetooth import BluetoothServer

# ── Configuration ─────────────────────────────────────────────────────────────
API_URL                = "https://dhrististick.vercel.app/api/sensors/data"
SOURCE_NAME            = "rpi-drishti"
UPLOAD_INTERVAL_SECONDS = 7.0

CAMERA_WIDTH    = 640
CAMERA_HEIGHT   = 480
YOLO_CONF       = 0.45
FRAME_QUEUE_MAX = 2       # Keep tiny — always process the freshest frame
LOG_DETECTIONS  = True    # Set False to quiet per-frame printout after testing
# ──────────────────────────────────────────────────────────────────────────────

# Shared stop event — all threads watch this
stop_event = threading.Event()

# Frame queue between camera thread and YOLO thread
frame_queue: queue.Queue = queue.Queue(maxsize=FRAME_QUEUE_MAX)

# Global sensor handles for cleanup
_ultrasonic = None
_mpu        = None
_gps        = None
_cam        = None
_bt_server  = None


# ── Shutdown ──────────────────────────────────────────────────────────────────

def cleanup(signum=None, frame=None):
    print("\n[MAIN] Stopping all subsystems...")
    stop_event.set()
    time.sleep(0.8)   # Let threads notice the stop event

    for obj, name in [(_ultrasonic, "Ultrasonic"),
                      (_mpu,        "MPU6050"),
                      (_gps,        "GPS"),
                      (_cam,        "Camera"),
                      (_bt_server,  "Bluetooth")]:
        if obj is not None:
            try:
                obj.close() if hasattr(obj, "close") else obj.stop()
            except Exception as e:
                print(f"[MAIN] Warning during {name} cleanup: {e}")

    print("[MAIN] All subsystems stopped. Goodbye.")
    sys.exit(0)


signal.signal(signal.SIGINT,  cleanup)
signal.signal(signal.SIGTERM, cleanup)


# ── Thread 1 — Sensor loop ────────────────────────────────────────────────────

def sensor_loop():
    global _ultrasonic, _mpu, _gps

    print("[SENSOR] Initializing sensors...")

    try:
        _ultrasonic = UltrasonicSensor()
        print("[SENSOR]   ✓ Ultrasonic (HC-SR04)")
    except Exception as e:
        print(f"[SENSOR]   ✗ Ultrasonic failed: {e}")

    try:
        _mpu = MPU6050Sensor()
        print("[SENSOR]   ✓ MPU6050")
    except Exception as e:
        print(f"[SENSOR]   ✗ MPU6050 failed: {e}")

    try:
        _gps = GPSSensor()
        print("[SENSOR]   ✓ NEO-6M GPS")
    except Exception as e:
        print(f"[SENSOR]   ✗ GPS failed: {e}")

    print(f"[SENSOR] Uploading to {API_URL} every {UPLOAD_INTERVAL_SECONDS} s")

    while not stop_event.is_set():
        payload = {"source": SOURCE_NAME}

        if _ultrasonic:
            try:
                payload["distance_cm"] = _ultrasonic.get_distance_cm()
            except Exception as e:
                print(f"[SENSOR] Ultrasonic read error: {e}")

        if _mpu:
            try:
                payload["mpu6050"] = _mpu.get_data()
            except Exception as e:
                print(f"[SENSOR] MPU6050 read error: {e}")

        if _gps:
            try:
                payload["gps"] = _gps.get_location()
            except Exception as e:
                print(f"[SENSOR] GPS read error: {e}")

        try:
            response = requests.post(API_URL, json=payload, timeout=5)
            status   = "OK" if response.status_code == 201 else f"FAILED ({response.status_code})"
            print(f"[SENSOR] Upload {status} | {payload}")
        except requests.exceptions.RequestException as e:
            print(f"[SENSOR] Upload error: {e}")

        # Sleep in short increments so we can respond to stop_event quickly
        for _ in range(int(UPLOAD_INTERVAL_SECONDS / 0.5)):
            if stop_event.is_set():
                break
            time.sleep(0.5)

    print("[SENSOR] Sensor loop stopped.")


# ── Thread 2 — Camera capture ─────────────────────────────────────────────────

def camera_thread_fn(cam: CameraCapture):
    print("[CAM]  Camera thread started.")
    while not stop_event.is_set():
        try:
            frame = cam.get_frame()
            if frame_queue.full():
                try:
                    frame_queue.get_nowait()
                except queue.Empty:
                    pass
            frame_queue.put(frame)
        except Exception as e:
            print(f"[CAM]  Error: {e}")
            time.sleep(0.1)
    print("[CAM]  Camera thread stopped.")


# ── Thread 3 — YOLO + detection ───────────────────────────────────────────────

def yolo_thread_fn(detector: YOLODetector,
                   processor: DetectionProcessor,
                   bt_server: BluetoothServer):
    print("[YOLO] Detection thread started.")
    while not stop_event.is_set():
        try:
            frame = frame_queue.get(timeout=1.0)
        except queue.Empty:
            continue

        try:
            detections = detector.detect(frame)
            command    = processor.process(detections)
            bt_server.send_command(command)

            if LOG_DETECTIONS and detections:
                for d in detections:
                    print(f"  {d.label:<22} {d.confidence:.2f}   {d.direction}")
                print(f"  → {command}\n")
        except Exception as e:
            print(f"[YOLO] Error: {e}")
    print("[YOLO] Detection thread stopped.")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    global _cam, _bt_server

    print("=" * 60)
    print("           DRISHTI STICK — FULL SYSTEM")
    print("=" * 60)

    # ── Vision subsystem ──────────────────────────────────────────────────────
    print("[MAIN] Initialising camera...")
    _cam = CameraCapture(width=CAMERA_WIDTH, height=CAMERA_HEIGHT)

    print("[MAIN] Loading YOLOv8n (downloads ~6 MB on first run)...")
    detector  = YOLODetector(conf_threshold=YOLO_CONF)
    processor = DetectionProcessor(min_confidence=YOLO_CONF)

    print("[MAIN] Starting Bluetooth server...")
    _bt_server = BluetoothServer()
    _bt_server.start()

    # ── Launch all threads ────────────────────────────────────────────────────
    threads = [
        threading.Thread(target=sensor_loop,       daemon=True, name="SensorThread"),
        threading.Thread(target=camera_thread_fn,  args=(_cam,),                          daemon=True, name="CamThread"),
        threading.Thread(target=yolo_thread_fn,    args=(detector, processor, _bt_server), daemon=True, name="YoloThread"),
    ]
    for t in threads:
        t.start()

    print()
    print("[MAIN] All subsystems running. Press CTRL+C to stop.")
    print("=" * 60)
    print()

    # Main thread just waits — cleanup() handles the rest on CTRL+C
    while not stop_event.is_set():
        time.sleep(0.5)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[MAIN] Fatal error: {e}")
        cleanup()