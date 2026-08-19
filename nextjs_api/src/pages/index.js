// Home page with sensor dashboard
import { useState, useEffect } from 'react';
import { subscribeToSensorData, subscribeToDeviceHealth } from '../lib/firebase-database';
import { useKillSwitch } from '../context/KillSwitchContext';
import DashboardLayout from '../components/DashboardLayout';
import SensorCard from '../components/SensorCard';
import DistanceChart from '../components/DistanceChart';
import AccelerometerChart from '../components/AccelerometerChart';
import GyroscopeChart from '../components/GyroscopeChart';
import MapComponent from '../components/MapComponent';
import DataTable from '../components/DataTable';
import KillSwitch from '../components/KillSwitch';
import { HEALTH_TIMEOUT_MS } from '../lib/device-health';

const parsedLiveWindow = Number.parseInt(process.env.NEXT_PUBLIC_SENSOR_WINDOW || '20', 10);
const LIVE_SENSOR_WINDOW = Number.isFinite(parsedLiveWindow) && parsedLiveWindow > 0
  ? Math.min(parsedLiveWindow, 100)
  : 20;

export default function Home() {
  const [sensorData, setSensorData] = useState([]);
  const [latestData, setLatestData] = useState(null);
  const [health, setHealth] = useState({
    isOnline: false,
    lastPingAt: null,
    timeoutMs: HEALTH_TIMEOUT_MS,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { isKilled } = useKillSwitch();

  useEffect(() => {
    // If kill switch is active, clear data and don't subscribe
    if (isKilled) {
      setSensorData([]);
      setLatestData(null);
      setHealth({
        isOnline: false,
        lastPingAt: null,
        timeoutMs: HEALTH_TIMEOUT_MS,
      });
      setLoading(false);
      return;
    }
    
    setLoading(true);
    
    // Set up the real-time listener using our helper function
    const unsubscribe = subscribeToSensorData(
      // Callback function that will be called whenever data changes
      (data, error) => {
        if (error) {
          console.error('Error getting sensor data:', error);
          setError(error.message);
          setLoading(false);
          return;
        }
        
        // Update state with all data
        setSensorData(data);
        
        // Update latest data if we have any
        if (data.length > 0) {
          setLatestData(data[0]);
        }
        
        setLoading(false);
      },
      LIVE_SENSOR_WINDOW // Configurable rolling window for live charts/table
    );

    const unsubscribeHealth = subscribeToDeviceHealth((healthState) => {
      setHealth((prev) => ({
        ...prev,
        ...healthState,
      }));
    });

    // Clean up the listener when component unmounts
    return () => {
      unsubscribe();
      unsubscribeHealth();
    };
  }, [isKilled]);

  useEffect(() => {
    if (isKilled) {
      return;
    }

    const id = setInterval(() => {
      setHealth((prev) => {
        if (!prev.lastPingAt) {
          return prev;
        }

        const timeoutMs = prev.timeoutMs || HEALTH_TIMEOUT_MS;
        const online = Date.now() - prev.lastPingAt <= timeoutMs;
        if (online === prev.isOnline) {
          return prev;
        }

        return {
          ...prev,
          isOnline: online,
          healthAgeMs: Date.now() - prev.lastPingAt,
        };
      });
    }, 1000);

    return () => clearInterval(id);
  }, [isKilled]);

  const isOnline = !isKilled && health.isOnline;

  const displayNumber = (value, digits = 1) => {
    if (!isOnline || value === null || value === undefined || Number.isNaN(Number(value))) {
      return '--';
    }
    return Number(value).toFixed(digits);
  };

  const displayText = (value) => {
    if (!isOnline) {
      return '--';
    }
    if (value === null || value === undefined || value === '') {
      return '--';
    }
    return value;
  };

  const chartData = isOnline ? sensorData : [];

  const hasLivePayload = Boolean(latestData) && isOnline;

  // Format date-time for display
  const formatDateTime = (date) => {
    if (!date) return 'N/A';
    
    const options = { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    };
    
    return date.toLocaleDateString(undefined, options);
  };

  return (
    <DashboardLayout>
      <div className="mb-6">
        <h2 className="text-2xl font-semibold mb-2">ESP32 Sensor Dashboard</h2>
        <p className="text-gray-600">
          Real-time sensor data from ESP32 device
        </p>
        
        {/* Kill Switch Component */}
        <div className="my-6">
          <KillSwitch />
        </div>
        
        {isKilled ? (
          <div className="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 my-4">
            <p className="font-semibold">Firebase Communication Disabled</p>
            <p>All Firebase operations have been stopped. Enable the kill switch to resume data updates.</p>
          </div>
        ) : loading ? (
          <div className="flex justify-center items-center h-40">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
          </div>
        ) : error ? (
          <div className="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 my-4">
            <p>Error: {error}</p>
            <p>Please check your Firebase configuration and connection.</p>
          </div>
        ) : (
          <>
            <div className="mb-4">
              <p className="text-gray-500">
                Device status: {isOnline ? 'Online' : 'Offline'}
              </p>
              <p className="text-gray-500">
                Last updated: {hasLivePayload ? formatDateTime(latestData.timestamp) : '--'}
              </p>
            </div>

            {!isOnline && (
              <div className="bg-amber-100 border-l-4 border-amber-500 text-amber-800 p-4 my-4">
                <p className="font-semibold">Device Offline</p>
                <p>No heartbeat received for more than 40 seconds. Sensor values are hidden to avoid stale readings.</p>
              </div>
            )}

            {!latestData && isOnline && (
              <div className="bg-yellow-100 border-l-4 border-yellow-500 text-yellow-700 p-4 my-4">
                <p>No sensor data available yet.</p>
                <p>Make sure your ESP32 device is connected and sending data.</p>
              </div>
            )}
            
            {/* Sensor Cards Row */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              <SensorCard 
                title="Distance" 
                value={displayNumber(latestData?.distance, 1)}
                unit="cm" 
                color="teal"
              />
              <SensorCard 
                title="Altitude" 
                value={displayNumber(latestData?.altitude, 1)}
                unit="m" 
                color="blue"
              />
              <SensorCard 
                title="Temperature" 
                value={displayNumber(latestData?.temperature, 1)}
                unit="°C" 
                color="orange"
              />
              <SensorCard 
                title="GPS Status" 
                value={isOnline ? (latestData?.valid ? 'Valid' : 'Invalid') : '--'}
                color={isOnline && latestData?.valid ? 'green' : 'red'}
              />
            </div>
            
            {/* Second Row of Sensor Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              <SensorCard 
                title="Satellites" 
                value={displayText(latestData?.satellites)}
                color="green"
              />
              <SensorCard 
                title="Accel X" 
                value={displayText(latestData?.accelX)}
                color="red"
              />
              <SensorCard 
                title="Accel Y" 
                value={displayText(latestData?.accelY)}
                color="blue"
              />
              <SensorCard 
                title="Accel Z" 
                value={displayText(latestData?.accelZ)}
                color="cyan"
              />
            </div>
            
            {/* Charts Row */}
            <div className="mb-6">
              <DistanceChart data={chartData} />
            </div>
            
            {/* MPU6050 Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
              <AccelerometerChart data={chartData} />
              <GyroscopeChart data={chartData} />
            </div>
            
            {/* Map */}
            <div className="mb-6">
              <h3 className="text-lg font-semibold mb-2">GPS Location</h3>
              <MapComponent 
                latitude={isOnline ? latestData?.latitude : null}
                longitude={isOnline ? latestData?.longitude : null}
                isValid={isOnline ? Boolean(latestData?.valid) : false}
              />
            </div>
            
            {/* Data Table */}
            <div className="mb-6">
              <h3 className="text-lg font-semibold mb-2">Recent Sensor Data</h3>
              <DataTable data={chartData} limit={10} />
            </div>
          </>
        )}
      </div>
    </DashboardLayout>
  );
}