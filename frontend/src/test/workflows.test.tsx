import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { CreateProductForm } from '../components/CreateProductForm';
import { KafkaDemoPanel } from '../components/KafkaDemoPanel';
import { listProducts } from '../api/products';
import { listReservations } from '../api/reservations';
import { listOrders } from '../api/orders';
import type { Product, Reservation } from '../api/types';

const product = (id: number): Product => ({ id: String(id), sku: `SKU-${id}`, name: `Widget ${id}`,
  totalStock: 1, availableStock: 1, reservedStock: 0, soldStock: 0, version: 0 });
const page = (content: unknown[], number = 0, totalPages = 1, totalElements = content.length) =>
  ({ content, number, totalPages, totalElements, size: 100 });
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

describe('complete dashboard data', () => {
  it('loads products, reservations, and orders past the first page', async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = new URL(String(input));
      const index = Number(url.searchParams.get('page'));
      const key = url.pathname === '/products' ? 'id' : url.pathname === '/orders' ? 'orderId' : 'reservationId';
      const content = index === 0 ? Array.from({ length: 100 }, (_, n) => ({ ...product(n), [key]: String(n) })) : [{ ...product(100), [key]: '100' }];
      return json(page(content, index, 2, 101));
    });
    expect(await listProducts()).toHaveLength(101);
    expect(await listReservations()).toHaveLength(101);
    expect(await listOrders()).toHaveLength(101);
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it('rejects partial collections when a later page fails', async () => {
    fetchMock.mockResolvedValueOnce(json(page([product(1)], 0, 2, 101)))
      .mockResolvedValueOnce(json({ detail: 'Database unavailable' }, 503));
    await expect(listProducts()).rejects.toThrow('Database unavailable');
  });

  it('shows complete totals for 101 products', async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = new URL(String(input));
      if (url.pathname === '/actuator/health') return json({ status: 'UP' });
      if (url.pathname !== '/products') return json(page([]));
      const index = Number(url.searchParams.get('page'));
      return json(page(index ? [product(101)] : Array.from({ length: 100 }, (_, n) => product(n)), index, 2, 101));
    });
    render(<App />);
    await screen.findByText('Widget 101', { selector: 'strong' });
    expect(screen.getAllByText('101', { selector: '.metric strong' })).toHaveLength(2);
  });
});

describe('honest failures', () => {
  it('loads stock despite a 503 health response and replaces a previous UP badge', async () => {
    let down = false;
    fetchMock.mockImplementation(async (input) => {
      const url = new URL(String(input));
      if (url.pathname === '/actuator/health') return json({ status: down ? 'DOWN' : 'UP' }, down ? 503 : 200);
      return json(page(url.pathname === '/products' ? [product(1)] : []));
    });
    render(<App />);
    await screen.findByText('UP');
    down = true;
    await userEvent.click(screen.getByRole('button', { name: 'Refresh dashboard' }));
    await screen.findByText('DOWN');
    expect(screen.queryByText('UP')).not.toBeInTheDocument();
    expect(screen.getByText('Widget 1', { selector: 'strong' })).toBeInTheDocument();
  });

  it('reports UNKNOWN and unavailable data instead of a healthy empty dashboard', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    render(<App />);
    await screen.findByText('UNKNOWN');
    expect(screen.queryByText('No products yet.')).not.toBeInTheDocument();
    expect(screen.getAllByText('—')).toHaveLength(5);
  });

  it('retains input when product creation fails and clears it only after success', async () => {
    const create = vi.fn().mockResolvedValue(false);
    render(<CreateProductForm onCreate={create} />);
    await userEvent.type(screen.getByLabelText('SKU'), 'EXISTING-SKU');
    await userEvent.type(screen.getByLabelText('Name'), 'My keyboard');
    await userEvent.click(screen.getByRole('button', { name: 'Create Product' }));
    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(screen.getByLabelText('SKU')).toHaveValue('EXISTING-SKU');
    expect(screen.getByLabelText('Name')).toHaveValue('My keyboard');
    create.mockResolvedValue(true);
    await userEvent.click(screen.getByRole('button', { name: 'Create Product' }));
    await waitFor(() => expect(screen.getByLabelText('SKU')).toHaveValue(''));
  });

  it('retains the selected reservation after event submission fails', async () => {
    const reservation: Reservation = { reservationId: 'reservation-1', productId: '1', userId: 'test-user', quantity: 1, status: 'ACTIVE', expiresAt: '2030-01-01T00:00:00Z' };
    render(<KafkaDemoPanel reservations={[reservation]} onSimulate={vi.fn().mockResolvedValue(false)} />);
    await userEvent.selectOptions(screen.getByLabelText('Active reservation'), 'reservation-1');
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Failure' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Simulate Failure' })).toBeEnabled());
    expect(screen.getByLabelText('Active reservation')).toHaveValue('reservation-1');
  });
});
