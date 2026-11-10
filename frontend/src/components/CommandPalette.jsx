import { useCallback, useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Input } from './ui/input';
import { Button } from './ui/button';
import { cn } from '../lib/utils';

/**
 * Palette de commandes Ctrl/Cmd+K (Phase 3).
 *
 * Ouvre un overlayer de recherche rapide avec le raccourci clavier
 * Ctrl+K (ou Cmd+K sur macOS). Permet de naviguer vers les pages
 * principales et d'exécuter des actions rapides.
 *
 * Les raccourcis :
 *   - Ctrl/Cmd + K : ouvrir/fermer
 *   - Échap : fermer
 *   - Flèches haut/bas : naviguer dans les résultats
 *   - Entrée : sélectionner
 */
export default function CommandPalette() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const inputRef = useRef(null);

  const items = [
    { label: 'Tableau de bord', href: '/dashboard', icon: 'LayoutDashboard' },
    { label: 'Mes initiatives', href: '/initiatives', icon: 'List' },
    { label: 'Nouvelle initiative', href: '/initiatives/creer', icon: 'Plus' },
    { label: 'Mon espace', href: '/mon-espace', icon: 'User' },
    { label: 'Demandes', href: '/demandes', icon: 'Mail' },
    { label: 'Risques', href: '/risques', icon: 'AlertTriangle' },
    { label: 'Historique', href: '/historique', icon: 'Clock' },
    { label: 'Notifications', href: '/notifications', icon: 'Bell' },
    { label: 'Déconnexion', href: '/logout', icon: 'LogOut' }
  ];

  const filtered = items.filter((item) =>
    item.label.toLowerCase().includes(query.toLowerCase())
  );

  const openPalette = useCallback((e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      setOpen((o) => !o);
    }
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    setQuery('');
    setHighlighted(0);
  }, []);

  useEffect(() => {
    window.addEventListener('keydown', openPalette);
    return () => window.removeEventListener('keydown', openPalette);
  }, [openPalette]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e) => {
      if (e.key === 'Escape') {
        close();
        return;
      }
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setHighlighted((h) => Math.min(h + 1, Math.max(filtered.length - 1, 0)));
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setHighlighted((h) => Math.max(h - 1, 0));
        return;
      }
      if (e.key === 'Enter' && filtered[highlighted]) {
        e.preventDefault();
        navigate(filtered[highlighted].href);
        close();
        return;
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [open, filtered, highlighted, navigate, close]);

  useEffect(() => {
    if (open && inputRef.current) {
      inputRef.current.focus();
      setQuery('');
      setHighlighted(0);
    }
  }, [open]);

  if (!open) return null;

  return (
    <div
      onClick={(e) => e.stopPropagation()}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onPointerDown={close}
    >
      <div className="relative z-[101] w-full max-w-md mx-4">
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between bg-card p-4 rounded-xl border border-input shadow-2xl">
            <h2 className="text-sm font-semibold">Commandes rapides</h2>
            <Button
              variant="ghost"
              size="sm"
              onClick={close}
              aria-label="Fermer"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
          <div className="p-4">
            <Input
              ref={inputRef}
              placeholder="Rechercher une page ou une action…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              autoFocus
            />
          </div>
          <div className="max-h-64 overflow-y-auto border-t border-input p-2">
            {filtered.length === 0 ? (
              <div className="p-4 text-center text-sm text-muted-foreground">
                Aucun résultat
              </div>
            ) : (
              filtered.map((item, index) => (
                <button
                  key={item.label}
                  onClick={() => {
                    navigate(item.href);
                    close();
                  }}
                  className={cn(
                    'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors',
                    highlighted === index
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground hover:bg-accent'
                  )}
                >
                  {item.label}
                </button>
              ))
            )}
          </div>
          <div className="bg-muted p-3 rounded-lg text-xs text-muted-foreground">
            <kbd className="mr-1 bg-background px-1.5 py-0.5 rounded border border-input">
              Ctrl<span className="text-foreground">+</span>K
            </kbd>
            pour ouvrir • <span className="text-foreground">Échap</span> pour fermer
          </div>
        </div>
      </div>
    </div>
  );
}
