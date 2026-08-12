import { Card, CardHeader } from '../components/ui/Card.tsx';

/** Placeholder until the Listening vertical slice lands in phase 2. */
export function ListeningPage() {
  return (
    <Card>
      <CardHeader
        title="Listening practice"
        description="Choose a part and a difficulty, then work through six questions."
      />
      <p className="text-sm text-ink-muted">The exercise player arrives in the next phase.</p>
    </Card>
  );
}
