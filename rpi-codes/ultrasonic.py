#!/usr/bin/env python3

import time
from gpiozero import DistanceSensor

class UltrasonicSensor:
    def __init__(self, trig_pin=23, echo_pin=24):
        self.sensor = DistanceSensor(
            trigger=trig_pin,
            echo=echo_pin,
            max_distance=4.0,
            queue_len=1
        )

    def get_distance_cm(self):
        return self.sensor.distance * 100

    def close(self):
        self.sensor.close()

if __name__ == "__main__":
    print("===================================")
    print("       HC-SR04 ULTRASONIC TEST")
    print("===================================")
    print("Press CTRL+C to stop.\n")

    us = UltrasonicSensor()
    try:
        while True:
            distance_cm = us.get_distance_cm()

            if distance_cm >= 400:
                print("Distance: OUT OF RANGE")
            else:
                print(f"Distance: {distance_cm:7.2f} cm")

            time.sleep(0.5)

    except KeyboardInterrupt:
        print("\nStopping...")

    finally:
        us.close()