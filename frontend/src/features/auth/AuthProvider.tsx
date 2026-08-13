import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { setAuthTokenProvider } from '../../lib/apiClient.ts';
import { AuthContext, type AuthMode, type AuthState } from './authContext.ts';
import {
  beginLogin,
  clearTokens,
  isExpired,
  loadTokens,
  logout as logoutRedirect,
  readConfig,
  refresh,
  saveTokens,
  type CognitoConfig,
  type StoredTokens,
} from './cognito.ts';

/**
 * Owns the session and installs the token provider the API client uses.
 *
 * <p>In `LOCAL_STUB` mode there is nothing to do: the backend accepts a dev
 * header and no token is ever sent. In `COGNITO` mode the access token is
 * attached to every request and refreshed just before it expires.
 */
export function AuthProvider({
  mode,
  children,
}: {
  readonly mode: AuthMode;
  readonly children: ReactNode;
}) {
  const config = useMemo<CognitoConfig | null>(
    () => (mode === 'COGNITO' ? readConfig() : null),
    [mode],
  );

  const [tokens, setTokensState] = useState<StoredTokens | null>(() =>
    mode === 'COGNITO' ? loadTokens() : null,
  );
  const [ready, setReady] = useState(mode !== 'COGNITO');
  const [error, setError] = useState<string | null>(null);

  const tokensRef = useRef(tokens);
  tokensRef.current = tokens;

  const applyTokens = useCallback((next: StoredTokens) => {
    saveTokens(next);
    setTokensState(next);
    setError(null);
  }, []);

  // A single provider closure, installed once, that always sees current tokens
  // and refreshes them on demand rather than on a timer.
  useEffect(() => {
    if (mode !== 'COGNITO' || !config) {
      setAuthTokenProvider(() => null);
      return;
    }

    setAuthTokenProvider(async () => {
      const current = tokensRef.current;
      if (!current) return null;
      if (!isExpired(current)) return current.accessToken;

      if (!current.refreshToken) {
        clearTokens();
        setTokensState(null);
        return null;
      }

      try {
        const refreshed = await refresh(config, current.refreshToken);
        applyTokens(refreshed);
        return refreshed.accessToken;
      } catch {
        clearTokens();
        setTokensState(null);
        setError('Your session expired. Please sign in again.');
        return null;
      }
    });
  }, [applyTokens, config, mode]);

  // On load in Cognito mode, drop a session that is already unusable.
  useEffect(() => {
    if (mode !== 'COGNITO') return;

    const existing = loadTokens();
    if (existing && isExpired(existing) && !existing.refreshToken) {
      clearTokens();
      setTokensState(null);
    }
    setReady(true);
  }, [mode]);

  const value = useMemo<AuthState>(
    () => ({
      mode,
      ready,
      signedIn: mode !== 'COGNITO' || tokens !== null,
      error:
        config === null && mode === 'COGNITO' ? 'Sign-in is not configured for this build.' : error,
      signIn: () => {
        if (config) void beginLogin(config);
      },
      signOut: () => {
        setTokensState(null);
        if (config) logoutRedirect(config);
        else clearTokens();
      },
      setTokens: applyTokens,
    }),
    [applyTokens, config, error, mode, ready, tokens],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
