import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import useReducedMotion from './useReducedMotion';

/**
 * Mini-démo de l'assistant IA : une question est « tapée » automatiquement,
 * puis la réponse apparaît ligne à ligne. Pure animation — aucun appel
 * réseau, aucune IA réelle — le libellé « aperçu » le rappelle.
 *
 * En `prefers-reduced-motion: reduce`, la question et la réponse sont
 * affichées d'emblée, sans machine à écrire.
 */

const DEMOS = [
  {
    q: 'landing.ia.q1',
    reponses: ['landing.ia.q1.r1', 'landing.ia.q1.r2', 'landing.ia.q1.r3']
  },
  {
    q: 'landing.ia.q2',
    reponses: ['landing.ia.q2.r1', 'landing.ia.q2.r2']
  }
];

const VITESSE_SAISIE = 34; // ms par caractère
const PAUSE_LIGNE = 900;   // ms entre deux lignes de réponse
const PAUSE_BOUCLE = 4200; // ms avant de passer à la question suivante

export default function DemoIA() {
  const { t } = useI18n();
  const reduit = useReducedMotion();
  const [index, setIndex] = useState(0);
  const [saisie, setSaisie] = useState('');
  const [lignes, setLignes] = useState(0);

  const demo = DEMOS[index];

  useEffect(() => {
    if (reduit) {
      setSaisie(t(demo.q));
      setLignes(demo.reponses.length);
      return undefined;
    }

    setSaisie('');
    setLignes(0);
    const question = t(demo.q);
    let caracteres = 0;
    let ligne = 0;
    let idMinuterie = 0;

    const etape = () => {
      if (caracteres < question.length) {
        caracteres += 1;
        setSaisie(question.slice(0, caracteres));
        idMinuterie = window.setTimeout(etape, VITESSE_SAISIE);
      } else if (ligne < demo.reponses.length) {
        ligne += 1;
        setLignes(ligne);
        idMinuterie = window.setTimeout(etape, PAUSE_LIGNE);
      } else {
        idMinuterie = window.setTimeout(
          () => setIndex((k) => (k + 1) % DEMOS.length),
          PAUSE_BOUCLE
        );
      }
    };
    idMinuterie = window.setTimeout(etape, 400);

    return () => window.clearTimeout(idMinuterie);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index, reduit]);

  return (
    <div className="ia-fenetre">
      <span className="ia-barre" aria-hidden="true">
        <span className="ia-barre-point" />
        <span className="ia-barre-point" />
        <span className="ia-barre-point" />
        <span className="ia-barre-url">{t('landing.ia.url')}</span>
      </span>

      <div className="ia-corps">
        <p className="ia-message ia-message-utilisateur">
          <span className="ia-avatar" aria-hidden="true">?</span>
          <span className="ia-texte">
            {saisie}
            {!reduit && saisie.length < t(demo.q).length && (
              <span className="ia-curseur" aria-hidden="true">▍</span>
            )}
          </span>
        </p>

        <p className="ia-message ia-message-ia" aria-live="polite">
          <span className="ia-avatar ia-avatar-ia" aria-hidden="true">IT6</span>
          <span className="ia-texte">
            {demo.reponses.slice(0, lignes).map((ligne, i) => (
              <span key={ligne} className="ia-ligne">
                {t(ligne)}
                {i < lignes - 1 && <br />}
              </span>
            ))}
            {!reduit && lignes < demo.reponses.length && (
              <span className="ia-curseur" aria-hidden="true">▍</span>
            )}
          </span>
        </p>

        <p className="ia-note">{t('landing.ia.note')}</p>
      </div>
    </div>
  );
}
