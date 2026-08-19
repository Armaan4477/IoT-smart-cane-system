#!/usr/bin/env python3
"""
detection.py — Maps YOLO detections to a single navigation command.

Command priority:
  1. STOP   — obstacle directly ahead (CENTER zone)
  2. LEFT / RIGHT — obstacle to the side; steer the user away
  3. FORWARD — no relevant obstacles detected
"""

import threading
from typing import List

from yolo import Detection

# Classes that count as navigation obstacles.
# Extend this list as needed.
OBSTACLE_CLASSES = {
    "person", "car", "truck", "bus", "motorcycle", "bicycle",
    "dog", "cat", "chair", "dining table", "bench", "potted plant",
    "traffic light", "stop sign", "fire hydrant", "backpack",
    "suitcase", "umbrella", "bottle", "cup", "bowl", "vase",
}


class DetectionProcessor:
    def __init__(self, min_confidence: float = 0.45):
        self.min_confidence   = min_confidence
        self._lock            = threading.Lock()
        self._current_command = "FORWARD"

    def process(self, detections: List[Detection]) -> str:
        """
        Filter detections and return the highest-priority navigation command.
        Thread-safe: updates internal state and returns the new command string.
        """
        relevant = [
            d for d in detections
            if d.confidence >= self.min_confidence
            and d.label.lower() in OBSTACLE_CLASSES
        ]

        if not relevant:
            command = "FORWARD"
        else:
            center_obstacles = [d for d in relevant if d.direction == "CENTER"]
            if center_obstacles:
                command = "STOP"
            else:
                # Pick the side with the highest-confidence obstacle
                best    = max(relevant, key=lambda d: d.confidence)
                command = best.direction   # "LEFT" or "RIGHT"

        with self._lock:
            self._current_command = command
        return command

    def get_current_command(self) -> str:
        with self._lock:
            return self._current_command
