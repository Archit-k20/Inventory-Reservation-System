import type { ReservationStatus } from '../api/types';

type StatusBadgeProps = {
  status: ReservationStatus | string;
};

export function StatusBadge({ status }: StatusBadgeProps) {
  const className = `status status-${status.toLowerCase()}`;

  return <span className={className}>{status}</span>;
}
