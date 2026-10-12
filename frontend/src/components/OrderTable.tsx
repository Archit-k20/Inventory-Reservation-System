import { ReceiptText } from 'lucide-react';
import type { Order } from '../api/types';

type OrderTableProps = {
  dataNote?: string;
  orders: Order[];
};

export function OrderTable({ dataNote, orders }: OrderTableProps) {
  return (
    <section className="panel table-panel">
      <div className="panel-heading">
        <div>
          <h2>Orders</h2>
          <p>Confirmed reservations create customer orders.</p>
        </div>
        <ReceiptText size={20} />
      </div>
      {dataNote ? <p className="data-note" role="status">{dataNote}</p> : null}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>User</th>
              <th>Quantity</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.orderId}>
                <td className="mono">{order.orderId.slice(0, 8)}</td>
                <td>{order.userId}</td>
                <td>{order.quantity}</td>
                <td>{new Date(order.createdAt).toLocaleString()}</td>
              </tr>
            ))}
            {orders.length === 0 ? (
              <tr>
                <td colSpan={4} className="empty-cell">
                  {dataNote || 'No orders yet.'}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
