#!/usr/bin/env python3

import time
import sys
import signal
import requests

from ultrasonic import UltrasonicSensor
from mpu6050 import MPU6050Sensor
from gps import GPSSensor

# --- CONFIGURATION ---
# Change this URL to match your Next.js API deployment URL
# e.g., "https://your-app-name.vercel.app/api/sensors/data"
API_URL = "https://dhrististick.vercel.app/api/sensors/data"
SOURCE_NAME = "rpi-drishti"
UPLOAD_INTERVAL_SECONDS = 2.0
# ---------------------

# Global instances so they can be cleaned up
ultrasonic = None
mpu = None
gps = None

def cleanup(signum=None, frame=None):
    print("\nStopping Drishti sensor system and cleaning up...")
    if ultrasonic:
        ultrasonic.close()
    if mpu:
        mpu.close()
    if gps:
        gps.close()
    print("All sensors stopped.")
    sys.exit(0)

signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)

def main():
    global ultrasonic, mpu, gps

    print("=" * 60)
    print("              DRISHTI STICK")
    print("             SENSOR SYSTEM")
    print("=" * 60)
    print()
    print("Initializing sensors...")
    
    try:
        ultrasonic = UltrasonicSensor()
        print("  - Ultrasonic initialized")
    except Exception as e:
        print(f"  - Failed to initialize Ultrasonic: {e}")
        
    try:
        mpu = MPU6050Sensor()
        print("  - MPU6050 initialized")
    except Exception as e:
        print(f"  - Failed to initialize MPU6050: {e}")
        
    try:
        gps = GPSSensor()
        print("  - NEO-6M GPS initialized")
    except Exception as e:
        print(f"  - Failed to initialize GPS: {e}")

    print()
    print(f"Data will be uploaded to: {API_URL}")
    print(f"Upload interval: {UPLOAD_INTERVAL_SECONDS} seconds")
    print("Press CTRL+C to stop.")
    print("=" * 60)
    print()

    while True:
        data_payload = {
            "source": SOURCE_NAME,
        }

        # 1. Read Ultrasonic
        if ultrasonic:
            try:
                distance = ultrasonic.get_distance_cm()
                data_payload["distance_cm"] = distance
            except Exception as e:
                print(f"Error reading ultrasonic: {e}")

        # 2. Read MPU6050
        if mpu:
            try:
                mpu_data = mpu.get_data()
                data_payload["mpu6050"] = mpu_data
            except Exception as e:
                print(f"Error reading MPU6050: {e}")

        # 3. Read GPS
        if gps:
            try:
                # gps.get_location() calls update() automatically
                loc = gps.get_location()
                data_payload["gps"] = loc
            except Exception as e:
                print(f"Error reading GPS: {e}")

        # Print local copy
        print(f"Uploading data: {data_payload}")

        # 4. Upload to API
        try:
            response = requests.post(API_URL, json=data_payload, timeout=5)
            if response.status_code == 201:
                print("Upload SUCCESS.")
            else:
                print(f"Upload FAILED. Status: {response.status_code}, Response: {response.text}")
        except requests.exceptions.RequestException as e:
            print(f"Upload EXCEPTION: Failed to connect to API ({e})")

        print("-" * 60)
        time.sleep(UPLOAD_INTERVAL_SECONDS)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Fatal error: {e}")
        cleanup()