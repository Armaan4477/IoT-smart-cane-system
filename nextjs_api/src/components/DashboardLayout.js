// Dashboard layout component
import React from 'react';
import Head from 'next/head';

const DashboardLayout = ({ children, title = 'Raspberry Pi Sensor Dashboard' }) => {
  return (
    <div className="min-h-screen bg-gray-100">
      <Head>
        <title>{title}</title>
        <link rel="icon" href="/favicon.ico" />
        <meta name="description" content="Raspberry Pi Sensor Data Dashboard" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </Head>

      <header className="bg-blue-600 shadow-lg">
        <div className="container mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-white">Raspberry Pi Sensor Dashboard</h1>
        </div>
      </header>

      <main className="container mx-auto px-4 py-6">
        {children}
      </main>

      <footer className="bg-gray-800 text-white py-4">
        <div className="container mx-auto px-4 text-center">
          <p className="text-sm">Raspberry Pi Sensor Dashboard &copy; {new Date().getFullYear()}</p>
        </div>
      </footer>
    </div>
  );
};

export default DashboardLayout;