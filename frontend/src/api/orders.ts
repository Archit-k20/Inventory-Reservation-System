import { request } from './client';
import type { Order, Page } from './types';

export function listOrders() {
  return request<Page<Order>>('/orders?size=100&sort=createdAt,desc');
}
