// Firebase admin SDK for server-side operations
import admin from 'firebase-admin';

// Check if Firebase admin is already initialized to prevent multiple initializations
if (!admin.apps.length) {
  try {
    admin.initializeApp({
      credential: admin.credential.cert({
        projectId: process.env.FIREBASE_PROJECT_ID,
        clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
        privateKey: process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n'),
      }),
      databaseURL: process.env.FIREBASE_DATABASE_URL || `https://${process.env.FIREBASE_PROJECT_ID}-default-rtdb.firebaseio.com`,
    });
  } catch (error) {
    console.log('Firebase admin initialization error', error.stack);
  }
}

// Export the Realtime Database instance
const db = admin.database();
export default db;
export { admin };