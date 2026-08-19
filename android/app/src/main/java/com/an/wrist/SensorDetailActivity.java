package com.an.wrist;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.an.wrist.models.AccelerometerData;
import com.an.wrist.models.GPSData;
import com.an.wrist.models.GyroscopeData;
import com.an.wrist.models.SensorData;
import com.an.wrist.viewmodel.SensorViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SensorDetailActivity extends AppCompatActivity {

    private SensorViewModel viewModel;
    private String sensorType;
    
    private TextView detailSensorType;
    private TextView detailTimestamp;
    private LinearLayout detailReadingsContainer;
    private LineChart detailChart;
    
    private List<SensorData> sensorDataList;
    
    // Auto-refresh handler
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 7000; // 7 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);
        
        // Get sensor type from intent
        sensorType = getIntent().getStringExtra("sensor_type");
        if (sensorType == null) {
            sensorType = "accelerometer"; // Default
        }
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.detailToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getSensorTitle(sensorType));
        
        // Initialize views
        detailSensorType = findViewById(R.id.detailSensorType);
        detailTimestamp = findViewById(R.id.detailTimestamp);
        detailReadingsContainer = findViewById(R.id.detailReadingsContainer);
        detailChart = findViewById(R.id.detailChart);
        
        // Set sensor type
        detailSensorType.setText(getSensorTitle(sensorType));
        
        // Setup chart
        setupChart(detailChart, getSensorColor(sensorType));
        
        // Setup view model
        viewModel = new ViewModelProvider(this).get(SensorViewModel.class);
        
        // Load data
        loadSensorData();
        
        // Setup auto-refresh
        setupAutoRefresh();
    }
    
    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadSensorDataSilent();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        // Start auto-refresh
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Resume auto-refresh when activity comes to foreground
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Stop auto-refresh when activity goes to background
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    private String getSensorTitle(String type) {
        switch (type.toLowerCase()) {
            case "accelerometer":
                return getString(R.string.accelerometer);
            case "gyroscope":
                return getString(R.string.gyroscope);
            case "ultrasonic":
                return getString(R.string.ultrasonic);
            case "temperature":
                return getString(R.string.temperature);
            case "gps":
                return getString(R.string.gps);
            default:
                return "Sensor Details";
        }
    }
    
    private int getSensorColor(String type) {
        switch (type.toLowerCase()) {
            case "accelerometer":
                return getColor(R.color.colorAccelerometerChart);
            case "gyroscope":
                return getColor(R.color.colorGyroscopeChart);
            case "ultrasonic":
                return getColor(R.color.colorUltrasonicChart);
            case "temperature":
                return getColor(R.color.colorTemperatureChart);
            default:
                return getColor(R.color.colorPrimary);
        }
    }
    
    private void setupChart(LineChart chart, int color) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setNoDataText("No data available");
        
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        
        chart.getLegend().setEnabled(true);
        
        // Empty data initially
        LineData data = new LineData();
        data.setValueTextColor(color);
        chart.setData(data);
    }
    
    private void loadSensorData() {
        viewModel.getLatestSensorData(20).observe(this, data -> {
            if (data != null && !data.isEmpty()) {
                sensorDataList = data;
                updateUI(data);
            } else if (data != null && data.isEmpty()) {
                updateOfflineDetails();
            }
        });
    }
    
    private void loadSensorDataSilent() {
        viewModel.getLatestSensorDataSilent(20).observe(this, data -> {
            if (data != null && !data.isEmpty()) {
                sensorDataList = data;
                updateUI(data);
            } else if (data != null && data.isEmpty()) {
                updateOfflineDetails();
            }
            // Silent refresh - no loading indicators or error messages
        });
    }
    
    private void updateUI(List<SensorData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // Get most recent data
        SensorData latestData = dataList.get(0);

        if (!latestData.isOnline()) {
            updateOfflineDetails();
            return;
        }
        
        // Update timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault());
        String formattedTime = sdf.format(latestData.getTimestampAsDate());
        detailTimestamp.setText(formattedTime);
        
        // Clear previous readings
        detailReadingsContainer.removeAllViews();
        
        // Update readings and chart based on sensor type
        switch (sensorType.toLowerCase()) {
            case "accelerometer":
                updateAccelerometerDetails(dataList);
                break;
            case "gyroscope":
                updateGyroscopeDetails(dataList);
                break;
            case "ultrasonic":
                updateUltrasonicDetails(dataList);
                break;
            case "temperature":
                updateTemperatureDetails(dataList);
                break;
            case "gps":
                updateGPSDetails(dataList);
                break;
        }
    }

    private void updateOfflineDetails() {
        detailTimestamp.setText("--");
        detailReadingsContainer.removeAllViews();

        addReadingRow("Status", "Offline", "");
        addReadingRow("Reading", "--", "");

        detailChart.clear();
        detailChart.setNoDataText("Offline");
        detailChart.invalidate();
    }
    
    private void updateAccelerometerDetails(List<SensorData> dataList) {
        // Get latest data
        SensorData latestData = dataList.get(0);
        AccelerometerData accel = latestData.getAccelerometer();
        
        if (accel != null) {
            // Add readings
            addReadingRow("X-Axis", String.valueOf(accel.getX()), "m/s²");
            addReadingRow("Y-Axis", String.valueOf(accel.getY()), "m/s²");
            addReadingRow("Z-Axis", String.valueOf(accel.getZ()), "m/s²");
            addReadingRow("Magnitude", String.format(Locale.getDefault(), "%.2f", accel.getMagnitude()), "m/s²");
            
            // Update chart
            List<Entry> xEntries = new ArrayList<>();
            List<Entry> yEntries = new ArrayList<>();
            List<Entry> zEntries = new ArrayList<>();
            
            for (int i = dataList.size() - 1; i >= 0; i--) {
                SensorData data = dataList.get(i);
                if (data.getAccelerometer() != null) {
                    float index = dataList.size() - 1 - i;
                    AccelerometerData a = data.getAccelerometer();
                    xEntries.add(new Entry(index, (float) a.getX()));
                    yEntries.add(new Entry(index, (float) a.getY()));
                    zEntries.add(new Entry(index, (float) a.getZ()));
                }
            }
            
            updateChart(xEntries, yEntries, zEntries, getColor(R.color.colorAccelerometerChart));
        }
    }
    
    private void updateGyroscopeDetails(List<SensorData> dataList) {
        // Get latest data
        SensorData latestData = dataList.get(0);
        GyroscopeData gyro = latestData.getGyroscope();
        
        if (gyro != null) {
            // Add readings
            addReadingRow("X-Axis", String.valueOf(gyro.getX()), "rad/s");
            addReadingRow("Y-Axis", String.valueOf(gyro.getY()), "rad/s");
            addReadingRow("Z-Axis", String.valueOf(gyro.getZ()), "rad/s");
            
            // Update chart
            List<Entry> xEntries = new ArrayList<>();
            List<Entry> yEntries = new ArrayList<>();
            List<Entry> zEntries = new ArrayList<>();
            
            for (int i = dataList.size() - 1; i >= 0; i--) {
                SensorData data = dataList.get(i);
                if (data.getGyroscope() != null) {
                    float index = dataList.size() - 1 - i;
                    GyroscopeData g = data.getGyroscope();
                    xEntries.add(new Entry(index, (float) g.getX()));
                    yEntries.add(new Entry(index, (float) g.getY()));
                    zEntries.add(new Entry(index, (float) g.getZ()));
                }
            }
            
            updateChart(xEntries, yEntries, zEntries, getColor(R.color.colorGyroscopeChart));
        }
    }
    
    private void updateUltrasonicDetails(List<SensorData> dataList) {
        // Get latest data
        SensorData latestData = dataList.get(0);
        
        if (latestData.isUltrasonicValid()) {
            // Add readings
            addReadingRow("Distance", String.format(Locale.getDefault(), "%.2f", latestData.getDistance()), "cm");
            addReadingRow("Sensor Status", latestData.isUltrasonicValid() ? "Valid" : "Invalid", "");
            
            // Show proximity alert status
            String proximityStatus = latestData.getDistance() < 50 ? "Alert - Too Close!" : "Safe Distance";
            addReadingRow("Proximity", proximityStatus, "");
            
            // Update chart with distance over time
            List<Entry> distanceEntries = new ArrayList<>();
            
            for (int i = dataList.size() - 1; i >= 0; i--) {
                SensorData data = dataList.get(i);
                if (data.isUltrasonicValid()) {
                    float index = dataList.size() - 1 - i;
                    distanceEntries.add(new Entry(index, (float) data.getDistance()));
                }
            }
            
            updateSingleLineChart(distanceEntries, "Distance (cm)", getColor(R.color.colorUltrasonicChart));
        }
    }
    
    private void updateTemperatureDetails(List<SensorData> dataList) {
        // Get latest data
        SensorData latestData = dataList.get(0);
        
        // Add readings
        addReadingRow("Current Temperature", String.format(Locale.getDefault(), "%.2f", latestData.getTemperature()), "°C");
        addReadingRow("In Fahrenheit", String.format(Locale.getDefault(), "%.2f", latestData.getTemperature() * 9/5 + 32), "°F");
        
        // Calculate min, max, avg from the list
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0;
        int count = 0;
        
        for (SensorData data : dataList) {
            double temp = data.getTemperature();
            if (temp > 0) {  // Filter out invalid readings
                min = Math.min(min, temp);
                max = Math.max(max, temp);
                sum += temp;
                count++;
            }
        }
        
        if (count > 0) {
            double avg = sum / count;
            addReadingRow("Average", String.format(Locale.getDefault(), "%.2f", avg), "°C");
            addReadingRow("Minimum", String.format(Locale.getDefault(), "%.2f", min), "°C");
            addReadingRow("Maximum", String.format(Locale.getDefault(), "%.2f", max), "°C");
        }
        
        // Update chart with temperature over time
        List<Entry> temperatureEntries = new ArrayList<>();
        
        for (int i = dataList.size() - 1; i >= 0; i--) {
            SensorData data = dataList.get(i);
            float index = dataList.size() - 1 - i;
            temperatureEntries.add(new Entry(index, (float) data.getTemperature()));
        }
        
        updateSingleLineChart(temperatureEntries, "Temperature (°C)", getColor(R.color.colorTemperatureChart));
    }
    
    private void updateGPSDetails(List<SensorData> dataList) {
        // Get latest data
        SensorData latestData = dataList.get(0);
        GPSData gps = latestData.getGps();
        
        if (gps != null) {
            // Add readings
            addReadingRow("Latitude", String.valueOf(gps.getLatitude()), "°");
            addReadingRow("Longitude", String.valueOf(gps.getLongitude()), "°");
            addReadingRow("Altitude", String.valueOf(gps.getAltitude()), "m");
            addReadingRow("Speed", String.valueOf(gps.getSpeed()), "m/s");
            addReadingRow("Accuracy", String.valueOf(gps.getAccuracy()), "m");
            
            // For GPS we could show a map instead of a chart, 
            // but we'll just update altitude over time for simplicity
            List<Entry> altitudeEntries = new ArrayList<>();
            
            for (int i = dataList.size() - 1; i >= 0; i--) {
                SensorData data = dataList.get(i);
                if (data.getGps() != null) {
                    float index = dataList.size() - 1 - i;
                    GPSData g = data.getGps();
                    altitudeEntries.add(new Entry(index, (float) g.getAltitude()));
                }
            }
            
            updateSingleLineChart(altitudeEntries, "Altitude (m)", getColor(R.color.colorPrimary));
        }
    }
    
    private void addReadingRow(String label, String value, String unit) {
        View view = getLayoutInflater().inflate(R.layout.item_sensor_reading, null);
        TextView labelText = view.findViewById(R.id.readingLabel);
        TextView valueText = view.findViewById(R.id.readingValue);
        TextView unitText = view.findViewById(R.id.readingUnit);
        
        labelText.setText(label);
        valueText.setText(value);
        unitText.setText(unit);
        
        detailReadingsContainer.addView(view);
    }
    
    private void updateChart(List<Entry> xEntries, List<Entry> yEntries, List<Entry> zEntries, int color) {
        LineDataSet xDataSet = new LineDataSet(xEntries, "X-Axis");
        LineDataSet yDataSet = new LineDataSet(yEntries, "Y-Axis");
        LineDataSet zDataSet = new LineDataSet(zEntries, "Z-Axis");
        
        styleLineDataSet(xDataSet, color, false);
        styleLineDataSet(yDataSet, color, true);
        styleLineDataSet(zDataSet, color, true);
        
        LineData lineData = new LineData(xDataSet, yDataSet, zDataSet);
        detailChart.setData(lineData);
        detailChart.invalidate();
    }
    
    private void updateSingleLineChart(List<Entry> entries, String label, int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        styleLineDataSet(dataSet, color, false);
        
        LineData lineData = new LineData(dataSet);
        detailChart.setData(lineData);
        detailChart.invalidate();
    }
    
    private void styleLineDataSet(LineDataSet dataSet, int color, boolean dashed) {
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(false);
        dataSet.setFormLineWidth(1f);
        dataSet.setFormSize(15f);
        
        if (dashed) {
            dataSet.enableDashedLine(10f, 5f, 0f);
            dataSet.setColor(color & 0x80FFFFFF); // Semi-transparent
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}