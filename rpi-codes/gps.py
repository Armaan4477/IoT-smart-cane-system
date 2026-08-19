#!/usr/bin/env python3

import serial
import time

class GPSSensor:
    def __init__(self, port="/dev/serial0", baud=9600):
        self.gps = serial.Serial(
            port=port,
            baudrate=baud,
            timeout=1
        )
        # Store the last known location
        self.last_lat = None
        self.last_lon = None

    def _parse_gpgga(self, nmea_sentence):
        # Example GPGGA:
        # $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
        parts = nmea_sentence.split(',')
        if len(parts) >= 6 and parts[2] and parts[4]:
            try:
                # Lat
                lat_raw = parts[2]
                lat_dir = parts[3]
                lat_deg = float(lat_raw[:2])
                lat_min = float(lat_raw[2:])
                lat = lat_deg + (lat_min / 60.0)
                if lat_dir == 'S': 
                    lat = -lat
                
                # Lon
                lon_raw = parts[4]
                lon_dir = parts[5]
                # Longitude degrees can be 3 digits
                lon_deg = float(lon_raw[:3])
                lon_min = float(lon_raw[3:])
                lon = lon_deg + (lon_min / 60.0)
                if lon_dir == 'W': 
                    lon = -lon
                
                return lat, lon
            except ValueError:
                pass
        return None, None

    def update(self):
        # Read all available lines in the buffer
        # This prevents falling behind the NMEA stream
        while self.gps.in_waiting > 0:
            try:
                line = self.gps.readline().decode("ascii", errors="ignore").strip()
                if line.startswith("$GPGGA"):
                    lat, lon = self._parse_gpgga(line)
                    if lat is not None and lon is not None:
                        self.last_lat = lat
                        self.last_lon = lon
            except Exception:
                pass

    def get_location(self):
        # Call update to process any pending data
        self.update()
        return {
            "lat": self.last_lat,
            "lon": self.last_lon
        }

    def close(self):
        self.gps.close()

if __name__ == "__main__":
    print("===================================")
    print("          NEO-6M GPS TEST")
    print("===================================")
    print("Waiting for GPS NMEA data...")
    print("Press CTRL+C to stop.\n")

    gps_sensor = GPSSensor()
    try:
        while True:
            # We call update constantly in the loop
            gps_sensor.update()
            loc = gps_sensor.get_location()
            if loc["lat"] is not None:
                print(f"Latitude: {loc['lat']:.6f}, Longitude: {loc['lon']:.6f}")
            else:
                print("No fix yet...")
            time.sleep(1)

    except KeyboardInterrupt:
        print("\nStopping...")

    finally:
        gps_sensor.close()