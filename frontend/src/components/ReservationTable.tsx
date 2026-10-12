import { CheckCircle2, Copy, RotateCcw } from 'lucide-react';
import type { Reservation } from '../api/types';
import { StatusBadge } from './StatusBadge';

type ReservationTableProps = {
  dataNote?: string;
  reservations: Reservation[];
  onConfirm: (reservationId: string) => Promise<void>;
  onRelease: (reservationId: string) => Promise<void>;
  busyId: string;
};

export function ReservationTable({ dataNote, reservations, onConfirm, onRelease, busyId }: ReservationTableProps) {
  return (
    <section className="panel table-panel wide-panel">
      <div className="panel-heading">
        <div>
          <h2>Reservations</h2>
          <p>Confirm active reservations into orders or release them back to stock.</p>
        </div>
        <RotateCcw size={20} />
      </div>
      {dataNote ? <p className="data-note" role="status">{dataNote}</p> : null}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Reservation</th>
              <th>User</th>
              <th>Quantity</th>
              <th>Status</th>
              <th>Expires</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {reservations.map((reservation) => {
              const isActive = reservation.status === 'ACTIVE';
              const isBusy = Boolean(busyId);
              return (
                <tr key={reservation.reservationId}>
                  <td>
                    <span className="mono">{reservation.reservationId.slice(0, 8)}</span>
                    <button
                      className="copy-button"
                      type="button"
                      title="Copy reservation id"
                      onClick={() => navigator.clipboard.writeText(reservation.reservationId)}
                    >
                      <Copy size={14} />
                    </button>
                  </td>
                  <td>{reservation.userId}</td>
                  <td>{reservation.quantity}</td>
                  <td>
                    <StatusBadge status={reservation.status} />
                  </td>
                  <td>{new Date(reservation.expiresAt).toLocaleString()}</td>
                  <td>
                    <div className="table-actions">
                      <button className="small-button" type="button" disabled={!isActive || isBusy} onClick={() => onConfirm(reservation.reservationId)}>
                        <CheckCircle2 size={14} />
                        Confirm
                      </button>
                      <button className="small-button" type="button" disabled={!isActive || isBusy} onClick={() => onRelease(reservation.reservationId)}>
                        <RotateCcw size={14} />
                        Release
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {reservations.length === 0 ? (
              <tr>
                <td colSpan={6} className="empty-cell">
                  {dataNote || 'No reservations yet.'}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
