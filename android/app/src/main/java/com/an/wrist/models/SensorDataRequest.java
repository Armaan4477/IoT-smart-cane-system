package com.an.wrist.models;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for sending sensor data to the API
 * Structured to match the expected format for the NextJS API endpoint
 */
public class SensorDataRequest {
    // Device identifier
    @SerializedName("deviceId")
    private String deviceId;
    
    // Client timestamp (when the data was recorded)
    @SerializedName("timestamp")
    private long timestamp;
    
    // Accelerometer data
    @SerializedName("accelerometer")
    private AccelerometerData accelerometer;
    
    // Gyroscope data
    @SerializedName("gyroscope")
    private GyroscopeData gyroscope;
    
    // GPS data
    @SerializedName("gps")
    private GPSData gps;
    
    // Constructor
    public SensorDataRequest() {
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public AccelerometerData getAccelerometer() {
        return accelerometer;
    }
    
    public void setAccelerometer(AccelerometerData accelerometer) {
        this.accelerometer = accelerometer;
    }
    
    public GyroscopeData getGyroscope() {
        return gyroscope;
    }
    
    public void setGyroscope(GyroscopeData gyroscope) {
        this.gyroscope = gyroscope;
    }
    
    public GPSData getGps() {
        return gps;
    }
    
    public void setGps(GPSData gps) {
        this.gps = gps;
    }
}