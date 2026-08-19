export const DEVICE_HEALTH_PATH = 'device_health/esp32_cam';
export const HEALTH_PING_INTERVAL_MS = 5000;
export const HEALTH_TIMEOUT_MS = 40000;

export const isDeviceOnline = (lastPingAt, now = Date.now()) => {
  if (!lastPingAt) {
    return false;
  }

  const ping = Number(lastPingAt);
  if (!Number.isFinite(ping)) {
    return false;
  }

  return now - ping <= HEALTH_TIMEOUT_MS;
};
