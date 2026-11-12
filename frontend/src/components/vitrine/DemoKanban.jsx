import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { IconCheck, IconClock, IconPause, IconArrowRight } from '../Icons';

/**
 * Démo Kanban interactive : un portefeuille fictif que le visiteur peut
 * manipuler sans compte. Chaque carte peut changer de colonne (bouton
 * « déplacer » ou flèches clavier), la progression du portefeuille se
 * recalcule à la volée. Aucune donnée réelle n'est touchée : tout vit
 * dans l'état React local.
 */

const STATUTS = [
  { cle: 'common.statut.aPlanifier', couleur: '#848484' },
  { cle: 'common.statut.enCours', couleur: '#24C2CE' },
  { cle: 'common.statut.enPause', couleur: '#FCA822' },
  { cle: 'common.statut.terminee', couleur: '#1E8E5A' }
];

const CARTE_DEPART = [
  { id: 1, cle: 'landing.demo.carte.1', statut: 1, avancement: 62, resp: 'S. Bennani' },
  { id: 2, cle: 'landing.demo.carte.2', statut: 0, avancement: 0, resp: 'K. Alaoui' },
  { id: 3, cle: 'landing.demo.carte.3', statut: 2, avancement: 45, resp: 'N. Idrissi' },
  { id: 4, cle: 'landing.demo.carte.4', statut: 3, avancement: 100, resp: 'S. Bennani' },
  { id: 5, cle: 'landing.demo.carte.5', statut: 0, avancement: 10, resp: 'K. Alaoui' }
];

export default function DemoKanban() {
  const { t } = useI18n();
  const [cartes, setCartes] = useState(CARTE_DEPART);
  const [message, setMessage] = useState('');
  const [idEnCours, setIdEnCours] = useState(null);

  const deplacer = (id, delta) => {
    const carte = cartes.find((c) => c.id === id);
    if (!carte) return;
    const statut = Math.min(STATUTS.length - 1, Math.max(0, carte.statut + delta));
    if (statut === carte.statut) return;
    setMessage(
      t('landing.demo.deplace', { nom: t(carte.cle), statut: t(STATUTS[statut].cle) })
    );
    setIdEnCours(id);
    setCartes((liste) =>
      liste.map((c) => (c.id === id ? { ...c, statut } : c))
    );
  };

  const reinitialiser = () => {
    setCartes(CARTE_DEPART);
    setMessage('');
    setIdEnCours(null);
  };

  const avancementMoyen = Math.round(
    cartes.reduce((s, c) => s + c.avancement, 0) / cartes.length
  );

  return (
    <div className="kd-zone">
      <p className="kd-indice">
        <IconClock size={14} />
        {t('landing.demo.indice')}
      </p>

      <div className="kd-board" role="group" aria-label={t('landing.demo.boardAria')}>
        {STATUTS.map((s, colonne) => {
          const dansColonne = cartes.filter((c) => c.statut === colonne);
          return (
            <div key={s.cle} className="kd-colonne" style={{ '--kd-couleur': s.couleur }}>
              <span className="kd-colonne-titre">
                <span className="kd-colonne-point" aria-hidden="true" />
                {t(s.cle)}
                <em>{dansColonne.length}</em>
              </span>
              {dansColonne.length === 0 && (
                <p className="kd-colonne-vide">{t('landing.demo.colonneVide')}</p>
              )}
              {dansColonne.map((c) => (
                <article
                  key={c.id}
                  className={`kd-carte${idEnCours === c.id ? ' kd-carte-bouge' : ''}`}
                >
                  <span className="kd-carte-nom">{t(c.cle)}</span>
                  <span className="kd-carte-resp">{c.resp}</span>
                  <span className="kd-carte-prog" aria-hidden="true">
                    <span style={{ width: `${c.avancement}%` }} />
                  </span>
                  <span className="kd-carte-pct">{c.avancement} %</span>
                  <div className="kd-carte-actions">
                    <button
                      type="button"
                      onClick={() => deplacer(c.id, -1)}
                      disabled={c.statut === 0}
                      aria-label={t('landing.demo.recule', { nom: t(c.cle) })}
                      title={t('landing.demo.recule', { nom: t(c.cle) })}
                    >
                      <IconArrowRight size={13} className="kd-fleche-gauche" />
                    </button>
                    <button
                      type="button"
                      onClick={() => deplacer(c.id, 1)}
                      disabled={c.statut === STATUTS.length - 1}
                      aria-label={t('landing.demo.avance', { nom: t(c.cle) })}
                      title={t('landing.demo.avance', { nom: t(c.cle) })}
                    >
                      <IconArrowRight size={13} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          );
        })}
      </div>

      <div className="kd-pied">
        <p className="kd-message" role="status" aria-live="polite">
          {message ? (
            <>
              <IconCheck size={14} />
              {message}
            </>
          ) : (
            t('landing.demo.aucunDeplacement')
          )}
        </p>
        <span className="kd-moyenne">
          {t('landing.demo.moyenne', { pct: avancementMoyen })}
          <span className="kd-moyenne-piste" aria-hidden="true">
            <span style={{ width: `${avancementMoyen}%` }} />
          </span>
        </span>
        <button type="button" className="kd-reinit" onClick={reinitialiser}>
          <IconPause size={13} />
          {t('landing.demo.reinitialiser')}
        </button>
      </div>
    </div>
  );
}
