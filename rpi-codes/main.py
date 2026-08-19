#!/usr/bin/env python3

import subprocess
import sys
import signal
import time


PROGRAMS = [
    "ultrasonic.py",
    "mpu6050.py",
    "gps.py"
]

processes = []


def stop_all(signum=None, frame=None):
    print("\nStopping Drishti sensor system...")

    for process in processes:
        if process.poll() is None:
            process.terminate()

    for process in processes:
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            process.kill()

    print("All sensors stopped.")
    sys.exit(0)


signal.signal(signal.SIGINT, stop_all)
signal.signal(signal.SIGTERM, stop_all)


print("=" * 60)
print("              DRISHTI STICK")
print("             SENSOR SYSTEM")
print("=" * 60)
print()
print("Starting:")
print("  - Ultrasonic")
print("  - MPU6050")
print("  - NEO-6M GPS")
print()
print("Press CTRL+C to stop.")
print("=" * 60)
print()


try:

    for program in PROGRAMS:

        print(f"Starting {program}...")

        process = subprocess.Popen(
            [
                sys.executable,
                program
            ]
        )

        processes.append(process)

        time.sleep(1)


    # Keep main.py alive while the sensor programs run

    while True:

        # Check whether any program has unexpectedly stopped

        for i, process in enumerate(processes):

            if process.poll() is not None:

                print(
                    f"\nWARNING: {PROGRAMS[i]} stopped "
                    f"with exit code {process.returncode}"
                )

        time.sleep(1)


except KeyboardInterrupt:

    stop_all()


finally:

    stop_all()