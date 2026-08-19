package com.an.wrist;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.an.wrist.models.GPSData;
import com.an.wrist.models.SensorData;
import com.an.wrist.viewmodel.SensorViewModel;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.bonuspack.routing.RoadNode;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    public static final String EXTRA_DESTINATION_QUERY = "extra_destination_query";
    public static final String EXTRA_AUTO_START_DIRECTIONS = "extra_auto_start_directions";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long ROUTE_UPDATE_INTERVAL_MS = 7000L;
    private static final double MIN_ROUTE_RECALC_DISTANCE_METERS = 10.0;
    private static final double STEP_REACHED_DISTANCE_METERS = 20.0;
    private static final long STEP_AUDIO_COOLDOWN_MS = 3000L;
    
    private MapView mapView;
    private SensorViewModel viewModel;
    private TextView coordinatesText;
    private TextView navigationStatusText;
    private TextView nextStepText;
    private TextView directionsStepsText;
    private Button getDirectionsButton;
    private Marker destinationMarker;
    private Marker userLocationMarker;
    private Polyline routeOverlay;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    
    private double latitude = 0.0;
    private double longitude = 0.0;
    private GeoPoint destinationPoint;
    private GeoPoint userCurrentPoint;
    private GeoPoint lastRouteOrigin;
    private long lastRouteUpdatedAtMs = 0L;
    private boolean directionsEnabled = false;
    private boolean pendingAutoStartDirections = false;
    private boolean voiceDestinationActive = false;
    private boolean arrivalAnnounced = false;
    private final ArrayList<NavigationStep> navigationSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private int lastSpokenStepIndex = -1;
    private long lastStepSpokenAtMs = 0L;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // Auto-refresh handler
    private android.os.Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 7000; // 7 seconds

    private static class NavigationStep {
        final String instruction;
        final String distanceLabel;
        final GeoPoint location;

        NavigationStep(String instruction, String distanceLabel, GeoPoint location) {
            this.instruction = instruction;
            this.distanceLabel = distanceLabel;
            this.location = location;
        }

        String asLine() {
            return instruction + " (" + distanceLabel + ")";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        
        setContentView(R.layout.activity_map);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("GPS Location");
        }
        
        // Initialize views
        mapView = findViewById(R.id.mapView);
        coordinatesText = findViewById(R.id.coordinatesText);
        navigationStatusText = findViewById(R.id.navigationStatusText);
        nextStepText = findViewById(R.id.nextStepText);
        directionsStepsText = findViewById(R.id.directionsStepsText);
        getDirectionsButton = findViewById(R.id.getDirectionsButton);
        getDirectionsButton.setOnClickListener(v -> onGetDirectionsClicked());
        
        // Setup map
        setupMap();
        
        // Check location permissions
        checkLocationPermissions();
        
        // Setup view model
        viewModel = new ViewModelProvider(this).get(SensorViewModel.class);
        
        // Observe sensor data
        observeSensorData();

        textToSpeech = new TextToSpeech(this, this);
        applyVoiceDestinationFromIntent(getIntent());

        setupAutoRefresh();
    }
    
    private void setupAutoRefresh() {
        refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // Poll for the latest sensor data silently
                viewModel.getLatestSensorDataSilent(1).observe(MapActivity.this, sensorDataList -> {
                    if (sensorDataList != null && !sensorDataList.isEmpty()) {
                        SensorData sensorData = sensorDataList.get(0);
                        if (!sensorData.isOnline()) {
                            coordinatesText.setText("--");
                            navigationStatusText.setText("Device offline");
                            return;
                        }
                        if (sensorData.getGps() != null) {
                            GPSData gpsData = sensorData.getGps();
                            updateDestinationLocation(gpsData);
                        }
                    }
                });
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }
    
    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        
        // Default location (will be updated with actual GPS data)
        GeoPoint startPoint = new GeoPoint(37.7749, -122.4194); // San Francisco as default
        mapView.getController().setCenter(startPoint);
    }
    
    private void checkLocationPermissions() {
        boolean hasFinePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarsePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;

        if (!hasFinePermission && !hasCoarsePermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
            promptEnableLocationServicesIfDisabled();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean fineGranted = false;
            boolean coarseGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                    fineGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
                if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[i])) {
                    coarseGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (fineGranted || coarseGranted) {
                startLocationUpdates();
                promptEnableLocationServicesIfDisabled();
            } else {
                boolean canAskFineAgain = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
                boolean canAskCoarseAgain = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);

                if (!canAskFineAgain && !canAskCoarseAgain) {
                    showOpenAppSettingsDialog();
                } else {
                    showPermissionNeededDialog();
                }
            }
        }
    }

    private void showPermissionNeededDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("Navigation needs location access to show your current position and guide you to destination.")
                .setPositiveButton("Allow", (dialog, which) -> ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE
                ))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOpenAppSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Location Permission")
                .setMessage("Location permission is permanently denied. Enable it from App Settings to use navigation.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isLocationServiceEnabled() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if (locationManager == null) {
            return false;
        }

        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {
            // Some devices throw if provider is unavailable.
        }
        return gpsEnabled || networkEnabled;
    }

    private void promptEnableLocationServicesIfDisabled() {
        if (isLocationServiceEnabled()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Turn On Location")
                .setMessage("Device location is off. Turn it on to receive real-time navigation updates.")
                .setPositiveButton("Open Location Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startLocationUpdates() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if (locationListener == null) {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    updateUserLocation(location);
                }
            };
        }

        boolean hasFinePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarsePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasFinePermission && !hasCoarsePermission) {
            return;
        }

        if (!isLocationServiceEnabled()) {
            return;
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, locationListener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, locationListener);
            }
        } catch (SecurityException ignored) {
            // Permission state changed unexpectedly.
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
    
    private void observeSensorData() {
        // Observe the latest sensor data (limit 1)
        viewModel.getLatestSensorData(1).observe(this, sensorDataList -> {
            if (sensorDataList != null && !sensorDataList.isEmpty()) {
                SensorData sensorData = sensorDataList.get(0);
                if (!sensorData.isOnline()) {
                    coordinatesText.setText("--");
                    navigationStatusText.setText("Device offline");
                    return;
                }
                if (sensorData.getGps() != null) {
                    GPSData gpsData = sensorData.getGps();
                    updateDestinationLocation(gpsData);
                }
            }
        });
        
        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateDestinationLocation(GPSData gpsData) {
        if (voiceDestinationActive) {
            return;
        }

        latitude = gpsData.getLatitude();
        longitude = gpsData.getLongitude();
        
        if (latitude != 0.0 && longitude != 0.0) {
            destinationPoint = new GeoPoint(latitude, longitude);
            mapView.getController().setCenter(destinationPoint);
            
            // Remove old marker if exists
            if (destinationMarker != null) {
                mapView.getOverlays().remove(destinationMarker);
            }
            
            // Add new marker
            destinationMarker = new Marker(mapView);
            destinationMarker.setPosition(destinationPoint);
            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            destinationMarker.setTitle("Destination");
            destinationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.marker_destination));
            
            // Create snippet with all GPS data
            String snippet = String.format("Lat: %.6f, Lng: %.6f\nAltitude: %.2fm\nSpeed: %.2f m/s\nAccuracy: %.2fm",
                    latitude, longitude, gpsData.getAltitude(), gpsData.getSpeed(), gpsData.getAccuracy());
            destinationMarker.setSnippet(snippet);
            
            mapView.getOverlays().add(destinationMarker);
            
            // Update coordinates text
            coordinatesText.setText(String.format(Locale.US, "Latitude: %.6f, Longitude: %.6f", latitude, longitude));

            if (directionsEnabled) {
                requestRouteUpdate(true);
            }
            
            // Refresh map
            mapView.invalidate();
        } else {
            coordinatesText.setText("GPS data not available");
        }
    }

    private void applyVoiceDestinationFromIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String destinationQuery = intent.getStringExtra(EXTRA_DESTINATION_QUERY);
        if (destinationQuery == null || destinationQuery.trim().isEmpty()) {
            return;
        }

        pendingAutoStartDirections = intent.getBooleanExtra(EXTRA_AUTO_START_DIRECTIONS, false);
        navigationStatusText.setText(getString(R.string.voice_destination_resolving, destinationQuery));
        resolveDestinationFromQuery(destinationQuery.trim());
    }

    private void resolveDestinationFromQuery(String query) {
        if (!Geocoder.isPresent()) {
            navigationStatusText.setText(R.string.voice_destination_geocoder_unavailable);
            Toast.makeText(this, R.string.voice_destination_geocoder_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        routeExecutor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(query, 3);

                if (results == null || results.isEmpty()) {
                    runOnUiThread(() -> {
                        navigationStatusText.setText(getString(R.string.voice_destination_not_found, query));
                        Toast.makeText(this, R.string.voice_destination_not_found_toast, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                Address bestMatch = results.get(0);
                GeoPoint resolvedPoint = new GeoPoint(bestMatch.getLatitude(), bestMatch.getLongitude());
                String label = bestMatch.getFeatureName();
                if (label == null || label.trim().isEmpty()) {
                    label = query;
                }
                String destinationLabel = label;

                runOnUiThread(() -> {
                    voiceDestinationActive = true;
                    setResolvedVoiceDestination(destinationLabel, resolvedPoint);
                    maybeStartPendingNavigation();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    navigationStatusText.setText(getString(R.string.voice_destination_not_found, query));
                    Toast.makeText(this, R.string.voice_destination_not_found_toast, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setResolvedVoiceDestination(String label, GeoPoint point) {
        destinationPoint = point;
        latitude = point.getLatitude();
        longitude = point.getLongitude();

        if (destinationMarker != null) {
            mapView.getOverlays().remove(destinationMarker);
        }

        destinationMarker = new Marker(mapView);
        destinationMarker.setPosition(point);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setTitle(label);
        destinationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.marker_destination));
        destinationMarker.setSnippet(String.format(Locale.US, "Lat: %.6f, Lng: %.6f", latitude, longitude));
        mapView.getOverlays().add(destinationMarker);

        coordinatesText.setText(String.format(Locale.US, "%s\nLatitude: %.6f, Longitude: %.6f", label, latitude, longitude));
        navigationStatusText.setText(getString(R.string.voice_destination_set, label));
        mapView.getController().setCenter(point);
        mapView.invalidate();
    }

    private void maybeStartPendingNavigation() {
        if (!pendingAutoStartDirections || directionsEnabled || destinationPoint == null) {
            return;
        }

        if (userCurrentPoint == null) {
            navigationStatusText.setText(R.string.voice_destination_waiting_location);
            startLocationUpdates();
            return;
        }

        pendingAutoStartDirections = false;
        startDirections();
    }

    private void updateUserLocation(Location location) {
        userCurrentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (userLocationMarker == null) {
            userLocationMarker = new Marker(mapView);
            userLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            userLocationMarker.setTitle("You are here");
            userLocationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.marker_user));
            mapView.getOverlays().add(userLocationMarker);
        }
        userLocationMarker.setPosition(userCurrentPoint);

        if (!directionsEnabled && destinationPoint == null) {
            mapView.getController().setCenter(userCurrentPoint);
        }

        if (directionsEnabled) {
            requestRouteUpdate(false);
        }

        updateCurrentStepFromLocation();
        maybeStartPendingNavigation();

        mapView.invalidate();
    }

    private void onGetDirectionsClicked() {
        if (directionsEnabled) {
            stopDirections();
        } else {
            pendingAutoStartDirections = false;
            startDirections();
        }
    }

    private void startDirections() {
        if (destinationPoint == null) {
            Toast.makeText(this, "Destination coordinates not available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermissions();
            return;
        }

        if (!isLocationServiceEnabled()) {
            promptEnableLocationServicesIfDisabled();
            return;
        }

        if (userCurrentPoint == null) {
            navigationStatusText.setText("Waiting for your current location...");
            startLocationUpdates();
            return;
        }

        directionsEnabled = true;
        arrivalAnnounced = false;
        lastSpokenStepIndex = -1;
        getDirectionsButton.setText("Stop Directions");
        navigationStatusText.setText("Starting navigation...");
        nextStepText.setText("Calculating first instruction...");
        directionsStepsText.setText("Preparing upcoming steps...");
        requestRouteUpdate(true);
    }

    private void stopDirections() {
        directionsEnabled = false;
        pendingAutoStartDirections = false;
        getDirectionsButton.setText("Get Directions");
        navigationStatusText.setText("Navigation stopped");
        nextStepText.setText("Current step will appear here");
        directionsStepsText.setText("Upcoming steps will appear here");
        navigationSteps.clear();
        currentStepIndex = 0;
        lastSpokenStepIndex = -1;
        clearRouteOverlay();
        mapView.invalidate();
    }

    private void requestRouteUpdate(boolean force) {
        if (!directionsEnabled || destinationPoint == null || userCurrentPoint == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastRouteOrigin != null) {
            double movedMeters = userCurrentPoint.distanceToAsDouble(lastRouteOrigin);
            boolean shouldThrottle = (now - lastRouteUpdatedAtMs) < ROUTE_UPDATE_INTERVAL_MS;
            if (movedMeters < MIN_ROUTE_RECALC_DISTANCE_METERS && shouldThrottle) {
                return;
            }
        }

        final GeoPoint routeStart = new GeoPoint(userCurrentPoint.getLatitude(), userCurrentPoint.getLongitude());
        final GeoPoint routeEnd = new GeoPoint(destinationPoint.getLatitude(), destinationPoint.getLongitude());
        lastRouteOrigin = routeStart;
        lastRouteUpdatedAtMs = now;
        navigationStatusText.setText("Updating route...");

        routeExecutor.execute(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(routeStart);
                waypoints.add(routeEnd);

                Road road = roadManager.getRoad(waypoints);
                runOnUiThread(() -> renderRoute(road, routeStart, routeEnd));
            } catch (Exception e) {
                runOnUiThread(() -> navigationStatusText.setText("Unable to fetch route. Check internet connection."));
            }
        });
    }

    private void renderRoute(Road road, GeoPoint routeStart, GeoPoint routeEnd) {
        if (!directionsEnabled) {
            return;
        }

        clearRouteOverlay();
        if (road == null || road.mStatus != Road.STATUS_OK) {
            navigationStatusText.setText("No route available for current destination");
            nextStepText.setText("No current step available");
            directionsStepsText.setText("No upcoming steps available for this route.");
            mapView.invalidate();
            return;
        }

        routeOverlay = RoadManager.buildRoadOverlay(road);
        mapView.getOverlays().add(routeOverlay);

        long durationSeconds = Math.round(road.mDuration);
        navigationStatusText.setText(String.format(Locale.US, "Distance %.2f km • ETA %s", road.mLength, formatDuration(durationSeconds)));
        buildNavigationSteps(road);
        updateCurrentStepFromLocation();
        updateDetailedDirections();
        announceCurrentStep(true);

        ArrayList<GeoPoint> visiblePoints = new ArrayList<>();
        visiblePoints.add(routeStart);
        visiblePoints.add(routeEnd);
        mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(visiblePoints), true, 140);
        mapView.invalidate();
    }

    private void buildNavigationSteps(Road road) {
        navigationSteps.clear();
        currentStepIndex = 0;

        if (road.mNodes == null || road.mNodes.isEmpty()) {
            return;
        }

        for (RoadNode node : road.mNodes) {
            if (node == null) {
                continue;
            }

            String instruction = sanitizeInstruction(node.mInstructions);
            if (instruction.isEmpty()) {
                continue;
            }

            String stepDistance = formatStepDistance(node.mLength);
            navigationSteps.add(new NavigationStep(instruction, stepDistance, node.mLocation));
        }
    }

    private void updateCurrentStepFromLocation() {
        if (!directionsEnabled || userCurrentPoint == null || navigationSteps.isEmpty()) {
            return;
        }

        int previousStepIndex = currentStepIndex;

        if (currentStepIndex >= navigationSteps.size()) {
            currentStepIndex = navigationSteps.size() - 1;
        }

        // Advance step when user is close enough to the current maneuver point.
        while (currentStepIndex < navigationSteps.size() - 1) {
            NavigationStep step = navigationSteps.get(currentStepIndex);
            if (step.location == null) {
                break;
            }

            double distanceMeters = userCurrentPoint.distanceToAsDouble(step.location);
            if (distanceMeters <= STEP_REACHED_DISTANCE_METERS) {
                currentStepIndex++;
            } else {
                break;
            }
        }

        updateDetailedDirections();

        if (currentStepIndex != previousStepIndex) {
            announceCurrentStep(true);
        }

        if (!arrivalAnnounced && destinationPoint != null
                && userCurrentPoint.distanceToAsDouble(destinationPoint) <= STEP_REACHED_DISTANCE_METERS) {
            arrivalAnnounced = true;
            speakNavigationCue(getString(R.string.voice_arrived_destination), true);
        }
    }

    private void updateDetailedDirections() {
        if (navigationSteps.isEmpty()) {
            nextStepText.setText("Head toward destination");
            directionsStepsText.setText("No upcoming steps returned for this route.");
            return;
        }

        if (currentStepIndex < 0) {
            currentStepIndex = 0;
        }
        if (currentStepIndex >= navigationSteps.size()) {
            currentStepIndex = navigationSteps.size() - 1;
        }

        nextStepText.setText("Now: " + navigationSteps.get(currentStepIndex).asLine());

        if (currentStepIndex >= navigationSteps.size() - 1) {
            directionsStepsText.setText("No future steps left after current step.");
            return;
        }

        StringBuilder futureStepsBuilder = new StringBuilder();
        int futureIndex = 1;
        for (int i = currentStepIndex + 1; i < navigationSteps.size(); i++) {
            futureStepsBuilder.append(futureIndex)
                    .append(". ")
                    .append(navigationSteps.get(i).asLine())
                    .append("\n");
            futureIndex++;
        }
        directionsStepsText.setText(futureStepsBuilder.toString().trim());
    }

    private void announceCurrentStep(boolean force) {
        if (!directionsEnabled || navigationSteps.isEmpty() || currentStepIndex < 0 || currentStepIndex >= navigationSteps.size()) {
            return;
        }

        if (!force && currentStepIndex == lastSpokenStepIndex) {
            return;
        }

        NavigationStep currentStep = navigationSteps.get(currentStepIndex);
        String cue = getString(R.string.voice_step_instruction, currentStep.instruction, currentStep.distanceLabel);
        if (speakNavigationCue(cue, force)) {
            lastSpokenStepIndex = currentStepIndex;
        }
    }

    private boolean speakNavigationCue(String message, boolean force) {
        if (!ttsReady || textToSpeech == null || message == null || message.trim().isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (!force && (now - lastStepSpokenAtMs) < STEP_AUDIO_COOLDOWN_MS) {
            return false;
        }

        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "navigation_step_voice");
        lastStepSpokenAtMs = now;
        return true;
    }

    private String sanitizeInstruction(String instruction) {
        if (instruction == null) {
            return "";
        }
        return instruction.replace("<b>", "")
                .replace("</b>", "")
                .replace("<strong>", "")
                .replace("</strong>", "")
                .replaceAll("<[^>]*>", "")
                .trim();
    }

    private String formatStepDistance(double distanceKm) {
        if (distanceKm >= 1.0) {
            return String.format(Locale.US, "%.2f km", distanceKm);
        }
        return String.format(Locale.US, "%d m", Math.round(distanceKm * 1000));
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.US, "%dm", minutes);
    }

    private void clearRouteOverlay() {
        if (routeOverlay != null) {
            mapView.getOverlays().remove(routeOverlay);
            routeOverlay = null;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        startLocationUpdates();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        stopLocationUpdates();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        routeExecutor.shutdownNow();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (mapView != null) {
            mapView.onDetach();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            int languageStatus = textToSpeech.setLanguage(Locale.US);
            ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady) {
                textToSpeech.setSpeechRate(0.9f);
                textToSpeech.setPitch(1.0f);
            }
        } else {
            ttsReady = false;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
