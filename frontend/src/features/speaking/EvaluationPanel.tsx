import { Button } from '../../components/ui/Button.tsx';
import { Callout } from '../../components/ui/Callout.tsx';
import { Card } from '../../components/ui/Card.tsx';
import { cn } from '../../lib/cn.ts';
import { CONFIDENCE_EXPLANATION, type SpeakingEvaluation } from './api.ts';

function ScoreBar({ score }: { readonly score: number }) {
  return (
    <div
      className="h-2 flex-1 overflow-hidden rounded-full bg-line"
      role="img"
      aria-label={`${score} out of 12`}
    >
      <div className="h-full rounded-full bg-accent" style={{ width: `${(score / 12) * 100}%` }} />
    </div>
  );
}

export interface EvaluationPanelProps {
  readonly evaluation: SpeakingEvaluation;
  readonly onPractiseAgain: () => void;
}

export function EvaluationPanel({ evaluation, onPractiseAgain }: EvaluationPanelProps) {
  const { metrics } = evaluation;

  return (
    <div className="space-y-6">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm text-ink-muted">Unofficial estimate</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums">
              {evaluation.estimatedLevel}
              <span className="text-2xl text-ink-subtle">/12</span>
            </p>
            <p className="mt-1 text-sm text-ink-muted">
              Confidence: {evaluation.confidence.toLowerCase()} —{' '}
              {CONFIDENCE_EXPLANATION[evaluation.confidence]}
            </p>
          </div>
          <Button variant="secondary" onClick={onPractiseAgain}>
            Practise again
          </Button>
        </div>

        <Callout tone="warning" className="mt-6">
          {evaluation.disclaimer}
        </Callout>
      </Card>

      <Card>
        <h2 className="mb-6 text-lg">The four dimensions</h2>
        <dl className="space-y-5">
          {evaluation.dimensions.map((dimension) => (
            <div key={dimension.dimension}>
              <div className="flex items-center gap-3">
                <dt className="w-44 shrink-0 text-sm font-medium">{dimension.label}</dt>
                <ScoreBar score={dimension.score} />
                <span className="w-10 shrink-0 text-right font-mono text-sm tabular-nums">
                  {dimension.score}
                </span>
              </div>
              <dd className="mt-1 pl-0 text-sm text-ink-muted sm:pl-47">{dimension.evidence}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-lg">What worked</h2>
          <ul className="space-y-3 text-sm text-ink-muted">
            {evaluation.strengths.map((strength) => (
              <li key={strength} className="border-l-2 border-l-positive pl-3">
                {strength}
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <h2 className="mb-4 text-lg">Fix these first</h2>
          <ol className="space-y-4 text-sm">
            {evaluation.improvements.map((improvement) => (
              <li key={improvement.issue} className="border-l-2 border-l-caution pl-3">
                <p className="font-medium">{improvement.issue}</p>
                <p className="mt-1 text-ink-muted">{improvement.whyItMatters}</p>
                <p className="mt-1 text-ink-muted">
                  <span className="font-medium text-ink">Try:</span> {improvement.howToFix}
                </p>
              </li>
            ))}
          </ol>
        </Card>
      </div>

      {evaluation.corrections.length > 0 && (
        <Card>
          <h2 className="mb-4 text-lg">Phrases to change</h2>
          <ul className="space-y-4 text-sm">
            {evaluation.corrections.map((correction) => (
              <li key={correction.original}>
                <p className="text-ink-muted line-through decoration-critical/60">
                  {correction.original}
                </p>
                <p className="font-medium text-ink">{correction.improved}</p>
                <p className="mt-1 text-xs text-ink-subtle">{correction.reason}</p>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card>
        <h2 className="mb-2 text-lg">A stronger version of your answer</h2>
        <p className="mb-4 text-sm text-ink-subtle">
          Same points you made, expressed more clearly. Not a script to memorise.
        </p>
        <p className="text-sm leading-relaxed text-ink-muted">{evaluation.sampleAnswer}</p>

        <Callout tone="info" title="Next drill" className="mt-6">
          {evaluation.nextDrill}
        </Callout>
      </Card>

      <Card>
        <h2 className="mb-4 text-lg">How you delivered it</h2>
        <dl
          className={cn('grid grid-cols-2 gap-4 text-sm sm:grid-cols-4')}
          aria-label="Delivery measurements"
        >
          {[
            { label: 'Time used', value: `${metrics.timeUsedPercent}%` },
            { label: 'Words', value: String(metrics.wordCount) },
            { label: 'Pace', value: `${metrics.wordsPerMinute} wpm` },
            { label: 'Fillers', value: String(metrics.fillerCount) },
            { label: 'Repeated starts', value: String(metrics.repeatedStarts) },
            { label: 'Silence', value: `${metrics.silencePercent}%` },
            { label: 'Longest pause', value: `${metrics.longestSilenceSeconds.toFixed(1)}s` },
            { label: 'Duration', value: `${metrics.durationSeconds.toFixed(1)}s` },
          ].map((item) => (
            <div key={item.label}>
              <dt className="text-xs text-ink-subtle">{item.label}</dt>
              <dd className="mt-0.5 font-mono tabular-nums">{item.value}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 text-xs text-ink-subtle">
          These are measurements of pace and pausing, not a pronunciation assessment. Nothing here
          judges your accent.
        </p>
      </Card>

      <Card>
        <h2 className="mb-4 text-lg">What we heard</h2>
        <p className="text-sm leading-relaxed text-ink-muted">{evaluation.transcript}</p>
      </Card>
    </div>
  );
}
