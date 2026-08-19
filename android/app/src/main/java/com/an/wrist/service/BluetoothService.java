package com.an.wrist.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.an.wrist.MainActivity;
import com.an.wrist.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Background service that connects to the Drishti Stick (Raspberry Pi) over
 * Bluetooth RFCOMM and broadcasts detection commands for MainActivity to speak.
 *
 * The Pi side is bluetooth.py — both must use the same RFCOMM_CHANNEL value.
 *
 * Broadcasts: ACTION_COMMAND_RECEIVED with EXTRA_COMMAND ("LEFT", "RIGHT", "FORWARD", "STOP")
 */
public class BluetoothService extends Service {

    private static final String TAG = "DrishtiBluetoothSvc";

    // ── Public constants ──────────────────────────────────────────────────────
    public static final String ACTION_COMMAND_RECEIVED = "com.an.wrist.BLUETOOTH_COMMAND";
    public static final String EXTRA_COMMAND           = "command";

    // ── Internal constants ────────────────────────────────────────────────────
    /** Must match bluetooth.py RFCOMM_CHANNEL */
    private static final int    RFCOMM_CHANNEL  = 3;
    private static final String DEVICE_NAME     = "DrishtiStick";
    private static final String CHANNEL_ID      = "drishti_bt_channel";
    private static final int    NOTIFICATION_ID = 2001;
    private static final long   RECONNECT_DELAY = 5_000L;

    private volatile boolean running     = false;
    private BluetoothSocket  activeSocket;
    private Thread           connectThread;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Searching for Drishti Stick…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            startConnectLoop();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        closeSocket();
        if (connectThread != null) connectThread.interrupt();
    }

    // ── Connection loop ───────────────────────────────────────────────────────

    private void startConnectLoop() {
        connectThread = new Thread(() -> {
            while (running) {
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null || !adapter.isEnabled()) {
                        Log.w(TAG, "Bluetooth not available or disabled.");
                        sleep(RECONNECT_DELAY);
                        continue;
                    }

                    BluetoothDevice device = findPairedDevice(adapter);
                    if (device == null) {
                        Log.w(TAG, "'" + DEVICE_NAME + "' not found in paired devices.");
                        updateNotification("Drishti Stick not paired — retrying…");
                        sleep(RECONNECT_DELAY);
                        continue;
                    }

                    adapter.cancelDiscovery();
                    updateNotification("Connecting to Drishti Stick…");
                    Log.i(TAG, "Connecting to " + device.getName());

                    BluetoothSocket sock = createSocket(device);
                    sock.connect();
                    activeSocket = sock;

                    Log.i(TAG, "Connected to Drishti Stick.");
                    updateNotification("Connected to Drishti Stick ✓");
                    readStream(sock);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Connection error: " + e.getMessage());
                    closeSocket();
                    updateNotification("Disconnected — reconnecting…");
                }

                if (running) sleep(RECONNECT_DELAY);
            }
        }, "DrishtiConnect");
        connectThread.start();
    }

    /**
     * Create an RFCOMM socket directly on the fixed channel via reflection.
     * This avoids the need for SDP advertisement on the Pi side.
     * Falls back to UUID-based lookup if reflection fails.
     */
    private BluetoothSocket createSocket(BluetoothDevice device) throws Exception {
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            return (BluetoothSocket) m.invoke(device, RFCOMM_CHANNEL);
        } catch (Exception reflectionEx) {
            Log.w(TAG, "Reflection socket failed, trying UUID fallback: " + reflectionEx.getMessage());
            java.util.UUID SPP_UUID =
                    java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            return device.createRfcommSocketToServiceRecord(SPP_UUID);
        }
    }

    private void readStream(BluetoothSocket sock) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
        String line;
        while (running && (line = reader.readLine()) != null) {
            try {
                JSONObject json    = new JSONObject(line.trim());
                String     command = json.optString(EXTRA_COMMAND, "").trim().toUpperCase();
                if (!command.isEmpty()) {
                    broadcastCommand(command);
                }
            } catch (Exception e) {
                Log.w(TAG, "Parse error for line: " + line);
            }
        }
    }

    private void broadcastCommand(String command) {
        Intent intent = new Intent(ACTION_COMMAND_RECEIVED);
        intent.setPackage(getPackageName()); // Explicitly target our own app (Android 14+ requirement)
        intent.putExtra(EXTRA_COMMAND, command);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast: " + command);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BluetoothDevice findPairedDevice(BluetoothAdapter adapter) {
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired == null) return null;
        for (BluetoothDevice d : paired) {
            if (DEVICE_NAME.equals(d.getName())) return d;
        }
        return null;
    }

    private synchronized void closeSocket() {
        if (activeSocket != null) {
            try { activeSocket.close(); } catch (Exception ignored) {}
            activeSocket = null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Drishti Stick Bluetooth",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Bluetooth link to Drishti walking stick");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Drishti Stick")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
