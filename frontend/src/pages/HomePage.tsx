import { Link } from 'react-router-dom';
import { Callout } from '../components/ui/Callout.tsx';
import { Card } from '../components/ui/Card.tsx';
import { useAppConfig } from '../features/config/api.ts';

const MODES = [
  {
    to: '/listening',
    title: 'Listening practice',
    body: 'Parts 1-6. Original scenarios, multi-voice audio, six questions, and a transcript-backed explanation after you submit.',
  },
  {
    to: '/speaking',
    title: 'Speaking practice',
    body: 'Tasks 1-8. Timed preparation and answer, then feedback on content, vocabulary, listenability, and task fulfillment.',
  },
] as const;

export function HomePage() {
  const config = useAppConfig();

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-2xl sm:text-3xl">One focused practice session a day.</h1>
        <p className="mt-3 max-w-2xl text-ink-muted">
          Independent, CELPIP-style Listening and Speaking practice with original exercises and
          detailed AI feedback.
        </p>
      </section>

      <div className="grid gap-4 sm:grid-cols-2">
        {MODES.map((mode) => (
          <Link
            key={mode.to}
            to={mode.to}
            className="rounded-card border border-line bg-surface p-6 transition-colors hover:border-accent"
          >
            <h2 className="text-lg">{mode.title}</h2>
            <p className="mt-2 text-sm text-ink-muted">{mode.body}</p>
          </Link>
        ))}
      </div>

      {config.data?.contentMode === 'SEED' && (
        <Callout tone="warning" title="Demo mode">
          The backend is running with deterministic sample content, so no AI provider calls are made
          and nothing is charged.
        </Callout>
      )}

      {config.isError && (
        <Callout tone="error" title="Backend unavailable">
          The app could not load its configuration. Practice pages will show a retry option.
        </Callout>
      )}

      <Card className="bg-canvas">
        <h2 className="text-base">What this is not</h2>
        <ul className="mt-3 space-y-2 text-sm text-ink-muted">
          <li>Not official CELPIP material, and not a calibrated official score.</li>
          <li>Difficulty labels describe style complexity only.</li>
          <li>Listening voices are AI-generated, and this is disclosed on every exercise.</li>
        </ul>
      </Card>
    </div>
  );
}
