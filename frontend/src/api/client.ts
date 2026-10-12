import type { Page } from './types';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function request<T>(path: string, init?: RequestInit, acceptedStatuses: number[] = []): Promise<T> {
  const timeout = AbortSignal.timeout(10_000);
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    signal: init?.signal ? AbortSignal.any([init.signal, timeout]) : timeout,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!response.ok && !acceptedStatuses.includes(response.status)) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      message = body.detail ?? body.title ?? message;
    } catch {
      // A proxy may return HTML rather than a problem response.
    }
    throw new ApiError(message, response.status);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export async function requestAll<T>(path: string, key: (item: T) => string): Promise<T[]> {
  const items = new Map<string, T>();
  const signal = AbortSignal.timeout(30_000);
  const separator = path.includes('?') ? '&' : '?';
  let pages = 1;
  for (let page = 0; page < pages; page++) {
    const response = await request<Page<T>>(`${path}${separator}page=${page}`, { signal });
    if (response.number !== page || !Number.isInteger(response.totalPages) || response.totalPages < 0) {
      throw new Error('The server returned invalid pagination data. Refresh to try again.');
    }
    pages = response.totalPages;
    response.content.forEach((item) => items.set(key(item), item));
  }
  return [...items.values()];
}
