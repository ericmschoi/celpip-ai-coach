import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { apiRequest } from '../../lib/apiClient.ts';

export const appConfigSchema = z.object({
  /** SEED = deterministic fixtures, LIVE = real provider calls. */
  contentMode: z.enum(['SEED', 'LIVE']),
  authMode: z.enum(['LOCAL_STUB', 'COGNITO']),
  listeningParts: z.array(z.number().int().min(1).max(6)),
  speakingTasks: z.array(
    z.object({
      taskNumber: z.number().int().min(1).max(8),
      title: z.string(),
      preparationSeconds: z.number().int().nonnegative(),
      answerSeconds: z.number().int().positive(),
    }),
  ),
  difficulties: z.array(z.enum(['DEVELOPING', 'COMPETENT', 'ADVANCED'])),
  dailyLimits: z.object({
    listening: z.number().int().nonnegative(),
    speaking: z.number().int().nonnegative(),
  }),
});

export type AppConfig = z.infer<typeof appConfigSchema>;

export const configQueryKey = ['config'] as const;

export function useAppConfig() {
  return useQuery({
    queryKey: configQueryKey,
    queryFn: ({ signal }) => apiRequest('/config', appConfigSchema, { signal }),
    staleTime: 5 * 60 * 1000,
  });
}
