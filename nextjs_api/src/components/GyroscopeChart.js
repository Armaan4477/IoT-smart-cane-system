// GyroscopeChart component
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

const GyroscopeChart = ({ data }) => {
  const timestamps = data.map((entry) => {
    const date = new Date(entry.timestamp);
    return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`;
  });

  const chartData = {
    labels: timestamps,
    datasets: [
      {
        label: 'X Axis',
        data: data.map((entry) => entry.gyroX),
        borderColor: 'rgb(255, 159, 64)',
        backgroundColor: 'rgba(255, 159, 64, 0.5)',
        tension: 0.1,
      },
      {
        label: 'Y Axis',
        data: data.map((entry) => entry.gyroY),
        borderColor: 'rgb(153, 102, 255)',
        backgroundColor: 'rgba(153, 102, 255, 0.5)',
        tension: 0.1,
      },
      {
        label: 'Z Axis',
        data: data.map((entry) => entry.gyroZ),
        borderColor: 'rgb(201, 203, 207)',
        backgroundColor: 'rgba(201, 203, 207, 0.5)',
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
        text: 'Gyroscope Data (rad/s)',
      },
    },
    scales: {
      y: {
        beginAtZero: false,
      },
    },
  };

  return (
    <div className="bg-white p-4 rounded-lg shadow-md h-80">
      <Line data={chartData} options={options} />
    </div>
  );
};

export default memo(GyroscopeChart);