import { request, requestAll } from './client';
import type { CreateProductRequest, Product } from './types';

export function listProducts() {
  return requestAll<Product>('/products?size=100&sort=sku,asc', (item) => item.id);
}

export function createProduct(payload: CreateProductRequest) {
  return request<Product>('/products', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
