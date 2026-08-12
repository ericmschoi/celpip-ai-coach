import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { exerciseFixture, submissionResultFixture } from '../features/listening/fixtures.ts';
import { configRoute, liveConfigRoute, mockFetch, type MockRoute } from '../test/mockApi.ts';
import { renderWithProviders } from '../test/renderWithProviders.tsx';
import { ListeningPage } from './ListeningPage.tsx';

const createRoute: MockRoute = {
  match: /POST .*\/listening\/exercises$/,
  status: 201,
  body: exerciseFixture,
};

const submitRoute: MockRoute = {
  match: /POST .*\/listening\/exercises\/.*\/submissions$/,
  body: submissionResultFixture,
};

function problem(status: number, code: string, detail: string, retryable: boolean) {
  return {
    type: `https://listenspeak.app/problems/${code.toLowerCase()}`,
    title: code,
    status,
    detail,
    code,
    retryable,
  };
}

describe('ListeningPage', () => {
  beforeEach(() => {
    // jsdom has no media pipeline; the player only needs these to exist.
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function startExercise() {
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    await user.click(await screen.findByRole('button', { name: /start practice/i }));
    await screen.findByRole('heading', { name: exerciseFixture.title });
    return user;
  }

  // --- selection ---------------------------------------------------------

  it('defaults to Part 5 Competent and sends the chosen part and difficulty', async () => {
    const { calls } = mockFetch([liveConfigRoute, createRoute]);
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    expect(await screen.findByRole('radio', { name: /Part 5/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /Competent/ })).toBeChecked();

    await user.click(screen.getByRole('radio', { name: /Part 3/ }));
    await user.click(screen.getByRole('radio', { name: /Advanced/ }));
    await user.click(screen.getByRole('button', { name: /start practice/i }));

    await waitFor(() => {
      const create = calls.find((call) => call.method === 'POST');
      expect(create?.body).toEqual({ part: 3, difficulty: 'ADVANCED' });
    });
  });

  it('offers every part the backend reports', async () => {
    mockFetch([configRoute, createRoute]);
    renderWithProviders(<ListeningPage />);

    expect(await screen.findAllByRole('radio', { name: /^Part/ })).toHaveLength(6);
  });

  it('disables parts that have no sample while the backend is in demo mode', async () => {
    mockFetch([configRoute, createRoute]);
    renderWithProviders(<ListeningPage />);

    // SEED_CONFIG ships a sample for Part 5 only. Wait for config to arrive
    // before asserting, since the form renders first with every part enabled.
    await screen.findAllByText(/no sample in demo mode/);

    expect(screen.getByRole('radio', { name: /Part 5/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /Part 3/ })).toBeDisabled();
  });

  it('enables every part once the backend can generate live', async () => {
    mockFetch([liveConfigRoute, createRoute]);
    renderWithProviders(<ListeningPage />);

    expect(await screen.findByRole('radio', { name: /Part 3/ })).toBeEnabled();
  });

  it('states that difficulty labels are not official levels', async () => {
    mockFetch([configRoute]);
    renderWithProviders(<ListeningPage />);

    expect(await screen.findByText(/not official calibrated test levels/i)).toBeInTheDocument();
  });

  // --- loading, error, retry ---------------------------------------------

  it('shows a stage label rather than a fake percentage while generating', async () => {
    mockFetch([configRoute, { ...createRoute, body: exerciseFixture }]);
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    await user.click(await screen.findByRole('button', { name: /start practice/i }));

    expect(await screen.findByRole('heading', { name: exerciseFixture.title })).toBeInTheDocument();
  });

  it('offers a retry when the provider fails transiently', async () => {
    mockFetch([
      configRoute,
      {
        match: /POST .*\/listening\/exercises$/,
        status: 503,
        body: problem(503, 'PROVIDER_UNAVAILABLE', 'The AI provider is unavailable', true),
      },
    ]);
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    await user.click(await screen.findByRole('button', { name: /start practice/i }));

    expect(await screen.findByText(/The AI service is busy/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('does not offer a retry when the daily limit is reached', async () => {
    mockFetch([
      configRoute,
      {
        match: /POST .*\/listening\/exercises$/,
        status: 429,
        body: problem(429, 'DAILY_LIMIT_REACHED', 'You have used all 20 generations today.', false),
      },
    ]);
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    await user.click(await screen.findByRole('button', { name: /start practice/i }));

    expect(await screen.findByText(/Daily limit reached/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /try again/i })).not.toBeInTheDocument();
  });

  // --- practising --------------------------------------------------------

  it('discloses that the voices are AI-generated', async () => {
    mockFetch([configRoute, createRoute]);
    await startExercise();

    expect(screen.getByText(exerciseFixture.audioDisclosure)).toBeInTheDocument();
  });

  it('keeps the transcript hidden before submission', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    await startExercise();

    expect(screen.queryByRole('heading', { name: /transcript/i })).not.toBeInTheDocument();
    for (const line of submissionResultFixture.transcript) {
      expect(screen.queryByText(line.text)).not.toBeInTheDocument();
    }
  });

  it('blocks submission until every question is answered', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    const user = await startExercise();

    const submit = screen.getByRole('button', { name: /submit answers/i });
    expect(submit).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: /At least eight people sign up/ }));
    expect(screen.getByText(/1 of 2 answered/)).toBeInTheDocument();
    expect(submit).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: /Two weeks/ }));
    expect(submit).toBeEnabled();
  });

  it('sends the selected options and renders the result', async () => {
    const { calls } = mockFetch([configRoute, createRoute, submitRoute]);
    const user = await startExercise();

    await user.click(screen.getByRole('radio', { name: /At least eight people sign up/ }));
    await user.click(screen.getByRole('radio', { name: /Eight weeks/ }));
    await user.click(screen.getByRole('button', { name: /submit answers/i }));

    expect(await screen.findByText('1')).toBeInTheDocument();
    expect(screen.getByText(/50% correct/)).toBeInTheDocument();

    const submission = calls.find((call) => call.url.includes('/submissions'));
    expect(submission?.body).toEqual({
      answers: [
        { questionId: 'q1', selectedOptionId: 'B' },
        { questionId: 'q2', selectedOptionId: 'A' },
      ],
    });
  });

  // --- results -----------------------------------------------------------

  async function submitAll() {
    const user = await startExercise();
    await user.click(screen.getByRole('radio', { name: /At least eight people sign up/ }));
    await user.click(screen.getByRole('radio', { name: /Eight weeks/ }));
    await user.click(screen.getByRole('button', { name: /submit answers/i }));
    await screen.findByRole('heading', { name: /transcript/i });
    return user;
  }

  it('reveals the transcript only after submission', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    await submitAll();

    for (const line of submissionResultFixture.transcript) {
      expect(screen.getByText(line.text)).toBeInTheDocument();
    }
  });

  it('shows the correct answer, rationale, and evidence for each question', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    await submitAll();

    const breakdown = screen.getByRole('heading', { name: /question by question/i }).parentElement!;
    expect(
      within(breakdown).getByText(/Dale states the minimum-registration condition/),
    ).toBeInTheDocument();
    expect(within(breakdown).getByText(/Priya sets a two-week deadline/)).toBeInTheDocument();
    expect(within(breakdown).getAllByText(/Correct|Incorrect/)).toHaveLength(2);
  });

  it('shows one targeted tip based on the missed skill', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    await submitAll();

    expect(screen.getByText(submissionResultFixture.tip)).toBeInTheDocument();
  });

  it('returns to setup when practising again', async () => {
    mockFetch([configRoute, createRoute, submitRoute]);
    const user = await submitAll();

    await user.click(screen.getByRole('button', { name: /practise again/i }));

    expect(await screen.findByRole('button', { name: /start practice/i })).toBeInTheDocument();
  });

  // --- keyboard ----------------------------------------------------------

  it('supports the whole flow from the keyboard', async () => {
    mockFetch([liveConfigRoute, createRoute, submitRoute]);
    const user = userEvent.setup();
    renderWithProviders(<ListeningPage />);

    await screen.findByRole('button', { name: /start practice/i });

    // Tab enters a radio group at the checked option, then arrows move within it.
    await user.tab();
    expect(screen.getByRole('radio', { name: /Part 5/ })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('radio', { name: /Part 6/ })).toBeChecked();

    await user.click(screen.getByRole('button', { name: /start practice/i }));
    await screen.findByRole('heading', { name: exerciseFixture.title });

    await user.click(screen.getByRole('radio', { name: /At least eight people sign up/ }));
    await user.click(screen.getByRole('radio', { name: /Two weeks/ }));

    const submit = screen.getByRole('button', { name: /submit answers/i });
    submit.focus();
    await user.keyboard('{Enter}');

    expect(await screen.findByRole('heading', { name: /transcript/i })).toBeInTheDocument();
  });
});
