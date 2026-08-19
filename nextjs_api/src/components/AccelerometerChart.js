// AccelerometerChart component
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

const AccelerometerChart = ({ data }) => {
  const timestamps = data.map((entry) => {
    const date = new Date(entry.timestamp);
    return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`;
  });

  const chartData = {
    labels: timestamps,
    datasets: [
      {
        label: 'X Axis',
        data: data.map((entry) => entry.accelX),
        borderColor: 'rgb(255, 99, 132)',
        backgroundColor: 'rgba(255, 99, 132, 0.5)',
        tension: 0.1,
      },
      {
        label: 'Y Axis',
        data: data.map((entry) => entry.accelY),
        borderColor: 'rgb(53, 162, 235)',
        backgroundColor: 'rgba(53, 162, 235, 0.5)',
        tension: 0.1,
      },
      {
        label: 'Z Axis',
        data: data.map((entry) => entry.accelZ),
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
        text: 'Accelerometer Data (m/s²)',
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

export default memo(AccelerometerChart);