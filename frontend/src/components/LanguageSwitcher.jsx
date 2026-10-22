import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nContext';
import { IconGlobe, IconChevronDown } from './Icons';

/**
 * Sélecteur de langue réutilisable (barre supérieure de l'appli, page
 * d'accueil, page de connexion). Le choix est mémorisé par I18nProvider.
 */
export default function LanguageSwitcher({ variante = 'app', className = '' }) {
  const { langue, setLangue, langues, t } = useI18n();
  const [ouvert, setOuvert] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const fermer = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOuvert(false);
    };
    document.addEventListener('mousedown', fermer);
    return () => document.removeEventListener('mousedown', fermer);
  }, []);

  const courante = langues.find((l) => l.code === langue) ?? langues[0];

  /* Variante « segmente » (vitrine) : les langues sont affichées côte à
     côte, un clic bascule la page (et le RTL pour l'arabe) sans menu. */
  if (variante === 'segmente') {
    return (
      <div className={`langue-switcher langue-segmente ${className}`.trim()} role="group"
           aria-label={t('common.langue.liste')}>
        {langues.map((l) => (
          <button
            key={l.code}
            type="button"
            className={`langue-segment${l.code === langue ? ' actif' : ''}`}
            aria-pressed={l.code === langue}
            onClick={() => setLangue(l.code)}
            title={l.libelle}
          >
            {l.code.toUpperCase()}
          </button>
        ))}
      </div>
    );
  }

  return (
    <div className={`langue-switcher langue-${variante} ${className}`.trim()} ref={ref}>
      <button
        type="button"
        className="langue-bouton"
        onClick={() => setOuvert((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={ouvert}
        aria-label={t('common.langue.courante', { langue: courante.libelle })}
        title={t('common.langue.courante', { langue: courante.libelle })}
      >
        <IconGlobe size={16} />
        <span className="langue-code">{courante.code.toUpperCase()}</span>
        <IconChevronDown size={14} />
      </button>

      {ouvert && (
        <ul className="langue-liste" role="listbox" aria-label={t('common.langue.liste')}>
          {langues.map((l) => (
            <li key={l.code} role="option" aria-selected={l.code === langue}>
              <button
                type="button"
                className={`langue-item${l.code === langue ? ' actif' : ''}`}
                onClick={() => {
                  setLangue(l.code);
                  setOuvert(false);
                }}
              >
                <span className="langue-item-code">{l.code.toUpperCase()}</span>
                <span>{l.libelle}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
