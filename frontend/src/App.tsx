import { AlertCircle, Boxes, CheckCircle2, Clock3, Package, ShoppingCart, Warehouse } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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

function errorMessage(error: unknown): string {
  if (error instanceof Error && ['TimeoutError', 'AbortError'].includes(error.name)) {
    return 'The request timed out. Refresh to check the result before retrying a change.';
  }
  return error instanceof Error ? error.message : 'Request failed. Refresh to try again.';
}

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
  const [loadErrors, setLoadErrors] = useState<Record<string, string>>({});
  const [loaded, setLoaded] = useState<Record<string, boolean>>({});
  const refreshId = useRef(0);

  const refresh = useCallback(async () => {
    const id = ++refreshId.current;
    setLoading(true);
    const results = await Promise.allSettled([listProducts(), listReservations(), listOrders(), getHealth()] as const);
    if (id !== refreshId.current) return;
    const errors: Record<string, string> = {};
    const fetched: Record<string, boolean> = {};
    const names = ['Products', 'Reservations', 'Orders', 'Health'];
    results.forEach((result, index) => {
      if (result.status === 'rejected') errors[names[index]] = errorMessage(result.reason);
      else fetched[names[index]] = true;
    });
    const [productResult, reservationResult, orderResult, healthResult] = results;
    if (productResult.status === 'fulfilled') {
      setProducts(productResult.value);
      setSelectedProductId((current) => productResult.value.some((product) => product.id === current)
        ? current : productResult.value[0]?.id ?? '');
    }
    if (reservationResult.status === 'fulfilled') setReservations(reservationResult.value);
    if (orderResult.status === 'fulfilled') setOrders(orderResult.value);
    setHealth(healthResult.status === 'fulfilled' ? healthResult.value : { status: 'UNKNOWN' });
    setLoadErrors(errors);
    setLoaded((previous) => ({ ...previous, ...fetched }));
    setLoading(false);
  }, []);

  useEffect(() => {
    void refresh();
    return () => { refreshId.current++; };
  }, [refresh]);

  function dataNote(section: string) {
    if (loadErrors[section]) return loaded[section] ? 'Refresh failed. Showing previously loaded records.' : 'Data unavailable. Refresh to try again.';
    return loaded[section] ? undefined : 'Loading records…';
  }

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
      return true;
    } catch (caught) {
      setError(errorMessage(caught));
      return false;
    }
  }

  async function handleCreateProduct(payload: CreateProductRequest) {
    return runAction('Product created successfully.', async () => {
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
    const queued = await runAction('Failure event queued. Waiting for the reservation status…', async () => {
      await simulateOrderFailure(reservationId, reason);
    });
    if (!queued) return false;
    try {
      for (let attempt = 0; attempt < 10; attempt++) {
        const records = await listReservations();
        const reservation = records.find((record) => record.reservationId === reservationId);
        if (reservation && reservation.status !== 'ACTIVE') {
          setMessage(reservation.status === 'RELEASED'
            ? 'Failure processed. The reservation was released back to stock.'
            : `The reservation is ${reservation.status.toLowerCase()}; no failure release was applied.`);
          await refresh();
          return true;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1_000));
      }
      setMessage('Failure event is queued; processing is still pending. Refresh to check again.');
    } catch {
      setMessage('Failure event is queued, but its current status could not be checked. Refresh to check again.');
    }
    return true;
  }

  return (
    <main className="app-shell">
      <Header health={health} loading={loading} onRefresh={refresh} />

      {Object.entries(loadErrors).map(([section, detail]) => (
        <div key={section} className="notice notice-error" role="alert">
          <AlertCircle size={18} /><span>{section}: {detail}</span>
        </div>
      ))}
      {error ? (
        <div className="notice notice-error" role="alert">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      ) : null}

      {message ? (
        <div className="notice notice-success" role="status">
          <CheckCircle2 size={18} />
          <span>{message}</span>
        </div>
      ) : null}

      <section className="metrics-grid">
        <MetricCard icon={Package} label="Products" value={loaded.Products ? products.length : '—'} tone="gray" />
        <MetricCard icon={Clock3} label="Active Reservations" value={loaded.Reservations ? activeReservations : '—'} tone="yellow" />
        <MetricCard icon={Warehouse} label="Available Stock" value={loaded.Products ? totals.available : '—'} tone="green" />
        <MetricCard icon={Boxes} label="Reserved Stock" value={loaded.Products ? totals.reserved : '—'} tone="yellow" />
        <MetricCard icon={ShoppingCart} label="Sold Stock" value={loaded.Products ? totals.sold : '—'} tone="blue" />
      </section>

      <section className="content-grid">
        <ProductTable dataNote={dataNote('Products')} products={products} selectedProductId={selectedProductId} onSelect={setSelectedProductId} />
        <CreateProductForm onCreate={handleCreateProduct} />
        <ReserveStockForm
          products={products}
          selectedProductId={selectedProductId}
          onSelectProduct={setSelectedProductId}
          onReserve={handleReserve}
        />
        <ReservationTable
          dataNote={dataNote('Reservations')}
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
        <OrderTable dataNote={dataNote('Orders')} orders={orders} />
        <KafkaDemoPanel reservations={reservations} onSimulate={handleKafkaSimulation} />
      </section>
    </main>
  );
}

export default App;
