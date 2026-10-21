import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import StatutBadge from '../components/StatutBadge';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';

const COULEURS = {
  'À planifier': '#D0BDF4',
  'En cours': '#8458B3',
  'En pause': '#C08A3E',
  'Terminée': '#4C9A7A',
  'Annulée': '#A28089'
};

const UTC_MINUIT = (iso) => {
  const [a, m, j] = iso.split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, j));
};

function moisEntre(debut, fin) {
  const mois = [];
  const d = new Date(Date.UTC(debut.getUTCFullYear(), debut.getUTCMonth(), 1));
  const f = new Date(Date.UTC(fin.getUTCFullYear(), fin.getUTCMonth(), 1));
  while (d <= f) {
    mois.push(new Date(d));
    d.setUTCMonth(d.getUTCMonth() + 1);
  }
  return mois;
}

const CLES_MOIS = [
  'roadmap.moisJanv', 'roadmap.moisFevr', 'roadmap.moisMars', 'roadmap.moisAvr',
  'roadmap.moisMai', 'roadmap.moisJuin', 'roadmap.moisJuil', 'roadmap.moisAout',
  'roadmap.moisSept', 'roadmap.moisOct', 'roadmap.moisNov', 'roadmap.moisDec'
];

/**
 * Roadmap visuelle : échéancier des initiatives
 * sur une période mensuelle, calculé uniquement à partir des données réelles
 * (dates de début / fin prévues). Filtres combinés et barres colorées par statut.
 */
