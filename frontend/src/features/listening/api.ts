import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { apiRequest } from '../../lib/apiClient.ts';

export const DIFFICULTIES = ['DEVELOPING', 'COMPETENT', 'ADVANCED'] as const;
export type Difficulty = (typeof DIFFICULTIES)[number];

export const DIFFICULTY_LABELS: Record<Difficulty, { label: string; hint: string }> = {
  DEVELOPING: { label: 'Developing', hint: 'Around CELPIP 5-7 style complexity' },
  COMPETENT: { label: 'Competent', hint: 'Around CELPIP 8-10 style complexity' },
  ADVANCED: { label: 'Advanced', hint: 'Around CELPIP 10-12 style complexity' },
};

export const PART_LABELS: Record<number, string> = {
  1: 'Problem Solving',
  2: 'Daily Life Conversation',
  3: 'Information',
  4: 'News Item',
  5: 'Discussion',
  6: 'Viewpoints',
};

const optionSchema = z.object({
  id: z.enum(['A', 'B', 'C', 'D']),
  text: z.string().min(1),
});

const questionSchema = z.object({
  id: z.string().min(1),
  stem: z.string().min(1),
  options: z.array(optionSchema).length(4),
});

/**
 * Shape of the pre-submission payload. It is deliberately impossible to read an
 * answer out of this: the fields do not exist on the server type either.
 */
export const exerciseSchema = z.object({
  id: z.string().uuid(),
  part: z.number().int().min(1).max(6),
  partLabel: z.string(),
  difficulty: z.enum(DIFFICULTIES),
  title: z.string(),
  scenario: z.string(),
  speakers: z.array(z.string()),
  questionCount: z.number().int(),
  questions: z.array(questionSchema).min(1),
  audioUrl: z.string().min(1),
  audioDurationSeconds: z.number().int().nonnegative(),
  audioDisclosure: z.string(),
  createdAt: z.string(),
});

export type Exercise = z.infer<typeof exerciseSchema>;
export type ExerciseQuestion = z.infer<typeof questionSchema>;

const skillSchema = z.enum([
  'DETAIL',
  'PURPOSE',
  'SPEAKER_IDENTIFICATION',
  'PARAPHRASE',
  'INFERENCE',
  'ATTITUDE',
  'FINAL_POSITION',
]);

export const submissionResultSchema = z.object({
  attemptId: z.string().uuid(),
  exerciseId: z.string().uuid(),
  correctCount: z.number().int(),
  totalQuestions: z.number().int(),
  scorePercent: z.number().int(),
  results: z.array(
    z.object({
      questionId: z.string(),
      stem: z.string(),
      selectedOptionId: z.string().nullable(),
      correctOptionId: z.string(),
      correctOptionText: z.string(),
      correct: z.boolean(),
      explanation: z.string(),
      evidence: z.string(),
      skill: skillSchema,
    }),
  ),
  transcript: z.array(z.object({ speaker: z.string(), text: z.string() })),
  tip: z.string(),
  weakestSkill: skillSchema.nullable().optional(),
  submittedAt: z.string(),
});

export type SubmissionResult = z.infer<typeof submissionResultSchema>;
export type QuestionResult = SubmissionResult['results'][number];

export const SKILL_LABELS: Record<z.infer<typeof skillSchema>, string> = {
  DETAIL: 'Detail',
  PURPOSE: 'Purpose',
  SPEAKER_IDENTIFICATION: 'Speaker identification',
  PARAPHRASE: 'Paraphrase',
  INFERENCE: 'Inference',
  ATTITUDE: 'Attitude',
  FINAL_POSITION: 'Final position',
};

export const exerciseQueryKey = (exerciseId: string) =>
  ['listening', 'exercise', exerciseId] as const;

export function useExercise(exerciseId: string | null) {
  return useQuery({
    queryKey: exerciseQueryKey(exerciseId ?? ''),
    queryFn: ({ signal }) =>
      apiRequest(`/listening/exercises/${exerciseId}`, exerciseSchema, { signal }),
    enabled: Boolean(exerciseId),
  });
}

export function useCreateExercise() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: { part: number; difficulty: Difficulty }) =>
      apiRequest('/listening/exercises', exerciseSchema, {
        method: 'POST',
        body: input,
        // Generation costs money, so a retried request must not create a second
        // exercise. The key is stable for one user action.
        idempotencyKey: crypto.randomUUID(),
      }),
    onSuccess: (exercise) => {
      queryClient.setQueryData(exerciseQueryKey(exercise.id), exercise);
    },
  });
}

export function useSubmitAnswers(exerciseId: string) {
  return useMutation({
    mutationFn: (answers: Record<string, string>) =>
      apiRequest(`/listening/exercises/${exerciseId}/submissions`, submissionResultSchema, {
        method: 'POST',
        body: {
          answers: Object.entries(answers).map(([questionId, selectedOptionId]) => ({
            questionId,
            selectedOptionId,
          })),
        },
      }),
  });
}
