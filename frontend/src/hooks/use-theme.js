import { useCallback, useEffect, useState } from 'react';

export function useTheme() {
  // Fichier JavaScript : pas de générique TypeScript ici. useState<Theme>(...)
  // serait interprété comme une comparaison et lèverait « Theme is not defined »
  // au premier rendu des pages authentifiées.
  const [theme, setTheme] = useState('light');
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    const stored = localStorage.getItem('theme');
    const html = document.documentElement;
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const initial = stored ?? (prefersDark ? 'dark' : 'light');
    setTheme(initial);
    html.setAttribute('data-theme', initial);
    if (initial === 'dark') {
      html.classList.add('dark');
    } else {
      html.classList.remove('dark');
    }
  }, []);

  const toggleTheme = useCallback(() => {
    const html = document.documentElement;
    const next = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    localStorage.setItem('theme', next);
    html.setAttribute('data-theme', next);
    if (next === 'dark') {
      html.classList.add('dark');
    } else {
      html.classList.remove('dark');
    }
  }, [theme]);

  return { theme, toggleTheme, mounted };
}
