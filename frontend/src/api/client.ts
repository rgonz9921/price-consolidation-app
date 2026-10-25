import type { PriceResponse, Problem, ProductRule, RuleInput } from './types';

export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? `Error ${problem.status}`);
    this.problem = problem;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json' },
  });
  if (!response.ok) {
    throw new ApiError(await readProblem(response));
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    return { ...(await response.json()), status: response.status };
  } catch {
    return { status: response.status };
  }
}

const rulePath = (sku: string) => `/product-rules/${encodeURIComponent(sku)}`;

export const api = {
  listRules: () => request<ProductRule[]>('/product-rules'),

  createRule: (rule: RuleInput) =>
    request<ProductRule>('/product-rules', { method: 'POST', body: JSON.stringify(rule) }),

  updateRule: ({ sku, ...rule }: RuleInput) =>
    request<ProductRule>(rulePath(sku), { method: 'PUT', body: JSON.stringify(rule) }),

  deleteRule: (sku: string) => request<void>(rulePath(sku), { method: 'DELETE' }),

  getPrice: (sku: string) => request<PriceResponse>(`/prices?sku=${encodeURIComponent(sku)}`),
};
