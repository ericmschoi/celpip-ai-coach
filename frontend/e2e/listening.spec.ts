import { expect, test } from '@playwright/test';

/**
 * The full Listening slice against the real backend in SEED mode: no provider
 * calls, no spend, and a deterministic Part 5 exercise.
 */
test.describe('listening practice', () => {
  test('generate, listen, answer six questions, submit, and read the transcript', async ({
    page,
  }) => {
    await page.goto('/listening');

    await expect(page.getByRole('radio', { name: /Part 5/ })).toBeChecked();
    await page.getByRole('button', { name: /start practice/i }).click();

    // Scenario and player appear; the transcript does not.
    const heading = page.getByRole('heading', { name: /Registered Courses or Drop-In Classes/i });
    await expect(heading).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('This exercise uses AI-generated voices.')).toBeVisible();
    await expect(page.getByRole('heading', { name: /^Transcript$/ })).toHaveCount(0);

    // The audio element has a real, playable source.
    const audio = page.getByTestId('listening-audio');
    await expect(audio).toHaveAttribute('src', /token=/);
    const duration = await audio.evaluate(async (element: HTMLAudioElement) => {
      if (Number.isFinite(element.duration) && element.duration > 0) return element.duration;
      await new Promise((resolve) => {
        element.addEventListener('loadedmetadata', resolve, { once: true });
        setTimeout(resolve, 10_000);
      });
      return element.duration;
    });
    expect(duration).toBeGreaterThan(60);

    await expect(page.getByRole('button', { name: /submit answers/i })).toBeDisabled();

    // Answer all six questions.
    const questions = page.getByRole('group');
    await expect(questions).toHaveCount(6);
    for (let i = 0; i < 6; i++) {
      await questions.nth(i).getByRole('radio').first().check();
    }

    const submit = page.getByRole('button', { name: /submit answers/i });
    await expect(submit).toBeEnabled();
    await submit.click();

    // Score, per-question feedback, and now the transcript.
    await expect(page.getByText(/% correct/)).toBeVisible();
    await expect(page.getByRole('heading', { name: /question by question/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /^Transcript$/ })).toBeVisible();
    // A spoken line that appears only in the transcript, never in the evidence.
    await expect(page.getByText(/Thanks for staying late, both of you/)).toBeVisible();
    await expect(page.getByText(/Next time/)).toBeVisible();
  });

  test('the API never returns the answer key before submission', async ({ request }) => {
    const response = await request.post('/api/v1/listening/exercises', {
      headers: { 'X-Dev-User': 'e2e-user', 'Content-Type': 'application/json' },
      data: { part: 5, difficulty: 'COMPETENT' },
    });
    expect(response.status()).toBe(201);

    const body = await response.text();
    expect(body).not.toContain('correctOptionId');
    expect(body).not.toContain('speakerTurns');
    expect(body).not.toContain('explanation');
    expect(body).not.toContain('evidence');

    const exercise = JSON.parse(body) as { id: string };

    // Refetching gives the same restricted view.
    const refetch = await request.get(`/api/v1/listening/exercises/${exercise.id}`, {
      headers: { 'X-Dev-User': 'e2e-user' },
    });
    expect(await refetch.text()).not.toContain('correctOptionId');

    // Another user cannot read it at all.
    const other = await request.get(`/api/v1/listening/exercises/${exercise.id}`, {
      headers: { 'X-Dev-User': 'someone-else' },
    });
    expect(other.status()).toBe(404);
  });

  test('practising again returns to the setup screen', async ({ page }) => {
    await page.goto('/listening');
    await page.getByRole('button', { name: /start practice/i }).click();
    await expect(page.getByRole('heading', { name: /Registered Courses/i })).toBeVisible({
      timeout: 30_000,
    });

    const questions = page.getByRole('group');
    for (let i = 0; i < 6; i++) {
      await questions.nth(i).getByRole('radio').first().check();
    }
    await page.getByRole('button', { name: /submit answers/i }).click();

    await page.getByRole('button', { name: /practise again/i }).click();
    await expect(page.getByRole('button', { name: /start practice/i })).toBeVisible();
  });
});
