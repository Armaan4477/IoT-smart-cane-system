import '../styles/globals.css';
import { KillSwitchProvider } from '../context/KillSwitchContext';

function MyApp({ Component, pageProps }) {
  return (
    <KillSwitchProvider>
      <Component {...pageProps} />
    </KillSwitchProvider>
  );
}

export default MyApp;