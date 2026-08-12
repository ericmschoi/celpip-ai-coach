import type { SpeakingEvaluation, SpeakingPrompt, SpeakingTask } from './api.ts';

export const tasksFixture: SpeakingTask[] = [
  {
    taskNumber: 1,
    title: 'Giving Advice',
    focus: 'Advise one person about a specific decision, with reasons.',
    preparationSeconds: 30,
    answerSeconds: 90,
  },
  {
    taskNumber: 5,
    title: 'Comparing and Persuading',
    focus: 'Choose between two options and persuade a specific listener.',
    preparationSeconds: 60,
    answerSeconds: 60,
  },
];

export const promptFixture: SpeakingPrompt = {
  id: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
  taskNumber: 1,
  taskTitle: 'Giving Advice',
  situation:
    'A friend has been offered a promotion that pays more but means moving to another city.',
  instruction: 'Give your friend advice about whether to accept the promotion.',
  bullets: ['What matters most', 'What they might regret', 'What you would do'],
  preparationSeconds: 30,
  answerSeconds: 90,
  createdAt: '2026-08-11T19:00:00Z',
};

export const evaluationFixture: SpeakingEvaluation = {
  id: 'ffffffff-bbbb-4ccc-8ddd-eeeeeeeeeeee',
  promptId: promptFixture.id,
  taskNumber: 1,
  estimatedLevel: 8,
  confidence: 'MEDIUM',
  disclaimer: 'This is an AI estimate for practice only, not an official CELPIP score.',
  transcriptAvailable: true,
  dimensions: [
    {
      dimension: 'CONTENT_COHERENCE',
      assessed: true,
      label: 'Content and Coherence',
      score: 8,
      evidence: 'You gave a position and two reasons, and the order was easy to follow.',
    },
    {
      dimension: 'VOCABULARY',
      assessed: true,
      label: 'Vocabulary',
      score: 7,
      evidence: 'You reused "big thing" where a more precise phrase was available.',
    },
    {
      dimension: 'LISTENABILITY',
      assessed: true,
      label: 'Listenability',
      score: 7,
      evidence: 'Pace was 142 words per minute with five fillers.',
    },
    {
      dimension: 'TASK_FULFILLMENT',
      assessed: true,
      label: 'Task Fulfillment',
      score: 9,
      evidence: 'You addressed the friend directly and gave a clear recommendation.',
    },
  ],
  strengths: [
    'You committed to a recommendation instead of listing options.',
    'You acknowledged the strongest objection before answering it.',
  ],
  improvements: [
    {
      issue: 'Hedged phrasing weakened the advice.',
      whyItMatters: 'Advice tasks reward a clear position.',
      howToFix: 'Replace "I think maybe" with "I would".',
    },
    {
      issue: 'The second reason had no support.',
      whyItMatters: 'A reason without support reads as an assertion.',
      howToFix: 'Add one sentence beginning "because".',
    },
  ],
  corrections: [
    {
      original: 'I think she should probably take the promotion',
      improved: 'I would encourage her to take the promotion',
      reason: 'A direct recommendation is stronger when the task asks for advice.',
    },
  ],
  sampleAnswer: 'I would take the promotion, but I would not rush it. The salary increase is real…',
  nextDrill: 'Record the same task again and aim to use at least 90 percent of the time.',
  transcript:
    'So, um, I think she should probably take the promotion, because it is a lot more money…',
  metrics: {
    durationSeconds: 78.4,
    allowedSeconds: 90,
    timeUsedPercent: 87,
    wordCount: 168,
    wordsPerMinute: 142,
    fillerCount: 5,
    repeatedStarts: 2,
    silencePercent: 12,
    longestSilenceSeconds: 2.1,
  },
  createdAt: '2026-08-11T19:05:00Z',
};

/**
 * Demo mode: nothing transcribed the answer, so nothing may be attributed to
 * the speaker. This is the shape a previous version got wrong.
 */
export const untranscribedEvaluationFixture: SpeakingEvaluation = {
  ...evaluationFixture,
  estimatedLevel: null,
  confidence: 'LOW',
  transcriptAvailable: false,
  transcript: '',
  corrections: [],
  dimensions: [
    {
      dimension: 'CONTENT_COHERENCE',
      label: 'Content and Coherence',
      score: null,
      assessed: false,
      evidence: 'Not assessed: demo mode has no AI provider configured.',
    },
    {
      dimension: 'VOCABULARY',
      label: 'Vocabulary',
      score: null,
      assessed: false,
      evidence: 'Not assessed: demo mode has no AI provider configured.',
    },
    {
      dimension: 'LISTENABILITY',
      label: 'Listenability',
      score: 8,
      assessed: true,
      evidence: 'Measured from your recording: 12% of it was silence.',
    },
    {
      dimension: 'TASK_FULFILLMENT',
      label: 'Task Fulfillment',
      score: 9,
      assessed: true,
      evidence: 'Measured from your recording: you used 87% of the time available.',
    },
  ],
  sampleAnswer: 'A strong answer to a task like this states a position in the first sentence.',
  metrics: { ...evaluationFixture.metrics, wordCount: 0, wordsPerMinute: 0, fillerCount: 0 },
};
