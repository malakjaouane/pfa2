import * as React from 'react';
import { useTheme } from '@/hooks/use-theme';
import { Sun, Moon } from 'lucide-react';

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();

  return (
    <button
      onClick={toggleTheme}
      className="rounded-md p-1.5 text-muted-foreground ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:opacity-50"
      aria-label={`Basculer vers le thème ${theme === 'light' ? 'sombre' : 'clair'}`}
    >
      {theme === 'light' ? (
        <Moon size={18} className="text-amber-600" />
      ) : (
        <Sun size={18} className="text-yellow-400" />
      )}
    </button>
  );
}
