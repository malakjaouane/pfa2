import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import StatutBadge from '../components/StatutBadge';

/** Taille de page fixe : un tableau de lecture se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

const vide = { recherche: '', statut: '', utilisateur: '', du: '', au: '' };

/** Lit l'ensemble des critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => {
  const page = parseInt(params.get('page') ?? '0', 10);
  return {
    recherche: params.get('recherche') ?? '',
    statut: params.get('statut') ?? '',
    utilisateur: params.get('utilisateur') ?? '',
    du: params.get('du') ?? '',
    au: params.get('au') ?? '',
    initiative: params.get('initiative') ?? '',
    direction: params.get('direction') === 'asc' ? 'asc' : 'desc',
    page: Number.isNaN(page) || page < 0 ? 0 : page
  };
};

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/**
 * Normalisation insensible à la casse et aux accents : « terminee » trouve
 * « Terminée ». Sert au surlignage des cellules de résultat.
 */
const normaliser = (texte) =>
  (texte ?? '').toString().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/** Surligne les occurrences de la requête dans un texte d'origine. */
function Surligner({ texte, requete }) {
  const source = texte ?? '';
  const aiguille = normaliser((requete ?? '').trim());
  if (!source || !aiguille) return <>{source}</>;

  // NFD peut allonger la chaîne : on garde la table index normalisé -> index
  // d'origine pour découper sur les bords exacts du texte affiché.
  const base = [];
  const carte = [];
  for (let i = 0; i < source.length; i += 1) {
    const morceau = source[i].normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    for (const c of morceau) {
      base.push(c);
      carte.push(i);
    }
  }
  const normalisee = base.join('');
  const morceaux = [];
  let curseur = 0;
  let pos = normalisee.indexOf(aiguille);
  while (pos !== -1) {
    const debut = carte[pos];
    const fin = carte[pos + aiguille.length - 1] + 1;
    if (debut > curseur) morceaux.push(source.slice(curseur, debut));
    morceaux.push(<mark key={`m${debut}`}>{source.slice(debut, fin)}</mark>);
    curseur = fin;
    pos = normalisee.indexOf(aiguille, pos + aiguille.length);
  }
  if (curseur < source.length) morceaux.push(source.slice(curseur));
  return <>{morceaux}</>;
}

/**
 * Page « Historique des statuts » : journal global de toutes les
 * initiatives, en lecture seule (aucun bouton d'édition ni de suppression —
 * les entrées ne sont modifiables nulle part depuis l'interface).
 *
 * Recherche (code ou nom), filtres statut / utilisateur / période, tri,
 * pagination et export CSV du résultat affiché. L'URL porte les critères :
 * la vue filtrée se partage, c'est ce que fait le bouton
 * « Voir tout l'historique » de la fiche initiative.
 */
