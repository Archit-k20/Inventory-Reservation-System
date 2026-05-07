import { request } from './client';
import type { CreateProductRequest, Page, Product } from './types';

export function listProducts() {
  return request<Page<Product>>('/products?size=100&sort=sku,asc');
}

export function createProduct(payload: CreateProductRequest) {
  return request<Product>('/products', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
