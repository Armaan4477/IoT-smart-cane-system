package com.an.wrist.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.an.wrist.api.ApiClient;
import com.an.wrist.api.ApiService;
import com.an.wrist.models.SensorData;
import com.an.wrist.models.SensorDataResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SensorRepository {
    private static final String TAG = "SensorRepository";
    private ApiService apiService;
    private MutableLiveData<Boolean> isLoading;
    private MutableLiveData<String> errorMessage;
    
    public SensorRepository(MutableLiveData<Boolean> isLoading, MutableLiveData<String> errorMessage) {
        this.apiService = ApiClient.getApiService();
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
    }
    
    public LiveData<List<SensorData>> getLatestSensorData(int limit) {
        final MutableLiveData<List<SensorData>> data = new MutableLiveData<>();
        
        // Set loading state
        if (isLoading != null) {
            isLoading.postValue(true);
        }
        
        Log.d(TAG, "Fetching latest sensor data with limit: " + limit);
        
        apiService.getLatestSensorData(limit).enqueue(new Callback<SensorDataResponse>() {
            @Override
            public void onResponse(Call<SensorDataResponse> call, Response<SensorDataResponse> response) {
                // Clear loading state
                if (isLoading != null) {
                    isLoading.postValue(false);
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SensorData> sensorDataList = response.body().getData();
                    Log.d(TAG, "Successfully fetched " + (sensorDataList != null ? sensorDataList.size() : 0) + " sensor data items");
                    data.setValue(sensorDataList);
                    
                    if (sensorDataList == null || sensorDataList.isEmpty()) {
                        if (errorMessage != null) {
                            errorMessage.postValue("No sensor data available");
                        }
                    }
                } else {
                    Log.e(TAG, "API response unsuccessful. Code: " + response.code() + ", Message: " + response.message());
                    if (errorMessage != null) {
                        errorMessage.postValue("Failed to load data: " + response.message());
                    }
                    data.setValue(null);
                }
            }
            
            @Override
            public void onFailure(Call<SensorDataResponse> call, Throwable t) {
                // Clear loading state
                if (isLoading != null) {
                    isLoading.postValue(false);
                }
                
                Log.e(TAG, "Failed to fetch sensor data", t);
                String errorMsg = "Network error: " + t.getMessage();
                
                if (errorMessage != null) {
                    errorMessage.postValue(errorMsg);
                }
                data.setValue(null);
            }
        });
        
        return data;
    }
    
    public LiveData<List<SensorData>> getLatestSensorDataSilent(int limit) {
        final MutableLiveData<List<SensorData>> data = new MutableLiveData<>();
        
        // Don't show loading indicator for silent refresh
        Log.d(TAG, "Silently fetching latest sensor data with limit: " + limit);
        
        apiService.getLatestSensorData(limit).enqueue(new Callback<SensorDataResponse>() {
            @Override
            public void onResponse(Call<SensorDataResponse> call, Response<SensorDataResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<SensorData> sensorDataList = response.body().getData();
                    Log.d(TAG, "Silently fetched " + (sensorDataList != null ? sensorDataList.size() : 0) + " sensor data items");
                    data.setValue(sensorDataList);
                } else {
                    Log.e(TAG, "Silent refresh - API response unsuccessful. Code: " + response.code());
                    // Don't show error messages for silent refresh
                    data.setValue(null);
                }
            }
            
            @Override
            public void onFailure(Call<SensorDataResponse> call, Throwable t) {
                Log.e(TAG, "Silent refresh failed", t);
                // Don't show error messages for silent refresh
                data.setValue(null);
            }
        });
        
        return data;
    }
}