import { createContext } from 'react';
import type { StoredTokens } from './cognito.ts';

export type AuthMode = 'LOCAL_STUB' | 'COGNITO';

export interface AuthState {
  readonly mode: AuthMode;
  readonly ready: boolean;
  readonly signedIn: boolean;
  readonly error: string | null;
  readonly signIn: () => void;
  readonly signOut: () => void;
  readonly setTokens: (tokens: StoredTokens) => void;
}

export const AuthContext = createContext<AuthState | null>(null);
