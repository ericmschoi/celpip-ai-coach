import type { ReactNode } from 'react';
import { cn } from '../../lib/cn.ts';

type Tone = 'info' | 'success' | 'warning' | 'error';

const TONES: Record<Tone, string> = {
  info: 'bg-accent-soft border-accent/25 text-ink',
  success: 'bg-positive-soft border-positive/25 text-ink',
  warning: 'bg-caution-soft border-caution/30 text-ink',
  error: 'bg-critical-soft border-critical/30 text-ink',
};

export function Callout({
  tone = 'info',
  title,
  children,
  className,
  role,
}: {
  readonly tone?: Tone;
  readonly title?: string;
  readonly children?: ReactNode;
  readonly className?: string;
  readonly role?: 'status' | 'alert';
}) {
  return (
    <div
      role={role ?? (tone === 'error' ? 'alert' : undefined)}
      className={cn('rounded-lg border px-4 py-3 text-sm', TONES[tone], className)}
    >
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={cn(title && 'mt-1', 'text-ink-muted')}>{children}</div>}
    </div>
  );
}
