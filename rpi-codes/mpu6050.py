#!/usr/bin/env python3

import time
import math
from smbus2 import SMBus

class MPU6050Sensor:
    def __init__(self, bus_num=1, address=0x68):
        self.bus_num = bus_num
        self.address = address
        self.bus = SMBus(self.bus_num)
        
        # Wake MPU6050
        self.bus.write_byte_data(self.address, 0x6B, 0x00)

    def read_signed_word(self, register):
        high = self.bus.read_byte_data(self.address, register)
        low = self.bus.read_byte_data(self.address, register + 1)
        value = (high << 8) | low
        if value >= 32768:
            value -= 65536
        return value

    def get_data(self):
        # Accelerometer
        raw_ax = self.read_signed_word(0x3B)
        raw_ay = self.read_signed_word(0x3D)
        raw_az = self.read_signed_word(0x3F)

        # Gyroscope
        raw_gx = self.read_signed_word(0x43)
        raw_gy = self.read_signed_word(0x45)
        raw_gz = self.read_signed_word(0x47)

        # Convert accelerometer to g
        ax = raw_ax / 16384.0
        ay = raw_ay / 16384.0
        az = raw_az / 16384.0

        # Convert gyro to degrees/second
        gx = raw_gx / 131.0
        gy = raw_gy / 131.0
        gz = raw_gz / 131.0

        total_accel = math.sqrt(ax**2 + ay**2 + az**2)

        return {
            "accel": {"x": ax, "y": ay, "z": az},
            "gyro": {"x": gx, "y": gy, "z": gz},
            "total_accel": total_accel
        }

    def close(self):
        self.bus.close()


if __name__ == "__main__":
    print("===================================")
    print("          MPU6050 TEST")
    print("===================================")
    print("Press CTRL+C to stop.\n")

    mpu = MPU6050Sensor()
    try:
        while True:
            data = mpu.get_data()
            ax, ay, az = data["accel"]["x"], data["accel"]["y"], data["accel"]["z"]
            gx, gy, gz = data["gyro"]["x"], data["gyro"]["y"], data["gyro"]["z"]
            total = data["total_accel"]

            print(f"ACCEL  X: {ax:7.3f} g  Y: {ay:7.3f} g  Z: {az:7.3f} g")
            print(f"GYRO   X: {gx:7.2f} °/s  Y: {gy:7.2f} °/s  Z: {gz:7.2f} °/s")
            print(f"TOTAL ACCELERATION: {total:7.3f} g")
            print("-" * 55)

            time.sleep(0.5)

    except KeyboardInterrupt:
        print("\nStopping...")

    finally:
        mpu.close()