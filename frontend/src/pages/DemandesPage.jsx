import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';

const STYLES_DEMANDE = {
  'Brouillon': 'badge-gris',
  'Soumise': 'badge-bleu',
  'En évaluation': 'badge-ambre',
  'Acceptée': 'badge-vert',
  'Refusée': 'badge-rouge'
};

/**
 * Les statuts de demande proviennent de la base en français : on les associe
 * à des clés de traduction pour les afficher dans la langue choisie.
 * Les chaînes de comparaison (STYLES_DEMANDE, conditions de workflow) restent
 * en français car ce sont des valeurs métier renvoyées par l'API.
 */
const CLES_STATUT_DEMANDE = {
  'Brouillon': 'demandes.statutBrouillon',
  'Soumise': 'demandes.statutSoumise',
  'En évaluation': 'demandes.statutEnEvaluation',
  'Acceptée': 'demandes.statutAcceptee',
  'Refusée': 'demandes.statutRefusee'
};

const formVide = { titre: '', descriptionBesoin: '', justificationMetier: '', idEntite: '', idPriorite: '' };

/**
 * Module « Demandes d'initiatives » (workflow SPM) :
 * workflow Brouillon -> Soumise -> En évaluation -> Acceptée / Refusée,
 * et transformation d'une demande acceptée en initiative du portefeuille.
 */
