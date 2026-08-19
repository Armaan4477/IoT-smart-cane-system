// API endpoint to fetch the latest sensor data
import db from '../../../lib/firebase-admin';
import {
  DEVICE_HEALTH_PATH,
  HEALTH_TIMEOUT_MS,
  isDeviceOnline,
} from '../../../lib/device-health';
import { KILL_SWITCH_ENABLED } from '../../../context/KillSwitchContext';

// Sensor data path in the Realtime Database
const SENSOR_DATA_PATH = 'sensor_data';

export default async function handler(req, res) {
  // Only allow GET requests
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed. Use GET.' });
  }

  // Guard: Firebase Admin failed to initialize (likely missing Vercel env vars)
  if (!db) {
    return res.status(503).json({
      error: 'Firebase not initialized',
      message: 'Server configuration error — check FIREBASE_* environment variables on Vercel.',
    });
  }

  try {
    if (KILL_SWITCH_ENABLED) {
      console.warn('Firebase communication disabled by kill switch');
      return res.status(503).json({
        error: 'Firebase communication disabled by kill switch',
        message: 'Data fetch blocked - kill switch is active',
        data: [],
        health: {
          isOnline: false,
          lastPingAt: null,
          lastPingIso: null,
          timeoutMs: HEALTH_TIMEOUT_MS,
          healthAgeMs: null,
        },
      });
    }

    // Get limit from query parameters or use default value
    const limit = parseInt(req.query.limit || '10', 10);

    const now = Date.now();

    const healthSnapshot = await db.ref(DEVICE_HEALTH_PATH).once('value');
    const healthData = healthSnapshot.exists() ? healthSnapshot.val() : {};
    const lastPingAt = Number(healthData.lastPingAt || 0);
    const online = isDeviceOnline(lastPingAt, now);
    const healthAgeMs = lastPingAt > 0 ? now - lastPingAt : null;
    
    // Query the latest sensor data entries
    // Realtime DB queries order ascending by default, so we need to use limitToLast to get the most recent entries
    const snapshot = await db
      .ref(SENSOR_DATA_PATH)
      .orderByChild('timestamp')
      .limitToLast(limit)
      .once('value');
    
    // Convert snapshot to array of data objects
    const data = [];
    
    if (snapshot.exists()) {
      snapshot.forEach((childSnapshot) => {
        // Add entry to data array
        data.push({
          id: childSnapshot.key,
          ...childSnapshot.val(),
          // Format timestamp as ISO string for consistency
          serverTimestamp: new Date(childSnapshot.val().timestamp).toISOString(),
          isOnline: online,
          healthAgeMs,
        });
      });
    }
    
    // Sort data by timestamp in descending order (newest first)
    data.sort((a, b) => b.timestamp - a.timestamp);
    
    // Return success response
    return res.status(200).json({
      data,
      health: {
        isOnline: online,
        lastPingAt,
        lastPingIso: healthData.lastPingIso || null,
        timeoutMs: HEALTH_TIMEOUT_MS,
        healthAgeMs,
      },
    });
  } catch (error) {
    console.error('Error retrieving sensor data:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
}