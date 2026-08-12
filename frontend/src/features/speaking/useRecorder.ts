import { useCallback, useEffect, useRef, useState } from 'react';
import { detectRecordingSupport, extensionFor } from './recording.ts';

const AUDIO_TYPE_BY_EXTENSION: Record<string, string> = {
  webm: 'audio/webm',
  ogg: 'audio/ogg',
  opus: 'audio/ogg',
  mp4: 'audio/mp4',
  m4a: 'audio/mp4',
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
};

function normalizeToAudioType(file: File): string {
  if (file.type.startsWith('audio/')) {
    return file.type;
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  return AUDIO_TYPE_BY_EXTENSION[extension] ?? 'audio/webm';
}

export type RecorderStatus =
  | 'idle'
  | 'requesting-permission'
  | 'recording'
  | 'paused'
  | 'stopped'
  | 'denied'
  | 'unsupported'
  | 'failed';

export interface RecordedClip {
  readonly blob: Blob;
  readonly url: string;
  readonly mimeType: string;
  readonly filename: string;
  readonly seconds: number;
}

export interface UseRecorderOptions {
  /** Recording stops automatically at this many seconds, like the real task. */
  readonly maxSeconds: number;
}

export interface Recorder {
  readonly status: RecorderStatus;
  readonly elapsedSeconds: number;
  readonly remainingSeconds: number;
  readonly clip: RecordedClip | null;
  readonly errorMessage: string | null;
  readonly canPause: boolean;
  readonly start: () => Promise<void>;
  readonly pause: () => void;
  readonly resume: () => void;
  readonly stop: () => void;
  readonly reset: () => void;
  /** Test/e2e hook: supply a recording without a microphone. */
  readonly acceptFile: (file: File) => void;
}

/**
 * Wraps MediaRecorder with the states this UI actually has to show: permission
 * denied, unsupported browser, recording, auto-stop at the time limit, playback,
 * and re-record.
 *
 * <p>The microphone is requested only when the user presses record, never on
 * mount, and the stream's tracks are stopped as soon as recording ends so the
 * browser's recording indicator goes away.
 */
export function useRecorder({ maxSeconds }: UseRecorderOptions): Recorder {
  const [status, setStatus] = useState<RecorderStatus>('idle');
  const [elapsedSeconds, setElapsed] = useState(0);
  const [clip, setClip] = useState<RecordedClip | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [canPause, setCanPause] = useState(false);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const startedAtRef = useRef(0);
  const clipUrlRef = useRef<string | null>(null);

  const clearTicker = useCallback(() => {
    if (tickRef.current !== null) {
      clearInterval(tickRef.current);
      tickRef.current = null;
    }
  }, []);

  const releaseStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  const revokeClip = useCallback(() => {
    if (clipUrlRef.current) {
      URL.revokeObjectURL(clipUrlRef.current);
      clipUrlRef.current = null;
    }
  }, []);

  // Never leave the microphone open or an object URL leaked.
  useEffect(
    () => () => {
      clearTicker();
      releaseStream();
      revokeClip();
    },
    [clearTicker, releaseStream, revokeClip],
  );

  const publish = useCallback(
    (blob: Blob, mimeType: string, seconds: number) => {
      revokeClip();
      const url = URL.createObjectURL(blob);
      clipUrlRef.current = url;
      setClip({
        blob,
        url,
        mimeType,
        filename: `answer.${extensionFor(mimeType)}`,
        seconds,
      });
    },
    [revokeClip],
  );

  const stop = useCallback(() => {
    clearTicker();
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      recorder.stop();
    }
  }, [clearTicker]);

  const start = useCallback(async () => {
    setErrorMessage(null);

    const support = detectRecordingSupport();
    if (!support.supported || !support.mimeType) {
      setStatus('unsupported');
      setErrorMessage(support.reason ?? 'Recording is not supported here.');
      return;
    }

    setStatus('requesting-permission');

    let stream: MediaStream;
    try {
      // Permission is requested here and nowhere else.
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      const name = error instanceof DOMException ? error.name : '';
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        setStatus('denied');
        setErrorMessage(
          'Microphone access was blocked. Allow it in your browser settings, then try again.',
        );
      } else if (name === 'NotFoundError') {
        setStatus('failed');
        setErrorMessage('No microphone was found on this device.');
      } else {
        setStatus('failed');
        setErrorMessage(
          'The microphone could not be started. Check that nothing else is using it.',
        );
      }
      return;
    }

    streamRef.current = stream;
    chunksRef.current = [];

    let recorder: MediaRecorder;
    try {
      recorder = new MediaRecorder(stream, { mimeType: support.mimeType });
    } catch {
      releaseStream();
      setStatus('unsupported');
      setErrorMessage('This browser cannot record in a format the server accepts.');
      return;
    }

    recorderRef.current = recorder;
    // Pause is unreliable on some mobile browsers; only offer it where it exists.
    setCanPause(typeof recorder.pause === 'function' && typeof recorder.resume === 'function');

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunksRef.current.push(event.data);
      }
    };

    recorder.onerror = () => {
      clearTicker();
      releaseStream();
      setStatus('failed');
      setErrorMessage('Recording stopped unexpectedly. Try again.');
    };

    recorder.onstop = () => {
      clearTicker();
      releaseStream();
      const seconds = (Date.now() - startedAtRef.current) / 1000;
      const blob = new Blob(chunksRef.current, { type: support.mimeType! });
      publish(blob, support.mimeType!, seconds);
      setStatus('stopped');
    };

    startedAtRef.current = Date.now();
    setElapsed(0);
    recorder.start();
    setStatus('recording');

    tickRef.current = setInterval(() => {
      const seconds = (Date.now() - startedAtRef.current) / 1000;
      setElapsed(seconds);
      if (seconds >= maxSeconds) {
        // Auto-stop at the task's limit, the same as the real thing.
        stop();
      }
    }, 200);
  }, [clearTicker, maxSeconds, publish, releaseStream, stop]);

  const pause = useCallback(() => {
    const recorder = recorderRef.current;
    if (recorder?.state === 'recording') {
      recorder.pause();
      clearTicker();
      setStatus('paused');
    }
  }, [clearTicker]);

  const resume = useCallback(() => {
    const recorder = recorderRef.current;
    if (recorder?.state === 'paused') {
      recorder.resume();
      setStatus('recording');
      tickRef.current = setInterval(() => {
        const seconds = (Date.now() - startedAtRef.current) / 1000;
        setElapsed(seconds);
        if (seconds >= maxSeconds) {
          stop();
        }
      }, 200);
    }
  }, [maxSeconds, stop]);

  const reset = useCallback(() => {
    clearTicker();
    releaseStream();
    revokeClip();
    recorderRef.current = null;
    chunksRef.current = [];
    setClip(null);
    setElapsed(0);
    setErrorMessage(null);
    setStatus('idle');
  }, [clearTicker, releaseStream, revokeClip]);

  const acceptFile = useCallback(
    (file: File) => {
      // A picked .webm is reported as video/webm by the browser even when it
      // holds only an audio track, so the container type is normalised to the
      // audio equivalent the API expects.
      const mimeType = normalizeToAudioType(file);
      publish(file.slice(0, file.size, mimeType), mimeType, 0);
      setStatus('stopped');
    },
    [publish],
  );

  return {
    status,
    elapsedSeconds,
    remainingSeconds: Math.max(0, maxSeconds - elapsedSeconds),
    clip,
    errorMessage,
    canPause,
    start,
    pause,
    resume,
    stop,
    reset,
    acceptFile,
  };
}
