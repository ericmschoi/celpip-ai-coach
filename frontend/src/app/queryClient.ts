import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../lib/problem.ts';

/**
 * Retries are deliberately conservative: several of these endpoints cost money
 * per call, so only clearly transient failures are retried, and never a
 * mutation.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (failureCount >= 2) return false;
          if (error instanceof ApiError) return error.isRetryable;
          return true; // NetworkError - worth one more attempt
        },
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
