import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell.tsx';
import { AuthProvider } from './features/auth/AuthProvider.tsx';
import type { AuthMode } from './features/auth/authContext.ts';
import { RequireAuth } from './features/auth/RequireAuth.tsx';
import { AuthCallbackPage } from './pages/AuthCallbackPage.tsx';
import { HomePage } from './pages/HomePage.tsx';
import { ListeningPage } from './pages/ListeningPage.tsx';
import { NotFoundPage } from './pages/NotFoundPage.tsx';
import { SpeakingPage } from './pages/SpeakingPage.tsx';
import { createQueryClient } from './app/queryClient.ts';

/**
 * Auth mode is a build-time decision, because the deployed bundle and the local
 * bundle are built separately. The backend enforces the same mode server-side;
 * this only decides what the browser renders.
 */
const AUTH_MODE: AuthMode = import.meta.env.VITE_AUTH_MODE === 'COGNITO' ? 'COGNITO' : 'LOCAL_STUB';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="auth/callback" element={<AuthCallbackPage />} />
        <Route
          path="listening"
          element={
            <RequireAuth>
              <ListeningPage />
            </RequireAuth>
          }
        />
        <Route
          path="speaking"
          element={
            <RequireAuth>
              <SpeakingPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider mode={AUTH_MODE}>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
