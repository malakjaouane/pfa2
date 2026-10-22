import { useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { IconCheck } from '../Icons';

/**
 * Slider « Avant / Après » : un curseur (input range, donc accessible au
 * clavier et au lecteur d'écran) masque/découvre la maquette « Avec la
 * plateforme » par-dessus la maquette « Aujourd'hui, sans outil dédié ».
 *
 * Les deux panneaux sont des maquettes CSS : aucune image, aucun texte
 * figé — tout est traduit.
 */

const LIGNES_AVANT = [
  'landing.contraste.probleme.i1',
  'landing.contraste.probleme.i2',
  'landing.contraste.probleme.i3'
];

const LIGNES_APRES = [
  'landing.contraste.solution.i1',
  'landing.contraste.solution.i2',
  'landing.contraste.solution.i3'
];

export default function AvantApres() {
  const { t } = useI18n();
  const [pos, setPos] = useState(50);
  const zoneRef = useRef(null);

  const surGlisser = (e) => {
    const zone = zoneRef.current;
    if (!zone) return;
    const rect = zone.getBoundingClientRect();
    const x = (e.clientX ?? e.touches?.[0]?.clientX ?? 0) - rect.left;
    setPos(Math.min(100, Math.max(0, (x / rect.width) * 100)));
  };

  const surPointerDown = (e) => {
    e.currentTarget.setPointerCapture?.(e.pointerId);
    surGlisser(e);
  };

  const surPointerMove = (e) => {
    if (e.buttons === 1 || e.pressure > 0) surGlisser(e);
  };

  return (
    <div className="aa-zone" ref={zoneRef}>
      <div className="aa-panneau aa-panneau-avant" aria-hidden="true">
        <span className="aa-badge aa-badge-avant">{t('landing.avantApres.avant')}</span>
        <ul className="aa-liste aa-liste-k-o">
          {LIGNES_AVANT.map((cle) => (
            <li key={cle}>
              <span className="aa-marque aa-marque-k-o" aria-hidden="true">×</span>
              {t(cle)}
            </li>
          ))}
        </ul>
        <span className="aa-tableur" aria-hidden="true">
          <span className="aa-tableur-ligne">Portefeuille_v7_FINAL_BIS.xlsx</span>
          <span className="aa-tableur-ligne aa-tableur-ligne-grise">Dernière MàJ : il y a 3 semaines</span>
          <span className="aa-tableur-ligne aa-tableur-ligne-erreur">#REF! · #N/A</span>
        </span>
      </div>

      <div
        className="aa-panneau aa-panneau-apres"
        style={{ clipPath: `inset(0 0 0 ${pos}%)` }}
        aria-hidden="true"
      >
        <span className="aa-badge aa-badge-apres">{t('landing.avantApres.apres')}</span>
        <ul className="aa-liste aa-liste-v-ok">
          {LIGNES_APRES.map((cle) => (
            <li key={cle}>
              <span className="aa-marque aa-marque-v-ok"><IconCheck size={11} /></span>
              {t(cle)}
            </li>
          ))}
        </ul>
        <span className="aa-kpis" aria-hidden="true">
          <span className="aa-kpi"><strong>24</strong>{t('landing.avantApres.kpiInitiatives')}</span>
          <span className="aa-kpi"><strong>68 %</strong>{t('landing.avantApres.kpiAvancement')}</span>
          <span className="aa-kpi"><strong>2,4 M</strong>{t('landing.avantApres.kpiBudget')}</span>
        </span>
      </div>

      <span className="aa-curseur" style={{ left: `${pos}%` }} aria-hidden="true">
        <span className="aa-curseur-poignee">⇔</span>
      </span>

      <input
        type="range"
        min="0"
        max="100"
        value={pos}
        onChange={(e) => setPos(Number(e.target.value))}
        className="aa-range"
        aria-label={t('landing.avantApres.curseurAria')}
      />
    </div>
  );
}
