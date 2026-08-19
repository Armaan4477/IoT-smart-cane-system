package com.an.wrist.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SensorDataResponse {
    @SerializedName("data")
    private List<SensorData> data;
    
    // Getters and setters
    public List<SensorData> getData() {
        return data;
    }
    
    public void setData(List<SensorData> data) {
        this.data = data;
    }
}