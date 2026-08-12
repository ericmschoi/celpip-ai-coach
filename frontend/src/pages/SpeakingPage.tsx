import { useState } from 'react';
import { Button } from '../components/ui/Button.tsx';
import { Callout } from '../components/ui/Callout.tsx';
import { Card, CardHeader } from '../components/ui/Card.tsx';
import { ErrorState } from '../components/ui/ErrorState.tsx';
import { useAppConfig } from '../features/config/api.ts';
import { Countdown } from '../features/speaking/Countdown.tsx';
import { EvaluationPanel } from '../features/speaking/EvaluationPanel.tsx';
import { Recorder } from '../features/speaking/Recorder.tsx';
import {
  useCreatePrompt,
  useSpeakingTasks,
  useSubmitRecording,
  type SpeakingPrompt,
  type SpeakingTask,
} from '../features/speaking/api.ts';
import { formatSeconds } from '../features/speaking/recording.ts';
import { useRecorder } from '../features/speaking/useRecorder.ts';
import { cn } from '../lib/cn.ts';
import { useOnlineStatus } from '../lib/useOnlineStatus.ts';

export function SpeakingPage() {
  const config = useAppConfig();
  const tasks = useSpeakingTasks();
  const createPrompt = useCreatePrompt();
  const online = useOnlineStatus();

  const [prompt, setPrompt] = useState<SpeakingPrompt | null>(null);
  const [selected, setSelected] = useState(1);

  if (prompt) {
    const task = tasks.data?.find((candidate) => candidate.taskNumber === prompt.taskNumber);
    return (
      <PromptSession
        prompt={prompt}
        task={task}
        onRestart={() => {
          setPrompt(null);
          createPrompt.reset();
        }}
      />
    );
  }

  return (
    <div className="space-y-4">
      {!online && (
        <Callout tone="warning" title="You are offline">
          Getting a prompt and submitting a recording both need a connection.
        </Callout>
      )}

      {config.data?.contentMode === 'SEED' && (
        <Callout tone="warning" title="Demo mode">
          Recording, timing, and delivery measurements are real. The transcript and the feedback are
          fixed demo content, because no AI provider is configured.
        </Callout>
      )}

      <Card>
        <CardHeader
          title="Speaking practice"
          description="Pick a task, prepare, record your answer, and get structured feedback."
        />

        {tasks.isPending && <p className="text-sm text-ink-muted">Loading tasks…</p>}
        {tasks.isError && <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />}

        {tasks.data && (
          <>
            <fieldset className="mb-8">
              <legend className="mb-3 text-sm font-medium">Task</legend>
              <div className="grid gap-2 sm:grid-cols-2">
                {tasks.data.map((task) => (
                  <label
                    key={task.taskNumber}
                    className={cn(
                      'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors',
                      selected === task.taskNumber
                        ? 'border-accent bg-accent-soft'
                        : 'border-line hover:border-accent',
                    )}
                  >
                    <input
                      type="radio"
                      name="task"
                      value={task.taskNumber}
                      checked={selected === task.taskNumber}
                      onChange={() => setSelected(task.taskNumber)}
                      className="mt-1 accent-[var(--color-accent)]"
                    />
                    <span>
                      <span className="block text-sm font-medium">
                        Task {task.taskNumber}: {task.title}
                      </span>
                      <span className="block text-xs text-ink-muted">{task.focus}</span>
                      <span className="mt-1 block text-xs text-ink-subtle">
                        {formatSeconds(task.preparationSeconds)} prep ·{' '}
                        {formatSeconds(task.answerSeconds)} answer
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <Button
              size="lg"
              loading={createPrompt.isPending}
              onClick={() => createPrompt.mutate(selected, { onSuccess: setPrompt })}
            >
              Get a prompt
            </Button>
          </>
        )}
      </Card>

      {createPrompt.isError && (
        <ErrorState error={createPrompt.error} onRetry={() => createPrompt.reset()} />
      )}
    </div>
  );
}

type Stage = 'reading' | 'preparing' | 'answering';

function PromptSession({
  prompt,
  task,
  onRestart,
}: {
  readonly prompt: SpeakingPrompt;
  readonly task: SpeakingTask | undefined;
  readonly onRestart: () => void;
}) {
  const [stage, setStage] = useState<Stage>('reading');
  const recorder = useRecorder({ maxSeconds: prompt.answerSeconds });
  const submit = useSubmitRecording();

  if (submit.isSuccess) {
    return <EvaluationPanel evaluation={submit.data} onPractiseAgain={onRestart} />;
  }

  const submitRecording = () => {
    const clip = recorder.clip;
    if (!clip) return;
    submit.mutate({ promptId: prompt.id, blob: clip.blob, filename: clip.filename });
  };

  return (
    <div className="space-y-6">
      <Card>
        <p className="text-sm text-ink-subtle">
          Task {prompt.taskNumber} · {prompt.taskTitle}
        </p>
        <h1 className="mt-1 text-xl sm:text-2xl">{prompt.instruction}</h1>
        <p className="mt-3 text-ink-muted">{prompt.situation}</p>

        {prompt.bullets.length > 0 && (
          <ul className="mt-4 space-y-1.5 text-sm text-ink-muted">
            {prompt.bullets.map((bullet) => (
              <li key={bullet} className="flex gap-2">
                <span aria-hidden="true" className="text-accent">
                  •
                </span>
                {bullet}
              </li>
            ))}
          </ul>
        )}

        <p className="mt-6 text-sm text-ink-subtle">
          {formatSeconds(prompt.preparationSeconds)} to prepare ·{' '}
          {formatSeconds(prompt.answerSeconds)} to answer
          {task && ` · ${task.focus}`}
        </p>
      </Card>

      <Card>
        {stage === 'reading' && (
          <div className="space-y-4">
            <p className="text-sm text-ink-muted">
              Start the preparation timer when you are ready to read the prompt properly.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button size="lg" onClick={() => setStage('preparing')}>
                Start preparation
              </Button>
              <Button variant="ghost" onClick={() => setStage('answering')}>
                Skip to recording
              </Button>
            </div>
          </div>
        )}

        {stage === 'preparing' && (
          <div className="space-y-4">
            <Countdown
              seconds={prompt.preparationSeconds}
              label="Preparation"
              running
              onFinished={() => setStage('answering')}
              warnAt={10}
            />
            <p className="text-sm text-ink-muted">
              Plan three points. Recording does not start on its own — you press record when the
              timer ends, and your browser asks for the microphone then.
            </p>
            <Button variant="secondary" onClick={() => setStage('answering')}>
              I am ready now
            </Button>
          </div>
        )}

        {stage === 'answering' && (
          <Recorder
            recorder={recorder}
            answerSeconds={prompt.answerSeconds}
            submitting={submit.isPending}
            onSubmit={submitRecording}
          />
        )}

        {submit.isPending && (
          <Callout tone="info" role="status" className="mt-4">
            Uploading your recording, transcribing it, then assessing the four dimensions.
          </Callout>
        )}

        {submit.isError && (
          <div className="mt-4">
            <ErrorState error={submit.error} onRetry={submitRecording} />
          </div>
        )}
      </Card>

      <div>
        <Button variant="ghost" onClick={onRestart}>
          Choose a different task
        </Button>
      </div>
    </div>
  );
}
