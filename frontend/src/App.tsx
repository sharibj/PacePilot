import { useState } from 'react';
import Coach from './coach/Coach';
import Simulator from './simulator/Simulator';

type Tab = 'simulator' | 'coach';

export default function App() {
  const [tab, setTab] = useState<Tab>('simulator');

  return (
    <main className="app">
      <h1>Pace Pilot</h1>
      <nav className="tabs">
        <button
          className={tab === 'simulator' ? 'tab tab-active' : 'tab'}
          onClick={() => setTab('simulator')}
        >
          Simulator
        </button>
        <button
          className={tab === 'coach' ? 'tab tab-active' : 'tab'}
          onClick={() => setTab('coach')}
        >
          Coach
        </button>
      </nav>
      {/* Both stay mounted so switching tabs preserves simulator streaming and coach chat state. */}
      <div hidden={tab !== 'simulator'}>
        <Simulator />
      </div>
      <div hidden={tab !== 'coach'}>
        <Coach />
      </div>
    </main>
  );
}
