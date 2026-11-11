import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useTheme } from '../hooks/use-theme';

/**
 * Le hook de thème est rendu par le layout de l'application (bouton clair /
 * sombre). Une erreur de syntaxe ici — un générique TypeScript dans un
 * fichier JavaScript, par exemple — plantait le rendu de TOUTES les pages
 * authentifiées sans qu'aucun test ne le voie.
 */
function Sonde() {
  const { theme, toggleTheme } = useTheme();
  return (
    <button type="button" onClick={toggleTheme}>
      thème : {theme}
    </button>
  );
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('dark');
  });

  it('se rend sans erreur et expose le thème courant', () => {
    render(<Sonde />);
    expect(screen.getByText(/thème :/i)).toBeInTheDocument();
  });

  it('bascule entre thème clair et sombre', () => {
    render(<Sonde />);
    const bouton = screen.getByRole('button');
    expect(bouton.textContent).toMatch(/light|dark|clair|sombre/i);
    bouton.click();
    expect(document.documentElement.getAttribute('data-theme')).toBeTruthy();
  });
});
