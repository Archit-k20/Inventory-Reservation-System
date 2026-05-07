import { AlertCircle, Boxes, CheckCircle2, Clock3, Package, ShoppingCart, Warehouse } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { createProduct, listProducts } from './api/products';
import { confirmReservation, listReservations, releaseReservation, reserveStock } from './api/reservations';
import { listOrders } from './api/orders';
import { getHealth } from './api/health';
import { simulateOrderFailure } from './api/demo';
import type { CreateProductRequest, Health, Order, Product, Reservation, ReserveInventoryRequest } from './api/types';
import { CreateProductForm } from './components/CreateProductForm';
import { Header } from './components/Header';
import { KafkaDemoPanel } from './components/KafkaDemoPanel';
import { MetricCard } from './components/MetricCard';
import { OrderTable } from './components/OrderTable';
import { ProductTable } from './components/ProductTable';
import { ReservationTable } from './components/ReservationTable';
import { ReserveStockForm } from './components/ReserveStockForm';

function App() {
  const [products, setProducts] = useState<Product[]>([]);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [health, setHealth] = useState<Health | null>(null);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busyReservationId, setBusyReservationId] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [productsPage, reservationsPage, ordersPage, healthResponse] = await Promise.all([
        listProducts(),
        listReservations(),
        listOrders(),
        getHealth(),
      ]);
      setProducts(productsPage.content);
      setReservations(reservationsPage.content);
      setOrders(ordersPage.content);
      setHealth(healthResponse);
      setSelectedProductId((current) => current || productsPage.content[0]?.id || '');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to load dashboard data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const totals = useMemo(
    () =>
      products.reduce(
        (accumulator, product) => ({
          available: accumulator.available + product.availableStock,
          reserved: accumulator.reserved + product.reservedStock,
          sold: accumulator.sold + product.soldStock,
        }),
        { available: 0, reserved: 0, sold: 0 },
      ),
    [products],
  );

  const activeReservations = reservations.filter((reservation) => reservation.status === 'ACTIVE').length;

  async function runAction(successMessage: string, action: () => Promise<void>) {
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      await refresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Action failed');
    }
  }

  async function handleCreateProduct(payload: CreateProductRequest) {
    await runAction('Product created successfully.', async () => {
      const product = await createProduct(payload);
      setSelectedProductId(product.id);
    });
  }

  async function handleReserve(payload: ReserveInventoryRequest, idempotencyKey?: string) {
    await runAction('Reservation request processed.', async () => {
      await reserveStock(payload, idempotencyKey);
    });
  }

  async function handleReservationAction(reservationId: string, action: () => Promise<void>, successMessage: string) {
    setBusyReservationId(reservationId);
    try {
      await runAction(successMessage, action);
    } finally {
      setBusyReservationId('');
    }
  }

  async function handleKafkaSimulation(reservationId: string, reason: string) {
    await runAction('order.failed event published. Refreshing stock after listener processing.', async () => {
      await simulateOrderFailure(reservationId, reason);
      await new Promise((resolve) => window.setTimeout(resolve, 1200));
    });
  }

  return (
    <main className="app-shell">
      <Header health={health} loading={loading} onRefresh={refresh} />

      {error ? (
        <div className="notice notice-error">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      ) : null}

      {message ? (
        <div className="notice notice-success">
          <CheckCircle2 size={18} />
          <span>{message}</span>
        </div>
      ) : null}

      <section className="metrics-grid">
        <MetricCard icon={Package} label="Products" value={products.length} tone="gray" />
        <MetricCard icon={Clock3} label="Active Reservations" value={activeReservations} tone="yellow" />
        <MetricCard icon={Warehouse} label="Available Stock" value={totals.available} tone="green" />
        <MetricCard icon={Boxes} label="Reserved Stock" value={totals.reserved} tone="yellow" />
        <MetricCard icon={ShoppingCart} label="Sold Stock" value={totals.sold} tone="blue" />
      </section>

      <section className="content-grid">
        <ProductTable products={products} selectedProductId={selectedProductId} onSelect={setSelectedProductId} />
        <CreateProductForm onCreate={handleCreateProduct} />
        <ReserveStockForm
          products={products}
          selectedProductId={selectedProductId}
          onSelectProduct={setSelectedProductId}
          onReserve={handleReserve}
        />
        <ReservationTable
          reservations={reservations}
          busyId={busyReservationId}
          onConfirm={(reservationId) =>
            handleReservationAction(
              reservationId,
              () => confirmReservation(reservationId).then(() => undefined),
              'Reservation confirmed and order created.',
            )
          }
          onRelease={(reservationId) =>
            handleReservationAction(
              reservationId,
              () => releaseReservation(reservationId).then(() => undefined),
              'Reservation released back to available stock.',
            )
          }
        />
        <OrderTable orders={orders} />
        <KafkaDemoPanel reservations={reservations} onSimulate={handleKafkaSimulation} />
      </section>
    </main>
  );
}

export default App;
