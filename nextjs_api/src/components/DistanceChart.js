// DistanceChart component
import React, { memo } from 'react';
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

const DistanceChart = ({ data }) => {
  const timestamps = data.map((entry) => {
    const date = new Date(entry.timestamp);
    return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`;
  });

  const chartData = {
    labels: timestamps,
    datasets: [
      {
        label: 'Distance (cm)',
        data: data.map((entry) => entry.distance),
        borderColor: 'rgb(75, 192, 192)',
        backgroundColor: 'rgba(75, 192, 192, 0.5)',
        tension: 0.1,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: 'Ultrasonic Distance Measurements (cm)',
      },
    },
    scales: {
      y: {
        beginAtZero: true,
      },
    },
  };

  return (
    <div className="bg-white p-4 rounded-lg shadow-md h-80">
      <Line data={chartData} options={options} />
    </div>
  );
};

export default memo(DistanceChart);