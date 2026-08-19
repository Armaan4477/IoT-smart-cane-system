package com.an.wrist.models;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for the API response after posting sensor data
 */
public class PostResponse {
    @SerializedName("message")
    private String message;
    
    @SerializedName("id")
    private String id;
    
    @SerializedName("timestamp")
    private String timestamp;
    
    // Getters and setters
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}