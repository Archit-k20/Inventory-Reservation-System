import { request } from './client';
import type { Health } from './types';

export function getHealth() {
  return request<Health>('/actuator/health');
}
