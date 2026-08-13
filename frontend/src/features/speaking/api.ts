import { useMutation, useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { apiRequest } from '../../lib/apiClient.ts';

export const taskSchema = z.object({
  taskNumber: z.number().int().min(1).max(8),
  title: z.string(),
  focus: z.string(),
  preparationSeconds: z.number().int().nonnegative(),
  answerSeconds: z.number().int().positive(),
});

export type SpeakingTask = z.infer<typeof taskSchema>;

export const promptSchema = z.object({
  id: z.string().uuid(),
  taskNumber: z.number().int().min(1).max(8),
  taskTitle: z.string(),
  situation: z.string(),
  instruction: z.string(),
  bullets: z.array(z.string()),
  preparationSeconds: z.number().int().nonnegative(),
  answerSeconds: z.number().int().positive(),
  createdAt: z.string(),
});

export type SpeakingPrompt = z.infer<typeof promptSchema>;

export const DIMENSION_ORDER = [
  'CONTENT_COHERENCE',
  'VOCABULARY',
  'LISTENABILITY',
  'TASK_FULFILLMENT',
] as const;

export const evaluationSchema = z.object({
  id: z.string().uuid(),
  promptId: z.string().uuid(),
  taskNumber: z.number().int(),
  // Null when no honest estimate is possible; the UI must not invent one.
  estimatedLevel: z.number().int().min(1).max(12).nullable().optional(),
  confidence: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  disclaimer: z.string(),
  // False when nothing could be transcribed, so nothing here is the user's words.
  transcriptAvailable: z.boolean(),
  dimensions: z
    .array(
      z.object({
        dimension: z.enum(DIMENSION_ORDER),
        label: z.string(),
        score: z.number().int().min(1).max(12).nullable().optional(),
        assessed: z.boolean(),
        evidence: z.string(),
      }),
    )
    .length(4),
  strengths: z.array(z.string()),
  improvements: z.array(
    z.object({ issue: z.string(), whyItMatters: z.string(), howToFix: z.string() }),
  ),
  corrections: z.array(
    z.object({ original: z.string(), improved: z.string(), reason: z.string() }),
  ),
  sampleAnswer: z.string(),
  nextDrill: z.string(),
  transcript: z.string(),
  // Diagnostics for the transcription step; used by the live verification run.
  transcriptionQuality: z.object({
    wordTimestampsAvailable: z.boolean(),
    wordCount: z.number().int(),
    averageWordConfidence: z.number().nullable().optional(),
    latencyMillis: z.number().int(),
    responseFormat: z.string(),
    verbatimRequested: z.boolean(),
  }),
  metrics: z.object({
    durationSeconds: z.number(),
    allowedSeconds: z.number().int(),
    timeUsedPercent: z.number().int(),
    wordCount: z.number().int(),
    wordsPerMinute: z.number().int(),
    fillerCount: z.number().int(),
    repeatedStarts: z.number().int(),
    silencePercent: z.number().int(),
    longestSilenceSeconds: z.number(),
  }),
  createdAt: z.string(),
});

export type SpeakingEvaluation = z.infer<typeof evaluationSchema>;

export const CONFIDENCE_EXPLANATION: Record<SpeakingEvaluation['confidence'], string> = {
  LOW: 'Treat this as a rough indication only.',
  MEDIUM: 'Reasonable indication; a longer answer would sharpen it.',
  HIGH: 'The answer gave enough evidence for a firm estimate.',
};

export function useSpeakingTasks() {
  return useQuery({
    queryKey: ['speaking', 'tasks'],
    queryFn: ({ signal }) => apiRequest('/speaking/tasks', z.array(taskSchema), { signal }),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreatePrompt() {
  return useMutation({
    mutationFn: (taskNumber: number) =>
      apiRequest(`/speaking/tasks/${taskNumber}/prompts`, promptSchema, { method: 'POST' }),
  });
}

export function useSubmitRecording() {
  return useMutation({
    mutationFn: ({
      promptId,
      blob,
      filename,
    }: {
      promptId: string;
      blob: Blob;
      filename: string;
    }) => {
      const form = new FormData();
      form.append('recording', blob, filename);

      return apiRequest(
        `/speaking/evaluations?promptId=${encodeURIComponent(promptId)}`,
        evaluationSchema,
        { method: 'POST', formData: form },
      );
    },
  });
}
