import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md py-12 text-center">
      <h1 className="text-2xl">Page not found</h1>
      <p className="mt-3 text-ink-muted">That route does not exist in this app.</p>
      <Link to="/" className="mt-6 inline-block text-accent underline underline-offset-4">
        Back to home
      </Link>
    </div>
  );
}
