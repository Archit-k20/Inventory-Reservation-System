import { request } from './client';
import type { Page, Reservation, ReserveInventoryRequest } from './types';

export function listReservations() {
  return request<Page<Reservation>>('/reservations?size=100&sort=expiresAt,desc');
}

export function reserveStock(payload: ReserveInventoryRequest, idempotencyKey?: string) {
  return request<Reservation>('/reservations', {
    method: 'POST',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    body: JSON.stringify(payload),
  });
}

export function confirmReservation(reservationId: string) {
  return request<{ reservationId: string; orderId: string; status: string }>(`/reservations/${reservationId}/confirm`, {
    method: 'POST',
  });
}

export function releaseReservation(reservationId: string) {
  return request<Reservation>(`/reservations/${reservationId}/release`, {
    method: 'POST',
  });
}
