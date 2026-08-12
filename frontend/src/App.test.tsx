import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from './App.tsx';
import { renderWithProviders } from './test/renderWithProviders.tsx';

const SEED_CONFIG = {
  contentMode: 'SEED',
  authMode: 'LOCAL_STUB',
  listeningParts: [1, 2, 3, 4, 5, 6],
  speakingTasks: [
    { taskNumber: 1, title: 'Giving advice', preparationSeconds: 30, answerSeconds: 90 },
  ],
  difficulties: ['DEVELOPING', 'COMPETENT', 'ADVANCED'],
  dailyLimits: { listening: 20, speaking: 30 },
};

describe('app shell', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(SEED_CONFIG), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the home page with both practice modes', async () => {
    renderWithProviders(<AppRoutes />);

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /listening practice/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /speaking practice/i })).toBeInTheDocument();
  });

  it('always shows the independence disclaimer', () => {
    renderWithProviders(<AppRoutes />);

    expect(
      screen.getByText(/not affiliated with, authorized by, or endorsed by CELPIP/i),
    ).toBeInTheDocument();
  });

  it('surfaces demo mode when the backend reports SEED content', async () => {
    renderWithProviders(<AppRoutes />);

    expect(await screen.findByText(/Demo mode/i)).toBeInTheDocument();
  });

  it('navigates to listening via the header nav using the keyboard', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AppRoutes />);

    await user.click(screen.getByRole('link', { name: 'Listening' }));

    expect(
      await screen.findByRole('heading', { name: /listening practice/i, level: 2 }),
    ).toBeInTheDocument();
  });

  it('renders a not-found page for unknown routes', () => {
    renderWithProviders(<AppRoutes />, { route: '/nope' });

    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument();
  });
});
