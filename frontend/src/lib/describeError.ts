import { ApiError, NetworkError, ResponseShapeError } from './problem.ts';

interface Presentation {
  readonly title: string;
  readonly message: string;
  readonly canRetry: boolean;
}

/**
 * Turns a thrown error into something a person can act on. Retry is offered
 * only when repeating the request could actually help, because these endpoints
 * cost money per attempt.
 */
export function describeError(error: unknown): Presentation {
  if (error instanceof NetworkError) {
    return {
      title: 'No connection',
      message: 'Your device could not reach the server. Check your connection and try again.',
      canRetry: true,
    };
  }

  if (error instanceof ResponseShapeError) {
    return {
      title: 'Unexpected response',
      message: 'The server replied in a form this app does not understand. Reloading may help.',
      canRetry: false,
    };
  }

  if (error instanceof ApiError) {
    switch (error.code) {
      case 'DAILY_LIMIT_REACHED':
        return {
          title: 'Daily limit reached',
          message: `${error.message} Practice resets tomorrow.`,
          canRetry: false,
        };
      case 'RATE_LIMITED':
        return {
          title: 'Slow down a moment',
          message: 'Too many requests in a short time. Wait a few seconds and try again.',
          canRetry: true,
        };
      case 'PROVIDER_NOT_CONFIGURED':
        return { title: 'AI features are off', message: error.message, canRetry: false };
      case 'GENERATION_INVALID':
      case 'AUDIO_QUALITY_FAILED':
        return {
          title: 'That exercise did not come out right',
          message:
            'The generated exercise failed our quality checks. Generating a new one usually works.',
          canRetry: true,
        };
      case 'PROVIDER_RATE_LIMITED':
      case 'PROVIDER_UNAVAILABLE':
      case 'PROVIDER_TIMEOUT':
        return {
          title: 'The AI service is busy',
          message: 'This is a temporary problem on the provider side. Try again in a moment.',
          canRetry: true,
        };
      case 'ALREADY_SUBMITTED':
        return { title: 'Already submitted', message: error.message, canRetry: false };
      case 'UNAUTHORIZED':
        return { title: 'Please sign in', message: 'Your session has expired.', canRetry: false };
      default:
        return {
          title: error.problem.title,
          message: error.message,
          canRetry: error.isRetryable,
        };
    }
  }

  return {
    title: 'Something went wrong',
    message: 'An unexpected error occurred. Please try again.',
    canRetry: true,
  };
}
