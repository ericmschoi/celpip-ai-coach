import { z } from 'zod';

/** RFC 9457 Problem Details, plus the extensions this API adds. */
export const problemDetailsSchema = z.object({
  type: z.string().default('about:blank'),
  title: z.string().default('Request failed'),
  status: z.number().int(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  code: z.string().optional(),
  /** Set by the backend when the same request is worth retrying. */
  retryable: z.boolean().optional(),
  errors: z.array(z.object({ field: z.string(), message: z.string() })).optional(),
  traceId: z.string().optional(),
});

export type ProblemDetails = z.infer<typeof problemDetailsSchema>;

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails;

  constructor(problem: ProblemDetails) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
    this.status = problem.status;
    this.problem = problem;
  }

  /** True when retrying the identical request could plausibly succeed. */
  get isRetryable(): boolean {
    if (this.problem.retryable !== undefined) return this.problem.retryable;
    return this.status >= 500 || this.status === 429;
  }

  get code(): string | undefined {
    return this.problem.code;
  }
}

/** Network failure, DNS failure, offline - never reached the server. */
export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super('Could not reach the server.');
    this.name = 'NetworkError';
    this.cause = cause;
  }
}

/** The server answered, but not in the shape the client requires. */
export class ResponseShapeError extends Error {
  readonly path: string;

  constructor(path: string, cause?: unknown) {
    super(`Unexpected response shape from ${path}`);
    this.name = 'ResponseShapeError';
    this.path = path;
    this.cause = cause;
  }
}
