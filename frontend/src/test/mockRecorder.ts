import { vi } from 'vitest';

/**
 * jsdom has neither MediaRecorder nor getUserMedia. These stubs give the
 * recorder hook something that behaves like the real APIs, including the
 * failure modes that matter: permission denied, no device, and a browser that
 * cannot encode anything the server accepts.
 */

type RecorderState = 'inactive' | 'recording' | 'paused';

export class FakeMediaRecorder {
  static supportedTypes: string[] = ['audio/webm;codecs=opus', 'audio/webm'];
  static instances: FakeMediaRecorder[] = [];
  /** Set to have the constructor throw, as Safari does for an unusable mimeType. */
  static constructorThrows = false;

  state: RecorderState = 'inactive';
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  onerror: (() => void) | null = null;

  readonly mimeType: string;
  readonly stream: MediaStream;

  constructor(stream: MediaStream, options?: { mimeType?: string }) {
    if (FakeMediaRecorder.constructorThrows) {
      throw new Error('mimeType not supported');
    }
    this.stream = stream;
    this.mimeType = options?.mimeType ?? 'audio/webm';
    FakeMediaRecorder.instances.push(this);
  }

  static isTypeSupported(type: string): boolean {
    return FakeMediaRecorder.supportedTypes.includes(type);
  }

  start(): void {
    this.state = 'recording';
  }

  pause(): void {
    this.state = 'paused';
  }

  resume(): void {
    this.state = 'recording';
  }

  stop(): void {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['fake audio'], { type: this.mimeType }) });
    this.onstop?.();
  }

  /** Simulates the recorder failing mid-capture. */
  fail(): void {
    this.state = 'inactive';
    this.onerror?.();
  }
}

export interface RecorderStubOptions {
  /** DOMException name to reject getUserMedia with, e.g. "NotAllowedError". */
  readonly denyWith?: string;
  readonly supportedTypes?: string[];
  readonly constructorThrows?: boolean;
  readonly noMediaDevices?: boolean;
}

export function stubRecording(options: RecorderStubOptions = {}) {
  FakeMediaRecorder.instances = [];
  FakeMediaRecorder.supportedTypes = options.supportedTypes ?? [
    'audio/webm;codecs=opus',
    'audio/webm',
  ];
  FakeMediaRecorder.constructorThrows = options.constructorThrows ?? false;

  const tracks = [{ stop: vi.fn() }];
  const stream = { getTracks: () => tracks } as unknown as MediaStream;

  const getUserMedia = vi.fn(async () => {
    if (options.denyWith) {
      throw new DOMException('denied', options.denyWith);
    }
    return stream;
  });

  vi.stubGlobal('MediaRecorder', FakeMediaRecorder);

  // jsdom's Navigator exposes read-only accessors, so mediaDevices is defined
  // directly on the existing object rather than by cloning it.
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: options.noMediaDevices ? undefined : { getUserMedia },
  });

  if (!('createObjectURL' in URL)) {
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: () => 'blob:fake', revokeObjectURL: () => {} }),
    );
  } else {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  }

  return { getUserMedia, tracks };
}

export function latestRecorder(): FakeMediaRecorder {
  const recorder = FakeMediaRecorder.instances.at(-1);
  if (!recorder) throw new Error('No MediaRecorder was created');
  return recorder;
}
