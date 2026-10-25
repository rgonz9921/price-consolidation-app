import type { UseMutationResult } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import type { PriceResponse } from '../api/types';
import ErrorAlert from '../ErrorAlert';

interface PricePanelProps {
  priceQuery: UseMutationResult<PriceResponse, Error, string>;
}

const money = (amount: number, currency: string) =>
  new Intl.NumberFormat('es', { style: 'currency', currency }).format(amount);

export default function PricePanel({ priceQuery }: PricePanelProps) {
  const [sku, setSku] = useState('');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    priceQuery.mutate(sku.trim().toUpperCase());
  };

  const price = priceQuery.data;

  return (
    <section className="price-panel">
      <h2>Consultar precio</h2>
      <form onSubmit={submit}>
        <input
          value={sku}
          onChange={(e) => setSku(e.target.value)}
          placeholder="SKU, por ejemplo ABC-123"
          required
        />
        <button type="submit" disabled={priceQuery.isPending}>
          {priceQuery.isPending ? 'Consultando…' : 'Consultar'}
        </button>
      </form>

      <ErrorAlert error={priceQuery.error} />

      {price && !priceQuery.isError && (
        <dl>
          <dt>Producto</dt>
          <dd>
            {price.sku} · {price.productName}
          </dd>
          <dt>Precio base del proveedor</dt>
          <dd>{money(price.providerPrice, price.currency)}</dd>
          <dt>Margen aplicado</dt>
          <dd>{price.marginPercentage} %</dd>
          <dt>Precio final</dt>
          <dd className="final-price">{money(price.finalPrice, price.currency)}</dd>
          <dt>Fuente</dt>
          <dd>
            <span className={`badge badge-${price.source}`}>{price.source}</span>
            {price.source !== price.providerSource && ` (origen ${price.providerSource})`}
          </dd>
          <dt>Frescura</dt>
          <dd>
            <span className={price.stale ? 'badge badge-stale' : 'badge badge-fresh'}>
              {price.stale ? 'Dato obsoleto' : 'Dato fresco'}
            </span>
          </dd>
          <dt>Latencia</dt>
          <dd>{price.responseTimeMs} ms</dd>
        </dl>
      )}
    </section>
  );
}
