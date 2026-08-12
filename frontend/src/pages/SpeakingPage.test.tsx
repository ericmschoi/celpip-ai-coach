import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { evaluationFixture, promptFixture, tasksFixture } from '../features/speaking/fixtures.ts';
import { configRoute, mockFetch, type MockRoute } from '../test/mockApi.ts';
import { latestRecorder, stubRecording } from '../test/mockRecorder.ts';
import { renderWithProviders } from '../test/renderWithProviders.tsx';
import { SpeakingPage } from './SpeakingPage.tsx';

const tasksRoute: MockRoute = { match: /GET .*\/speaking\/tasks$/, body: tasksFixture };
const promptRoute: MockRoute = {
  match: /POST .*\/speaking\/tasks\/\d+\/prompts$/,
  status: 201,
  body: promptFixture,
};
const evaluateRoute: MockRoute = {
  match: /POST .*\/speaking\/evaluations/,
  body: evaluationFixture,
};

const ROUTES = [configRoute, tasksRoute, promptRoute, evaluateRoute];

describe('SpeakingPage', () => {
  beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  async function openPrompt() {
    const user = userEvent.setup();
    renderWithProviders(<SpeakingPage />);

    await user.click(await screen.findByRole('button', { name: /get a prompt/i }));
    await screen.findByRole('heading', { name: promptFixture.instruction });
    return user;
  }

  async function reachRecorder() {
    const user = await openPrompt();
    await user.click(screen.getByRole('button', { name: /skip to recording/i }));
    return user;
  }

  // --- task selection ----------------------------------------------------

  it('lists the tasks the backend reports, with their timings', async () => {
    stubRecording();
    mockFetch(ROUTES);
    renderWithProviders(<SpeakingPage />);

    expect(await screen.findByRole('radio', { name: /Task 1: Giving Advice/ })).toBeInTheDocument();
    expect(screen.getByText(/0:30 prep · 1:30 answer/)).toBeInTheDocument();
    expect(screen.getByText(/1:00 prep · 1:00 answer/)).toBeInTheDocument();
  });

  it('says plainly that demo mode feedback is fixed content', async () => {
    stubRecording();
    mockFetch(ROUTES);
    renderWithProviders(<SpeakingPage />);

    expect(await screen.findByText(/fixed demo content/i)).toBeInTheDocument();
  });

  it('shows the prompt with its situation, instruction, and bullets', async () => {
    stubRecording();
    mockFetch(ROUTES);
    await openPrompt();

    expect(screen.getByText(promptFixture.situation)).toBeInTheDocument();
    for (const bullet of promptFixture.bullets) {
      expect(screen.getByText(bullet)).toBeInTheDocument();
    }
  });

  // --- permission --------------------------------------------------------

  it('does not touch the microphone before the user presses record', async () => {
    const { getUserMedia } = stubRecording();
    mockFetch(ROUTES);
    await reachRecorder();

    expect(getUserMedia).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /start recording/i })).toBeInTheDocument();
  });

  it('requests the microphone only when recording starts', async () => {
    const { getUserMedia } = stubRecording();
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));

    await waitFor(() => expect(getUserMedia).toHaveBeenCalledWith({ audio: true }));
  });

  it('explains what to do when the microphone is blocked', async () => {
    stubRecording({ denyWith: 'NotAllowedError' });
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));

    expect(await screen.findByText(/Microphone blocked/i)).toBeInTheDocument();
    expect(screen.getByText(/allow access for this site/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('distinguishes a missing microphone from a blocked one', async () => {
    stubRecording({ denyWith: 'NotFoundError' });
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));

    expect(await screen.findByText(/No microphone was found/i)).toBeInTheDocument();
  });

  it('reports an unsupported browser instead of failing silently', async () => {
    stubRecording({ supportedTypes: [] });
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));

    expect(
      await screen.findByText(/Recording is not available in this browser/i),
    ).toBeInTheDocument();
  });

  it('reports when the page cannot reach the microphone API at all', async () => {
    stubRecording({ noMediaDevices: true });
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));

    expect(await screen.findByText(/not served over HTTPS/i)).toBeInTheDocument();
  });

  // --- recording ---------------------------------------------------------

  it('offers stop and pause while recording, and releases the microphone on stop', async () => {
    const { tracks } = stubRecording();
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));
    expect(await screen.findByRole('button', { name: /^stop$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^pause$/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^stop$/i }));

    expect(await screen.findByTestId('answer-playback')).toBeInTheDocument();
    expect(tracks[0]!.stop).toHaveBeenCalled();
  });

  it('stops automatically when the answer time runs out', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stubRecording();
    mockFetch(ROUTES);

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithProviders(<SpeakingPage />);
    await user.click(await screen.findByRole('button', { name: /get a prompt/i }));
    await screen.findByRole('heading', { name: promptFixture.instruction });
    await user.click(screen.getByRole('button', { name: /skip to recording/i }));
    await user.click(screen.getByRole('button', { name: /start recording/i }));

    await screen.findByRole('button', { name: /^stop$/i });
    expect(latestRecorder().state).toBe('recording');

    // The fixture task allows 90 seconds.
    await act(async () => {
      vi.advanceTimersByTime(91_000);
    });

    await waitFor(() => expect(latestRecorder().state).toBe('inactive'));
  });

  it('lets the user re-record instead of submitting', async () => {
    stubRecording();
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));
    await user.click(await screen.findByRole('button', { name: /^stop$/i }));
    await user.click(await screen.findByRole('button', { name: /record again/i }));

    expect(await screen.findByRole('button', { name: /start recording/i })).toBeInTheDocument();
    expect(screen.queryByTestId('answer-playback')).not.toBeInTheDocument();
  });

  it('surfaces a recorder failure mid-capture', async () => {
    stubRecording();
    mockFetch(ROUTES);
    const user = await reachRecorder();

    await user.click(screen.getByRole('button', { name: /start recording/i }));
    await screen.findByRole('button', { name: /^stop$/i });

    await act(async () => {
      latestRecorder().fail();
    });

    expect(await screen.findByText(/Recording stopped unexpectedly/i)).toBeInTheDocument();
  });

  // --- submission and results --------------------------------------------

  async function submitAnswer() {
    const user = await reachRecorder();
    await user.click(screen.getByRole('button', { name: /start recording/i }));
    await user.click(await screen.findByRole('button', { name: /^stop$/i }));
    await user.click(await screen.findByRole('button', { name: /submit for feedback/i }));
    await screen.findByText(/Unofficial estimate/i);
    return user;
  }

  it('uploads the recording as multipart form data', async () => {
    stubRecording();
    const { calls } = mockFetch(ROUTES);
    await submitAnswer();

    const upload = calls.find((call) => call.url.includes('/speaking/evaluations'));
    expect(upload?.url).toContain(`promptId=${promptFixture.id}`);
    expect(upload?.body).toBeInstanceOf(FormData);
  });

  it('renders all four dimensions with their evidence', async () => {
    stubRecording();
    mockFetch(ROUTES);
    await submitAnswer();

    for (const dimension of evaluationFixture.dimensions) {
      expect(screen.getByText(dimension.label)).toBeInTheDocument();
      expect(screen.getByText(dimension.evidence)).toBeInTheDocument();
    }
  });

  it('shows the estimate, its confidence, and the not-official disclaimer', async () => {
    stubRecording();
    mockFetch(ROUTES);
    await submitAnswer();

    expect(screen.getByText('/12')).toBeInTheDocument();
    expect(screen.getByText('/12').parentElement).toHaveTextContent('8/12');
    expect(screen.getByText(/Confidence: medium/i)).toBeInTheDocument();
    expect(screen.getByText(evaluationFixture.disclaimer)).toBeInTheDocument();
  });

  it('shows strengths, improvements, corrections, sample answer, and the next drill', async () => {
    stubRecording();
    mockFetch(ROUTES);
    await submitAnswer();

    expect(screen.getByText(evaluationFixture.strengths[0]!)).toBeInTheDocument();
    expect(screen.getByText(evaluationFixture.improvements[0]!.issue)).toBeInTheDocument();
    expect(screen.getByText(evaluationFixture.corrections[0]!.improved)).toBeInTheDocument();
    expect(screen.getByText(evaluationFixture.sampleAnswer)).toBeInTheDocument();
    expect(screen.getByText(evaluationFixture.nextDrill)).toBeInTheDocument();
  });

  it('presents delivery metrics as measurements, not a pronunciation verdict', async () => {
    stubRecording();
    mockFetch(ROUTES);
    await submitAnswer();

    expect(screen.getByText('142 wpm')).toBeInTheDocument();
    expect(screen.getByText('87%')).toBeInTheDocument();
    expect(screen.getByText(/not a pronunciation assessment/i)).toBeInTheDocument();
  });

  it('offers a retry when submission fails transiently', async () => {
    stubRecording();
    mockFetch([
      configRoute,
      tasksRoute,
      promptRoute,
      {
        match: /POST .*\/speaking\/evaluations/,
        status: 503,
        body: {
          type: 'https://listenspeak.app/problems/provider-unavailable',
          title: 'Provider unavailable',
          status: 503,
          detail: 'The AI provider is unavailable',
          code: 'PROVIDER_UNAVAILABLE',
          retryable: true,
        },
      },
    ]);

    const user = await reachRecorder();
    await user.click(screen.getByRole('button', { name: /start recording/i }));
    await user.click(await screen.findByRole('button', { name: /^stop$/i }));
    await user.click(await screen.findByRole('button', { name: /submit for feedback/i }));

    expect(await screen.findByText(/The AI service is busy/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('returns to task selection when practising again', async () => {
    stubRecording();
    mockFetch(ROUTES);
    const user = await submitAnswer();

    await user.click(screen.getByRole('button', { name: /practise again/i }));

    expect(await screen.findByRole('button', { name: /get a prompt/i })).toBeInTheDocument();
  });
});
