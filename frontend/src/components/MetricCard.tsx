import type { LucideIcon } from 'lucide-react';

type MetricCardProps = {
  icon: LucideIcon;
  label: string;
  value: string | number;
  tone?: 'green' | 'yellow' | 'blue' | 'gray';
};

export function MetricCard({ icon: Icon, label, value, tone = 'gray' }: MetricCardProps) {
  return (
    <div className={`metric metric-${tone}`}>
      <div className="metric-icon">
        <Icon size={18} />
      </div>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
      </div>
    </div>
  );
}
