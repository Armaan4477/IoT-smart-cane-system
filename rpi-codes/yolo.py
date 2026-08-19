#!/usr/bin/env python3
"""
yolo.py — YOLOv8n object detection on Raspberry Pi 5 (CPU inference).

Standalone test:
    ~/sih/venv/bin/python ~/sih/yolo.py

The model (~6 MB) is downloaded automatically to ~/sih/models/ on first run.
"""

import os
import time
from dataclasses import dataclass
from typing import List

import numpy as np

_MODEL_DIR  = os.path.expanduser("~/sih/models")
_MODEL_PATH = os.path.join(_MODEL_DIR, "yolov8n.pt")


@dataclass
class Detection:
    label:      str
    confidence: float
    x1:         int
    y1:         int
    x2:         int
    y2:         int
    direction:  str   # "LEFT" | "CENTER" | "RIGHT"


class YOLODetector:
    def __init__(self, conf_threshold: float = 0.45):
        from ultralytics import YOLO
        os.makedirs(_MODEL_DIR, exist_ok=True)
        self.model          = YOLO(_MODEL_PATH)   # downloads if not present
        self.conf_threshold = conf_threshold

    def detect(self, frame: np.ndarray) -> List[Detection]:
        results = self.model(frame, conf=self.conf_threshold, verbose=False)
        detections: List[Detection] = []
        if not results:
            return detections

        result    = results[0]
        img_width = frame.shape[1]

        for box in result.boxes:
            x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
            conf   = float(box.conf[0])
            cls_id = int(box.cls[0])
            label  = self.model.names[cls_id]

            cx  = (x1 + x2) / 2.0
            rel = cx / img_width
            if rel < 0.33:
                direction = "LEFT"
            elif rel > 0.67:
                direction = "RIGHT"
            else:
                direction = "CENTER"

            detections.append(Detection(
                label=label, confidence=conf,
                x1=x1, y1=y1, x2=x2, y2=y2,
                direction=direction,
            ))
        return detections


if __name__ == "__main__":
    from camera import CameraCapture

    print("=== YOLO Detection Test ===")
    print("Loading camera...")
    cam = CameraCapture()
    print("Loading YOLOv8n (downloads ~6 MB on first run)...")
    detector = YOLODetector()
    print("Running. Press CTRL+C to stop.\n")
    print(f"{'Label':<22} {'Conf':>5}   {'Direction'}")
    print("-" * 42)

    try:
        while True:
            t0         = time.time()
            frame      = cam.get_frame()
            detections = detector.detect(frame)
            fps        = 1.0 / max(time.time() - t0, 1e-9)

            if detections:
                for d in detections:
                    print(f"{d.label:<22} {d.confidence:>5.2f}   {d.direction}")
            else:
                print(f"(no detections)  [{fps:.1f} fps]")
    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        cam.close()
