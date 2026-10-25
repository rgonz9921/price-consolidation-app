import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError, api } from '../api/client';
import type { PreferredProvider, ProductRule, RuleInput } from '../api/types';
import ErrorAlert from '../ErrorAlert';

const EMPTY_RULE: RuleInput = {
  sku: '',
  name: '',
  marginPercentage: 0,
  preferredProvider: 'AUTO',
  active: true,
};

const toInput = ({ sku, name, marginPercentage, preferredProvider, active }: ProductRule) => ({
  sku,
  name,
  marginPercentage,
  preferredProvider,
  active,
});

interface RuleFormProps {
  editing: ProductRule | null;
  onDone: () => void;
}

export default function RuleForm({ editing, onDone }: RuleFormProps) {
  const queryClient = useQueryClient();
  const [rule, setRule] = useState<RuleInput>(editing ? toInput(editing) : EMPTY_RULE);
  const save = useMutation({
    mutationFn: editing ? api.updateRule : api.createRule,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['rules'] });
      onDone();
    },
  });

  const fieldError = (field: keyof RuleInput) =>
    save.error instanceof ApiError
      ? save.error.problem.errors?.find((error) => error.field === field)?.message
      : undefined;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate(rule);
  };

  return (
    <form className="rule-form" onSubmit={submit}>
      <h2>{editing ? `Editar ${editing.sku}` : 'Nueva regla'}</h2>

      <label>
        SKU
        <input
          value={rule.sku}
          onChange={(e) => setRule({ ...rule, sku: e.target.value.toUpperCase() })}
          disabled={Boolean(editing)}
          required
          minLength={3}
          maxLength={64}
          pattern="[A-Z0-9]+(-[A-Z0-9]+)*"
          title="Mayúsculas, dígitos y guiones entre bloques, por ejemplo ABC-123"
        />
        <small>{fieldError('sku')}</small>
      </label>

      <label>
        Nombre
        <input
          value={rule.name}
          onChange={(e) => setRule({ ...rule, name: e.target.value })}
          required
          maxLength={120}
        />
        <small>{fieldError('name')}</small>
      </label>

      <label>
        Margen %
        <input
          type="number"
          value={rule.marginPercentage}
          onChange={(e) => setRule({ ...rule, marginPercentage: Number(e.target.value) })}
          required
          min={0}
          max={100}
          step={0.01}
        />
        <small>{fieldError('marginPercentage')}</small>
      </label>

      <label>
        Proveedor preferido
        <select
          value={rule.preferredProvider}
          onChange={(e) =>
            setRule({ ...rule, preferredProvider: e.target.value as PreferredProvider })
          }
        >
          <option value="AUTO">AUTO</option>
          <option value="PROVIDER_A">PROVIDER_A</option>
          <option value="PROVIDER_B">PROVIDER_B</option>
        </select>
        <small>{fieldError('preferredProvider')}</small>
      </label>

      <label className="checkbox">
        <input
          type="checkbox"
          checked={rule.active}
          onChange={(e) => setRule({ ...rule, active: e.target.checked })}
        />
        Activa
      </label>

      <ErrorAlert error={save.error} />

      <div className="actions">
        <button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar'}
        </button>
        <button type="button" onClick={onDone}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
