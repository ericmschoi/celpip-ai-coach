import { useState } from 'react';
import { Button } from '../components/ui/Button.tsx';
import { Callout } from '../components/ui/Callout.tsx';
import { Card } from '../components/ui/Card.tsx';
import { ErrorState } from '../components/ui/ErrorState.tsx';
import { useAppConfig } from '../features/config/api.ts';
import { AudioPlayer } from '../features/listening/AudioPlayer.tsx';
import { ExerciseSetup } from '../features/listening/ExerciseSetup.tsx';
import { QuestionList } from '../features/listening/QuestionList.tsx';
import { ResultPanel } from '../features/listening/ResultPanel.tsx';
import {
  useCreateExercise,
  useSubmitAnswers,
  type Difficulty,
  type Exercise,
} from '../features/listening/api.ts';
import { useOnlineStatus } from '../lib/useOnlineStatus.ts';

const DEFAULT_PARTS = [1, 2, 3, 4, 5, 6];

export function ListeningPage() {
  const config = useAppConfig();
  const online = useOnlineStatus();
  const createExercise = useCreateExercise();
  const [exercise, setExercise] = useState<Exercise | null>(null);

  const start = (input: { part: number; difficulty: Difficulty }) => {
    createExercise.mutate(input, { onSuccess: setExercise });
  };

  if (exercise) {
    return (
      <ExerciseRunner
        exercise={exercise}
        onPractiseAgain={() => {
          setExercise(null);
          createExercise.reset();
        }}
      />
    );
  }

  return (
    <div className="space-y-4">
      {!online && (
        <Callout tone="warning" title="You are offline">
          Generating an exercise needs a connection.
        </Callout>
      )}

      <ExerciseSetup
        parts={config.data?.listeningParts ?? DEFAULT_PARTS}
        sampleOnlyParts={config.data?.seedListeningParts}
        demoMode={config.data?.contentMode === 'SEED'}
        onStart={start}
        isStarting={createExercise.isPending}
      />

      {createExercise.isPending && (
        <Callout tone="info" role="status">
          Writing the conversation, then synthesizing the voices. This usually takes under a minute.
        </Callout>
      )}

      {createExercise.isError && (
        <ErrorState
          error={createExercise.error}
          retrying={createExercise.isPending}
          onRetry={() => createExercise.reset()}
        />
      )}
    </div>
  );
}

function ExerciseRunner({
  exercise,
  onPractiseAgain,
}: {
  readonly exercise: Exercise;
  readonly onPractiseAgain: () => void;
}) {
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const submit = useSubmitAnswers(exercise.id);

  const answered = Object.keys(answers).length;
  const complete = answered === exercise.questions.length;

  if (submit.isSuccess) {
    return (
      <ResultPanel exercise={exercise} result={submit.data} onPractiseAgain={onPractiseAgain} />
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <p className="text-sm text-ink-subtle">
          Part {exercise.part} · {exercise.partLabel}
        </p>
        <h1 className="mt-1 text-xl sm:text-2xl">{exercise.title}</h1>
        <p className="mt-3 text-ink-muted">{exercise.scenario}</p>
        {exercise.speakers.length > 0 && (
          <p className="mt-2 text-sm text-ink-subtle">Speakers: {exercise.speakers.join(', ')}</p>
        )}

        <div className="mt-6">
          <AudioPlayer
            src={exercise.audioUrl}
            durationSeconds={exercise.audioDurationSeconds}
            disclosure={exercise.audioDisclosure}
          />
        </div>
      </Card>

      <Card>
        <h2 className="mb-6 text-lg">Questions</h2>
        <QuestionList
          questions={exercise.questions}
          answers={answers}
          disabled={submit.isPending}
          onSelect={(questionId, optionId) =>
            setAnswers((current) => ({ ...current, [questionId]: optionId }))
          }
        />

        <div className="mt-8 flex flex-wrap items-center gap-4 border-t border-line pt-6">
          <Button
            size="lg"
            disabled={!complete}
            loading={submit.isPending}
            onClick={() => submit.mutate(answers)}
          >
            Submit answers
          </Button>
          <p className="text-sm text-ink-muted" aria-live="polite">
            {answered} of {exercise.questions.length} answered
            {!complete && ' — answer every question to submit'}
          </p>
        </div>

        {submit.isError && (
          <div className="mt-4">
            <ErrorState error={submit.error} onRetry={() => submit.mutate(answers)} />
          </div>
        )}
      </Card>
    </div>
  );
}
