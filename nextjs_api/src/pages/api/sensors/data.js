// API endpoint to handle sensor data
import db from '../../../lib/firebase-admin';
import { format } from 'date-fns';
import {
  DEVICE_HEALTH_PATH,
  HEALTH_PING_INTERVAL_MS,
  HEALTH_TIMEOUT_MS,
} from '../../../lib/device-health';
import { KILL_SWITCH_ENABLED } from '../../../lib/kill-switch';

// Sensor data path in the Realtime Database
const SENSOR_DATA_PATH = 'sensor_data';

export default async function handler(req, res) {
  // Only allow POST requests
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  // Guard: Firebase Admin failed to initialize (likely missing Vercel env vars)
  if (!db) {
    console.error('data.js: Firebase db is null — check FIREBASE_* environment variables on Vercel');
    return res.status(503).json({
      error: 'Firebase not initialized',
      message: 'Server configuration error — check FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY, and FIREBASE_DATABASE_URL are set in Vercel.',
    });
  }

  try {
    if (KILL_SWITCH_ENABLED) {
      console.warn('Firebase communication disabled by kill switch');
      return res.status(503).json({
        error: 'Firebase communication disabled by kill switch',
        message: 'Data not stored - kill switch is active',
      });
    }
    
    // Parse request body
    const sensorData = req.body;
    
    // Basic validation
    if (!sensorData) {
      return res.status(400).json({ error: 'No sensor data provided' });
    }
    
    // Add server timestamp and format dates
    const now = new Date();
    const timestamp = now.getTime(); // Unix timestamp in milliseconds
    const dataToStore = {
      ...sensorData,
      timestamp: timestamp,
      serverTimestamp: timestamp,
      receivedAt: format(now, "yyyy-MM-dd'T'HH:mm:ss.SSSxxx"),
    };
    
    // Store data in Realtime Database with a push (auto-generated key)
    const newEntryRef = db.ref(SENSOR_DATA_PATH).push();
    await newEntryRef.set(dataToStore);

    // Refresh health state on every sensor upload as a fallback heartbeat.
    await db.ref(DEVICE_HEALTH_PATH).set({
      lastPingAt: timestamp,
      lastPingIso: now.toISOString(),
      source: sensorData.source || 'rpi-drishti',
      pingIntervalMs: HEALTH_PING_INTERVAL_MS,
      timeoutMs: HEALTH_TIMEOUT_MS,
      updatedAt: timestamp,
    });
    
    // Return success response
    return res.status(201).json({ 
      message: 'Sensor data stored successfully', 
      id: newEntryRef.key,
      timestamp: now.toISOString()
    });
  } catch (error) {
    console.error('Error storing sensor data:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
}