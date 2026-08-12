import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { apiRequest } from './apiClient.ts';
import { ApiError, NetworkError, ResponseShapeError } from './problem.ts';

const schema = z.object({ ok: z.boolean() });

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls the versioned API prefix', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ok: true }));

    await apiRequest('/config', schema);

    const [url] = vi.mocked(fetch).mock.calls[0]!;
    expect(url).toBe('/api/v1/config');
  });

  it('returns parsed data on success', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ok: true }));
    await expect(apiRequest('/config', schema)).resolves.toEqual({ ok: true });
  });

  it('maps RFC 9457 problem details to ApiError', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(
        {
          type: 'https://listenspeak.app/problems/rate-limited',
          title: 'Daily limit reached',
          status: 429,
          detail: 'You have used all 20 listening generations today.',
          code: 'DAILY_LIMIT_REACHED',
          retryable: false,
        },
        429,
      ),
    );

    const error = await apiRequest('/listening/exercises', schema).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(429);
    expect((error as ApiError).code).toBe('DAILY_LIMIT_REACHED');
    // The server explicitly said not to retry, which must win over the
    // "429 is usually retryable" default.
    expect((error as ApiError).isRetryable).toBe(false);
  });

  it('still produces an ApiError when the error body is not problem details', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('boom', { status: 502 }));

    const error = await apiRequest('/config', schema).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).isRetryable).toBe(true);
  });

  it('rejects a success response that does not match the schema', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ok: 'yes' }));
    await expect(apiRequest('/config', schema)).rejects.toBeInstanceOf(ResponseShapeError);
  });

  it('wraps transport failures as NetworkError', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(apiRequest('/config', schema)).rejects.toBeInstanceOf(NetworkError);
  });

  it('sends an idempotency key when one is supplied', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ok: true }));

    await apiRequest('/listening/exercises', schema, {
      method: 'POST',
      body: { part: 5 },
      idempotencyKey: 'abc-123',
    });

    const init = vi.mocked(fetch).mock.calls[0]![1]!;
    expect((init.headers as Headers).get('Idempotency-Key')).toBe('abc-123');
    expect(init.body).toBe(JSON.stringify({ part: 5 }));
  });
});
