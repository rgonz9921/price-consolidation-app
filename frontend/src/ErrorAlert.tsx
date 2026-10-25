import { ApiError } from './api/client';

const MESSAGES_BY_CODE: Record<string, string> = {
  SKU_INACTIVE: 'El SKU está inactivo. Actívalo en la tabla para poder consultar su precio.',
  PROVIDERS_UNAVAILABLE:
    'Ambos proveedores están caídos y no hay un precio reciente en caché. Reintenta en unos segundos.',
  VALIDATION_FAILED: 'Hay campos inválidos. Revisa los mensajes debajo de cada campo.',
};

function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No se pudo contactar al servidor.';
  }
  const { code, detail, status } = error.problem;
  return (code && MESSAGES_BY_CODE[code]) ?? detail ?? `Error ${status}`;
}

export default function ErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  return (
    <p className="alert" role="alert">
      {messageFor(error)}
    </p>
  );
}
