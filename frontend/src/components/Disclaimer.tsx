/**
 * Required, non-dismissible disclosure. This product is independent and has no
 * relationship with CELPIP or Paragon Testing Enterprises.
 */
export const INDEPENDENCE_NOTICE =
  'ListenSpeak AI Coach is an independent practice tool. It is not affiliated with, ' +
  'authorized by, or endorsed by CELPIP or Paragon Testing Enterprises. All exercises ' +
  'are originally generated and no official test material is used.';

export const AI_VOICE_NOTICE = 'This exercise uses AI-generated voices.';

export const AI_ESTIMATE_NOTICE =
  'This is an AI estimate for practice only, not an official CELPIP score.';

export function IndependenceNotice({ className }: { readonly className?: string }) {
  return (
    <p className={className ?? 'text-xs leading-relaxed text-ink-subtle'}>{INDEPENDENCE_NOTICE}</p>
  );
}
