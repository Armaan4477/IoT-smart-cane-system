#!/usr/bin/env python3
"""
camera.py — Raspberry Pi Camera capture using picamera2 (libcamera / Bookworm).

Standalone test:
    ~/sih/venv/bin/python ~/sih/camera.py
"""

import time
import numpy as np
from picamera2 import Picamera2


class CameraCapture:
    def __init__(self, width: int = 640, height: int = 480):
        self.width  = width
        self.height = height
        self._cam   = Picamera2()
        config = self._cam.create_preview_configuration(
            main={"format": "RGB888", "size": (width, height)}
        )
        self._cam.configure(config)
        self._cam.start()
        # Allow the sensor to settle before the first real capture
        time.sleep(0.5)

    def get_frame(self) -> np.ndarray:
        """Return the latest frame as a uint8 RGB numpy array (H, W, 3)."""
        return self._cam.capture_array()

    def close(self):
        self._cam.stop()
        self._cam.close()


if __name__ == "__main__":
    print("=== CameraCapture Test ===")
    cam = CameraCapture()
    try:
        for i in range(10):
            t0    = time.time()
            frame = cam.get_frame()
            ms    = (time.time() - t0) * 1000
            print(f"Frame {i+1:2d}: shape={frame.shape}  dtype={frame.dtype}  capture={ms:.1f} ms")
        print("\nCamera OK — picamera2 is working correctly.")
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        cam.close()
