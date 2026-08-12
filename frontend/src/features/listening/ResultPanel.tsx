import { Button } from '../../components/ui/Button.tsx';
import { Callout } from '../../components/ui/Callout.tsx';
import { Card } from '../../components/ui/Card.tsx';
import { cn } from '../../lib/cn.ts';
import { SKILL_LABELS, type Exercise, type SubmissionResult } from './api.ts';

export interface ResultPanelProps {
  readonly exercise: Exercise;
  readonly result: SubmissionResult;
  readonly onPractiseAgain: () => void;
}

export function ResultPanel({ exercise, result, onPractiseAgain }: ResultPanelProps) {
  return (
    <div className="space-y-6">
      <Card>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm text-ink-muted">Your score</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums">
              {result.correctCount}
              <span className="text-2xl text-ink-subtle">/{result.totalQuestions}</span>
            </p>
            <p className="mt-1 text-sm text-ink-muted">{result.scorePercent}% correct</p>
          </div>
          <Button variant="secondary" onClick={onPractiseAgain}>
            Practise again
          </Button>
        </div>

        <Callout tone="info" title="Next time" className="mt-6">
          {result.tip}
        </Callout>
      </Card>

      <Card>
        <h2 className="mb-6 text-lg">Question by question</h2>
        <ol className="space-y-6">
          {result.results.map((item, index) => (
            <li
              key={item.questionId}
              className={cn(
                'rounded-lg border-l-4 pl-4',
                item.correct ? 'border-l-positive' : 'border-l-critical',
              )}
            >
              <div className="flex flex-wrap items-baseline gap-2">
                <span className="text-ink-subtle">{index + 1}.</span>
                <p className="flex-1 font-medium">{item.stem}</p>
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-xs font-semibold',
                    item.correct
                      ? 'bg-positive-soft text-positive'
                      : 'bg-critical-soft text-critical',
                  )}
                >
                  {item.correct ? 'Correct' : 'Incorrect'}
                </span>
              </div>

              <dl className="mt-3 space-y-2 text-sm">
                {!item.correct && (
                  <div className="flex gap-2">
                    <dt className="w-24 shrink-0 text-ink-subtle">You chose</dt>
                    <dd>{item.selectedOptionId ?? 'No answer'}</dd>
                  </div>
                )}
                <div className="flex gap-2">
                  <dt className="w-24 shrink-0 text-ink-subtle">Answer</dt>
                  <dd>
                    <span className="font-semibold">{item.correctOptionId}</span> —{' '}
                    {item.correctOptionText}
                  </dd>
                </div>
                <div className="flex gap-2">
                  <dt className="w-24 shrink-0 text-ink-subtle">Why</dt>
                  <dd className="text-ink-muted">{item.explanation}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="w-24 shrink-0 text-ink-subtle">Evidence</dt>
                  <dd className="text-ink-muted italic">“{item.evidence}”</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="w-24 shrink-0 text-ink-subtle">Tests</dt>
                  <dd className="text-ink-muted">{SKILL_LABELS[item.skill]}</dd>
                </div>
              </dl>
            </li>
          ))}
        </ol>
      </Card>

      <Card>
        <h2 className="mb-2 text-lg">Transcript</h2>
        <p className="mb-6 text-sm text-ink-muted">
          {exercise.title} — available now that you have submitted.
        </p>
        <div className="space-y-4">
          {result.transcript.map((line, index) => (
            <p key={`${line.speaker}-${index}`} className="text-sm leading-relaxed">
              <span className="mr-2 font-semibold text-accent">{line.speaker}</span>
              <span className="text-ink-muted">{line.text}</span>
            </p>
          ))}
        </div>
      </Card>
    </div>
  );
}
