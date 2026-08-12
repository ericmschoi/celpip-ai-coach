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

  // Word count, pace, and fillers are all derived from a transcript. Without
  // one they would all read zero, which looks like a measurement rather than
  // an absence, so they are not shown at all.
  const transcribed = evaluation.transcriptAvailable;

  return (
    <div className="space-y-6">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm text-ink-muted">Unofficial estimate</p>
            {evaluation.estimatedLevel == null ? (
              <>
                <p className="mt-1 text-2xl font-semibold text-ink-subtle">Not estimated</p>
                <p className="mt-1 text-sm text-ink-muted">
                  A level cannot be estimated without assessing what you said.
                </p>
              </>
            ) : (
              <>
                <p className="mt-1 text-4xl font-semibold tabular-nums">
                  {evaluation.estimatedLevel}
                  <span className="text-2xl text-ink-subtle">/12</span>
                </p>
                <p className="mt-1 text-sm text-ink-muted">
                  Confidence: {evaluation.confidence.toLowerCase()} —{' '}
                  {CONFIDENCE_EXPLANATION[evaluation.confidence]}
                </p>
              </>
            )}
          </div>
          <Button variant="secondary" onClick={onPractiseAgain}>
            Practise again
          </Button>
        </div>

        {!transcribed && (
          <Callout tone="warning" title="Your answer was not transcribed" className="mt-6">
            No AI provider is configured, so nothing listened to this recording. Everything below is
            measured from the audio itself — how long you spoke and how much of it was silence.
            Nothing here describes what you said.
          </Callout>
        )}

        <Callout tone="warning" className="mt-4">
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
                {dimension.assessed && dimension.score != null ? (
                  <>
                    <ScoreBar score={dimension.score} />
                    <span className="w-24 shrink-0 text-right font-mono text-sm tabular-nums">
                      {dimension.score}
                    </span>
                  </>
                ) : (
                  <>
                    <div className="h-2 flex-1 rounded-full bg-line/60" aria-hidden="true" />
                    <span className="w-24 shrink-0 text-right text-xs text-ink-subtle">
                      Not assessed
                    </span>
                  </>
                )}
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
        {/* Only a rewrite of the user's answer when the user's answer was read. */}
        <h2 className="mb-2 text-lg">
          {transcribed ? 'A stronger version of your answer' : 'What a strong answer looks like'}
        </h2>
        <p className="mb-4 text-sm text-ink-subtle">
          {transcribed
            ? 'Same points you made, expressed more clearly. Not a script to memorise.'
            : 'A general example for this kind of task. It is not based on your recording.'}
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
            { label: 'Time used', value: `${metrics.timeUsedPercent}%`, needsTranscript: false },
            {
              label: 'Duration',
              value: `${metrics.durationSeconds.toFixed(1)}s`,
              needsTranscript: false,
            },
            { label: 'Silence', value: `${metrics.silencePercent}%`, needsTranscript: false },
            {
              label: 'Longest pause',
              value: `${metrics.longestSilenceSeconds.toFixed(1)}s`,
              needsTranscript: false,
            },
            { label: 'Words', value: String(metrics.wordCount), needsTranscript: true },
            { label: 'Pace', value: `${metrics.wordsPerMinute} wpm`, needsTranscript: true },
            { label: 'Fillers', value: String(metrics.fillerCount), needsTranscript: true },
            {
              label: 'Repeated starts',
              value: String(metrics.repeatedStarts),
              needsTranscript: true,
            },
          ]
            .filter((item) => transcribed || !item.needsTranscript)
            .map((item) => (
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
        {transcribed ? (
          <p className="text-sm leading-relaxed text-ink-muted">{evaluation.transcript}</p>
        ) : (
          <p className="text-sm leading-relaxed text-ink-subtle">
            Nothing. Demo mode has no AI provider configured, so your recording was measured but
            never transcribed. Rather than show you words you did not say, this section is empty.
          </p>
        )}
      </Card>
    </div>
  );
}
