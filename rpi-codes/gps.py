#!/usr/bin/env python3

import serial

GPS_PORT = "/dev/serial0"
GPS_BAUD = 9600

gps = serial.Serial(
    port=GPS_PORT,
    baudrate=GPS_BAUD,
    timeout=1
)

print("===================================")
print("          NEO-6M GPS TEST")
print("===================================")
print("Waiting for GPS NMEA data...")
print("Press CTRL+C to stop.\n")

try:

    while True:

        line = gps.readline().decode(
            "ascii",
            errors="ignore"
        ).strip()

        if line:
            print(line)

except KeyboardInterrupt:

    print("\nStopping...")

finally:

    gps.close()