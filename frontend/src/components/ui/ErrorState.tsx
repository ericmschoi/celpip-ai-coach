import { Button } from './Button.tsx';
import { Callout } from './Callout.tsx';
import { describeError } from '../../lib/describeError.ts';

export function ErrorState({
  error,
  onRetry,
  retrying,
}: {
  readonly error: unknown;
  readonly onRetry?: () => void;
  readonly retrying?: boolean;
}) {
  const { title, message, canRetry } = describeError(error);

  return (
    <Callout tone="error" title={title}>
      <p>{message}</p>
      {canRetry && onRetry && (
        <Button variant="secondary" size="sm" className="mt-3" loading={retrying} onClick={onRetry}>
          Try again
        </Button>
      )}
    </Callout>
  );
}
