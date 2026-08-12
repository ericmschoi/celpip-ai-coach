import { NavLink, Outlet } from 'react-router-dom';
import { cn } from '../../lib/cn.ts';
import { IndependenceNotice } from '../Disclaimer.tsx';

const NAV = [
  { to: '/listening', label: 'Listening' },
  { to: '/speaking', label: 'Speaking' },
] as const;

function navClass({ isActive }: { isActive: boolean }): string {
  return cn(
    'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
    isActive ? 'bg-accent-soft text-accent' : 'text-ink-muted hover:text-ink',
  );
}

export function AppShell() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a className="skip-link" href="#main">
        Skip to main content
      </a>

      <header className="border-b border-line bg-surface/80 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <NavLink
            to="/"
            className="flex items-center gap-2.5"
            aria-label="ListenSpeak AI Coach home"
          >
            <span
              aria-hidden="true"
              className="grid size-8 place-items-center rounded-lg bg-accent text-sm font-bold text-white"
            >
              LS
            </span>
            <span className="text-sm font-semibold tracking-tight sm:text-base">
              ListenSpeak <span className="text-ink-subtle font-normal">AI Coach</span>
            </span>
          </NavLink>

          <nav aria-label="Practice modes" className="flex items-center gap-1">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} className={navClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <Outlet />
      </main>

      <footer className="border-t border-line px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-5xl">
          <IndependenceNotice />
        </div>
      </footer>
    </div>
  );
}
