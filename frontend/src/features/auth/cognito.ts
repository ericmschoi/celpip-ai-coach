/**
 * Authorization Code + PKCE against the Cognito hosted UI.
 *
 * <p>No client secret exists, because a browser cannot keep one. PKCE is what
 * stops an intercepted authorization code from being redeemed by anyone else.
 *
 * <p>Tokens live in `sessionStorage`: they are cleared when the tab closes, and
 * unlike a cookie they are never attached to a cross-site request, which is why
 * the API needs no CSRF token.
 */
export interface CognitoConfig {
  readonly domain: string;
  readonly clientId: string;
  readonly redirectUri: string;
}

export interface StoredTokens {
  readonly accessToken: string;
  readonly idToken: string;
  readonly refreshToken?: string;
  /** Epoch milliseconds. */
  readonly expiresAt: number;
}

const TOKENS_KEY = 'listenspeak.tokens';
const VERIFIER_KEY = 'listenspeak.pkce.verifier';
const STATE_KEY = 'listenspeak.pkce.state';

/** Refresh this long before expiry, so a request never races the clock. */
const REFRESH_MARGIN_MS = 60_000;

export function readConfig(): CognitoConfig | null {
  const domain = import.meta.env.VITE_COGNITO_DOMAIN;
  const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID;
  const redirectUri =
    import.meta.env.VITE_COGNITO_REDIRECT_URI ??
    (typeof window === 'undefined' ? undefined : `${window.location.origin}/auth/callback`);

  if (!domain || !clientId || !redirectUri) {
    return null;
  }
  return { domain: domain.replace(/\/$/, ''), clientId, redirectUri };
}

function randomString(bytes = 32): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return base64Url(buffer);
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function challengeFor(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

export function loadTokens(): StoredTokens | null {
  const raw = sessionStorage.getItem(TOKENS_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredTokens;
  } catch {
    sessionStorage.removeItem(TOKENS_KEY);
    return null;
  }
}

export function saveTokens(tokens: StoredTokens): void {
  sessionStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
}

export function clearTokens(): void {
  sessionStorage.removeItem(TOKENS_KEY);
}

/** Sends the browser to the hosted UI. Does not return. */
export async function beginLogin(config: CognitoConfig): Promise<void> {
  const verifier = randomString();
  const state = randomString(16);

  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);

  const params = new URLSearchParams({
    client_id: config.clientId,
    response_type: 'code',
    scope: 'openid email profile',
    redirect_uri: config.redirectUri,
    state,
    code_challenge: await challengeFor(verifier),
    code_challenge_method: 'S256',
  });

  window.location.assign(`${config.domain}/oauth2/authorize?${params.toString()}`);
}

/** Exchanges the authorization code for tokens. Throws if state does not match. */
export async function completeLogin(
  config: CognitoConfig,
  code: string,
  state: string | null,
): Promise<StoredTokens> {
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(VERIFIER_KEY);

  if (!expectedState || state !== expectedState) {
    throw new Error('Sign-in could not be verified. Start again.');
  }
  if (!verifier) {
    throw new Error('Sign-in session expired. Start again.');
  }

  const response = await fetch(`${config.domain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: config.redirectUri,
      code_verifier: verifier,
    }),
  });

  if (!response.ok) {
    throw new Error('Sign-in failed. Please try again.');
  }
  return toStoredTokens(await response.json());
}

export async function refresh(config: CognitoConfig, refreshToken: string): Promise<StoredTokens> {
  const response = await fetch(`${config.domain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: config.clientId,
      refresh_token: refreshToken,
    }),
  });

  if (!response.ok) {
    throw new Error('Session expired.');
  }

  const tokens = toStoredTokens(await response.json());
  // Cognito does not return a new refresh token on refresh.
  return { ...tokens, refreshToken };
}

export function logout(config: CognitoConfig): void {
  clearTokens();
  const params = new URLSearchParams({
    client_id: config.clientId,
    logout_uri: new URL(config.redirectUri).origin,
  });
  window.location.assign(`${config.domain}/logout?${params.toString()}`);
}

export function isExpired(tokens: StoredTokens): boolean {
  return Date.now() >= tokens.expiresAt - REFRESH_MARGIN_MS;
}

function toStoredTokens(payload: unknown): StoredTokens {
  const body = payload as {
    access_token?: string;
    id_token?: string;
    refresh_token?: string;
    expires_in?: number;
  };

  if (!body.access_token || !body.id_token) {
    throw new Error('The sign-in service returned an unexpected response.');
  }

  return {
    accessToken: body.access_token,
    idToken: body.id_token,
    refreshToken: body.refresh_token,
    expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
  };
}
