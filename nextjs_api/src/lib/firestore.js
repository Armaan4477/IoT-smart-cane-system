// Helper functions to interact with Firestore
import { collection, addDoc, query, orderBy, limit, getDocs, serverTimestamp } from 'firebase/firestore';
import { firestore } from './firebase';
import { KILL_SWITCH_ENABLED } from '../context/KillSwitchContext';

// Collection name for ESP32 sensor data
const SENSOR_DATA_COLLECTION = 'sensor_data';

// Kill switch check helper
const isKillSwitchActive = () => {
  return KILL_SWITCH_ENABLED;
};

/**
 * Add ESP32 sensor data to Firestore
 * @param {Object} sensorData - ESP32 sensor data object
 * @returns {Promise<Object>} - Created document reference
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
      timestamp: serverTimestamp(),
    };
    
    // Add document to Firestore
    const docRef = await addDoc(collection(firestore, SENSOR_DATA_COLLECTION), dataWithTimestamp);
    return { id: docRef.id, ...dataWithTimestamp };
  } catch (error) {
    console.error('Error adding sensor data:', error);
    throw error;
  }
};

/**
 * Get the latest sensor data entries from Firestore
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
    const q = query(
      collection(firestore, SENSOR_DATA_COLLECTION),
      orderBy('timestamp', 'desc'),
      limit(limitCount)
    );
    
    // Execute query
    const querySnapshot = await getDocs(q);
    
    // Map documents to data objects
    return querySnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      // Convert Firestore Timestamp to JavaScript Date
      timestamp: doc.data().timestamp?.toDate() || new Date(),
    }));
  } catch (error) {
    console.error('Error getting sensor data:', error);
    throw error;
  }
};