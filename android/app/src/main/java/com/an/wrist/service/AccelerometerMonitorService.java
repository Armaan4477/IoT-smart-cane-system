package com.an.wrist.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.an.wrist.MainActivity;
import com.an.wrist.R;
import com.an.wrist.api.ApiClient;
import com.an.wrist.models.SensorData;
import com.an.wrist.models.SensorDataResponse;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Background service to monitor accelerometer data for sudden spikes
 * Runs even when the app is closed and sends notifications for anomalies
 */
public class AccelerometerMonitorService extends Service {
    
    private static final String TAG = "AccelMonitorService";
    private static final String CHANNEL_ID = "accelerometer_monitoring_channel";
    private static final String ALERT_CHANNEL_ID = "accelerometer_alert_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ALERT_NOTIFICATION_ID = 1002;
    
    // Monitoring interval (7 seconds)
    private static final long MONITORING_INTERVAL = 7000;
    
    // Spike detection threshold (magnitude change in raw MPU6050 units)
    // MPU6050 outputs raw values, not m/s². The ESP32 uses 8000 as threshold for single-axis shock detection.
    // For magnitude changes (sqrt(x² + y² + z²)), a single-axis change of 8000 results in a smaller magnitude change.
    // Typical values: normal movement < 2000, significant shock 2000-5000, major impact > 5000
    private static final double SPIKE_THRESHOLD = 6500.0; // Threshold for detecting actual shocks/falls
    private static final double NORMAL_GRAVITY = 12384.0; // Normal gravity in raw MPU6050 units (1g at ±2g sensitivity)
    
    private Handler monitoringHandler;
    private Runnable monitoringRunnable;
    private double lastMagnitude = NORMAL_GRAVITY;
    private boolean isFirstReading = true;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AccelerometerMonitorService created");
        
        // Create notification channels
        createNotificationChannels();
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createForegroundNotification());
        
        // Initialize monitoring
        setupMonitoring();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AccelerometerMonitorService started");
        
        // Start monitoring if not already running
        if (monitoringHandler != null && monitoringRunnable != null) {
            monitoringHandler.postDelayed(monitoringRunnable, MONITORING_INTERVAL);
        }
        
        // Restart service if killed by system
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AccelerometerMonitorService destroyed");
        
        // Clean up handler
        if (monitoringHandler != null && monitoringRunnable != null) {
            monitoringHandler.removeCallbacks(monitoringRunnable);
        }
    }
    
    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            
            // Channel for foreground service notification
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Accelerometer Monitoring",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Background monitoring of accelerometer data");
            notificationManager.createNotificationChannel(serviceChannel);
            
            // Channel for alert notifications
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Accelerometer Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Alerts for sudden accelerometer spikes");
            alertChannel.enableVibration(true);
            alertChannel.setVibrationPattern(new long[]{0, 500, 250, 500});
            
            // Set custom notification sound
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.notification_sound);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            alertChannel.setSound(soundUri, audioAttributes);
            
            notificationManager.createNotificationChannel(alertChannel);
        }
    }
    
    /**
     * Create foreground notification
     */
    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Elderly Care Monitor Active")
                .setContentText("Monitoring accelerometer data in background")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    /**
     * Setup monitoring handler and runnable
     */
    private void setupMonitoring() {
        monitoringHandler = new Handler(Looper.getMainLooper());
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                // Fetch latest sensor data
                fetchLatestSensorData();
                
                // Schedule next check
                monitoringHandler.postDelayed(this, MONITORING_INTERVAL);
            }
        };
    }
    
    /**
     * Fetch latest sensor data from API
     */
    private void fetchLatestSensorData() {
        ApiClient.getApiService().getLatestSensorData(1).enqueue(new Callback<SensorDataResponse>() {
            @Override
            public void onResponse(Call<SensorDataResponse> call, Response<SensorDataResponse> response) {
                if (response.isSuccessful() && response.body() != null && 
                    response.body().getData() != null && !response.body().getData().isEmpty()) {
                    SensorData latestData = response.body().getData().get(0);
                    checkForSpike(latestData);
                } else {
                    Log.w(TAG, "No sensor data available or empty response");
                }
            }
            
            @Override
            public void onFailure(Call<SensorDataResponse> call, Throwable t) {
                Log.e(TAG, "Failed to fetch sensor data", t);
            }
        });
    }
    
    /**
     * Check for sudden spike in accelerometer data
     */
    private void checkForSpike(SensorData data) {
        if (!data.isOnline()) {
            Log.w(TAG, "Skipping spike check - device reported offline");
            return;
        }

        if (data.getAccelerometer() == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check if data is fresh (less than 60 seconds old)
        long dataAge = currentTime - data.getTimestamp();
        if (dataAge > 60000) { // Data is older than 60 seconds
            Log.w(TAG, "Skipping spike check - data is stale (age: " + (dataAge / 1000) + " seconds)");
            return;
        }
        
        double currentMagnitude = data.getAccelerometer().getMagnitude();
        
        // Skip first reading to establish baseline
        if (isFirstReading) {
            lastMagnitude = currentMagnitude;
            isFirstReading = false;
            Log.d(TAG, "Baseline magnitude set: " + currentMagnitude);
            return;
        }
        
        // Calculate change in magnitude
        double magnitudeChange = Math.abs(currentMagnitude - lastMagnitude);
        
        Log.d(TAG, String.format("Current: %.2f, Last: %.2f, Change: %.2f", 
                currentMagnitude, lastMagnitude, magnitudeChange));
        
        // Check if spike exceeds threshold
        if (magnitudeChange > SPIKE_THRESHOLD) {
            Log.w(TAG, "SPIKE DETECTED! Change: " + magnitudeChange);
            sendSpikeNotification(currentMagnitude, lastMagnitude, magnitudeChange);
        }
        
        // Update last magnitude
        lastMagnitude = currentMagnitude;
    }
    
    /**
     * Send notification for detected spike
     */
    private void sendSpikeNotification(double current, double previous, double change) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = String.format(Locale.getDefault(),
                "Sudden spike detected! Change: %.0f units", change);
        String bigText = String.format(Locale.getDefault(),
                "Previous: %.0f units\nCurrent: %.0f units\nChange: %.0f units",
                previous, current, change);
        
        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("⚠️ Accelerometer Spike Alert")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setSmallIcon(R.drawable.ic_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 250, 500})
                .build();
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification);
    }
}
