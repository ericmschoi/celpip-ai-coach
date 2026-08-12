import { useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/cn.ts';
import { formatSeconds } from './recording.ts';

export interface CountdownProps {
  readonly seconds: number;
  readonly label: string;
  readonly running: boolean;
  readonly onFinished?: () => void;
  /** Turns the display red under this many seconds remaining. */
  readonly warnAt?: number;
}

/** A real countdown of known length — never a fake progress percentage. */
export function Countdown({ seconds, label, running, onFinished, warnAt = 10 }: CountdownProps) {
  const [remaining, setRemaining] = useState(seconds);
  const finishedRef = useRef(false);

  useEffect(() => {
    setRemaining(seconds);
    finishedRef.current = false;
  }, [seconds]);

  useEffect(() => {
    if (!running) return;

    const startedAt = Date.now();
    const timer = setInterval(() => {
      const left = seconds - (Date.now() - startedAt) / 1000;
      setRemaining(Math.max(0, left));

      if (left <= 0 && !finishedRef.current) {
        finishedRef.current = true;
        clearInterval(timer);
        onFinished?.();
      }
    }, 200);

    return () => clearInterval(timer);
  }, [running, seconds, onFinished]);

  const urgent = running && remaining <= warnAt;

  return (
    <div className="flex items-baseline gap-3" role="timer" aria-live="off">
      <span className="text-sm text-ink-muted">{label}</span>
      <span
        className={cn(
          'font-mono text-2xl font-semibold tabular-nums',
          urgent ? 'text-critical' : 'text-ink',
        )}
      >
        {formatSeconds(remaining)}
      </span>
    </div>
  );
}
