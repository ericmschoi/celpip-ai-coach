import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../components/ui/Button.tsx';
import { Callout } from '../components/ui/Callout.tsx';
import { Card } from '../components/ui/Card.tsx';
import { completeLogin, readConfig } from '../features/auth/cognito.ts';
import { useAuth } from '../features/auth/useAuth.ts';

/** Where the Cognito hosted UI returns to after a successful sign-in. */
export function AuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const auth = useAuth();
  const [error, setError] = useState<string | null>(null);
  const exchanged = useRef(false);

  useEffect(() => {
    // React runs effects twice in development; the code is single-use.
    if (exchanged.current) return;
    exchanged.current = true;

    const config = readConfig();
    const code = params.get('code');
    const oauthError = params.get('error_description') ?? params.get('error');

    if (oauthError) {
      setError(oauthError);
      return;
    }
    if (!config || !code) {
      setError('This sign-in link is incomplete.');
      return;
    }

    completeLogin(config, code, params.get('state'))
      .then((tokens) => {
        auth.setTokens(tokens);
        navigate('/', { replace: true });
      })
      .catch((cause: unknown) => {
        setError(cause instanceof Error ? cause.message : 'Sign-in failed.');
      });
  }, [auth, navigate, params]);

  return (
    <Card className="mx-auto max-w-md">
      {error ? (
        <>
          <Callout tone="error" title="Sign-in did not complete">
            {error}
          </Callout>
          <Button className="mt-4" onClick={auth.signIn}>
            Try again
          </Button>
        </>
      ) : (
        <p role="status" className="text-sm text-ink-muted">
          Completing sign-in…
        </p>
      )}
    </Card>
  );
}
