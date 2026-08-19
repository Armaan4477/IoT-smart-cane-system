package com.an.wrist.models;

import com.google.gson.annotations.SerializedName;

public class GPSData {
    @SerializedName(value = "latitude", alternate = {"lat"})
    private double latitude;
    
    @SerializedName(value = "longitude", alternate = {"lon"})
    private double longitude;
    
    @SerializedName("altitude")
    private double altitude;
    
    @SerializedName("speed")
    private double speed;
    
    @SerializedName("accuracy")
    private double accuracy;
    
    // Getters and setters
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public double getAltitude() {
        return altitude;
    }
    
    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }
    
    public double getSpeed() {
        return speed;
    }
    
    public void setSpeed(double speed) {
        this.speed = speed;
    }
    
    public double getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
    
    @Override
    public String toString() {
        return "Lat: " + latitude + ", Lng: " + longitude;
    }
}