export default function HistoriquePage() {
  const { t, langue } = useI18n();
  const ref = useReferentiels();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [statuts, setStatuts] = useState([]);
  const [auteurs, setAuteurs] = useState([]);
  const [initiativeFiltree, setInitiativeFiltree] = useState(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  // Référentiel des statuts + auteurs présents dans le journal (une fois).
  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/historique-statuts/auteurs')
    ])
      .then(([s, a]) => {
        setStatuts(s.data);
        setAuteurs(a.data);
      })
      .catch((err) => setErreur(messageErreur(err)));
  }, []);

  // Toute modification de l'URL (appliquer, tri, pagination, lien partagé,
  // retour du navigateur) recharge la liste avec les critères correspondants.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);

    // Le pastille « filtré sur cette initiative » montre le code et le nom.
    if (depuisUrl.initiative) {
      api.get(`/initiatives/${depuisUrl.initiative}`)
        .then((res) => setInitiativeFiltree({ id: depuisUrl.initiative, code: res.data.code, nom: res.data.nom }))
        .catch(() => setInitiativeFiltree({ id: depuisUrl.initiative, code: null, nom: null }));
    } else {
      setInitiativeFiltree(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const charger = async (f) => {
    setChargement(true);
    setErreur('');
    try {
      const params = new URLSearchParams();
      if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
      if (f.statut) params.set('statut', f.statut);
      if (f.utilisateur) params.set('utilisateur', f.utilisateur);
      if (f.du) params.set('du', f.du);
      if (f.au) params.set('au', f.au);
      if (f.initiative) params.set('initiative', f.initiative);
      if (f.direction === 'asc') params.set('direction', 'asc');
      params.set('page', String(f.page));
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/historique-statuts?${params.toString()}`);
      setLignes(res.data);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? res.data.length : compte);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.statut) params.set('statut', f.statut);
    if (f.utilisateur) params.set('utilisateur', f.utilisateur);
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    if (f.initiative) params.set('initiative', f.initiative);
    if (f.direction === 'asc') params.set('direction', 'asc');
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // Une nouvelle recherche repart à la première page.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres({ ...vide, initiative: '', direction: 'desc', page: 0 });
    setSearchParams({}, { replace: true });
  };

  const changerTri = (direction) => {
    setSearchParams(construireParams({ ...filtres, direction }, { page: 0 }), { replace: true });
  };

  const allerPage = (page) => {
    setSearchParams(construireParams(filtres, { page }), { replace: true });
  };

  const retirerFiltreInitiative = () => {
    setSearchParams(construireParams({ ...filtres, initiative: '' }, { page: 0 }), { replace: true });
  };

  /** Export du résultat AFFICHÉ : UTF-8 avec BOM et « ; » (Excel, accents, arabe). */
  const exporterCsv = () => {
    const guillemeter = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const entetes = [
      t('historique.colCode'), t('historique.colInitiative'), t('historique.colAncienStatut'),
      t('historique.colNouveauStatut'), t('historique.colUtilisateur'), t('historique.colDate'),
      t('historique.colOrigine')
    ];
    const lignesCsv = lignes.map((ev) => [
      ev.initiative?.code ?? '',
      ev.initiative?.nom ?? '',
      ev.ancienStatut ?? '',
      ev.nouveauStatut ?? '',
      ev.utilisateur?.nomComplet ?? t('initiativeDetail.auteurInconnu'),
      formaterDate(ev.dateChangement, langue),
      ev.automatique ? t('historique.origineAutomatique') : t('historique.origineManuelle')
    ].map(guillemeter).join(';'));
    const csv = `\uFEFF${[entetes.map(guillemeter).join(';'), ...lignesCsv].join('\r\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = 'historique-statuts.csv';
    document.body.appendChild(lien);
    lien.click();
    lien.remove();
    URL.revokeObjectURL(url);
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.statut || filtres.utilisateur
    || filtres.du || filtres.au || filtres.initiative
  );

  const champRecherche = (e) => setFiltres((f) => ({ ...f, recherche: e.target.value }));
  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('historique.titre')}</h2>
          <p>{t('historique.sousTitre')}</p>
        </div>
        <button
          type="button"
          className="btn btn-secondaire"
          onClick={exporterCsv}
          disabled={lignes.length === 0}
        >
          {t('historique.exportCsv')}
        </button>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('historique.recherchePlaceholder')}
            aria-label={t('historique.recherchePlaceholder')}
            value={filtres.recherche}
            onChange={champRecherche}
          />
          <select aria-label={t('historique.ariaStatut')} value={filtres.statut} onChange={champ('statut')}>
            <option value="">{t('historique.tousStatuts')}</option>
            {statuts.map((s) => (
              <option key={s.idStatut} value={s.libelle}>{ref.statut(s.libelle)}</option>
            ))}
          </select>
          <select aria-label={t('historique.ariaUtilisateur')} value={filtres.utilisateur} onChange={champ('utilisateur')}>
            <option value="">{t('historique.tousUtilisateurs')}</option>
            {auteurs.map((a) => (
              <option key={a.idUser} value={a.idUser}>{a.nomComplet}</option>
            ))}
          </select>
        </div>
        <div className="filtre-ligne">
          <span className="filtre-periode-libelle">{t('historique.periodeTitre')}</span>
          <input type="date" aria-label={t('historique.periodeDu')} title={t('historique.periodeDu')}
            value={filtres.du} onChange={champ('du')} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('historique.periodeAu')} title={t('historique.periodeAu')}
            value={filtres.au} onChange={champ('au')} />
          <small className="texte-muet filtre-aide">{t('historique.periodeAide')}</small>
          <button type="submit" className="btn btn-primaire" disabled={chargement}>{t('historique.appliquer')}</button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>{t('historique.reinitialiser')}</button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <div className="vue-toggle" role="group" aria-label={t('historique.triAria')}>
            <button
              type="button"
              className={filtres.direction === 'desc' ? 'actif' : ''}
              onClick={() => changerTri('desc')}
            >
              {t('historique.triRecent')}
            </button>
            <button
              type="button"
              className={filtres.direction === 'asc' ? 'actif' : ''}
              onClick={() => changerTri('asc')}
            >
              {t('historique.triAncien')}
            </button>
          </div>
          <span className="texte-muet">
            {t('historique.compteur', { de: depart, a: arrivee, total })}
            {filtresActifs && (
              <>
                {' · '}
                <button type="button" className="lien-bouton" onClick={reinitialiser}>
                  {t('historique.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {initiativeFiltree && (
          <p className="filtre-pastille">
            {initiativeFiltree.code ? (
              <>
                <Link to={`/initiatives/${initiativeFiltree.id}`}>
                  {t('historique.pastilleInitiative', { code: initiativeFiltree.code, nom: initiativeFiltree.nom })}
                </Link>
                <button
                  type="button"
                  className="pastille-fermer"
                  onClick={retirerFiltreInitiative}
                  aria-label={t('historique.pastilleRetirer')}
                >
                  ×
                </button>
              </>
            ) : (
              <>
                <span>{t('historique.pastilleSansNom', { id: initiativeFiltree.id })}</span>
                <button
                  type="button"
                  className="pastille-fermer"
                  onClick={retirerFiltreInitiative}
                  aria-label={t('historique.pastilleRetirer')}
                >
                  ×
                </button>
              </>
            )}
          </p>
        )}

        {chargement ? (
          <div className="center-page">{t('historique.chargement')}</div>
        ) : lignes.length === 0 ? (
          <p className="texte-muet">{t('historique.aucunResultat')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('historique.colCode')}</th>
                  <th>{t('historique.colInitiative')}</th>
                  <th>{t('historique.colAncienStatut')}</th>
                  <th>{t('historique.colNouveauStatut')}</th>
                  <th>{t('historique.colUtilisateur')}</th>
                  <th>{t('historique.colDate')}</th>
                  <th>{t('historique.colOrigine')}</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((ev) => (
                  <tr key={ev.idHistorique}>
                    <td>
                      <Link
                        to={`/initiatives/${ev.initiative?.idInitiative}`}
                        className="table-lien-code"
                      >
                        <Surligner texte={ev.initiative?.code} requete={filtres.recherche} />
                      </Link>
                    </td>
                    <td><Surligner texte={ev.initiative?.nom} requete={filtres.recherche} /></td>
                    <td>
                      {ev.ancienStatut
                        ? <StatutBadge libelle={ev.ancienStatut} />
                        : <span className="texte-muet">—</span>}
                    </td>
                    <td><StatutBadge libelle={ev.nouveauStatut} /></td>
                    <td title={ev.utilisateur?.email ?? ''}>
                      {ev.utilisateur?.nomComplet ?? (
                        <span className="texte-muet">{t('initiativeDetail.auteurInconnu')}</span>
                      )}
                    </td>
                    <td>{formaterDate(ev.dateChangement, langue)}</td>
                    <td>
                      <span className={`badge ${ev.automatique ? 'badge-bleu' : 'badge-gris'}`}>
                        {ev.automatique ? t('historique.origineAutomatique') : t('historique.origineManuelle')}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {total > TAILLE_PAGE && (
          <nav className="pagination" aria-label={t('historique.titre')}>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('historique.pagePrecedente')}
            </button>
            <span className="texte-muet">{t('historique.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}</span>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('historique.pageSuivante')}
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}
