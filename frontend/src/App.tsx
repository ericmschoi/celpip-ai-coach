import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell.tsx';
import { HomePage } from './pages/HomePage.tsx';
import { ListeningPage } from './pages/ListeningPage.tsx';
import { NotFoundPage } from './pages/NotFoundPage.tsx';
import { SpeakingPage } from './pages/SpeakingPage.tsx';
import { createQueryClient } from './app/queryClient.ts';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="listening" element={<ListeningPage />} />
        <Route path="speaking" element={<SpeakingPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
