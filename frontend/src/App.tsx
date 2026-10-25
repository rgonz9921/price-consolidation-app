import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from './api/client';
import type { ProductRule } from './api/types';
import PricePanel from './prices/PricePanel';
import RuleForm from './rules/RuleForm';
import RulesTable from './rules/RulesTable';

type FormState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; rule: ProductRule };

export default function App() {
  const [form, setForm] = useState<FormState>({ mode: 'closed' });
  const priceQuery = useMutation({ mutationFn: api.getPrice });
  const closeForm = () => setForm({ mode: 'closed' });

  return (
    <main>
      <h1>Consulta consolidada de precios</h1>

      <PricePanel priceQuery={priceQuery} />

      <section>
        <div className="section-header">
          <h2>Reglas de productos</h2>
          {form.mode === 'closed' && (
            <button onClick={() => setForm({ mode: 'create' })}>Nueva regla</button>
          )}
        </div>
        {form.mode !== 'closed' && (
          <RuleForm
            key={form.mode === 'edit' ? form.rule.sku : 'new'}
            editing={form.mode === 'edit' ? form.rule : null}
            onDone={closeForm}
          />
        )}
        <RulesTable
          onEdit={(rule) => setForm({ mode: 'edit', rule })}
          onQuote={(sku) => priceQuery.mutate(sku)}
        />
      </section>
    </main>
  );
}
