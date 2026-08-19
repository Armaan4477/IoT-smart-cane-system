// Firebase Admin SDK — modular API (firebase-admin v14+)
// Do NOT use `import admin from 'firebase-admin'` — the default export no longer
// provides `.apps`, `.initializeApp()`, etc. Use the sub-package imports instead.
import { initializeApp, getApps, cert } from 'firebase-admin/app';
import { getDatabase } from 'firebase-admin/database';

const missingVars = [];
if (!process.env.FIREBASE_PROJECT_ID) missingVars.push('FIREBASE_PROJECT_ID');
if (!process.env.FIREBASE_CLIENT_EMAIL) missingVars.push('FIREBASE_CLIENT_EMAIL');
if (!process.env.FIREBASE_PRIVATE_KEY) missingVars.push('FIREBASE_PRIVATE_KEY');
if (!process.env.FIREBASE_DATABASE_URL) missingVars.push('FIREBASE_DATABASE_URL');

if (missingVars.length > 0) {
  console.error('Firebase admin: missing environment variables:', missingVars.join(', '));
} else if (!getApps().length) {
  try {
    initializeApp({
      credential: cert({
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

// Export the Realtime Database instance, guarded so a failed init doesn't crash the module
let db = null;
try {
  if (getApps().length > 0) {
    db = getDatabase();
  } else {
    console.error('Firebase admin: no app initialized — db will be null');
  }
} catch (error) {
  console.error('Firebase admin: failed to get database instance:', error.message);
  db = null;
}

export default db;