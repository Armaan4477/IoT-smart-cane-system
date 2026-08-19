// Kill Switch Context to control Firebase communication
import { createContext, useContext } from 'react';

// KILL_SWITCH_ENABLED lives in a plain JS file so API routes can import it
// without pulling in this JSX-containing file into a Node.js serverless context.
import { KILL_SWITCH_ENABLED } from '../lib/kill-switch';

export { KILL_SWITCH_ENABLED };

const KillSwitchContext = createContext();

export const useKillSwitch = () => {
  const context = useContext(KillSwitchContext);
  if (!context) {
    throw new Error('useKillSwitch must be used within a KillSwitchProvider');
  }
  return context;
};

export const KillSwitchProvider = ({ children }) => {
  const isKilled = KILL_SWITCH_ENABLED;

  // These functions are no-ops since the value is hardcoded.
  // They are kept for API compatibility.
  const toggleKillSwitch = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in lib/kill-switch.js to toggle.');
  };

  const enableCommunication = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in lib/kill-switch.js to enable.');
  };

  const disableCommunication = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in lib/kill-switch.js to disable.');
  };

  return (
    <KillSwitchContext.Provider
      value={{
        isKilled,
        toggleKillSwitch,
        enableCommunication,
        disableCommunication,
        isCommunicationEnabled: !isKilled,
      }}
    >
      {children}
    </KillSwitchContext.Provider>
  );
};
