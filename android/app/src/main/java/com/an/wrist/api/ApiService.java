package com.an.wrist.api;

import com.an.wrist.models.SensorDataResponse;
import com.an.wrist.models.SensorDataRequest;
import com.an.wrist.models.PostResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Body;
import retrofit2.http.Query;

public interface ApiService {
    // Get latest sensor data
    @GET("api/sensors/latest")
    Call<SensorDataResponse> getLatestSensorData(@Query("limit") int limit);
    
    // Post sensor data
    @POST("api/sensors/data")
    Call<PostResponse> postSensorData(@Body SensorDataRequest request);
}