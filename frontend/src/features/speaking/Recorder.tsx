import { useRef } from 'react';
import { Button } from '../../components/ui/Button.tsx';
import { Callout } from '../../components/ui/Callout.tsx';
import { Countdown } from './Countdown.tsx';
import { formatSeconds } from './recording.ts';
import type { Recorder as RecorderState } from './useRecorder.ts';

/**
 * Test hook: lets the e2e suite supply a fixture recording instead of driving a
 * real microphone. Compiled out of the production bundle - the flag is only set
 * for the e2e build.
 */
const TEST_HOOKS_ENABLED = import.meta.env.VITE_ENABLE_TEST_HOOKS === 'true';

export interface RecorderProps {
  readonly recorder: RecorderState;
  readonly answerSeconds: number;
  readonly submitting: boolean;
  readonly onSubmit: () => void;
}

export function Recorder({ recorder, answerSeconds, submitting, onSubmit }: RecorderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { status, clip, errorMessage } = recorder;

  if (status === 'unsupported') {
    return (
      <Callout tone="error" title="Recording is not available in this browser">
        <p>{errorMessage}</p>
        <p className="mt-2">
          Chrome, Edge, Firefox, and Safari all support recording on a page served over HTTPS.
        </p>
      </Callout>
    );
  }

  if (status === 'denied') {
    return (
      <div className="space-y-3">
        <Callout tone="error" title="Microphone blocked">
          <p>{errorMessage}</p>
          <p className="mt-2">
            Look for the microphone icon in your browser&apos;s address bar and allow access for
            this site, then try again.
          </p>
        </Callout>
        <Button variant="secondary" onClick={() => void recorder.start()}>
          Try again
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {status === 'idle' && (
        <div className="space-y-3">
          <p className="text-sm text-ink-muted">
            Your browser will ask for microphone access when you start. The recording stops
            automatically after {formatSeconds(answerSeconds)}.
          </p>
          <Button size="lg" onClick={() => void recorder.start()}>
            Start recording
          </Button>
        </div>
      )}

      {status === 'requesting-permission' && (
        <Callout tone="info" role="status" title="Waiting for microphone access">
          Allow microphone access in the prompt your browser is showing.
        </Callout>
      )}

      {(status === 'recording' || status === 'paused') && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-4">
            <span className="flex items-center gap-2 text-sm font-medium">
              <span
                aria-hidden="true"
                className={
                  status === 'recording'
                    ? 'size-3 animate-pulse rounded-full bg-critical'
                    : 'size-3 rounded-full bg-caution'
                }
              />
              {status === 'recording' ? 'Recording' : 'Paused'}
            </span>
            <Countdown
              seconds={answerSeconds}
              label="Time left"
              running={status === 'recording'}
              warnAt={10}
            />
          </div>

          <div className="flex flex-wrap gap-3">
            {recorder.canPause &&
              (status === 'recording' ? (
                <Button variant="secondary" onClick={recorder.pause}>
                  Pause
                </Button>
              ) : (
                <Button variant="secondary" onClick={recorder.resume}>
                  Resume
                </Button>
              ))}
            <Button variant="danger" onClick={recorder.stop}>
              Stop
            </Button>
          </div>
        </div>
      )}

      {status === 'failed' && (
        <div className="space-y-3">
          <Callout tone="error" title="Recording failed">
            {errorMessage}
          </Callout>
          <Button variant="secondary" onClick={recorder.reset}>
            Start over
          </Button>
        </div>
      )}

      {status === 'stopped' && clip && (
        <div className="space-y-4">
          <div>
            <p className="mb-2 text-sm font-medium">Your answer</p>
            <audio
              controls
              src={clip.url}
              className="w-full"
              data-testid="answer-playback"
              aria-label="Play back your recorded answer"
            >
              <track kind="captions" label="No captions" />
            </audio>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button size="lg" loading={submitting} onClick={onSubmit}>
              Submit for feedback
            </Button>
            <Button variant="secondary" disabled={submitting} onClick={recorder.reset}>
              Record again
            </Button>
          </div>
        </div>
      )}

      {TEST_HOOKS_ENABLED && status !== 'stopped' && (
        <div className="border-t border-line pt-3">
          <label className="text-xs text-ink-subtle" htmlFor="e2e-recording">
            Test hook: upload a recording instead of using the microphone
          </label>
          <input
            id="e2e-recording"
            ref={fileInputRef}
            type="file"
            accept="audio/*"
            data-testid="e2e-recording-input"
            className="mt-1 block text-xs"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) recorder.acceptFile(file);
            }}
          />
        </div>
      )}
    </div>
  );
}
