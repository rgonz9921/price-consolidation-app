export type PreferredProvider = 'AUTO' | 'PROVIDER_A' | 'PROVIDER_B';

export type Provider = 'PROVIDER_A' | 'PROVIDER_B';

export type PriceSource = Provider | 'CACHE' | 'FALLBACK_CACHE';

export interface ProductRule {
  id: number;
  sku: string;
  name: string;
  marginPercentage: number;
  preferredProvider: PreferredProvider;
  active: boolean;
  updatedAt: string;
}

export interface RuleInput {
  sku: string;
  name: string;
  marginPercentage: number;
  preferredProvider: PreferredProvider;
  active: boolean;
}

export interface PriceResponse {
  sku: string;
  productName: string;
  providerPrice: number;
  marginPercentage: number;
  finalPrice: number;
  currency: string;
  source: PriceSource;
  providerSource: Provider;
  stale: boolean;
  responseTimeMs: number;
}

export interface InvalidField {
  field: string;
  message: string;
}

export interface Problem {
  status: number;
  code?: string;
  detail?: string;
  errors?: InvalidField[];
}
