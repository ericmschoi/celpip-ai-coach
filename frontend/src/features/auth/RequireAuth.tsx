import type { ReactNode } from 'react';
import { Button } from '../../components/ui/Button.tsx';
import { Callout } from '../../components/ui/Callout.tsx';
import { Card } from '../../components/ui/Card.tsx';
import { useAuth } from './useAuth.ts';

/**
 * Gate in front of every practice route.
 *
 * <p>This is convenience, not security: the API rejects an unauthenticated call
 * regardless of what the browser renders.
 */
export function RequireAuth({ children }: { readonly children: ReactNode }) {
  const auth = useAuth();

  if (!auth.ready) {
    return (
      <p role="status" className="text-sm text-ink-muted">
        Checking your session…
      </p>
    );
  }

  if (auth.signedIn) {
    return <>{children}</>;
  }

  return (
    <Card className="mx-auto max-w-md text-center">
      <h1 className="text-xl">Sign in to practise</h1>
      <p className="mt-3 text-sm text-ink-muted">
        This is a private app. Accounts are created by the owner; there is no public sign-up.
      </p>

      {auth.error && (
        <Callout tone="error" className="mt-4 text-left">
          {auth.error}
        </Callout>
      )}

      <Button size="lg" className="mt-6" onClick={auth.signIn}>
        Sign in
      </Button>
    </Card>
  );
}
