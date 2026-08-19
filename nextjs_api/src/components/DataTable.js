// DataTable component to display raw sensor data
import React, { memo } from 'react';
import { format } from 'date-fns';

const DataTable = ({ data, limit = 10 }) => {
  // Take only the most recent entries up to the limit
  const limitedData = data.slice(0, limit);

  return (
    <div className="bg-white rounded-lg shadow-md overflow-hidden">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Time
              </th>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                GPS
              </th>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Distance (cm)
              </th>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Temp (°C)
              </th>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Accel (X,Y,Z)
              </th>
              <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Gyro (X,Y,Z)
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {limitedData.length === 0 && (
              <tr>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">--</td>
              </tr>
            )}
            {limitedData.map((entry, index) => {
              const timestamp = entry.timestamp instanceof Date 
                ? entry.timestamp 
                : new Date(entry.timestamp);
              
              return (
                <tr key={entry.id || index} className={index % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {format(timestamp, 'HH:mm:ss')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {entry.valid ? (
                      <span>
                        {entry.latitude?.toFixed(6)}, {entry.longitude?.toFixed(6)}
                      </span>
                    ) : (
                      <span className="text-red-500">Invalid</span>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {entry.distance?.toFixed(2)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {entry.temperature?.toFixed(1) || 'N/A'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    ({entry.accelX || 0}, {entry.accelY || 0}, {entry.accelZ || 0})
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    ({entry.gyroX || 0}, {entry.gyroY || 0}, {entry.gyroZ || 0})
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default memo(DataTable);