import { requestAll } from './client';
import type { Order } from './types';

export function listOrders() {
  return requestAll<Order>('/orders?size=100&sort=createdAt,desc&sort=orderId,asc', (item) => item.orderId);
}
