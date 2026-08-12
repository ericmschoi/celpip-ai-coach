import { useState } from 'react';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardHeader } from '../../components/ui/Card.tsx';
import { cn } from '../../lib/cn.ts';
import { DIFFICULTIES, DIFFICULTY_LABELS, PART_LABELS, type Difficulty } from './api.ts';

export interface ExerciseSetupProps {
  readonly parts: number[];
  /** Parts with an offline sample; only these work while the backend is in demo mode. */
  readonly sampleOnlyParts?: number[];
  readonly demoMode?: boolean;
  readonly onStart: (input: { part: number; difficulty: Difficulty }) => void;
  readonly isStarting: boolean;
}

export function ExerciseSetup({
  parts,
  sampleOnlyParts,
  demoMode = false,
  onStart,
  isStarting,
}: ExerciseSetupProps) {
  const [part, setPart] = useState(5);
  const [difficulty, setDifficulty] = useState<Difficulty>('COMPETENT');

  // In demo mode a part without a committed sample simply cannot be generated,
  // so say so here rather than letting the request fail.
  const unavailable = (value: number) =>
    demoMode && sampleOnlyParts !== undefined && !sampleOnlyParts.includes(value);

  return (
    <Card>
      <CardHeader
        title="Listening practice"
        description="Six questions on an original, AI-generated conversation."
      />

      <fieldset className="mb-8">
        <legend className="mb-3 text-sm font-medium text-ink">Part</legend>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {parts.map((value) => (
            <label
              key={value}
              className={cn(
                'flex items-start gap-3 rounded-lg border p-3 transition-colors',
                unavailable(value)
                  ? 'cursor-not-allowed border-line opacity-60'
                  : 'cursor-pointer hover:border-accent',
                part === value ? 'border-accent bg-accent-soft' : 'border-line',
              )}
            >
              <input
                type="radio"
                name="part"
                value={value}
                checked={part === value}
                disabled={unavailable(value)}
                onChange={() => setPart(value)}
                className="mt-1 accent-[var(--color-accent)]"
              />
              <span>
                <span className="block text-sm font-medium">Part {value}</span>
                <span className="block text-xs text-ink-muted">
                  {PART_LABELS[value]}
                  {unavailable(value) && ' — no sample in demo mode'}
                </span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="mb-8">
        <legend className="mb-1 text-sm font-medium text-ink">Target difficulty</legend>
        <p className="mb-3 text-xs text-ink-subtle">
          These describe style complexity only. They are not official calibrated test levels.
        </p>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          {DIFFICULTIES.map((value) => (
            <label
              key={value}
              className={cn(
                'flex cursor-pointer flex-col gap-1 rounded-lg border p-3 transition-colors',
                difficulty === value
                  ? 'border-accent bg-accent-soft'
                  : 'border-line hover:border-accent',
              )}
            >
              <span className="flex items-center gap-2 text-sm font-medium">
                <input
                  type="radio"
                  name="difficulty"
                  value={value}
                  checked={difficulty === value}
                  onChange={() => setDifficulty(value)}
                  className="accent-[var(--color-accent)]"
                />
                {DIFFICULTY_LABELS[value].label}
              </span>
              <span className="text-xs text-ink-muted">{DIFFICULTY_LABELS[value].hint}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <Button
        size="lg"
        loading={isStarting}
        onClick={() => onStart({ part, difficulty })}
        className="w-full sm:w-auto"
      >
        {isStarting ? 'Preparing your exercise' : 'Start practice'}
      </Button>
    </Card>
  );
}
