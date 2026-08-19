#!/usr/bin/env python3

import time
from gpiozero import DistanceSensor

TRIG_PIN = 23
ECHO_PIN = 24

sensor = DistanceSensor(
    trigger=TRIG_PIN,
    echo=ECHO_PIN,
    max_distance=4.0,
    queue_len=1
)

print("===================================")
print("       HC-SR04 ULTRASONIC TEST")
print("===================================")
print("Press CTRL+C to stop.\n")

try:
    while True:

        distance_cm = sensor.distance * 100

        if distance_cm >= 400:
            print("Distance: OUT OF RANGE")
        else:
            print(f"Distance: {distance_cm:7.2f} cm")

        time.sleep(0.5)

except KeyboardInterrupt:
    print("\nStopping...")

finally:
    sensor.close()