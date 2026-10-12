import { Activity, RefreshCw } from 'lucide-react';
import type { Health } from '../api/types';

type HeaderProps = {
  health: Health | null;
  loading: boolean;
  onRefresh: () => void;
};

export function Header({ health, loading, onRefresh }: HeaderProps) {
  const isUp = health?.status === 'UP';

  return (
    <header className="app-header">
      <div>
        <p className="eyebrow">Backend Demo Console</p>
        <h1>Inventory Reservation System</h1>
      </div>
      <div className="header-actions">
        <div className={`health-pill ${isUp ? 'health-up' : 'health-down'}`}>
          <Activity size={16} />
          <span>{health?.status ?? (loading ? 'CHECKING' : 'UNKNOWN')}</span>
        </div>
        <button className="icon-button" type="button" onClick={onRefresh} disabled={loading} title="Refresh dashboard">
          <RefreshCw size={18} className={loading ? 'spin' : ''} />
        </button>
      </div>
    </header>
  );
}
