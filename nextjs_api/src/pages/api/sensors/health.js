import db from '../../../lib/firebase-admin';
import {
  DEVICE_HEALTH_PATH,
  HEALTH_PING_INTERVAL_MS,
  HEALTH_TIMEOUT_MS,
  isDeviceOnline,
} from '../../../lib/device-health';
import { KILL_SWITCH_ENABLED } from '../../../context/KillSwitchContext';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  try {
    if (KILL_SWITCH_ENABLED) {
      console.warn('Firebase communication disabled by kill switch');
      return res.status(503).json({
        error: 'Firebase communication disabled by kill switch',
        message: 'Health ping not stored - kill switch is active',
      });
    }

    const now = Date.now();
    const source = (req.body && req.body.source) || 'rpi-drishti';

    const payload = {
      lastPingAt: now,
      lastPingIso: new Date(now).toISOString(),
      source,
      pingIntervalMs: HEALTH_PING_INTERVAL_MS,
      timeoutMs: HEALTH_TIMEOUT_MS,
      updatedAt: now,
    };

    await db.ref(DEVICE_HEALTH_PATH).set(payload);

    return res.status(200).json({
      message: 'Health ping recorded',
      health: {
        ...payload,
        isOnline: isDeviceOnline(now),
      },
    });
  } catch (error) {
    console.error('Error storing health ping:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
}
