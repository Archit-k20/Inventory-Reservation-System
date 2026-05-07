import { RadioTower } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import type { Reservation } from '../api/types';

type KafkaDemoPanelProps = {
  reservations: Reservation[];
  onSimulate: (reservationId: string, reason: string) => Promise<void>;
};

export function KafkaDemoPanel({ reservations, onSimulate }: KafkaDemoPanelProps) {
  const activeReservations = reservations.filter((reservation) => reservation.status === 'ACTIVE');
  const [reservationId, setReservationId] = useState('');
  const [reason, setReason] = useState('payment_failed');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!reservationId) {
      return;
    }
    setSubmitting(true);
    try {
      await onSimulate(reservationId, reason);
      setReservationId('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="panel wide-panel kafka-panel">
      <div className="panel-heading">
        <div>
          <h2>Kafka Event Demo</h2>
          <p>Publish an order.failed event and let the backend listener release stock asynchronously.</p>
        </div>
        <RadioTower size={20} />
      </div>
      <form className="reserve-grid" onSubmit={handleSubmit}>
        <label>
          Active reservation
          <select value={reservationId} onChange={(event) => setReservationId(event.target.value)} required>
            <option value="">Select active reservation</option>
            {activeReservations.map((reservation) => (
              <option key={reservation.reservationId} value={reservation.reservationId}>
                {reservation.reservationId.slice(0, 8)} - {reservation.userId} - qty {reservation.quantity}
              </option>
            ))}
          </select>
        </label>
        <label>
          Reason
          <input value={reason} onChange={(event) => setReason(event.target.value)} required />
        </label>
        <button className="primary-button" type="submit" disabled={submitting || !reservationId}>
          <RadioTower size={16} />
          {submitting ? 'Publishing' : 'Simulate Failure'}
        </button>
      </form>
    </section>
  );
}
