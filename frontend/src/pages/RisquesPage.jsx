import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import SanteBadge from '../components/SanteBadge';

/** Taille de page fixe : un registre se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

/** Statuts d'un risque — doit suivre Risque.STATUT_* côté backend. */
const STATUTS = ['ouverte', 'attenuation', 'close'];

const vide = { recherche: '', statut: '', initiative: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  statut: STATUTS.includes(params.get('statut')) ? params.get('statut') : '',
  initiative: params.get('initiative') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleDateString(langue, { day: '2-digit', month: 'short', year: 'numeric' });

/**
 * Registre global des risques : toutes les initiatives, lecture ouverte
 * (INITIATIVE_CONSULTER), suppression réservée à RISQUE_GERER (19).
 *
 * Filtres recherche / statut / initiative, tri du plus critique au moins
 * critique (côté serveur), pagination et export CSV complet. L'URL porte
 * les critères : la vue filtrée se partage, c'est ce que fait le lien
 * « Risques » depuis une fiche initiative.
 */
export default function RisquesPage() {
  const { t, langue } = useI18n();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [initiatives, setInitiatives] = useState([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  const peutGerer = Boolean(user?.permissions?.includes('RISQUE_GERER'));

  // Liste déroulante des initiatives (filtre) : chargée une fois.
  useEffect(() => {
    api.get('/initiatives?size=200')
      .then((res) => setInitiatives(res.data ?? []))
      .catch((err) => setErreur(messageErreur(err)));
  }, []);

  // Toute modification de l'URL (appliquer, suppression, pagination, lien
  // partagé, retour du navigateur) recharge le registre.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.statut) params.set('statut', f.statut);
    if (f.initiative) params.set('initiative', f.initiative);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const charger = async (f) => {
    setChargement(true);
    setErreur('');
    try {
      const params = construireParams(f, { page: f.page });
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/risques?${params.toString()}`);
      setLignes(res.data ?? []);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? (res.data?.length ?? 0) : compte);
    } catch (err) {
      setErreur(messageErreur(err));
      setLignes([]);
    } finally {
      setChargement(false);
    }
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // Une nouvelle recherche repart à la première page.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres({ ...vide, page: 0 });
    setSearchParams({}, { replace: true });
  };

  const allerPage = (page) => {
    setSearchParams(construireParams(filtres, { page }), { replace: true });
  };

  const exporterCsv = async () => {
    setErreur('');
    try {
      await telechargerFichier(
        `/risques/export?${construireParams(filtres).toString()}`,
        'risques.csv'
      );
    } catch (err) {
      setErreur(await messageErreurBlob(err));
    }
  };

  const supprimer = async (risque) => {
    if (!window.confirm(t('risques.confirmSupprimer', { titre: risque.titre }))) return;
    setErreur('');
    try {
      await api.delete(`/risques/${risque.idRisque}`);
      charger(filtres);
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(filtres.recherche.trim() || filtres.statut || filtres.initiative);

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('risques.titre')}</h2>
          <p>{t('risques.sousTitre')}</p>
        </div>
        <div className="actions">
          <button
            type="button"
            className="btn btn-secondaire"
            onClick={exporterCsv}
            disabled={lignes.length === 0}
            title={t('risques.exportAide')}
          >
            {t('risques.exportCsv')}
          </button>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('risques.recherche')}
            aria-label={t('risques.recherche')}
            value={filtres.recherche}
            onChange={champ('recherche')}
          />
          <select aria-label={t('risques.colStatut')} value={filtres.statut} onChange={champ('statut')}>
            <option value="">{t('risques.tousStatuts')}</option>
            {STATUTS.map((s) => (
              <option key={s} value={s}>{t(`suivi.risques.statut.${s}`)}</option>
            ))}
          </select>
          <select aria-label={t('risques.colInitiative')} value={filtres.initiative} onChange={champ('initiative')}>
            <option value="">{t('risques.toutesInitiatives')}</option>
            {initiatives.map((i) => (
              <option key={i.idInitiative} value={i.idInitiative}>{i.code}</option>
            ))}
          </select>
          <button type="submit" className="btn btn-primaire" disabled={chargement}>
            {t('risques.appliquer')}
          </button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>
            {t('risques.reinitialiser')}
          </button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <span className="texte-muet">
            {total > 0 ? t('risques.compteur', { de: depart, a: arrivee, total }) : ''}
            {filtresActifs && (
              <>
                {' · '}
                <button type="button" className="lien-bouton" onClick={reinitialiser}>
                  {t('risques.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="center-page">{t('common.chargement')}</div>
        ) : lignes.length === 0 ? (
          <p className="texte-muet">{t('risques.aucun')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('risques.colTitre')}</th>
                  <th>{t('risques.colInitiative')}</th>
                  <th>{t('risques.colScore')}</th>
                  <th>{t('risques.colStatut')}</th>
                  <th>{t('risques.colEcheance')}</th>
                  <th>{t('risques.colResponsable')}</th>
                  {peutGerer && <th aria-label={t('risques.supprimer')} />}
                </tr>
              </thead>
              <tbody>
                {lignes.map((r) => (
                  <tr key={r.idRisque}>
                    <td>
                      <strong>{r.titre}</strong>
                      {r.description && (
                        <div className="texte-muet">{r.description}</div>
                      )}
                    </td>
                    <td>
                      {r.idInitiative ? (
                        <Link to={`/initiatives/${r.idInitiative}`} className="table-lien-code">
                          {r.codeInitiative}
                        </Link>
                      ) : (
                        <span className="texte-muet">—</span>
                      )}
                    </td>
                    <td>
                      <SanteBadge sante={{ niveau: r.niveau }} />
                      <span className="texte-muet"> {r.score}/25</span>
                    </td>
                    <td>
                      <span className={`badge badge-${r.statut === 'close' ? 'gris' : r.statut === 'attenuation' ? 'ambre' : 'bleu'}`}>
                        {t(`suivi.risques.statut.${r.statut}`)}
                      </span>
                    </td>
                    <td>{r.echeance ? formaterDate(r.echeance, langue) : <span className="texte-muet">—</span>}</td>
                    <td title={r.responsable?.email ?? ''}>{r.responsable?.nomComplet ?? <span className="texte-muet">—</span>}</td>
                    {peutGerer && (
                      <td>
                        <button
                          type="button"
                          className="btn btn-petit btn-danger"
                          onClick={() => supprimer(r)}
                          aria-label={`${t('risques.supprimer')} : ${r.titre}`}
                        >
                          ×
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {total > TAILLE_PAGE && (
          <nav className="pagination" aria-label={t('risques.titre')}>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('risques.pagePrecedente')}
            </button>
            <span className="texte-muet">
              {t('risques.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('risques.pageSuivante')}
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}
