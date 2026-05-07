export type ReservationStatus = 'ACTIVE' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED';

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type Product = {
  id: string;
  sku: string;
  name: string;
  totalStock: number;
  availableStock: number;
  reservedStock: number;
  soldStock: number;
  version: number;
};

export type Reservation = {
  reservationId: string;
  productId: string;
  userId: string;
  quantity: number;
  status: ReservationStatus;
  expiresAt: string;
};

export type Order = {
  orderId: string;
  reservationId: string;
  productId: string;
  userId: string;
  quantity: number;
  createdAt: string;
};

export type Health = {
  status: string;
  components?: Record<string, { status: string }>;
};

export type CreateProductRequest = {
  sku: string;
  name: string;
  totalStock: number;
};

export type ReserveInventoryRequest = {
  productId: string;
  userId: string;
  quantity: number;
};
