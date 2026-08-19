// Helper functions to interact with Firebase Realtime Database
import {
  ref,
  set,
  push,
  onValue,
  onChildAdded,
  onChildChanged,
  onChildRemoved,
  query,
  orderByChild,
  limitToLast,
  get,
} from 'firebase/database';
import { db } from './firebase';
import { KILL_SWITCH_ENABLED } from '../context/KillSwitchContext';
import {
  DEVICE_HEALTH_PATH,
  HEALTH_TIMEOUT_MS,
  isDeviceOnline,
} from './device-health';

// Path to the sensor data in the database
const SENSOR_DATA_PATH = 'sensor_data';

// Kill switch check helper
const isKillSwitchActive = () => {
  return KILL_SWITCH_ENABLED;
};

/**
 * Add ESP32 sensor data to Realtime Database
 * @param {Object} sensorData - ESP32 sensor data object
 * @returns {Promise<Object>} - Created data reference
 */
export const addSensorData = async (sensorData) => {
  // Check kill switch
  if (isKillSwitchActive()) {
    console.warn('Firebase communication disabled by kill switch');
    throw new Error('Firebase communication disabled by kill switch');
  }
  
  try {
    // Add timestamp to the data
    const dataWithTimestamp = {
      ...sensorData,
      timestamp: Date.now(),
    };
    
    // Create a reference to a new location and add the data
    const newDataRef = push(ref(db, SENSOR_DATA_PATH));
    await set(newDataRef, dataWithTimestamp);
    
    return { 
      id: newDataRef.key, 
      ...dataWithTimestamp 
    };
  } catch (error) {
    console.error('Error adding sensor data:', error);
    throw error;
  }
};

/**
 * Get the latest sensor data entries from Realtime Database
 * @param {number} limitCount - Maximum number of entries to retrieve
 * @returns {Promise<Array>} - Array of sensor data objects
 */
export const getLatestSensorData = async (limitCount = 100) => {
  // Check kill switch
  if (isKillSwitchActive()) {
    console.warn('Firebase communication disabled by kill switch');
    return [];
  }
  
  try {
    // Create query to get the latest entries
    const dataQuery = query(
      ref(db, SENSOR_DATA_PATH),
      orderByChild('timestamp'),
      limitToLast(limitCount)
    );
    
    // Execute query
    const snapshot = await get(dataQuery);
    
    if (snapshot.exists()) {
      const data = [];
      // Convert object to array with IDs
      snapshot.forEach((childSnapshot) => {
        data.push({
          id: childSnapshot.key,
          ...childSnapshot.val(),
          // Convert timestamp to JavaScript Date
          timestamp: new Date(childSnapshot.val().timestamp),
        });
      });
      
      // Sort by timestamp descending (newest first)
      return data.sort((a, b) => b.timestamp - a.timestamp);
    }
    
    return [];
  } catch (error) {
    console.error('Error getting sensor data:', error);
    throw error;
  }
};

/**
 * Set up a real-time listener for sensor data updates
 * @param {Function} callback - Function to call when data changes
 * @param {number} limitCount - Maximum number of entries to retrieve
 * @returns {Function} - Unsubscribe function
 */
export const subscribeToSensorData = (callback, limitCount = 20) => {
  // Check kill switch before setting up listener
  if (isKillSwitchActive()) {
    console.warn('Firebase communication disabled by kill switch');
    // Return a no-op unsubscribe function
    return () => {};
  }
  
  const dataQuery = query(
    ref(db, SENSOR_DATA_PATH),
    orderByChild('timestamp'),
    limitToLast(limitCount)
  );

  const toRecord = (childSnapshot) => {
    const payload = childSnapshot.val() || {};
    const parsedTimestamp = Number(payload.timestamp);
    const timestampMs = Number.isFinite(parsedTimestamp) ? parsedTimestamp : 0;

    return {
      id: childSnapshot.key,
      ...payload,
      timestampMs,
    };
  };

  const records = new Map();
  let emitScheduled = false;

  // Coalesce bursts of child events to avoid unnecessary React re-renders.
  const scheduleEmit = () => {
    if (emitScheduled) {
      return;
    }
    emitScheduled = true;

    Promise.resolve().then(() => {
      emitScheduled = false;

      if (isKillSwitchActive()) {
        callback([]);
        return;
      }

      const data = Array.from(records.values())
        .sort((a, b) => b.timestampMs - a.timestampMs)
        .map(({ timestampMs, ...item }) => ({
          ...item,
          timestamp: new Date(timestampMs),
        }));

      callback(data);
    });
  };

  const onError = (error) => {
    console.error('Error subscribing to sensor data:', error);
    callback([], error);
  };

  const unsubscribeAdded = onChildAdded(dataQuery, (childSnapshot) => {
    records.set(childSnapshot.key, toRecord(childSnapshot));
    scheduleEmit();
  }, onError);

  const unsubscribeChanged = onChildChanged(dataQuery, (childSnapshot) => {
    records.set(childSnapshot.key, toRecord(childSnapshot));
    scheduleEmit();
  }, onError);

  const unsubscribeRemoved = onChildRemoved(dataQuery, (childSnapshot) => {
    records.delete(childSnapshot.key);
    scheduleEmit();
  }, onError);

  // Return unsubscribe function
  return () => {
    unsubscribeAdded();
    unsubscribeChanged();
    unsubscribeRemoved();
  };
};

export const subscribeToDeviceHealth = (callback) => {
  if (isKillSwitchActive()) {
    return () => {};
  }

  const healthRef = ref(db, DEVICE_HEALTH_PATH);

  const unsubscribe = onValue(healthRef, (snapshot) => {
    if (isKillSwitchActive()) {
      callback({
        isOnline: false,
        lastPingAt: null,
        timeoutMs: HEALTH_TIMEOUT_MS,
      });
      return;
    }

    const health = snapshot.exists() ? snapshot.val() : {};
    const lastPingAt = Number(health.lastPingAt || 0);
    const now = Date.now();

    callback({
      isOnline: isDeviceOnline(lastPingAt, now),
      lastPingAt: lastPingAt > 0 ? lastPingAt : null,
      healthAgeMs: lastPingAt > 0 ? now - lastPingAt : null,
      timeoutMs: HEALTH_TIMEOUT_MS,
    });
  }, (error) => {
    callback({
      isOnline: false,
      lastPingAt: null,
      timeoutMs: HEALTH_TIMEOUT_MS,
      error,
    });
  });

  return unsubscribe;
};