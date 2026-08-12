import type { Exercise, SubmissionResult } from './api.ts';

const EXERCISE_ID = '11111111-2222-4333-8444-555555555555';

export const exerciseFixture: Exercise = {
  id: EXERCISE_ID,
  part: 5,
  partLabel: 'Discussion',
  difficulty: 'COMPETENT',
  title: 'Registered Courses or Drop-In Classes',
  scenario: 'Three members of a programming committee decide how classes should be offered.',
  speakers: ['Priya', 'Dale', 'Marcus'],
  questionCount: 2,
  questions: [
    {
      id: 'q1',
      stem: 'What has to happen before a registered course will run?',
      options: [
        { id: 'A', text: 'The board approves the rate.' },
        { id: 'B', text: 'At least eight people sign up.' },
        { id: 'C', text: 'The course runs eight weeks.' },
        { id: 'D', text: 'Three courses are advertised.' },
      ],
    },
    {
      id: 'q2',
      stem: 'How long does the committee have to decide?',
      options: [
        { id: 'A', text: 'Eight weeks.' },
        { id: 'B', text: 'Until the end of the season.' },
        { id: 'C', text: 'Two weeks.' },
        { id: 'D', text: 'Until August.' },
      ],
    },
  ],
  audioUrl: '/media/listening?token=test-token',
  audioDurationSeconds: 180,
  audioDisclosure: 'This exercise uses AI-generated voices.',
  createdAt: '2026-08-11T12:00:00Z',
};

export const submissionResultFixture: SubmissionResult = {
  attemptId: '99999999-2222-4333-8444-555555555555',
  exerciseId: EXERCISE_ID,
  correctCount: 1,
  totalQuestions: 2,
  scorePercent: 50,
  results: [
    {
      questionId: 'q1',
      stem: 'What has to happen before a registered course will run?',
      selectedOptionId: 'B',
      correctOptionId: 'B',
      correctOptionText: 'At least eight people sign up.',
      correct: true,
      explanation: 'Dale states the minimum-registration condition directly.',
      evidence: 'A registered course only runs if at least eight people sign up.',
      skill: 'DETAIL',
    },
    {
      questionId: 'q2',
      stem: 'How long does the committee have to decide?',
      selectedOptionId: 'A',
      correctOptionId: 'C',
      correctOptionText: 'Two weeks.',
      correct: false,
      explanation: 'Priya sets a two-week deadline at the start and repeats it at the end.',
      evidence: 'The board wants our recommendation within two weeks.',
      skill: 'DETAIL',
    },
  ],
  transcript: [
    { speaker: 'Priya', text: 'The board wants our recommendation within two weeks.' },
    { speaker: 'Dale', text: 'A registered course only runs if at least eight people sign up.' },
  ],
  tip: 'Track concrete facts: numbers, dates, names, and conditions.',
  weakestSkill: 'DETAIL',
  submittedAt: '2026-08-11T12:10:00Z',
};
