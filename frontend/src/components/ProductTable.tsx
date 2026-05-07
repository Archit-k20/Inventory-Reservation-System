import { Copy, PackageCheck } from 'lucide-react';
import type { Product } from '../api/types';

type ProductTableProps = {
  products: Product[];
  selectedProductId: string;
  onSelect: (productId: string) => void;
};

export function ProductTable({ products, selectedProductId, onSelect }: ProductTableProps) {
  return (
    <section className="panel table-panel">
      <div className="panel-heading">
        <div>
          <h2>Products</h2>
          <p>Current stock split across available, reserved, and sold units.</p>
        </div>
        <PackageCheck size={20} />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th>SKU</th>
              <th>Total</th>
              <th>Available</th>
              <th>Reserved</th>
              <th>Sold</th>
              <th>Select</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>
                  <strong>{product.name}</strong>
                  <button
                    className="copy-button"
                    type="button"
                    title="Copy product id"
                    onClick={() => navigator.clipboard.writeText(product.id)}
                  >
                    <Copy size={14} />
                  </button>
                </td>
                <td>{product.sku}</td>
                <td>{product.totalStock}</td>
                <td className="stock-available">{product.availableStock}</td>
                <td className="stock-reserved">{product.reservedStock}</td>
                <td className="stock-sold">{product.soldStock}</td>
                <td>
                  <button
                    className={selectedProductId === product.id ? 'small-button active' : 'small-button'}
                    type="button"
                    onClick={() => onSelect(product.id)}
                  >
                    Use
                  </button>
                </td>
              </tr>
            ))}
            {products.length === 0 ? (
              <tr>
                <td colSpan={7} className="empty-cell">
                  No products yet.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
