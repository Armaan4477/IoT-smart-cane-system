package com.an.wrist.models;

import com.google.gson.annotations.SerializedName;
import java.util.Date;

public class SensorData {
    @SerializedName("id")
    private String id;
    
    @SerializedName("timestamp")
    private long timestamp;
    
    @SerializedName("serverTimestamp")
    private String serverTimestamp;
    
    @SerializedName("receivedAt")
    private String receivedAt;
    
    // Flat accelerometer data from API
    @SerializedName("accelX")
    private double accelX;
    
    @SerializedName("accelY")
    private double accelY;
    
    @SerializedName("accelZ")
    private double accelZ;
    
    // Flat gyroscope data from API
    @SerializedName("gyroX")
    private double gyroX;
    
    @SerializedName("gyroY")
    private double gyroY;
    
    @SerializedName("gyroZ")
    private double gyroZ;
    
    // Flat GPS data from API
    @SerializedName("latitude")
    private double latitude;
    
    @SerializedName("longitude")
    private double longitude;
    
    @SerializedName("altitude")
    private double altitude;
    
    @SerializedName("valid")
    private boolean valid;
    
    @SerializedName("satellites")
    private int satellites;
    
    // Additional sensor data
    @SerializedName("temperature")
    private double temperature;
    
    @SerializedName(value = "distance", alternate = {"distance_cm"})
    private double distance;
    
    @SerializedName("mpuValid")
    private boolean mpuValid;
    
    @SerializedName("ultrasonicValid")
    private boolean ultrasonicValid;

    @SerializedName("isOnline")
    private boolean online = true;

    @SerializedName("mpu6050")
    private MpuData mpu6050;
    
    @SerializedName("gps")
    private GPSData gpsData;
    
    @SerializedName("accelerometer")
    private AccelerometerData accelerometer;
    
    @SerializedName("gyroscope")
    private GyroscopeData gyroscope;

    public static class MpuData {
        @SerializedName("accel")
        public AccelerometerData accel;
        @SerializedName("gyro")
        public GyroscopeData gyro;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getServerTimestamp() {
        return serverTimestamp;
    }
    
    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }
    
    public String getReceivedAt() {
        return receivedAt;
    }
    
    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }
    
    // Helper methods to return data in nested object format for backward compatibility
    public AccelerometerData getAccelerometer() {
        if (mpu6050 != null && mpu6050.accel != null) {
            return mpu6050.accel;
        } else if (accelerometer != null) {
            return accelerometer;
        }
        AccelerometerData data = new AccelerometerData();
        data.setX(accelX);
        data.setY(accelY);
        data.setZ(accelZ);
        return data;
    }
    
    public void setAccelerometer(AccelerometerData accelerometer) {
        if (accelerometer != null) {
            this.accelX = accelerometer.getX();
            this.accelY = accelerometer.getY();
            this.accelZ = accelerometer.getZ();
        }
    }
    
    public GyroscopeData getGyroscope() {
        if (mpu6050 != null && mpu6050.gyro != null) {
            return mpu6050.gyro;
        } else if (gyroscope != null) {
            return gyroscope;
        }
        GyroscopeData data = new GyroscopeData();
        data.setX(gyroX);
        data.setY(gyroY);
        data.setZ(gyroZ);
        return data;
    }
    
    public void setGyroscope(GyroscopeData gyroscope) {
        if (gyroscope != null) {
            this.gyroX = gyroscope.getX();
            this.gyroY = gyroscope.getY();
            this.gyroZ = gyroscope.getZ();
        }
    }
    
    public GPSData getGps() {
        if (gpsData != null) {
            return gpsData;
        }
        GPSData data = new GPSData();
        data.setLatitude(latitude);
        data.setLongitude(longitude);
        data.setAltitude(altitude);
        return data;
    }
    
    public void setGps(GPSData gps) {
        if (gps != null) {
            this.latitude = gps.getLatitude();
            this.longitude = gps.getLongitude();
            this.altitude = gps.getAltitude();
        }
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public double getDistance() {
        return distance;
    }
    
    public void setDistance(double distance) {
        this.distance = distance;
    }
    
    public boolean isMpuValid() {
        return mpuValid;
    }
    
    public void setMpuValid(boolean mpuValid) {
        this.mpuValid = mpuValid;
    }
    
    public boolean isUltrasonicValid() {
        if (!ultrasonicValid && distance > 0) {
            return true;
        }
        return ultrasonicValid;
    }
    
    public void setUltrasonicValid(boolean ultrasonicValid) {
        this.ultrasonicValid = ultrasonicValid;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public int getSatellites() {
        return satellites;
    }
    
    public void setSatellites(int satellites) {
        this.satellites = satellites;
    }
    
    public Date getTimestampAsDate() {
        return new Date(timestamp);
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
    
    // Helper method to format timestamp to readable format
    public String getFormattedTimestamp() {
        return new Date(timestamp).toString();
    }
}