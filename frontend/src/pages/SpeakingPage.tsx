import { Card, CardHeader } from '../components/ui/Card.tsx';

/** Placeholder until the Speaking vertical slice lands in phase 3. */
export function SpeakingPage() {
  return (
    <Card>
      <CardHeader
        title="Speaking practice"
        description="Pick a task, prepare, record your answer, and get structured feedback."
      />
      <p className="text-sm text-ink-muted">The recorder arrives in a later phase.</p>
    </Card>
  );
}
