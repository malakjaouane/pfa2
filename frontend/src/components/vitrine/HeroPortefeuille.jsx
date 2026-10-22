import { useI18n } from '../../i18n/I18nContext';

/**
 * Démo vivante du hero : un mini-portefeuille dessiné en SVG/CSS,
 * sans image ni appel réseau.
 *
 *  - bulles d'initiatives (axe horizontal = avancement, taille = budget,
 *    couleur = score de santé du moteur existant) ;
 *  - anneaux de santé qui se remplissent au chargement ;
 *  - bandeau « jalons de la semaine » animé.
 *
 * Toutes les animations sont en CSS pur : la règle
 * `prefers-reduced-motion: reduce` (styles.css) les désactive et laisse
 * les valeurs finales visibles.
 */

const DEMO = [
  { cle: 'landing.demo.nom.1', budget: 3.2, avancement: 72, sante: 'VERTE' },
  { cle: 'landing.demo.nom.2', budget: 1.8, avancement: 38, sante: 'ORANGE' },
  { cle: 'landing.demo.nom.3', budget: 4.5, avancement: 15, sante: 'ROUGE' },
  { cle: 'landing.demo.nom.4', budget: 0.9, avancement: 90, sante: 'VERTE' }
];

const COULEUR = { VERTE: '#1E8E5A', ORANGE: '#FCA822', ROUGE: '#8C2136' };

const ANNEAUX = [
  { niveau: 'VERTE', cle: 'sante.verte', n: 2, pct: 50 },
  { niveau: 'ORANGE', cle: 'sante.orange', n: 1, pct: 25 },
  { niveau: 'ROUGE', cle: 'sante.rouge', n: 1, pct: 25 }
];

const JALONS = [
  { cle: 'landing.demo.jalon.1', pct: 82 },
  { cle: 'landing.demo.jalon.2', pct: 55 },
  { cle: 'landing.demo.jalon.3', pct: 30 }
];

export default function HeroPortefeuille() {
  const { t } = useI18n();

  return (
    <figure className="hp-carte" role="img" aria-label={t('landing.demo.hero.aria')}>
      <span className="hp-barre" aria-hidden="true">
        <span className="hp-barre-point" />
        <span className="hp-barre-point" />
        <span className="hp-barre-point" />
        <span className="hp-barre-url">portefeuille.it6.ma — {t('landing.demo.hero.titre')}</span>
      </span>

      <div className="hp-corps">
        {/* Bulles : budget × avancement, teintées par la santé */}
        <div className="hp-bulles">
          <svg viewBox="0 0 320 150" className="hp-bulles-svg" aria-hidden="true">
            <line x1="16" y1="134" x2="308" y2="134" className="hp-axe" />
            <text x="164" y="148" className="hp-axe-texte">{t('landing.demo.hero.avancement')}</text>
            {DEMO.map((d, i) => (
              <g key={d.cle}>
                <circle
                  className="hp-bulle"
                  cx={24 + d.avancement * 2.7}
                  cy={104 - i * 24}
                  r={8 + d.budget * 3.4}
                  fill={COULEUR[d.sante]}
                  fillOpacity="0.28"
                  stroke={COULEUR[d.sante]}
                  strokeWidth="1.6"
                  style={{ '--hp-i': i }}
                />
                <text
                  x={24 + d.avancement * 2.7}
                  y={108 - i * 24}
                  className="hp-bulle-texte"
                  textAnchor="middle"
                >
                  {t(d.cle)}
                </text>
              </g>
            ))}
          </svg>
        </div>

        {/* Anneaux de santé */}
        <ul className="hp-sante" aria-hidden="true">
          {ANNEAUX.map((a, i) => (
            <li key={a.niveau} className="hp-anneau-item" style={{ '--hp-i': i }}>
              <svg viewBox="0 0 44 44" className="hp-anneau">
                <circle cx="22" cy="22" r="18" className="hp-anneau-piste" />
                <circle
                  cx="22" cy="22" r="18"
                  className="hp-anneau-valeur"
                  stroke={COULEUR[a.niveau]}
                  style={{ '--hp-pct': a.pct }}
                />
              </svg>
              <strong>{a.n}</strong>
              <span>{t(a.cle)}</span>
            </li>
          ))}
        </ul>

        {/* Jalons de la semaine */}
        <div className="hp-jalons" aria-hidden="true">
          <span className="hp-jalons-titre">{t('landing.demo.hero.jalons')}</span>
          {JALONS.map((j, i) => (
            <div key={j.cle} className="hp-jalon" style={{ '--hp-i': i }}>
              <span className="hp-jalon-nom">{t(j.cle)}</span>
              <span className="hp-jalon-piste">
                <span className="hp-jalon-valeur" style={{ '--hp-pct': j.pct }} />
              </span>
            </div>
          ))}
        </div>
      </div>
    </figure>
  );
}
