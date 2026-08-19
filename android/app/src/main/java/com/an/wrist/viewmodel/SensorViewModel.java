package com.an.wrist.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.an.wrist.models.AccelerometerData;
import com.an.wrist.models.GPSData;
import com.an.wrist.models.GyroscopeData;
import com.an.wrist.models.SensorData;
import com.an.wrist.repository.SensorRepository;
import com.an.wrist.repository.SensorDataRepository;

import java.util.List;

public class SensorViewModel extends AndroidViewModel {
    private SensorRepository repository;
    private SensorDataRepository dataRepository;
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    public SensorViewModel(Application application) {
        super(application);
        repository = new SensorRepository(isLoading, errorMessage);
        dataRepository = new SensorDataRepository(application);
    }
    
    public LiveData<List<SensorData>> getLatestSensorData(int limit) {
        return repository.getLatestSensorData(limit);
    }
    
    public LiveData<List<SensorData>> getLatestSensorDataSilent(int limit) {
        return repository.getLatestSensorDataSilent(limit);
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
        isLoading.setValue(true);
        dataRepository.sendSensorData(accelerometerData, gyroscopeData, gpsData);
    }
    
    /**
     * Get status message LiveData
     * 
     * @return Status message LiveData
     */
    public LiveData<String> getStatusMessage() {
        return dataRepository.getStatusMessage();
    }
    
    /**
     * Get error message LiveData
     * 
     * @return Error message LiveData
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}