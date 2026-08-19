// Firebase admin SDK for server-side operations
import admin from 'firebase-admin';

// Check if Firebase admin is already initialized to prevent multiple initializations
if (!admin.apps.length) {
  const missingVars = [];
  if (!process.env.FIREBASE_PROJECT_ID) missingVars.push('FIREBASE_PROJECT_ID');
  if (!process.env.FIREBASE_CLIENT_EMAIL) missingVars.push('FIREBASE_CLIENT_EMAIL');
  if (!process.env.FIREBASE_PRIVATE_KEY) missingVars.push('FIREBASE_PRIVATE_KEY');
  if (!process.env.FIREBASE_DATABASE_URL) missingVars.push('FIREBASE_DATABASE_URL');

  if (missingVars.length > 0) {
    console.error('Firebase admin: missing environment variables:', missingVars.join(', '));
  } else {
    try {
      admin.initializeApp({
        credential: admin.credential.cert({
          projectId: process.env.FIREBASE_PROJECT_ID,
          clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
          // Vercel stores env vars with literal \n — replace them with real newlines
          privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
        }),
        // Always use the explicit DATABASE_URL — the default fallback uses the wrong region
        databaseURL: process.env.FIREBASE_DATABASE_URL,
      });
      console.log('Firebase admin initialized successfully');
    } catch (error) {
      console.error('Firebase admin initialization error:', error.stack);
    }
  }
}

// Export the Realtime Database instance, guarded so a failed init doesn't crash the module
let db;
try {
  db = admin.database();
} catch (error) {
  console.error('Firebase admin: failed to get database instance:', error.message);
  // db remains undefined — API handlers must check and return 503
  db = null;
}

export default db;
export { admin };