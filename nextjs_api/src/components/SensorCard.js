// Card component for displaying sensor data
import React from 'react';

const SensorCard = ({ title, value, unit, icon, color = 'blue' }) => {
  const colorClasses = {
    blue: 'bg-blue-100 border-blue-500 text-blue-700',
    green: 'bg-green-100 border-green-500 text-green-700',
    red: 'bg-red-100 border-red-500 text-red-700',
    yellow: 'bg-yellow-100 border-yellow-500 text-yellow-700',
    purple: 'bg-purple-100 border-purple-500 text-purple-700',
    gray: 'bg-gray-100 border-gray-500 text-gray-700',
  };

  return (
    <div className={`p-4 rounded-lg shadow-md border-l-4 ${colorClasses[color] || colorClasses.blue}`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center">
          {icon && <span className="mr-2">{icon}</span>}
          <h3 className="font-semibold">{title}</h3>
        </div>
        <div className="text-right">
          <p className="text-2xl font-bold">
            {value} {unit && <span className="text-sm font-normal">{unit}</span>}
          </p>
        </div>
      </div>
    </div>
  );
};

export default SensorCard;