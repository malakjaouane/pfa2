import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import StatutBadge from '../components/StatutBadge';
import SanteBadge from '../components/SanteBadge';
import ProgressBar from '../components/ProgressBar';
import KanbanBoard from '../components/KanbanBoard';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';

const vide = {
  recherche: '',
  statut: '',
  entite: '',
  responsable: '',
  priorite: '',
  dateDu: '',
  dateAu: '',
  voirArchive: false
};

/** Lit l'ensemble des filtres depuis l'URL (vue partageable). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  statut: params.get('statut') ?? '',
  entite: params.get('entite') ?? '',
  responsable: params.get('responsable') ?? '',
  priorite: params.get('priorite') ?? '',
  dateDu: params.get('dateDu') ?? '',
  dateAu: params.get('dateAu') ?? '',
  voirArchive: params.get('archive') === '1' || params.get('archive') === 'true'
});

/**
 * Liste des initiatives avec recherche et filtres combinés
 * (statut, entité, responsable, période, priorité).
 */
export default function InitiativesPage() {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const ref = useReferentiels();
  // L'URL est la source de vérité des filtres : la vue filtrée se partage et
  // survit à un rafraîchissement (y compris le lien « Archives »).
  const [searchParams, setSearchParams] = useSearchParams();
  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [initiatives, setInitiatives] = useState([]);
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], responsables: [], priorites: [] });
  const [vue, setVue] = useState('table'); // 'table' | 'kanban'
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/responsables'),
      api.get('/referentiels/priorites')
    ])
      .then(([s, e, r, p]) =>
        setReferentiels({ statuts: s.data, entites: e.data, responsables: r.data, priorites: p.data }))
      .catch((err) => setErreur(messageErreur(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Toute modification de l'URL (appliquer, réinitialiser, retour du
  // navigateur, lien partagé) recharge la liste avec les filtres correspondants.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    chargerListe(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.statut) params.set('statut', f.statut);
    if (f.entite) params.set('entite', f.entite);
    if (f.responsable) params.set('responsable', f.responsable);
    if (f.priorite) params.set('priorite', f.priorite);
    // Période « du … au … » : initiatives actives sur l'intervalle.
    if (f.dateDu) params.set('dateDu', f.dateDu);
    if (f.dateAu) params.set('dateAu', f.dateAu);
    if (f.voirArchive) params.set('archive', 'true');
    return params;
  };

  const chargerListe = async (f = filtres) => {
    setChargement(true);
    setErreur('');
    try {
      const query = construireParams(f).toString();
      const res = await api.get(`/initiatives${query ? `?${query}` : ''}`);
      setInitiatives(res.data);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  /**
   * Après un glisser-déposer sur le Kanban : la carte a changé de colonne,
   * donc de statut. On recharge la liste — et non la seule carte — car le
   * déplacement peut aussi la faire sortir des filtres actifs (glisser dans
   * « Annulée » alors que seules les initiatives « En cours » sont affichées).
   */
  const apresDeplacement = async () => {
    await chargerListe();
    setInfo({ cle: 'initiatives.deplacementEffectue' });
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // L'effet déclenché par le changement d'URL recharge la liste.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres(vide);
    setSearchParams({}, { replace: true });
  };

  const archiver = async (initiative) => {
    if (!window.confirm(t('initiatives.confirmArchiver', { nom: initiative.nom }))) {
      return;
    }
    try {
      await api.patch(`/initiatives/${initiative.idInitiative}/archive`);
      setInfo({ cle: 'initiatives.initiativeArchivee', params: { nom: initiative.nom } });
      chargerListe();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const peutArchiver = (i) =>
    hasPermission('INITIATIVE_ARCHIVER') &&
    !i.archive &&
    ['Terminée', 'Annulée'].includes(i.statut?.libelle);

  const champ = (champs) =>
    champs.map((c) => ({
      ...c,
      value: filtres[c.name],
      onChange: (e) => setFiltres((f) => ({ ...f, [c.name]: e.target.value }))
    }));

  const selects = [
    { name: 'statut', aria: t('initiatives.ariaStatut'), options: referentiels.statuts.map((s) => ({ value: s.idStatut, label: ref.statut(s.libelle) })), vide: t('initiatives.filtreTousStatuts') },
    { name: 'entite', aria: t('initiatives.ariaEntite'), options: referentiels.entites.map((e) => ({ value: e.idEntite, label: e.nom })), vide: t('initiatives.filtreToutesEntites') },
    { name: 'responsable', aria: t('initiatives.ariaResponsable'), options: referentiels.responsables.map((r) => ({ value: r.idUser, label: r.nomComplet })), vide: t('initiatives.filtreTousResponsables') },
    { name: 'priorite', aria: t('initiatives.ariaPriorite'), options: referentiels.priorites.map((p) => ({ value: p.idPriorite, label: ref.priorite(p.libelle) })), vide: t('initiatives.filtreToutesPriorites') }
  ].map((s) => champ([s])[0]);

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('initiatives.titre')}</h2>
          <p>{t('initiatives.sousTitre')}</p>
        </div>
        {hasPermission('INITIATIVE_CREER') && (
          <Link to="/initiatives/nouvelle" className="btn btn-primaire">{t('initiatives.nouvelle')}</Link>
        )}
      </header>

      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('initiatives.recherchePlaceholder')}
            value={filtres.recherche}
            onChange={(e) => setFiltres((f) => ({ ...f, recherche: e.target.value }))}
          />
          {selects.map((sel) => (
            <select key={sel.name} aria-label={sel.aria} value={sel.value} onChange={sel.onChange}>
              <option value="">{sel.vide}</option>
              {sel.options.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          ))}
        </div>
        <div className="filtre-ligne">
          <span className="filtre-periode-libelle">{t('initiatives.periodeTitre')}</span>
          <input type="date" aria-label={t('initiatives.periodeDu')} title={t('initiatives.periodeDu')}
            value={filtres.dateDu} onChange={(e) => setFiltres((f) => ({ ...f, dateDu: e.target.value }))} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('initiatives.periodeAu')} title={t('initiatives.periodeAu')}
            value={filtres.dateAu} onChange={(e) => setFiltres((f) => ({ ...f, dateAu: e.target.value }))} />
          <small className="texte-muet filtre-aide">{t('initiatives.periodeAide')}</small>
          <label className="case">
            <input type="checkbox" checked={filtres.voirArchive}
              onChange={(e) => setFiltres((f) => ({ ...f, voirArchive: e.target.checked }))} />
            {t('initiatives.voirArchive')}
          </label>
          <button type="submit" className="btn btn-primaire" disabled={chargement}>{t('initiatives.appliquer')}</button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>{t('initiatives.reinitialiser')}</button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <div className="vue-toggle">
            <button type="button" className={vue === 'table' ? 'actif' : ''} onClick={() => setVue('table')}>{t('initiatives.vueTableau')}</button>
            <button type="button" className={vue === 'kanban' ? 'actif' : ''} onClick={() => setVue('kanban')}>{t('initiatives.vueKanban')}</button>
          </div>
          <span className="texte-muet">
            {t(initiatives.length > 1 ? 'initiatives.nombrePluriel' : 'initiatives.nombreSingulier', { n: initiatives.length })}
          </span>
        </div>
        {chargement ? (
          <div className="center-page">{t('initiatives.chargement')}</div>
        ) : vue === 'kanban' ? (
          <KanbanBoard
            initiatives={initiatives}
            statuts={referentiels.statuts}
            onDeplacee={apresDeplacement}
          />
        ) : initiatives.length === 0 ? (
          <p className="texte-muet">{t('initiatives.aucunResultat')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('initiatives.colCode')}</th>
                  <th>{t('initiatives.colInitiative')}</th>
                  <th>{t('initiatives.colEntite')}</th>
                  <th>{t('initiatives.colResponsable')}</th>
                  <th>{t('initiatives.colStatut')}</th>
                  <th>{t('sante.colonne')}</th>
                  <th>{t('initiatives.colPriorite')}</th>
                  <th>{t('initiatives.colAvancement')}</th>
                  <th>{t('initiatives.colDebut')}</th>
                  <th>{t('initiatives.colFinPrevue')}</th>
                  <th title={t('initiatives.budgetAide')}>{t('initiatives.colBudget')}</th>
                  <th>{t('initiatives.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {initiatives.map((i) => (
                  <tr key={i.idInitiative} className={i.enRetard ? 'ligne-retard' : ''} title={i.enRetard ? t('initiatives.enRetard') : ''}>
                    <td className="texte-muet">{i.code}</td>
                    <td><Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link></td>
                    <td>{i.entite?.nom ?? '—'}</td>
                    <td>{i.responsable?.nomComplet ?? '—'}</td>
                    <td><StatutBadge libelle={i.statut?.libelle} /></td>
                    <td><SanteBadge sante={i.sante} avecRaison /></td>
                    <td>{ref.priorite(i.priorite?.libelle) ?? '—'}</td>
                    <td><ProgressBar valeur={i.avancement} /></td>
                    <td>{i.dateDebut ?? '—'}</td>
                    <td>{i.dateFinPrevue ?? '—'}</td>
                    <td>{i.budget?.toLocaleString('fr-FR')} MAD</td>
                    <td>
                      <div className="actions">
                        <Link to={`/initiatives/${i.idInitiative}`} className="btn btn-petit">{t('initiatives.voir')}</Link>
                        {hasPermission('INITIATIVE_MODIFIER') && !i.archive && (
                          <Link to={`/initiatives/${i.idInitiative}/modifier`} className="btn btn-petit btn-secondaire">{t('initiatives.modifier')}</Link>
                        )}
                        {peutArchiver(i) && (
                          <button className="btn btn-petit btn-danger" onClick={() => archiver(i)}>{t('initiatives.archiver')}</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
      </div>
    </div>
  );
}
