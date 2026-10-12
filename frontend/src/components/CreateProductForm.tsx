import { Plus } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import type { CreateProductRequest } from '../api/types';

type CreateProductFormProps = {
  onCreate: (payload: CreateProductRequest) => Promise<boolean>;
};

export function CreateProductForm({ onCreate }: CreateProductFormProps) {
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [totalStock, setTotalStock] = useState(10);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const created = await onCreate({ sku: sku.trim(), name: name.trim(), totalStock });
      if (!created) return;
      setSku('');
      setName('');
      setTotalStock(10);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Create Product</h2>
          <p>Add stock that can be reserved by users.</p>
        </div>
        <Plus size={20} />
      </div>
      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          SKU
          <input value={sku} onChange={(event) => setSku(event.target.value)} placeholder="SKU-1001" maxLength={128} required />
        </label>
        <label>
          Name
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Wireless Keyboard" maxLength={255} required />
        </label>
        <label>
          Total stock
          <input
            min={0}
            type="number"
            value={totalStock}
            onChange={(event) => setTotalStock(Number(event.target.value))}
            required
          />
        </label>
        <button className="primary-button" type="submit" disabled={submitting}>
          <Plus size={16} />
          {submitting ? 'Creating' : 'Create Product'}
        </button>
      </form>
    </section>
  );
}
