// Kill Switch Context to control Firebase communication
import { createContext, useContext } from 'react';

// Import the shared constant — API routes import directly from lib/kill-switch.js
// to avoid this file (which contains JSX) being loaded in a Node.js serverless context.
export { KILL_SWITCH_ENABLED } from '../lib/kill-switch';


export const KillSwitchProvider = ({ children }) => {
  // Use the hardcoded value
  const isKilled = KILL_SWITCH_ENABLED;

  // These functions are no-ops since the value is hardcoded
  // They are kept for API compatibility
  const toggleKillSwitch = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in KillSwitchContext.js to toggle.');
  };

  const enableCommunication = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in KillSwitchContext.js to enable.');
  };

  const disableCommunication = () => {
    console.warn('Kill switch is hardcoded. Change KILL_SWITCH_ENABLED in KillSwitchContext.js to disable.');
  };

  return (
    <KillSwitchContext.Provider 
      value={{ 
        isKilled, 
        toggleKillSwitch, 
        enableCommunication, 
        disableCommunication,
        isCommunicationEnabled: !isKilled
      }}
    >
      {children}
    </KillSwitchContext.Provider>
  );
};