export default function DemandesPage() {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const ref = useReferentiels();
  const peutSoumettre = hasPermission('DEMANDE_SOUMETTRE');
  const peutEvaluer = hasPermission('DEMANDE_EVALUER');

  const [demandes, setDemandes] = useState([]);
  const [entites, setEntites] = useState([]);
  const [priorites, setPriorites] = useState([]);
  const [recherche, setRecherche] = useState('');
  const [rechercheAppliquee, setRechercheAppliquee] = useState('');
  const [form, setForm] = useState(formVide);
  const [idEdition, setIdEdition] = useState(null);
  const [formVisible, setFormVisible] = useState(false);
  const [refusPour, setRefusPour] = useState(null);
  const [motifRefus, setMotifRefus] = useState('');
  const [chargement, setChargement] = useState(true);
  const [soumission, setSoumission] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  const libelleStatut = (libelle) =>
    libelle && CLES_STATUT_DEMANDE[libelle] ? t(CLES_STATUT_DEMANDE[libelle]) : libelle;

  /**
   * Rechargement : demandes (avec la recherche libre éventuelle), entités
   * et priorités. `motCle` permet de forcer le mot-clé au moment de la
   * soumission du formulaire — l'état React n'est pas encore mis à jour.
   */
  const charger = (motCle) => {
    const q = (motCle ?? rechercheAppliquee).trim();
    Promise.all([
      api.get('/demandes', q ? { params: { recherche: q } } : {}),
      api.get('/referentiels/entites'),
      api.get('/referentiels/priorites')
    ])
      .then(([d, en, pr]) => {
        setDemandes(d.data);
        setEntites(en.data);
        setPriorites(pr.data);
      })
      .catch((err) => setErreur(messageErreur(err)))
      .finally(() => setChargement(false));
  };

  const appliquerRecherche = (e) => {
    e.preventDefault();
    setRechercheAppliquee(recherche);
    setChargement(true);
    charger(recherche);
  };

  const effacerRecherche = () => {
    setRecherche('');
    setRechercheAppliquee('');
    setChargement(true);
    charger('');
  };

  useEffect(() => {
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  const ouvrirNouvelle = () => {
    setForm(formVide);
    setIdEdition(null);
    setFormVisible(true);
  };

  const ouvrirEdition = (d) => {
    setForm({
      titre: d.titre,
      descriptionBesoin: d.descriptionBesoin ?? '',
      justificationMetier: d.justificationMetier ?? '',
      idEntite: d.entite?.idEntite ?? '',
      idPriorite: d.priorite?.idPriorite ?? ''
    });
    setIdEdition(d.idDemande);
    setFormVisible(true);
  };

  const enregistrer = async (e) => {
    e.preventDefault();
    setErreur('');
    setSoumission(true);
    const corps = {
      titre: form.titre,
      descriptionBesoin: form.descriptionBesoin || null,
      justificationMetier: form.justificationMetier || null,
      idEntite: Number(form.idEntite),
      idPriorite: Number(form.idPriorite)
    };
    try {
      if (idEdition) {
        await api.put(`/demandes/${idEdition}`, corps);
        setInfo({ cle: 'demandes.infoModifiee' });
      } else {
        await api.post('/demandes', corps);
        setInfo({ cle: 'demandes.infoCreee' });
      }
      setFormVisible(false);
      setForm(formVide);
      setIdEdition(null);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setSoumission(false);
    }
  };

  const action = async (id, route, message) => {
    try {
      await api.post(`/demandes/${id}/${route}`);
      setInfo(message);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const confirmerRefus = async (id) => {
    try {
      await api.post(`/demandes/${id}/refuser`, { motifRefus: motifRefus || null });
      setInfo({ cle: 'demandes.infoRefusee' });
      setRefusPour(null);
      setMotifRefus('');
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const transformer = async (d) => {
    if (!window.confirm(t('demandes.confirmTransformer', { titre: d.titre }))) return;
    try {
      const res = await api.post(`/demandes/${d.idDemande}/transformer`);
      setInfo({ cle: 'demandes.infoTransformee', params: { code: res.data.initiativeLiee?.code ?? '' } });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  /**
   * Suppression définitive (DEMANDE_EVALUER) : confirmation puis
   * rechargement. Une demande déjà transformée en initiative est masquée
   * côté interface et refusée côté serveur (400).
   */
  const supprimer = async (d) => {
    if (!window.confirm(t('demandes.confirmSupprimer', { titre: d.titre }))) return;
    try {
      await api.delete(`/demandes/${d.idDemande}`);
      setInfo({ cle: 'demandes.infoSupprimee' });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('demandes.titre')}</h2>
          <p>{t('demandes.sousTitre')}</p>
        </div>
        {peutSoumettre && (
          <button className="btn btn-primaire" onClick={() => (formVisible ? setFormVisible(false) : ouvrirNouvelle())}>
            {formVisible ? t('demandes.fermer') : t('demandes.nouvelle')}
          </button>
        )}
      </header>

      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      {formVisible && (
        <form className="carte formulaire" onSubmit={enregistrer}>
          <h3>{idEdition ? t('demandes.formModifierTitre') : t('demandes.formNouvelleTitre')}</h3>
          <div className="form-grille">
            <div className="champ champ-plein">
              <label>{t('demandes.champTitre')} *</label>
              <input value={form.titre} onChange={set('titre')} required placeholder={t('demandes.placeholderTitre')} />
            </div>
            <div className="champ">
              <label htmlFor="dem-entite">{t('demandes.champEntite')} *</label>
              <select id="dem-entite" value={form.idEntite} onChange={set('idEntite')} required>
                <option value="">—</option>
                {entites.map((en) => <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>)}
              </select>
            </div>
            <div className="champ">
              <label htmlFor="dem-priorite">{t('demandes.champPriorite')} *</label>
              <select id="dem-priorite" value={form.idPriorite} onChange={set('idPriorite')} required>
                <option value="">—</option>
                {priorites.map((p) => <option key={p.idPriorite} value={p.idPriorite}>{ref.priorite(p.libelle)}</option>)}
              </select>
            </div>
            <div className="champ champ-plein">
              <label>{t('demandes.champDescription')}</label>
              <textarea rows={3} value={form.descriptionBesoin} onChange={set('descriptionBesoin')} placeholder={t('demandes.placeholderDescription')} />
            </div>
            <div className="champ champ-plein">
              <label>{t('demandes.champJustification')}</label>
              <textarea rows={2} value={form.justificationMetier} onChange={set('justificationMetier')} placeholder={t('demandes.placeholderJustification')} />
            </div>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondaire" onClick={() => setFormVisible(false)}>{t('demandes.annuler')}</button>
            <button type="submit" className="btn btn-primaire" disabled={soumission}>
              {soumission ? t('demandes.enregistrement') : idEdition ? t('demandes.enregistrerModifications') : t('demandes.creer')}
            </button>
          </div>
        </form>
      )}

      <form className="carte filtre-bar" onSubmit={appliquerRecherche}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('demandes.recherchePlaceholder')}
            aria-label={t('demandes.recherchePlaceholder')}
            value={recherche}
            onChange={(e) => setRecherche(e.target.value)}
          />
          <button type="submit" className="btn btn-primaire" disabled={chargement}>
            {t('demandes.appliquer')}
          </button>
          <button
            type="button"
            className="btn btn-secondaire"
            onClick={effacerRecherche}
            disabled={!recherche && !rechercheAppliquee}
          >
            {t('demandes.reinitialiser')}
          </button>
        </div>
      </form>

      <div className="carte">
        <div className="table-enveloppe">
          {chargement ? (
            <div className="center-page">{t('demandes.chargement')}</div>
          ) : demandes.length === 0 ? (
            <p className="texte-muet">{t('demandes.aucune')}</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>{t('demandes.colTitre')}</th>
                  <th>{t('demandes.colDemandeur')}</th>
                  <th>{t('demandes.colEntite')}</th>
                  <th>{t('demandes.colPriorite')}</th>
                  <th>{t('demandes.colSoumission')}</th>
                  <th>{t('demandes.colStatut')}</th>
                  <th>{t('demandes.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {demandes.map((d) => (
                  <tr key={d.idDemande}>
                      <td>
                        <strong>{d.titre}</strong>
                        {d.justificationMetier && <div className="texte-muet"><small>{d.justificationMetier}</small></div>}
                        {d.motifRefus && (
                          <div className="texte-muet"><small>{t('demandes.motifRefus', { motif: d.motifRefus })}</small></div>
                        )}
                        {d.initiativeLiee && (
                          <div>
                            <Link to={`/initiatives/${d.initiativeLiee.idInitiative}`} className="badge badge-vert">
                              {t('demandes.transformee', { code: d.initiativeLiee.code })}
                            </Link>
                          </div>
                        )}
                      </td>
                      <td>{d.demandeur?.nomComplet ?? '—'}</td>
                      <td>{d.entite?.nom ?? '—'}</td>
                      <td>{ref.priorite(d.priorite?.libelle) ?? '—'}</td>
                      <td>{d.dateSoumission ?? '—'}</td>
                      <td><span className={`badge ${STYLES_DEMANDE[d.statut?.libelle] ?? 'badge-gris'}`}>{libelleStatut(d.statut?.libelle)}</span></td>
                      <td>
                        <div className="actions">
                          {d.statut?.libelle === 'Brouillon' && peutSoumettre && (
                            <>
                              <button className="btn btn-petit btn-secondaire" onClick={() => ouvrirEdition(d)}>{t('demandes.modifier')}</button>
                              <button className="btn btn-petit btn-primaire" onClick={() => action(d.idDemande, 'soumettre', { cle: 'demandes.infoSoumise' })}>
                                {t('demandes.soumettre')}
                              </button>
                            </>
                          )}
                          {d.statut?.libelle === 'Soumise' && peutEvaluer && (
                            <button className="btn btn-petit btn-primaire" onClick={() => action(d.idDemande, 'evaluer', { cle: 'demandes.infoEnEvaluation' })}>
                              {t('demandes.evaluer')}
                            </button>
                          )}
                          {d.statut?.libelle === 'En évaluation' && peutEvaluer && (
                            <>
                              <button className="btn btn-petit btn-primaire" onClick={() => action(d.idDemande, 'accepter', { cle: 'demandes.infoAcceptee' })}>
                                {t('demandes.accepter')}
                              </button>
                              <button className="btn btn-petit btn-danger" onClick={() => { setRefusPour(d.idDemande); setMotifRefus(''); }}>
                                {t('demandes.refuser')}
                              </button>
                              {refusPour === d.idDemande && (
                                <div className="formulaire-inline">
                                  <input
                                    value={motifRefus}
                                    onChange={(e) => setMotifRefus(e.target.value)}
                                    placeholder={t('demandes.placeholderMotifRefus')}
                                  />
                                  <button className="btn btn-petit btn-danger" onClick={() => confirmerRefus(d.idDemande)}>{t('demandes.confirmer')}</button>
                                  <button className="btn btn-petit btn-secondaire" onClick={() => setRefusPour(null)}>{t('demandes.annuler')}</button>
                                </div>
                              )}
                            </>
                          )}
                          {d.statut?.libelle === 'Acceptée' && peutEvaluer && !d.initiativeLiee && (
                            <button className="btn btn-petit btn-primaire" onClick={() => transformer(d)}>
                              {t('demandes.transformer')}
                            </button>
                          )}
                          {d.statut?.libelle === 'Acceptée' && d.initiativeLiee && (
                            <Link to={`/initiatives/${d.initiativeLiee.idInitiative}`} className="btn btn-petit btn-secondaire">{t('demandes.voirInitiative')}</Link>
                          )}
                          {peutEvaluer && !d.initiativeLiee && (
                            <button className="btn btn-petit btn-danger" onClick={() => supprimer(d)}>
                              {t('demandes.supprimer')}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
