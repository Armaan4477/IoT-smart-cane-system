#!/usr/bin/env python3
"""
vision_main.py — Drishti Stick YOLO + Bluetooth pipeline entry point.

Run ALONGSIDE the existing main.py (sensor/API loop) in a separate terminal:
    ~/sih/venv/bin/python ~/sih/vision_main.py

Pipeline:
    CameraCapture  ─►  frame queue  ─►  YOLODetector
                                              │
                                     DetectionProcessor
                                              │
                                      BluetoothServer  ─►  Android TTS
"""

import queue
import signal
import sys
import threading
import time

from camera    import CameraCapture
from yolo      import YOLODetector
from detection import DetectionProcessor
from bluetooth import BluetoothServer

# ── Configuration ─────────────────────────────────────────────────────────────
CAMERA_WIDTH    = 640
CAMERA_HEIGHT   = 480
YOLO_CONF       = 0.45
FRAME_QUEUE_MAX = 2      # Tiny queue — always process the freshest frame
LOG_DETECTIONS  = True   # Set False to silence per-frame printout after testing
# ──────────────────────────────────────────────────────────────────────────────

frame_queue = queue.Queue(maxsize=FRAME_QUEUE_MAX)
stop_event  = threading.Event()


def camera_thread_fn(cam: CameraCapture):
    print("[CAM]  Camera thread started.")
    while not stop_event.is_set():
        try:
            frame = cam.get_frame()
            # Drop the stale frame if the queue is full — always keep freshest
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


def shutdown(signum=None, frame=None):
    print("\n[MAIN] Shutdown signal received.")
    stop_event.set()


def main():
    signal.signal(signal.SIGINT,  shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    print("=" * 60)
    print("      DRISHTI STICK — VISION + BLUETOOTH PIPELINE")
    print("=" * 60)

    print("[MAIN] Initialising camera...")
    cam = CameraCapture(width=CAMERA_WIDTH, height=CAMERA_HEIGHT)

    print("[MAIN] Loading YOLOv8n (downloads ~6 MB on first run)...")
    detector  = YOLODetector(conf_threshold=YOLO_CONF)
    processor = DetectionProcessor(min_confidence=YOLO_CONF)

    print("[MAIN] Starting Bluetooth server...")
    bt_server = BluetoothServer()
    bt_server.start()

    threads = [
        threading.Thread(
            target=camera_thread_fn,
            args=(cam,),
            daemon=True,
            name="CamThread",
        ),
        threading.Thread(
            target=yolo_thread_fn,
            args=(detector, processor, bt_server),
            daemon=True,
            name="YoloThread",
        ),
    ]
    for t in threads:
        t.start()

    print("[MAIN] Pipeline running. Press CTRL+C to stop.")
    print("=" * 60)

    while not stop_event.is_set():
        time.sleep(0.5)

    print("[MAIN] Stopping all subsystems...")
    bt_server.stop()
    cam.close()
    sys.exit(0)


if __name__ == "__main__":
    main()
