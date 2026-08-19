#!/usr/bin/env python3

import time
import math
from smbus2 import SMBus

I2C_BUS = 1
MPU6050_ADDRESS = 0x68

bus = SMBus(I2C_BUS)


def read_signed_word(register):

    high = bus.read_byte_data(
        MPU6050_ADDRESS,
        register
    )

    low = bus.read_byte_data(
        MPU6050_ADDRESS,
        register + 1
    )

    value = (high << 8) | low

    if value >= 32768:
        value -= 65536

    return value


def read_sensor():

    # Accelerometer
    raw_ax = read_signed_word(0x3B)
    raw_ay = read_signed_word(0x3D)
    raw_az = read_signed_word(0x3F)

    # Gyroscope
    raw_gx = read_signed_word(0x43)
    raw_gy = read_signed_word(0x45)
    raw_gz = read_signed_word(0x47)

    # Convert accelerometer to g
    ax = raw_ax / 16384.0
    ay = raw_ay / 16384.0
    az = raw_az / 16384.0

    # Convert gyro to degrees/second
    gx = raw_gx / 131.0
    gy = raw_gy / 131.0
    gz = raw_gz / 131.0

    total_accel = math.sqrt(
        ax**2 +
        ay**2 +
        az**2
    )

    return ax, ay, az, gx, gy, gz, total_accel


# Wake MPU6050
bus.write_byte_data(
    MPU6050_ADDRESS,
    0x6B,
    0x00
)

print("===================================")
print("          MPU6050 TEST")
print("===================================")
print("Press CTRL+C to stop.\n")

try:

    while True:

        (
            ax,
            ay,
            az,
            gx,
            gy,
            gz,
            total
        ) = read_sensor()

        print(
            f"ACCEL  "
            f"X: {ax:7.3f} g  "
            f"Y: {ay:7.3f} g  "
            f"Z: {az:7.3f} g"
        )

        print(
            f"GYRO   "
            f"X: {gx:7.2f} °/s  "
            f"Y: {gy:7.2f} °/s  "
            f"Z: {gz:7.2f} °/s"
        )

        print(
            f"TOTAL ACCELERATION: {total:7.3f} g"
        )

        print("-" * 55)

        time.sleep(0.5)

except KeyboardInterrupt:

    print("\nStopping...")

finally:

    bus.close()