import type { z } from 'zod';
import { ApiError, NetworkError, ResponseShapeError, problemDetailsSchema } from './problem.ts';

/**
 * Empty base URL means "same origin", which is what local dev uses through the
 * Vite proxy. In AWS the deployed bundle points at the ALB origin.
 */
const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export const API_PREFIX = '/api/v1';

type TokenProvider = () => Promise<string | null> | string | null;

let tokenProvider: TokenProvider = () => null;

/** Installed once by the auth layer; kept out of every call site. */
export function setAuthTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

export interface RequestOptions {
  readonly method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  readonly body?: unknown;
  readonly formData?: FormData;
  readonly signal?: AbortSignal;
  /** Sent as `Idempotency-Key` so a retried POST cannot double-charge. */
  readonly idempotencyKey?: string;
}

async function buildHeaders(options: RequestOptions): Promise<Headers> {
  const headers = new Headers({ Accept: 'application/json' });

  // FormData must set its own multipart boundary.
  if (options.body !== undefined && !options.formData) {
    headers.set('Content-Type', 'application/json');
  }
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }

  const token = await tokenProvider();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  return headers;
}

async function toProblem(response: Response, path: string): Promise<ApiError> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = undefined;
  }

  const parsed = problemDetailsSchema.safeParse(payload);
  if (parsed.success) {
    return new ApiError(parsed.data);
  }
  return new ApiError({
    type: 'about:blank',
    title: response.statusText || 'Request failed',
    status: response.status,
    instance: path,
  });
}

/**
 * Single entry point for every call to the backend. Responses are validated
 * with Zod at this boundary so no unvalidated server data reaches components.
 */
export async function apiRequest<T>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestOptions = {},
): Promise<T> {
  const url = `${BASE_URL}${API_PREFIX}${path}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers: await buildHeaders(options),
      body:
        options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new NetworkError(cause);
  }

  if (!response.ok) {
    throw await toProblem(response, path);
  }

  const raw: unknown = response.status === 204 ? null : await response.json();
  const parsed = schema.safeParse(raw);
  if (!parsed.success) {
    throw new ResponseShapeError(path, parsed.error);
  }
  return parsed.data;
}
