/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // firebase-admin v14+ must NOT be bundled by webpack — it relies on Node.js
  // built-ins that break when bundled. This tells Next.js to treat it as an
  // external package and load it natively at runtime in the serverless function.
  serverExternalPackages: ['firebase-admin'],
};

module.exports = nextConfig;