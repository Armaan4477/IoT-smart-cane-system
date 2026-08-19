# ESP32 Sensor Dashboard

This project consists of an ESP32 firmware that collects sensor data (GPS, accelerometer, gyroscope, temperature) and a Next.js web application that receives, stores, and displays this data in real-time.

## Project Structure

1. **ESP32 Firmware** - Arduino code to collect and upload sensor data
2. **Next.js API** - RESTful API endpoint to receive data from ESP32
3. **Firebase Database** - For storing and retrieving sensor data
4. **Web Dashboard** - Real-time visualization of sensor data

## Setup Instructions

### 1. Firebase Setup

1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable Realtime Database
   - Go to Build > Realtime Database
   - Click "Create Database"
   - Start in test mode for development (you can set up rules later)
   - Replace test rules with the indexed production-ready rules in `firebase.database.rules.json`
3. Create a web app in your Firebase project to get your configuration
4. Generate a service account key for server-side access:
   - Go to Project Settings > Service accounts
   - Click "Generate new private key"
   - Save the JSON file securely

### 2. Next.js Application Setup

1. Clone the repository
2. Install dependencies:
   ```bash
   cd esp32-nextjs
   npm install
   ```
3. Create a `.env.local` file with your Firebase configuration (based on `.env.local.example`):
   ```
   # Firebase Client Configuration
   NEXT_PUBLIC_FIREBASE_API_KEY=your-api-key
   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=your-project-id.firebaseapp.com
   NEXT_PUBLIC_FIREBASE_PROJECT_ID=your-project-id
   NEXT_PUBLIC_FIREBASE_DATABASE_URL=https://your-project-id-default-rtdb.firebaseio.com
   NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=your-project-id.appspot.com
   NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your-messaging-sender-id
   NEXT_PUBLIC_FIREBASE_APP_ID=your-app-id
   NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID=your-measurement-id
   NEXT_PUBLIC_SENSOR_WINDOW=20

   # Firebase Admin Configuration
   FIREBASE_PROJECT_ID=your-project-id
   FIREBASE_DATABASE_URL=https://your-project-id-default-rtdb.firebaseio.com
   FIREBASE_CLIENT_EMAIL=your-service-account-client-email
   FIREBASE_PRIVATE_KEY=your-service-account-private-key
   ```

4. Run the development server:
   ```bash
   npm run dev
   ```
   
5. Open [http://localhost:3000](http://localhost:3000) in your browser

### 3. ESP32 Configuration

1. Open the `esp32-with-api.ino` file in the Arduino IDE
2. Install required libraries:
   - TinyGPS++
   - WiFi
   - HTTPClient
   - Wire
   - Adafruit_MPU6050
   - Adafruit_Sensor
   - ArduinoJson
3. Update the WiFi credentials and API endpoint:
   ```cpp
   // WiFi credentials
   const char* ssid = "Your_WiFi_SSID";
   const char* password = "Your_WiFi_Password";

   // API endpoint URL - Replace with your Vercel deployment URL
   const char* apiEndpoint = "https://your-nextjs-app.vercel.app/api/sensors/data";
   ```

4. Upload the sketch to your ESP32

## Deploying to Vercel

1. Install the Vercel CLI:
   ```bash
   npm install -g vercel
   ```

2. Log in to Vercel:
   ```bash
   vercel login
   ```

3. Deploy the project:
   ```bash
   cd esp32-nextjs
   vercel
   ```

4. Follow the prompts to deploy your project
   - Link to an existing project or create a new one
   - The CLI will detect that it's a Next.js project
   - Set up environment variables using the Vercel UI or CLI

5. Once deployed, Vercel will provide a URL for your application
   - Update the `apiEndpoint` in your ESP32 code to point to this URL

6. For production deployment:
   ```bash
   vercel --prod
   ```

### Environment Variables on Vercel

Make sure to add all your Firebase configuration variables to your Vercel project:

1. Go to the Vercel dashboard
2. Select your project
3. Go to Settings > Environment Variables
4. Add all variables from your `.env.local` file
5. For `FIREBASE_PRIVATE_KEY`, make sure to include the entire key with newlines and quotes
6. Lower `NEXT_PUBLIC_SENSOR_WINDOW` (for example, `10-20`) to reduce dashboard realtime bandwidth and chart render load.

## Important Notes

1. In a production environment, consider adding authentication to your API endpoint to prevent unauthorized access.
2. Make sure your ESP32 has reliable WiFi connectivity.
3. Consider implementing error handling and retry logic in the ESP32 code for robustness.
4. If you're experiencing CORS issues during local development, you may need to configure CORS headers in your API endpoints.

## Realtime Database Rules

This project includes an indexed rules file at `firebase.database.rules.json`.

1. Open Firebase Console > Realtime Database > Rules.
2. Paste the contents of `firebase.database.rules.json`.
3. Publish the rules.

The rules include:

- `sensor_data` index on `timestamp` and `serverTimestamp` for efficient `orderByChild(...).limitToLast(...)` queries.
- read access only for `sensor_data` and `device_health/esp32_cam`.
- write access disabled for client SDK paths (server-side Admin SDK writes are unaffected).

## Troubleshooting

- **ESP32 not connecting**: Check your WiFi credentials and ensure the network is accessible.
- **Data not appearing in dashboard**: Check the ESP32 serial monitor for API response codes and verify your Firebase configuration.
- **API errors**: Check the Vercel function logs in your Vercel dashboard.

## License

MIT