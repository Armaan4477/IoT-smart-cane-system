package com.an.wrist;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.an.wrist.models.AccelerometerData;
import com.an.wrist.models.GPSData;
import com.an.wrist.models.GyroscopeData;
import com.an.wrist.models.SensorData;
import com.an.wrist.service.AccelerometerMonitorService;
import com.an.wrist.service.BluetoothService;
import com.an.wrist.viewmodel.SensorViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2001;
    private static final int MICROPHONE_PERMISSION_REQUEST_CODE = 2002;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 2003;
    private static final long DASHBOARD_HOLD_DURATION_MS = 4000L;
    private static final long VOICE_PROMPT_DELAY_MS = 2200L;
    private static final long HOLD_COUNTDOWN_TICK_MS = 120L;

    private SensorViewModel viewModel;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout loadingLayout;
    private TextView lastUpdatedText;
    private TextView statusText;
    private ImageView statusIndicator;

    // Accelerometer views
    private TextView accelX, accelY, accelZ;
    private View accelerometerCard;

    // Gyroscope views
    private TextView gyroX, gyroY, gyroZ;
    private View gyroscopeCard;

    // Ultrasonic views
    private TextView ultrasonicValue;
    private View ultrasonicCard;

    // Temperature views
    private TextView temperatureValue;
    private View temperatureCard;

    // GPS views
    private TextView gpsTitle;
    private TextView locationText;
    private MaterialButton gpsDetailButton;
    private View gpsCard;

    // Latest data
    private List<SensorData> sensorDataList = new ArrayList<>();
    
    // Auto-refresh handler
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 7000; // 7 seconds

    // Bluetooth command receiver — replaces the previous HTTP polling loop
    private BroadcastReceiver bluetoothCommandReceiver;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private String lastSpokenCommand = "";

    // Voice entry mode (4-second hold anywhere on dashboard)
    private Handler voiceEntryHandler;
    private Runnable voiceEntryRunnable;
    private Runnable holdCountdownRunnable;
    private boolean voiceEntryTriggered;
    private long voiceHoldStartedAtMs = 0L;
    private int lastHapticCountdownSecond = -1;
    private float holdStartX;
    private float holdStartY;
    private int holdTouchSlopPx;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;
    private FrameLayout voiceHoldOverlay;
    private TextView voiceHoldCountdownText;
    private TextView voiceHoldHintText;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ensureNotificationPermission();
        startAccelerometerMonitoringService();
        
        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup view model - Initialize view model before initializing views
        viewModel = new ViewModelProvider(this).get(SensorViewModel.class);
        
        // Initialize views
        initializeViews();
        
        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            loadingLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            swipeRefreshLayout.setRefreshing(isLoading);
        });
        
        // Observe status message
        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });

        // Initialize text-to-speech and Bluetooth command receiver
        textToSpeech = new TextToSpeech(this, this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        setupBluetoothReceiver();
        setupVoiceDestinationRecognizer();
        setupDashboardVoiceHoldGesture();
        
        // Load data
        loadSensorData();
        
        // Setup refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::loadSensorData);
        
        // Setup auto-refresh
        setupAutoRefresh();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    BLUETOOTH_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void startAccelerometerMonitoringService() {
        Intent serviceIntent = new Intent(this, AccelerometerMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
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
        registerBluetoothReceiver();
        if (hasBluetoothPermissions()) {
            startBluetoothService();
        } else {
            requestBluetoothPermissions();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Stop auto-refresh when activity goes to background
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        cancelPendingVoiceEntryTrigger();
        unregisterBluetoothReceiver();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        cancelPendingVoiceEntryTrigger();
        unregisterBluetoothReceiver();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
    
    private void initializeViews() {
        // Find views
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        lastUpdatedText = findViewById(R.id.lastUpdatedText);
        statusText = findViewById(R.id.statusText);
        statusIndicator = findViewById(R.id.statusIndicator);
        voiceHoldOverlay = findViewById(R.id.voiceHoldOverlay);
        voiceHoldCountdownText = findViewById(R.id.voiceHoldCountdownText);
        voiceHoldHintText = findViewById(R.id.voiceHoldHintText);

        if (voiceHoldHintText != null) {
            voiceHoldHintText.setText(R.string.voice_hold_hint);
        }
        
        // Accelerometer card
        accelerometerCard = findViewById(R.id.accelerometerCard);
        if (accelerometerCard != null) {
            accelX = accelerometerCard.findViewById(R.id.accelX);
            accelY = accelerometerCard.findViewById(R.id.accelY);
            accelZ = accelerometerCard.findViewById(R.id.accelZ);
            accelerometerCard.setOnClickListener(v -> openSensorDetail("accelerometer"));
        }
        
        // Gyroscope card
        gyroscopeCard = findViewById(R.id.gyroscopeCard);
        if (gyroscopeCard != null) {
            gyroX = gyroscopeCard.findViewById(R.id.gyroX);
            gyroY = gyroscopeCard.findViewById(R.id.gyroY);
            gyroZ = gyroscopeCard.findViewById(R.id.gyroZ);
            gyroscopeCard.setOnClickListener(v -> openSensorDetail("gyroscope"));
        }
        
        // Ultrasonic card
        ultrasonicCard = findViewById(R.id.ultrasonicCard);
        if (ultrasonicCard != null) {
            ultrasonicValue = ultrasonicCard.findViewById(R.id.ultrasonicValue);
            ultrasonicCard.setOnClickListener(v -> openSensorDetail("ultrasonic"));
        }
        
        // Temperature card
        temperatureCard = findViewById(R.id.temperatureCard);
        if (temperatureCard != null) {
            temperatureValue = temperatureCard.findViewById(R.id.temperatureValue);
            temperatureCard.setOnClickListener(v -> openSensorDetail("temperature"));
        }
        
        // GPS card
        gpsCard = findViewById(R.id.gpsCard);
        if (gpsCard != null) {
            gpsTitle = gpsCard.findViewById(R.id.mapTitle);
            locationText = gpsCard.findViewById(R.id.locationText);
            gpsDetailButton = gpsCard.findViewById(R.id.mapDetailButton);
            if (gpsDetailButton != null) {
                gpsDetailButton.setOnClickListener(v -> openMapActivity());
            }
            // Also make the card clickable to open map
            gpsCard.setOnClickListener(v -> openMapActivity());
        }
        
        // Setup floating action button to send data to API
        FloatingActionButton fabSendData = findViewById(R.id.fabSendData);
        if (fabSendData != null) {
            fabSendData.setOnClickListener(v -> sendCurrentSensorData());
        }
        
        // We'll observe the status message in onCreate() after viewModel is initialized
        // to avoid NullPointerException
    }

    private void setupBluetoothReceiver() {
        bluetoothCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothService.ACTION_COMMAND_RECEIVED.equals(intent.getAction())) {
                    String command = intent.getStringExtra(BluetoothService.EXTRA_COMMAND);
                    if (command != null && !command.isEmpty()) {
                        // Show a temporary visual Toast for debugging so we know it arrived
                        Toast.makeText(context, "Command Received: " + command, Toast.LENGTH_SHORT).show();
                        speakCommandIfChanged(command);
                    }
                }
            }
        };
    }

    private void registerBluetoothReceiver() {
        if (bluetoothCommandReceiver != null) {
            IntentFilter filter = new IntentFilter(BluetoothService.ACTION_COMMAND_RECEIVED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(bluetoothCommandReceiver, filter);
            }
        }
    }

    private void unregisterBluetoothReceiver() {
        if (bluetoothCommandReceiver != null) {
            try {
                unregisterReceiver(bluetoothCommandReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was not registered — safe to ignore
            }
        }
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void setupVoiceDestinationRecognizer() {
        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        Toast.makeText(this, R.string.voice_entry_not_captured, Toast.LENGTH_SHORT).show();
                        showManualDestinationDialog();
                        return;
                    }

                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches == null || matches.isEmpty()) {
                        Toast.makeText(this, R.string.voice_entry_not_captured, Toast.LENGTH_SHORT).show();
                        showManualDestinationDialog();
                        return;
                    }

                    String destinationQuery = matches.get(0) != null ? matches.get(0).trim() : "";
                    if (destinationQuery.isEmpty()) {
                        Toast.makeText(this, R.string.voice_entry_not_captured, Toast.LENGTH_SHORT).show();
                        showManualDestinationDialog();
                        return;
                    }

                    openMapActivityWithVoiceDestination(destinationQuery);
                }
        );
    }

    private void setupDashboardVoiceHoldGesture() {
        voiceEntryHandler = new Handler(Looper.getMainLooper());
        holdTouchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop() * 2;
        voiceEntryTriggered = false;

        voiceEntryRunnable = () -> {
            voiceEntryTriggered = true;
            hideVoiceHoldCountdown();
            beginVoiceEntryMode();
        };

        holdCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.elapsedRealtime() - voiceHoldStartedAtMs;
                long remainingMs = DASHBOARD_HOLD_DURATION_MS - elapsed;
                if (remainingMs <= 0) {
                    return;
                }
                updateVoiceHoldCountdown(remainingMs);
                if (voiceEntryHandler != null) {
                    voiceEntryHandler.postDelayed(this, HOLD_COUNTDOWN_TICK_MS);
                }
            }
        };
    }

    private void cancelPendingVoiceEntryTrigger() {
        if (voiceEntryHandler != null && voiceEntryRunnable != null) {
            voiceEntryHandler.removeCallbacks(voiceEntryRunnable);
        }
        if (voiceEntryHandler != null && holdCountdownRunnable != null) {
            voiceEntryHandler.removeCallbacks(holdCountdownRunnable);
        }
        hideVoiceHoldCountdown();
    }

    private void showVoiceHoldCountdown() {
        if (voiceHoldOverlay != null) {
            voiceHoldOverlay.setVisibility(View.VISIBLE);
        }
        lastHapticCountdownSecond = -1;
        updateVoiceHoldCountdown(DASHBOARD_HOLD_DURATION_MS);
    }

    private void hideVoiceHoldCountdown() {
        if (voiceHoldOverlay != null) {
            voiceHoldOverlay.setVisibility(View.GONE);
        }
    }

    private void updateVoiceHoldCountdown(long remainingMs) {
        if (voiceHoldCountdownText == null) {
            return;
        }
        int secondsRemaining = (int) Math.ceil(remainingMs / 1000.0);
        if (secondsRemaining < 1) {
            secondsRemaining = 1;
        }
        voiceHoldCountdownText.setText(getString(R.string.voice_hold_seconds_remaining, secondsRemaining));
        maybeVibrateForCountdown(secondsRemaining);
    }

    private void maybeVibrateForCountdown(int secondsRemaining) {
        if (secondsRemaining < 1 || secondsRemaining > 3) {
            return;
        }
        if (secondsRemaining == lastHapticCountdownSecond) {
            return;
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        long durationMs = 35L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
        lastHapticCountdownSecond = secondsRemaining;
    }

    private void beginVoiceEntryMode() {
        hideVoiceHoldCountdown();

        if (!hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST_CODE
            );
            return;
        }

        Toast.makeText(this, R.string.voice_entry_mode_enabled, Toast.LENGTH_SHORT).show();
        String prompt = getString(R.string.voice_where_to_go_prompt);

        if (ttsReady && textToSpeech != null) {
            textToSpeech.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "voice_destination_prompt");
        }

        if (voiceEntryHandler != null) {
            voiceEntryHandler.postDelayed(this::launchVoiceRecognizer, VOICE_PROMPT_DELAY_MS);
        } else {
            launchVoiceRecognizer();
        }
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void launchVoiceRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_recognition_unavailable, Toast.LENGTH_LONG).show();
            showManualDestinationDialog();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_listening_prompt));

        try {
            speechRecognizerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_recognition_unavailable, Toast.LENGTH_LONG).show();
            showManualDestinationDialog();
        }
    }

    private void showManualDestinationDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.voice_manual_destination_hint);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.voice_manual_destination_title)
                .setView(input)
                .setPositiveButton(R.string.voice_manual_destination_use, (dialog, which) -> {
                    String destinationQuery = input.getText().toString().trim();
                    if (destinationQuery.isEmpty()) {
                        Toast.makeText(this, R.string.voice_manual_destination_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    openMapActivityWithVoiceDestination(destinationQuery);
                })
                .setNegativeButton(R.string.voice_manual_destination_cancel, null)
                .show();
    }



    private void speakCommandIfChanged(String command) {
        if (!ttsReady || textToSpeech == null) {
            return;
        }

        String normalizedCommand = command.toUpperCase(Locale.US);
        if (normalizedCommand.equals(lastSpokenCommand)) {
            return;
        }

        textToSpeech.speak(toSpeechText(normalizedCommand), TextToSpeech.QUEUE_FLUSH, null, "navigation_command");
        lastSpokenCommand = normalizedCommand;
    }

    private String toSpeechText(String command) {
        switch (command) {
            case "LEFT":
                return "Turn left";
            case "RIGHT":
                return "Turn right";
            case "FORWARD":
                return "Move forward";
            case "STOP":
                return "Stop";
            default:
                return command;
        }
    }



    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int langStatus = textToSpeech.setLanguage(Locale.US);
            ttsReady = langStatus != TextToSpeech.LANG_MISSING_DATA
                    && langStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady) {
                textToSpeech.setSpeechRate(0.9f);
                textToSpeech.setPitch(1.0f);
            }
        } else {
            ttsReady = false;
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        handleDashboardTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void handleDashboardTouchEvent(MotionEvent event) {
        if (voiceEntryHandler == null || voiceEntryRunnable == null) {
            return;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holdStartX = event.getRawX();
                holdStartY = event.getRawY();
                voiceEntryTriggered = false;
                voiceHoldStartedAtMs = SystemClock.elapsedRealtime();
                cancelPendingVoiceEntryTrigger();
                showVoiceHoldCountdown();
                voiceEntryHandler.postDelayed(voiceEntryRunnable, DASHBOARD_HOLD_DURATION_MS);
                if (holdCountdownRunnable != null) {
                    voiceEntryHandler.post(holdCountdownRunnable);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (voiceEntryTriggered) {
                    break;
                }
                float dx = Math.abs(event.getRawX() - holdStartX);
                float dy = Math.abs(event.getRawY() - holdStartY);
                if (dx > holdTouchSlopPx || dy > holdTouchSlopPx) {
                    cancelPendingVoiceEntryTrigger();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPendingVoiceEntryTrigger();
                break;
            default:
                break;
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
        viewModel.getLatestSensorData(10).observe(this, data -> {
            if (data != null && !data.isEmpty()) {
                sensorDataList = data;
                updateUI(data);
            } else if (data != null && data.isEmpty()) {
                applyOfflineReadings();
                // Data is empty but request was successful
                Toast.makeText(this, "No sensor data available yet", Toast.LENGTH_SHORT).show();
            }
            // If data is null, error will be shown by error observer
        });
    }
    
    private void loadSensorDataSilent() {
        viewModel.getLatestSensorDataSilent(10).observe(this, data -> {
            if (data != null && !data.isEmpty()) {
                sensorDataList = data;
                updateUI(data);
            } else if (data != null && data.isEmpty()) {
                applyOfflineReadings();
            }
            // Silent refresh - no toasts or error messages
        });
    }
    
    private void updateUI(List<SensorData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // Get most recent data
        SensorData latestData = dataList.get(0);

        if (!latestData.isOnline()) {
            applyOfflineReadings();
            return;
        }

        setConnectionStatus(true);
        
        // Update timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        String formattedTime = sdf.format(latestData.getTimestampAsDate());
        lastUpdatedText.setText(getString(R.string.last_updated, formattedTime));
        
        // Update accelerometer values
        if (latestData.getAccelerometer() != null) {
            AccelerometerData accel = latestData.getAccelerometer();
            if (accelX != null) accelX.setText(String.format(Locale.getDefault(), "%.2f", accel.getX()));
            if (accelY != null) accelY.setText(String.format(Locale.getDefault(), "%.2f", accel.getY()));
            if (accelZ != null) accelZ.setText(String.format(Locale.getDefault(), "%.2f", accel.getZ()));
        }
        
        // Update gyroscope values
        if (latestData.getGyroscope() != null) {
            GyroscopeData gyro = latestData.getGyroscope();
            if (gyroX != null) gyroX.setText(String.format(Locale.getDefault(), "%.2f", gyro.getX()));
            if (gyroY != null) gyroY.setText(String.format(Locale.getDefault(), "%.2f", gyro.getY()));
            if (gyroZ != null) gyroZ.setText(String.format(Locale.getDefault(), "%.2f", gyro.getZ()));
        }
        
        // Update ultrasonic values
        if (latestData.isUltrasonicValid() && ultrasonicValue != null) {
            double distance = latestData.getDistance();
            ultrasonicValue.setText(String.format(Locale.getDefault(), "%.0f", distance));
        }
        
        // Update temperature values
        if (temperatureValue != null) {
            double temperature = latestData.getTemperature();
            temperatureValue.setText(String.format(Locale.getDefault(), "%.1f °C", temperature));
        }
        
        // Update GPS values
        if (latestData.getGps() != null && locationText != null) {
            GPSData gps = latestData.getGps();
            locationText.setText(gps.toString());
        }
    }

    private void applyOfflineReadings() {
        setConnectionStatus(false);

        if (lastUpdatedText != null) {
            lastUpdatedText.setText("Last updated: --");
        }

        if (accelX != null) accelX.setText("--");
        if (accelY != null) accelY.setText("--");
        if (accelZ != null) accelZ.setText("--");

        if (gyroX != null) gyroX.setText("--");
        if (gyroY != null) gyroY.setText("--");
        if (gyroZ != null) gyroZ.setText("--");

        if (ultrasonicValue != null) ultrasonicValue.setText("--");
        if (temperatureValue != null) temperatureValue.setText("--");
        if (locationText != null) locationText.setText("--");
    }

    private void setConnectionStatus(boolean online) {
        if (statusText != null) {
            statusText.setText(online ? "Connected" : "Offline");
        }

        if (statusIndicator != null) {
            statusIndicator.setAlpha(online ? 1.0f : 0.35f);
        }
    }
    
    // Chart methods commented out - charts not used in current layout
    /*
    private void updateAccelerometerChart(List<SensorData> dataList) {
        if (accelerometerChart == null) return;
        
        List<Entry> xEntries = new ArrayList<>();
        List<Entry> yEntries = new ArrayList<>();
        List<Entry> zEntries = new ArrayList<>();
        
        for (int i = dataList.size() - 1; i >= 0; i--) {
            SensorData data = dataList.get(i);
            if (data.getAccelerometer() != null) {
                float index = dataList.size() - 1 - i;
                AccelerometerData accel = data.getAccelerometer();
                xEntries.add(new Entry(index, (float) accel.getX()));
                yEntries.add(new Entry(index, (float) accel.getY()));
                zEntries.add(new Entry(index, (float) accel.getZ()));
            }
        }
        
        LineDataSet xDataSet = new LineDataSet(xEntries, "X-Axis");
        LineDataSet yDataSet = new LineDataSet(yEntries, "Y-Axis");
        LineDataSet zDataSet = new LineDataSet(zEntries, "Z-Axis");
        
        int color = getColor(R.color.colorAccelerometerChart);
        styleLineDataSet(xDataSet, color, false);
        styleLineDataSet(yDataSet, color, true);
        styleLineDataSet(zDataSet, color, true);
        
        LineData lineData = new LineData(xDataSet, yDataSet, zDataSet);
        accelerometerChart.setData(lineData);
        accelerometerChart.invalidate();
    }
    
    private void updateGyroscopeChart(List<SensorData> dataList) {
        if (gyroscopeChart == null) return;
        
        List<Entry> xEntries = new ArrayList<>();
        List<Entry> yEntries = new ArrayList<>();
        List<Entry> zEntries = new ArrayList<>();
        
        for (int i = dataList.size() - 1; i >= 0; i--) {
            SensorData data = dataList.get(i);
            if (data.getGyroscope() != null) {
                float index = dataList.size() - 1 - i;
                GyroscopeData gyro = data.getGyroscope();
                xEntries.add(new Entry(index, (float) gyro.getX()));
                yEntries.add(new Entry(index, (float) gyro.getY()));
                zEntries.add(new Entry(index, (float) gyro.getZ()));
            }
        }
        
        LineDataSet xDataSet = new LineDataSet(xEntries, "X-Axis");
        LineDataSet yDataSet = new LineDataSet(yEntries, "Y-Axis");
        LineDataSet zDataSet = new LineDataSet(zEntries, "Z-Axis");
        
        int color = getColor(R.color.colorGyroscopeChart);
        styleLineDataSet(xDataSet, color, false);
        styleLineDataSet(yDataSet, color, true);
        styleLineDataSet(zDataSet, color, true);
        
        LineData lineData = new LineData(xDataSet, yDataSet, zDataSet);
        gyroscopeChart.setData(lineData);
        gyroscopeChart.invalidate();
    }
    
    private void updateUltrasonicChart(List<SensorData> dataList) {
        if (ultrasonicChart == null) return;
        
        List<Entry> distanceEntries = new ArrayList<>();
        
        for (int i = dataList.size() - 1; i >= 0; i--) {
            SensorData data = dataList.get(i);
            if (data.isUltrasonicValid()) {
                float index = dataList.size() - 1 - i;
                distanceEntries.add(new Entry(index, (float) data.getDistance()));
            }
        }
        
        LineDataSet dataSet = new LineDataSet(distanceEntries, "Distance (cm)");
        
        int color = getColor(R.color.colorUltrasonicChart);
        styleLineDataSet(dataSet, color, false);
        
        LineData lineData = new LineData(dataSet);
        ultrasonicChart.setData(lineData);
        ultrasonicChart.invalidate();
    }
    
    private void updateTemperatureChart(List<SensorData> dataList) {
        if (temperatureChart == null) return;
        
        List<Entry> temperatureEntries = new ArrayList<>();
        
        for (int i = dataList.size() - 1; i >= 0; i--) {
            SensorData data = dataList.get(i);
            float index = dataList.size() - 1 - i;
            temperatureEntries.add(new Entry(index, (float) data.getTemperature()));
        }
        
        LineDataSet dataSet = new LineDataSet(temperatureEntries, "Temperature (°C)");
        
        int color = getColor(R.color.colorTemperatureChart);
        styleLineDataSet(dataSet, color, false);
        
        LineData lineData = new LineData(dataSet);
        temperatureChart.setData(lineData);
        temperatureChart.invalidate();
    }
    */
    
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
    
    private void openSensorDetail(String sensorType) {
        Intent intent = new Intent(this, SensorDetailActivity.class);
        intent.putExtra("sensor_type", sensorType);
        // We would normally pass the data here or use a shared ViewModel
        startActivity(intent);
    }
    
    private void openMapActivity() {
        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
    }

    private void openMapActivityWithVoiceDestination(String destinationQuery) {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_DESTINATION_QUERY, destinationQuery);
        intent.putExtra(MapActivity.EXTRA_AUTO_START_DIRECTIONS, true);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MICROPHONE_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                beginVoiceEntryMode();
            } else {
                Toast.makeText(this, R.string.voice_permission_required, Toast.LENGTH_SHORT).show();
                showManualDestinationDialog();
            }
        } else if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBluetoothService();
            } else {
                Toast.makeText(this, "Bluetooth permission required for Drishti Stick", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * Send current sensor data to the API
     */
    private void sendCurrentSensorData() {
        if (sensorDataList.isEmpty()) {
            Toast.makeText(this, "No sensor data available to send", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get latest sensor data
        SensorData latestData = sensorDataList.get(0);
        
        // Send data to API
        viewModel.sendSensorData(
                latestData.getAccelerometer(),
                latestData.getGyroscope(),
                latestData.getGps()
        );
    }
}