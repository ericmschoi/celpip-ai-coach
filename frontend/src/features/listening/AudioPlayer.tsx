import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/ui/Button.tsx';

function formatTime(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

export interface AudioPlayerProps {
  readonly src: string;
  readonly durationSeconds: number;
  readonly disclosure: string;
}

/**
 * Player with an optional "play once" practice constraint.
 *
 * The constraint is a learning aid, not a security control: the audio URL is
 * still fetchable, and that is fine. The point is to make the honest choice the
 * default one.
 */
export function AudioPlayer({ src, durationSeconds, disclosure }: AudioPlayerProps) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [playOnce, setPlayOnce] = useState(true);
  const [hasPlayedThrough, setHasPlayedThrough] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setHasPlayedThrough(false);
    setElapsed(0);
    setFailed(false);
  }, [src]);

  const locked = playOnce && hasPlayedThrough;

  const toggle = () => {
    const audio = audioRef.current;
    if (!audio || locked) return;
    if (audio.paused) {
      void audio.play().catch(() => setFailed(true));
    } else {
      audio.pause();
    }
  };

  return (
    <div className="rounded-lg border border-line bg-canvas p-4">
      <audio
        ref={audioRef}
        src={src}
        preload="auto"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={(event) => setElapsed(event.currentTarget.currentTime)}
        onEnded={() => {
          setIsPlaying(false);
          setHasPlayedThrough(true);
        }}
        onError={() => setFailed(true)}
        data-testid="listening-audio"
      >
        <track kind="captions" label="No captions before submission" />
      </audio>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={toggle}
          disabled={locked || failed}
          aria-label={isPlaying ? 'Pause audio' : 'Play audio'}
        >
          {isPlaying ? 'Pause' : hasPlayedThrough ? 'Play again' : 'Play'}
        </Button>

        <p className="font-mono text-sm text-ink-muted" aria-live="off">
          {formatTime(elapsed)} / {formatTime(durationSeconds)}
        </p>

        <label className="ml-auto flex items-center gap-2 text-sm text-ink-muted">
          <input
            type="checkbox"
            checked={playOnce}
            onChange={(event) => setPlayOnce(event.target.checked)}
            className="accent-[var(--color-accent)]"
          />
          Play once
        </label>
      </div>

      <div
        className="mt-3 h-1.5 overflow-hidden rounded-full bg-line"
        role="progressbar"
        aria-label="Audio progress"
        aria-valuemin={0}
        aria-valuemax={durationSeconds}
        aria-valuenow={Math.floor(elapsed)}
      >
        <div
          className="h-full bg-accent transition-[width] duration-200"
          style={{ width: `${durationSeconds ? (elapsed / durationSeconds) * 100 : 0}%` }}
        />
      </div>

      {failed && (
        <p role="alert" className="mt-3 text-sm text-critical">
          The audio could not be loaded. Its link may have expired — reload the page to get a fresh
          one.
        </p>
      )}

      {locked && (
        <p role="status" className="mt-3 text-sm text-ink-muted">
          Play once is on, so this recording has finished. Uncheck it if you need to listen again.
        </p>
      )}

      <p className="mt-3 text-xs text-ink-subtle">{disclosure}</p>
    </div>
  );
}
