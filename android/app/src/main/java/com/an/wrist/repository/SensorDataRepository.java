package com.an.wrist.repository;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.an.wrist.api.ApiClient;
import com.an.wrist.models.AccelerometerData;
import com.an.wrist.models.GPSData;
import com.an.wrist.models.GyroscopeData;
import com.an.wrist.models.PostResponse;
import com.an.wrist.models.SensorDataRequest;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository class for sending sensor data to the API
 */
public class SensorDataRepository {
    private static final String TAG = "SensorDataRepository";
    private final Context context;
    
    // Status LiveData
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    
    public SensorDataRepository(Context context) {
        this.context = context;
    }
    
    /**
     * Send sensor data to the API
     * 
     * @param accelerometerData Accelerometer data
     * @param gyroscopeData Gyroscope data
     * @param gpsData GPS data
     */
    public void sendSensorData(AccelerometerData accelerometerData, 
                              GyroscopeData gyroscopeData, 
                              GPSData gpsData) {
        // Create request object
        SensorDataRequest request = new SensorDataRequest();
        request.setDeviceId(getDeviceId());
        request.setAccelerometer(accelerometerData);
        request.setGyroscope(gyroscopeData);
        request.setGps(gpsData);
        
        // Set loading state
        isLoading.setValue(true);
        
        // Send data to API
        ApiClient.getApiService().postSensorData(request).enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                isLoading.setValue(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    statusMessage.setValue("Data sent successfully! ID: " + response.body().getId());
                    Log.d(TAG, "Sensor data sent successfully: " + response.body().getMessage());
                } else {
                    statusMessage.setValue("Failed to send data: " + 
                            (response.errorBody() != null ? response.code() : "Unknown error"));
                    Log.e(TAG, "Error sending sensor data: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {
                isLoading.setValue(false);
                statusMessage.setValue("Network error: " + t.getMessage());
                Log.e(TAG, "Network error when sending sensor data", t);
            }
        });
    }
    
    /**
     * Get a unique device ID
     * 
     * @return Device ID string
     */
    private String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), 
                Settings.Secure.ANDROID_ID);
    }
    
    /**
     * Get status message LiveData
     * 
     * @return Status message LiveData
     */
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    /**
     * Get loading state LiveData
     * 
     * @return Loading state LiveData
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}