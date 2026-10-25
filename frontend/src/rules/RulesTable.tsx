import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { ProductRule } from '../api/types';
import ErrorAlert from '../ErrorAlert';

interface RulesTableProps {
  onEdit: (rule: ProductRule) => void;
  onQuote: (sku: string) => void;
}

export default function RulesTable({ onEdit, onQuote }: RulesTableProps) {
  const queryClient = useQueryClient();
  const rules = useQuery({ queryKey: ['rules'], queryFn: api.listRules });
  const remove = useMutation({
    mutationFn: api.deleteRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rules'] }),
  });

  const confirmRemove = (sku: string) => {
    if (window.confirm(`¿Eliminar la regla ${sku}?`)) {
      remove.mutate(sku);
    }
  };

  if (rules.isPending) {
    return <p>Cargando reglas…</p>;
  }
  if (rules.isError) {
    return <ErrorAlert error={rules.error} />;
  }

  return (
    <>
      <ErrorAlert error={remove.error} />
      {rules.data.length === 0 ? (
        <p>No hay reglas registradas.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Nombre</th>
              <th>Margen %</th>
              <th>Proveedor preferido</th>
              <th>Estado</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rules.data.map((rule) => (
              <tr key={rule.sku}>
                <td>{rule.sku}</td>
                <td>{rule.name}</td>
                <td>{rule.marginPercentage}</td>
                <td>{rule.preferredProvider}</td>
                <td>{rule.active ? 'Activa' : 'Inactiva'}</td>
                <td className="actions">
                  <button onClick={() => onQuote(rule.sku)}>Consultar precio</button>
                  <button onClick={() => onEdit(rule)}>Editar</button>
                  <button onClick={() => confirmRemove(rule.sku)}>Eliminar</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
