import { vi } from 'vitest';

export interface MockRoute {
  /** Matched against `${method} ${path}`, e.g. "POST /api/v1/listening/exercises". */
  readonly match: RegExp;
  readonly status?: number;
  readonly body: unknown;
}

export const SEED_CONFIG = {
  contentMode: 'SEED',
  authMode: 'LOCAL_STUB',
  listeningParts: [1, 2, 3, 4, 5, 6],
  seedListeningParts: [5],
  speakingTasks: [
    {
      taskNumber: 1,
      title: 'Giving Advice',
      focus: 'Advise one person about a specific decision, with reasons.',
      preparationSeconds: 30,
      answerSeconds: 90,
    },
  ],
  difficulties: ['DEVELOPING', 'COMPETENT', 'ADVANCED'],
  dailyLimits: { listening: 20, speaking: 30 },
};

/**
 * Installs a fetch stub that answers from a route table. Unmatched requests
 * throw, so a test can never pass by silently hitting nothing.
 */
export function mockFetch(routes: MockRoute[]) {
  const calls: Array<{ url: string; method: string; body: unknown }> = [];

  const impl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const key = `${method} ${url}`;
    calls.push({
      url,
      method,
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body,
    });

    const route = routes.find((candidate) => candidate.match.test(key));
    if (!route) {
      throw new Error(`No mock route for ${key}`);
    }
    return new Response(JSON.stringify(route.body), {
      status: route.status ?? 200,
      headers: { 'Content-Type': 'application/json' },
    });
  });

  vi.stubGlobal('fetch', impl);
  return { calls, impl };
}

export const configRoute: MockRoute = { match: /GET .*\/api\/v1\/config$/, body: SEED_CONFIG };

/** A backend with a real API key, where every part can be generated. */
export const LIVE_CONFIG = {
  ...SEED_CONFIG,
  contentMode: 'LIVE',
  seedListeningParts: [5],
};

export const liveConfigRoute: MockRoute = {
  match: /GET .*\/api\/v1\/config$/,
  body: LIVE_CONFIG,
};
