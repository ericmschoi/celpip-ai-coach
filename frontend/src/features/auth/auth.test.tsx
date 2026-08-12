import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { apiRequest } from '../../lib/apiClient.ts';
import { createTestQueryClient } from '../../test/renderWithProviders.tsx';
import { AuthProvider } from './AuthProvider.tsx';
import { RequireAuth } from './RequireAuth.tsx';
import { saveTokens } from './cognito.ts';

function renderGated(mode: 'LOCAL_STUB' | 'COGNITO') {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <AuthProvider mode={mode}>
        <RequireAuth>
          <p>practice content</p>
        </RequireAuth>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('authentication', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubEnv('VITE_COGNITO_DOMAIN', 'https://example.auth.ca-central-1.amazoncognito.com');
    vi.stubEnv('VITE_COGNITO_CLIENT_ID', 'test-client');
    vi.stubEnv('VITE_COGNITO_REDIRECT_URI', 'http://localhost:5173/auth/callback');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('does not gate anything in local mode', async () => {
    renderGated('LOCAL_STUB');

    expect(await screen.findByText('practice content')).toBeInTheDocument();
  });

  it('asks an unauthenticated visitor to sign in', async () => {
    renderGated('COGNITO');

    expect(
      await screen.findByRole('heading', { name: /sign in to practise/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText('practice content')).not.toBeInTheDocument();
  });

  it('says plainly that there is no public sign-up', async () => {
    renderGated('COGNITO');

    expect(await screen.findByText(/no public sign-up/i)).toBeInTheDocument();
  });

  it('shows the app once a valid session exists', async () => {
    saveTokens({
      accessToken: 'access',
      idToken: 'id',
      refreshToken: 'refresh',
      expiresAt: Date.now() + 3_600_000,
    });

    renderGated('COGNITO');

    expect(await screen.findByText('practice content')).toBeInTheDocument();
  });

  it('sends the browser to the hosted UI with PKCE parameters', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign, origin: 'http://localhost:5173' });

    const user = userEvent.setup();
    renderGated('COGNITO');
    await user.click(await screen.findByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(assign).toHaveBeenCalled());

    const target = new URL(assign.mock.calls[0]![0] as string);
    expect(target.pathname).toBe('/oauth2/authorize');
    expect(target.searchParams.get('response_type')).toBe('code');
    expect(target.searchParams.get('code_challenge_method')).toBe('S256');
    expect(target.searchParams.get('code_challenge')).toBeTruthy();
    // A public client must never carry a secret.
    expect(target.searchParams.get('client_secret')).toBeNull();
    expect(sessionStorage.getItem('listenspeak.pkce.verifier')).toBeTruthy();
  });

  it('attaches the access token to API requests', async () => {
    saveTokens({
      accessToken: 'the-access-token',
      idToken: 'id',
      refreshToken: 'refresh',
      expiresAt: Date.now() + 3_600_000,
    });

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderGated('COGNITO');
    await screen.findByText('practice content');

    await apiRequest('/config', z.object({ ok: z.boolean() }));

    const headers = fetchMock.mock.calls[0]![1]!.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer the-access-token');
  });

  it('refreshes an expired token instead of failing the request', async () => {
    saveTokens({
      accessToken: 'stale',
      idToken: 'id',
      refreshToken: 'refresh-token',
      expiresAt: Date.now() - 1000,
    });

    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      if (String(input).includes('/oauth2/token')) {
        return new Response(
          JSON.stringify({ access_token: 'fresh', id_token: 'id', expires_in: 3600 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    renderGated('COGNITO');
    await screen.findByText('practice content');

    await apiRequest('/config', z.object({ ok: z.boolean() }));

    const apiCall = fetchMock.mock.calls.find((call) => !String(call[0]).includes('/oauth2/token'));
    expect((apiCall![1]!.headers as Headers).get('Authorization')).toBe('Bearer fresh');
  });

  it('sends the user back to sign-in when the refresh token is rejected', async () => {
    saveTokens({
      accessToken: 'stale',
      idToken: 'id',
      refreshToken: 'revoked',
      expiresAt: Date.now() - 1000,
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
        if (String(input).includes('/oauth2/token')) {
          return new Response('{}', { status: 400 });
        }
        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    renderGated('COGNITO');
    await screen.findByText('practice content');

    await apiRequest('/config', z.object({ ok: z.boolean() }));

    expect(
      await screen.findByRole('heading', { name: /sign in to practise/i }),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem('listenspeak.tokens')).toBeNull();
  });
});
