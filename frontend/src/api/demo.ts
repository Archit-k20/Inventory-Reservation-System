import { request } from './client';

export function simulateOrderFailure(reservationId: string, reason: string) {
  return request<{ reservationId: string; event: string; reason: string; published: boolean }>(
    `/demo/orders/${reservationId}/fail`,
    {
      method: 'POST',
      body: JSON.stringify({ reason }),
    },
  );
}
