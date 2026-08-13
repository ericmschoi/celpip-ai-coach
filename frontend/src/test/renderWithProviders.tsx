import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthProvider.tsx';

/** Test-only client: no retries, no caching surprises between tests. */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0, gcTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function renderWithProviders(
  ui: ReactElement,
  options: { route?: string; queryClient?: QueryClient } = {},
): RenderResult & { queryClient: QueryClient } {
  const queryClient = options.queryClient ?? createTestQueryClient();
  const result = render(
    <QueryClientProvider client={queryClient}>
      {/* Tests run in LOCAL_STUB mode, matching a local backend. */}
      <AuthProvider mode="LOCAL_STUB">
        <MemoryRouter initialEntries={[options.route ?? '/']}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}
