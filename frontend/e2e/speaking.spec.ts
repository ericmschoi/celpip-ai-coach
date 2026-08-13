import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

/**
 * The Speaking slice against the real backend in SEED mode.
 *
 * A microphone cannot be driven in CI, so the fixture recording is supplied
 * through the upload test hook, which only exists in the e2e build. Everything
 * downstream of it — upload validation, FFmpeg measurement, scoring, and the
 * results screen — is the real code path.
 */
const FIXTURE = fileURLToPath(new URL('./fixtures/sample-answer.webm', import.meta.url));
const SILENCE_FIXTURE = fileURLToPath(new URL('./fixtures/silence.webm', import.meta.url));

test.describe('speaking practice', () => {
  test('pick a task, get a prompt, submit a recording, and read the feedback', async ({ page }) => {
    await page.goto('/speaking');

    await expect(page.getByRole('radio', { name: /Task 1: Giving Advice/ })).toBeChecked();
    // Tasks 1 and 7 share these timings, so match the first occurrence.
    await expect(page.getByText(/0:30 prep · 1:30 answer/).first()).toBeVisible();

    await page.getByRole('button', { name: /get a prompt/i }).click();

    // The prompt carries the task's own timings.
    await expect(page.getByText(/0:30 to prepare · 1:30 to answer/)).toBeVisible({
      timeout: 30_000,
    });

    await page.getByRole('button', { name: /skip to recording/i }).click();
    await expect(page.getByRole('button', { name: /start recording/i })).toBeVisible();

    // Supply the fixture instead of a live microphone.
    await page.getByTestId('e2e-recording-input').setInputFiles(FIXTURE);

    await expect(page.getByTestId('answer-playback')).toBeVisible();
    await page.getByRole('button', { name: /submit for feedback/i }).click();

    // Demo mode has no transcription, so the results must say so and must not
    // show a level, a transcript, or anything attributed to the speaker.
    await expect(page.getByText(/Unofficial estimate/i)).toBeVisible({ timeout: 60_000 });
    await expect(
      page.getByText('This is an AI estimate for practice only, not an official CELPIP score.'),
    ).toBeVisible();
    await expect(page.getByText(/Your answer was not transcribed/i)).toBeVisible();
    await expect(page.getByText(/Not estimated/i)).toBeVisible();
    await expect(page.getByText('/12')).toHaveCount(0);
    await expect(page.getByText(/never transcribed/i)).toBeVisible();

    for (const label of [
      'Content and Coherence',
      'Vocabulary',
      'Listenability',
      'Task Fulfillment',
    ]) {
      await expect(page.getByText(label, { exact: true })).toBeVisible();
    }

    await expect(page.getByRole('heading', { name: /what worked/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /fix these first/i })).toBeVisible();
    // Not "a stronger version of your answer": nothing read the answer.
    await expect(
      page.getByRole('heading', { name: /what a strong answer looks like/i }),
    ).toBeVisible();
    await expect(page.getByText(/Next drill/)).toBeVisible();

    // Delivery metrics come from the real recording, not a constant.
    const duration = page.getByText(/^2[0-9]\.\ds$/);
    await expect(duration.first()).toBeVisible();
    await expect(page.getByText(/not a pronunciation assessment/i)).toBeVisible();
    // Word-derived metrics need a transcript, so they are absent here.
    await expect(page.getByText('Words', { exact: true })).toHaveCount(0);

    await page.getByRole('button', { name: /practise again/i }).click();
    await expect(page.getByRole('button', { name: /get a prompt/i })).toBeVisible();
  });

  test('the API rejects an unsupported upload format', async ({ request }) => {
    const prompt = await request.post('/api/v1/speaking/tasks/1/prompts', {
      headers: { 'X-Dev-User': 'e2e-speaking' },
    });
    expect(prompt.status()).toBe(201);
    const { id } = (await prompt.json()) as { id: string };

    const response = await request.post(`/api/v1/speaking/evaluations?promptId=${id}`, {
      headers: { 'X-Dev-User': 'e2e-speaking' },
      multipart: {
        recording: {
          name: 'notes.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('%PDF-1.4 not audio'),
        },
      },
    });

    expect(response.status()).toBe(415);
    expect((await response.json()).code).toBe('UNSUPPORTED_MEDIA_TYPE');
  });

  test('an evaluation is not readable by another user', async ({ request }) => {
    const prompt = await request.post('/api/v1/speaking/tasks/1/prompts', {
      headers: { 'X-Dev-User': 'e2e-owner' },
    });
    const { id } = (await prompt.json()) as { id: string };

    const stolen = await request.post(`/api/v1/speaking/evaluations?promptId=${id}`, {
      headers: { 'X-Dev-User': 'e2e-thief' },
      multipart: {
        recording: { name: 'a.webm', mimeType: 'audio/webm', buffer: Buffer.from('x') },
      },
    });

    expect(stolen.status()).toBe(404);
  });

  test('a silent recording is refused instead of evaluated', async ({ request }) => {
    const prompt = await request.post('/api/v1/speaking/tasks/1/prompts', {
      headers: { 'X-Dev-User': 'e2e-silence' },
    });
    const { id } = (await prompt.json()) as { id: string };

    // A WebM container holding nothing but digital silence.
    const response = await request.post(`/api/v1/speaking/evaluations?promptId=${id}`, {
      headers: { 'X-Dev-User': 'e2e-silence' },
      multipart: {
        recording: {
          name: 'silence.webm',
          mimeType: 'audio/webm',
          buffer: readFileSync(SILENCE_FIXTURE),
        },
      },
    });

    expect(response.status()).toBe(400);
    const problem = (await response.json()) as { code: string; detail: string };
    expect(problem.code).toBe('VALIDATION_FAILED');
    expect(problem.detail).toMatch(/silent/i);
  });
});
