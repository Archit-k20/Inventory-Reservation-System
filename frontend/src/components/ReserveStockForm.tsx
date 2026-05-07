import { KeyRound, RotateCcw, ShoppingBasket } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import type { Product, ReserveInventoryRequest } from '../api/types';

type ReserveStockFormProps = {
  products: Product[];
  selectedProductId: string;
  onSelectProduct: (productId: string) => void;
  onReserve: (payload: ReserveInventoryRequest, idempotencyKey?: string) => Promise<void>;
};

function generateKey() {
  return `reserve-${crypto.randomUUID()}`;
}

export function ReserveStockForm({ products, selectedProductId, onSelectProduct, onReserve }: ReserveStockFormProps) {
  const [userId, setUserId] = useState('user-1');
  const [quantity, setQuantity] = useState(1);
  const [idempotencyKey, setIdempotencyKey] = useState(generateKey());
  const [submitting, setSubmitting] = useState(false);

  const selectedProduct = useMemo(
    () => products.find((product) => product.id === selectedProductId),
    [products, selectedProductId],
  );

  async function submitReservation(event?: FormEvent) {
    event?.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setSubmitting(true);
    try {
      await onReserve({ productId: selectedProductId, userId: userId.trim(), quantity }, idempotencyKey.trim());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="panel wide-panel">
      <div className="panel-heading">
        <div>
          <h2>Reserve Stock</h2>
          <p>Create an active reservation and watch stock move from available to reserved.</p>
        </div>
        <ShoppingBasket size={20} />
      </div>
      <form className="reserve-grid" onSubmit={submitReservation}>
        <label>
          Product
          <select value={selectedProductId} onChange={(event) => onSelectProduct(event.target.value)} required>
            <option value="">Select product</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name} ({product.availableStock} available)
              </option>
            ))}
          </select>
        </label>
        <label>
          User ID
          <input value={userId} onChange={(event) => setUserId(event.target.value)} required />
        </label>
        <label>
          Quantity
          <input
            min={1}
            max={selectedProduct?.availableStock || undefined}
            type="number"
            value={quantity}
            onChange={(event) => setQuantity(Number(event.target.value))}
            required
          />
        </label>
        <label className="key-field">
          Idempotency key
          <span>
            <input value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} />
            <button type="button" className="icon-button" title="Generate idempotency key" onClick={() => setIdempotencyKey(generateKey())}>
              <KeyRound size={16} />
            </button>
          </span>
        </label>
        <div className="button-row">
          <button className="primary-button" type="submit" disabled={submitting || !selectedProductId}>
            <ShoppingBasket size={16} />
            {submitting ? 'Reserving' : 'Reserve'}
          </button>
          <button className="secondary-button" type="button" onClick={() => submitReservation()} disabled={submitting || !selectedProductId}>
            <RotateCcw size={16} />
            Retry Same
          </button>
        </div>
      </form>
    </section>
  );
}
