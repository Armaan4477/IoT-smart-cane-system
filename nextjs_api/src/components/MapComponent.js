// MapComponent to display GPS location
import React, { useEffect, useRef } from 'react';

// Note: For a production application, you should include the map library properly
// This is a simplified version for demonstration purposes
const MapComponent = ({ latitude, longitude, isValid }) => {
  const mapRef = useRef(null);
  
  useEffect(() => {
    // Check if coordinates are valid
    if (!isValid || !latitude || !longitude) {
      return;
    }

    // In a real application, you would initialize your map here
    // For example, using Leaflet or Google Maps
    
    // Display coordinates as text for this example
    if (mapRef.current) {
      mapRef.current.innerHTML = `
        <div class="p-4 text-center">
          <p>Map would show location at:</p>
          <p class="font-bold text-lg mt-2">Latitude: ${latitude.toFixed(6)}, Longitude: ${longitude.toFixed(6)}</p>
          <p class="text-sm text-gray-500 mt-4">
            For a full implementation, integrate a mapping library like Leaflet or Google Maps.
          </p>
        </div>
      `;
    }
  }, [latitude, longitude, isValid]);

  if (!isValid) {
    return (
      <div className="bg-white p-4 rounded-lg shadow-md h-80 flex items-center justify-center">
        <p className="text-gray-500">GPS location not available</p>
      </div>
    );
  }

  return (
    <div 
      ref={mapRef} 
      className="bg-white p-4 rounded-lg shadow-md h-80 flex items-center justify-center border"
    >
      Loading map...
    </div>
  );
};

export default MapComponent;