export default function RoadmapPage() {
  const { t } = useI18n();
  const ref = useReferentiels();
  const [initiatives, setInitiatives] = useState([]);
  const [objectifs, setObjectifs] = useState([]);
  const [statuts, setStatuts] = useState([]);
  const [entites, setEntites] = useState([]);
  const [filtres, setFiltres] = useState({ statut: '', entite: '', objectif: '' });
  const [erreur, setErreur] = useState('');
  const [chargement, setChargement] = useState(true);

  useEffect(() => {
    Promise.all([
      api.get('/initiatives'),
      api.get('/objectifs'),
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites')
    ])
      .then(([init, obj, st, en]) => {
        setInitiatives(init.data);
        setObjectifs(obj.data);
        setStatuts(st.data);
        setEntites(en.data);
      })
      .catch((err) => setErreur(messageErreur(err)))
      .finally(() => setChargement(false));
  }, []);

  const filtrees = useMemo(
    () =>
      initiatives.filter(
        (i) =>
          (!filtres.statut || String(i.statut?.idStatut) === filtres.statut) &&
          (!filtres.entite || String(i.entite?.idEntite) === filtres.entite) &&
          (!filtres.objectif || String(i.objectif?.idObjectif) === filtres.objectif)
      ),
    [initiatives, filtres]
  );

  const avecDates = filtrees.filter((i) => i.dateDebut && i.dateFinPrevue);
  const sansDates = filtrees.filter((i) => !i.dateDebut || !i.dateFinPrevue);

  const { mois, minStart, spanMs, maxFin } = useMemo(() => {
    if (avecDates.length === 0) return { mois: [], minStart: null, spanMs: 0, maxFin: null };
    let min = UTC_MINUIT(avecDates[0].dateDebut);
    let max = UTC_MINUIT(avecDates[0].dateFinPrevue);
    avecDates.forEach((i) => {
      const d = UTC_MINUIT(i.dateDebut);
      const f = UTC_MINUIT(i.dateFinPrevue);
      if (d < min) min = d;
      if (f > max) max = f;
    });
    const minStart = new Date(Date.UTC(min.getUTCFullYear(), min.getUTCMonth(), 1));
    const maxFin = new Date(Date.UTC(max.getUTCFullYear(), max.getUTCMonth() + 1, 1));
    return { mois: moisEntre(minStart, maxFin), minStart, spanMs: maxFin - minStart, maxFin };
  }, [avecDates]);

  const aujourdHui = new Date();
  const aujourdHuiDansPeriode = mois.length > 0 && aujourdHui >= minStart && aujourdHui <= maxFin;
  const pctJour = (date) => ((date - minStart) / spanMs) * 100;

  const barre = (i) => {
    const debut = UTC_MINUIT(i.dateDebut);
    const fin = new Date(UTC_MINUIT(i.dateFinPrevue).getTime() + 86400000); // inclut la date de fin
    const gauche = Math.max(0, pctJour(debut));
    const largeur = Math.min(100, pctJour(fin) - gauche);
    return { gauche, largeur, couleur: COULEURS[i.statut?.libelle] ?? '#8458B3' };
  };

  const reinitialiser = () => setFiltres({ statut: '', entite: '', objectif: '' });

  if (chargement) return <div className="center-page">{t('roadmap.chargement')}</div>;
  if (erreur) return <div className="alerte alerte-erreur">{erreur}</div>;

  return (
    <div className="page roadmap-page">
      <header className="page-entete">
        <div>
          <h2>{t('roadmap.titre')}</h2>
          <p>{t('roadmap.sousTitre')}</p>
        </div>
      </header>

      <form className="carte filtre-bar" onSubmit={(e) => e.preventDefault()}>
        <div className="filtre-ligne">
          <select aria-label={t('roadmap.ariaStatut')}
            value={filtres.statut} onChange={(e) => setFiltres((f) => ({ ...f, statut: e.target.value }))}>
            <option value="">{t('roadmap.filtreTousStatuts')}</option>
            {statuts.map((s) => <option key={s.idStatut} value={s.idStatut}>{ref.statut(s.libelle)}</option>)}
          </select>
          <select aria-label={t('roadmap.ariaEntite')}
            value={filtres.entite} onChange={(e) => setFiltres((f) => ({ ...f, entite: e.target.value }))}>
            <option value="">{t('roadmap.filtreToutesEntites')}</option>
            {entites.map((en) => <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>)}
          </select>
          <select aria-label={t('roadmap.ariaObjectif')}
            value={filtres.objectif} onChange={(e) => setFiltres((f) => ({ ...f, objectif: e.target.value }))}>
            <option value="">{t('roadmap.filtreTousObjectifs')}</option>
            {objectifs.map((o) => <option key={o.idObjectif} value={o.idObjectif}>{o.libelle}</option>)}
          </select>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>{t('roadmap.reinitialiser')}</button>
        </div>
      </form>

      <div className="carte roadmap-legende">
        {statuts.map((s) => (
          <span key={s.idStatut} className="roadmap-legende-item">
            <span className="pastille" style={{ background: COULEURS[s.libelle] ?? '#8458B3' }} />
            {ref.statut(s.libelle)}
          </span>
        ))}
        <span className="texte-muet">{t('roadmap.legendeRetard')}</span>
      </div>

      {avecDates.length === 0 ? (
        <div className="carte">
          <p className="texte-muet">{t('roadmap.aucune')}</p>
        </div>
      ) : (
        <div className="carte roadmap-enveloppe">
          <div className="roadmap-grille">
            <div className="roadmap-titre roadmap-entete-ligne" />
            <div className="roadmap-calendrier roadmap-entete-ligne">
              {mois.map((m) => (
                <div key={m.toISOString()} className="roadmap-mois" style={{ left: `${pctJour(m)}%`, width: `${100 / mois.length}%` }}>
                  {t(CLES_MOIS[m.getUTCMonth()])} {m.getUTCFullYear()}
                </div>
              ))}
              {aujourdHuiDansPeriode && (
                <div className="roadmap-aujourdhui" style={{ left: `${pctJour(aujourdHui)}%` }} title={t('roadmap.aujourdhui')} />
              )}
            </div>

            {avecDates.map((i) => {
              const b = barre(i);
              return (
                <div key={i.idInitiative} className="roadmap-ligne">
                  <div className="roadmap-titre">
                    <Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link>
                    <small className="texte-muet">
                      {i.code} · {i.entite?.nom} {i.objectif ? `· ${i.objectif.libelle}` : ''}
                    </small>
                  </div>
                  <div className="roadmap-calendrier">
                    <div
                      className={`roadmap-barre${i.enRetard ? ' barre-retard' : ''}`}
                      style={{ left: `${b.gauche}%`, width: `${b.largeur}%`, background: b.couleur }}
                      title={t('roadmap.barreTitre', {
                        nom: i.nom,
                        debut: i.dateDebut,
                        fin: i.dateFinPrevue,
                        avancement: i.avancement
                      })}
                    >
                      <span className="roadmap-avancement">{t('roadmap.pourcentage', { n: i.avancement })}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {sansDates.length > 0 && (
        <div className="carte">
          <h3>{t('roadmap.nonPlanifiees', { n: sansDates.length })}</h3>
          {sansDates.map((i) => (
            <p key={i.idInitiative}>
              <Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link> <span className="texte-muet">({i.code})</span>{' '}
              <StatutBadge libelle={i.statut?.libelle} />
            </p>
          ))}
        </div>
      )}
    </div>
  );
}