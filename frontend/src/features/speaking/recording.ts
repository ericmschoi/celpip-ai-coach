/**
 * Recording format selection.
 *
 * Browsers disagree about what they can encode, so the format is detected at
 * runtime rather than assumed. Opus in WebM is preferred because it is small
 * and widely supported; Safari needs the MP4/AAC fallback.
 */
const CANDIDATE_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/mp4',
  'audio/mpeg',
] as const;

export interface RecordingSupport {
  readonly supported: boolean;
  /** The chosen MIME type, or null when nothing usable was found. */
  readonly mimeType: string | null;
  readonly reason?: string;
}

export function detectRecordingSupport(): RecordingSupport {
  if (typeof window === 'undefined' || typeof MediaRecorder === 'undefined') {
    return {
      supported: false,
      mimeType: null,
      reason: 'This browser does not support audio recording.',
    };
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    return {
      supported: false,
      mimeType: null,
      // getUserMedia is unavailable on insecure origins, which is the usual cause.
      reason:
        'Microphone access is unavailable. This usually means the page is not served over HTTPS.',
    };
  }

  for (const type of CANDIDATE_TYPES) {
    if (MediaRecorder.isTypeSupported?.(type)) {
      return { supported: true, mimeType: type };
    }
  }

  return {
    supported: false,
    mimeType: null,
    reason: 'This browser cannot record in a format the server accepts.',
  };
}

/** File extension matching a MIME type, used for the uploaded part's filename. */
export function extensionFor(mimeType: string): string {
  if (mimeType.startsWith('audio/webm')) return 'webm';
  if (mimeType.startsWith('audio/ogg')) return 'ogg';
  if (mimeType.startsWith('audio/mp4')) return 'mp4';
  if (mimeType.startsWith('audio/mpeg')) return 'mp3';
  return 'bin';
}

export function formatSeconds(totalSeconds: number): string {
  const whole = Math.max(0, Math.ceil(totalSeconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}
