import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import ListeAlertes from './ListeAlertes';
import SanteBadge from './SanteBadge';

/** Formulaires vides (statut « ouverte » par défaut, échéance facultative). */
const risqueVide = {
  titre: '', description: '', probabilite: '3', impact: '3',
  statut: 'ouverte', echeance: '', planAction: '', idResponsable: ''
};
const depenseVide = { libelle: '', montant: '', dateDepense: '', categorie: '' };

/** Notes 1-5 du registre (étiquettes d'options). */
const NOTES = [1, 2, 3, 4, 5];

const formaterDateHeure = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

const formaterMontant = (valeur, langue) =>
  new Intl.NumberFormat(langue, { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
    .format(Number(valeur ?? 0));

/**
 * Blocs de suivi d'une initiative : alertes calculées, fil de commentaires
 * immuable, registre des risques (score p × i, 1-25) et dépenses.
 *
 * Composant autonome : chacun de ses quatre blocs porte ses propres
 * permissions (COMMENTAIRE_AJOUTER 18, RISQUE_GERER 19, DEPENSE_SAISIR 20,
 * suppression d'un commentaire par son auteur ou UTILISATEUR_GERER) et se
 * recharge après chaque écriture. Une initiative archivée reste visible en
 * lecture : les formulaires disparaissent, le serveur refuserait de toute
 * façon toute écriture.
 *
 * @param {number} idInitiative  initiative servie
 * @param {boolean} archivee     fiche en lecture seule
 */
export default function FicheSuivi({ idInitiative, archivee = false }) {
  const { t, langue } = useI18n();
  const { user, hasPermission } = useAuth();

  const peutConsulter = hasPermission('INITIATIVE_CONSULTER');
  const peutCommenter = hasPermission('COMMENTAIRE_AJOUTER');
  const peutGererRisques = hasPermission('RISQUE_GERER');
  const peutSaisirDepenses = hasPermission('DEPENSE_SAISIR');
  const peutSupprimerCommentaire = hasPermission('UTILISATEUR_GERER');

  const [alertes, setAlertes] = useState([]);
  const [commentaires, setCommentaires] = useState([]);
  const [risques, setRisques] = useState([]);
  const [depenses, setDepenses] = useState(null); // null = pas le droit (DEPENSE_SAISIR)
  const [responsables, setResponsables] = useState([]);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  const [nouveauCommentaire, setNouveauCommentaire] = useState('');
  const [nouveauRisque, setNouveauRisque] = useState(risqueVide);
  const [editionRisque, setEditionRisque] = useState(null); // { idRisque, ... }
  const [nouvelleDepense, setNouvelleDepense] = useState(depenseVide);

  const charger = useCallback(() => {
    // Les quatre lectures suivent les permissions de la fiche elle-même ;
    // les dépenses, elles, exigent DEPENSE_SAISIR — un visiteur ne doit
    // pas recevoir un 403 à chaque affichage, on n'appelle pas du tout.
    api.get(`/initiatives/${idInitiative}/alertes`).then((r) => setAlertes(r.data ?? [])).catch(() => {});
    api.get(`/initiatives/${idInitiative}/commentaires`).then((r) => setCommentaires(r.data ?? [])).catch(() => {});
    api.get(`/initiatives/${idInitiative}/risques`).then((r) => setRisques(r.data ?? [])).catch(() => {});
    if (peutSaisirDepenses) {
      api.get(`/initiatives/${idInitiative}/depenses`).then((r) => setDepenses(r.data ?? [])).catch(() => {});
    }
  }, [idInitiative, peutSaisirDepenses]);

  useEffect(() => {
    charger();
    if (peutGererRisques && !archivee) {
      api.get('/referentiels/responsables').then((r) => setResponsables(r.data ?? [])).catch(() => {});
    }
  }, [charger, peutGererRisques, archivee]);

  /** Affiche brièvement un message de succès après une écriture. */
  const signaler = (cle) => {
    setErreur('');
    setInfo(cle);
    charger();
  };

  // ---------------- Commentaires ----------------

  const publierCommentaire = async (e) => {
    e.preventDefault();
    const contenu = nouveauCommentaire.trim();
    if (!contenu) return;
    setInfo('');
    try {
      await api.post(`/initiatives/${idInitiative}/commentaires`, { contenu });
      setNouveauCommentaire('');
      signaler('suivi.commentaires.ajoute');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const supprimerCommentaire = async (c) => {
    if (!window.confirm(t('suivi.commentaires.confirmSupprimer'))) return;
    setInfo('');
    try {
      await api.delete(`/commentaires/${c.idCommentaire}`);
      signaler('suivi.commentaires.supprime');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  // ---------------- Risques ----------------

  const corpsRisque = (form) => ({
    titre: form.titre,
    description: form.description,
    probabilite: Number(form.probabilite),
    impact: Number(form.impact),
    statut: form.statut || 'ouverte',
    echeance: form.echeance || null,
    planAction: form.planAction || null,
    idResponsable: form.idResponsable ? Number(form.idResponsable) : null
  });

  const soumettreRisque = async (e) => {
    e.preventDefault();
    setInfo('');
    try {
      if (editionRisque) {
        await api.put(`/risques/${editionRisque.idRisque}`, corpsRisque(editionRisque));
      } else {
        await api.post(`/initiatives/${idInitiative}/risques`, corpsRisque(nouveauRisque));
        setNouveauRisque(risqueVide);
      }
      setEditionRisque(null);
      signaler('suivi.risques.ajoute');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const supprimerRisque = async (risque) => {
    if (!window.confirm(t('risques.confirmSupprimer', { titre: risque.titre }))) return;
    setInfo('');
    try {
      await api.delete(`/risques/${risque.idRisque}`);
      signaler('risques.supprime');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  /** Champs communs aux formulaires d'ajout et d'édition d'un risque. */
  const champsRisque = (form, setForm) => (
    <>
      <input value={form.titre} required maxLength={150}
        onChange={(e) => setForm({ ...form, titre: e.target.value })}
        placeholder={t('suivi.risques.champTitre')}
        aria-label={t('suivi.risques.champTitre')} />
      <input value={form.description} required
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder={t('suivi.risques.champDescription')}
        aria-label={t('suivi.risques.champDescription')} />
      <select aria-label={t('suivi.risques.probabilite')} value={form.probabilite}
        onChange={(e) => setForm({ ...form, probabilite: e.target.value })}>
        {NOTES.map((n) => <option key={n} value={n}>{t('suivi.risques.probabilite')} : {n}</option>)}
      </select>
      <select aria-label={t('suivi.risques.impact')} value={form.impact}
        onChange={(e) => setForm({ ...form, impact: e.target.value })}>
        {NOTES.map((n) => <option key={n} value={n}>{t('suivi.risques.impact')} : {n}</option>)}
      </select>
      <select aria-label={t('suivi.risques.champStatut')} value={form.statut}
        onChange={(e) => setForm({ ...form, statut: e.target.value })}>
        {['ouverte', 'attenuation', 'close'].map((s) => (
          <option key={s} value={s}>{t(`suivi.risques.statut.${s}`)}</option>
        ))}
      </select>
      <input type="date" aria-label={t('suivi.risques.champEcheance')} title={t('suivi.risques.champEcheance')}
        value={form.echeance}
        onChange={(e) => setForm({ ...form, echeance: e.target.value })} />
      <input value={form.planAction}
        onChange={(e) => setForm({ ...form, planAction: e.target.value })}
        placeholder={t('suivi.risques.champPlan')}
        aria-label={t('suivi.risques.champPlan')} />
      <select aria-label={t('risques.colResponsable')} value={form.idResponsable}
        onChange={(e) => setForm({ ...form, idResponsable: e.target.value })}>
        <option value="">{t('risques.colResponsable')}</option>
        {responsables.map((u) => <option key={u.idUser} value={u.idUser}>{u.nomComplet}</option>)}
      </select>
    </>
  );

  // ---------------- Dépenses ----------------

  const soumettreDepense = async (e) => {
    e.preventDefault();
    setInfo('');
    try {
      await api.post(`/initiatives/${idInitiative}/depenses`, {
        libelle: nouvelleDepense.libelle,
        montant: Number(nouvelleDepense.montant),
        dateDepense: nouvelleDepense.dateDepense,
        categorie: nouvelleDepense.categorie || null
      });
      setNouvelleDepense(depenseVide);
      signaler('suivi.depenses.ajoute');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const totalDepenses = Array.isArray(depenses)
    ? depenses.reduce((somme, d) => somme + Number(d.montant ?? 0), 0)
    : 0;

  const statutRisque = (r) =>
    r.statut === 'close' ? 'gris' : r.statut === 'attenuation' ? 'ambre' : 'bleu';

  if (!peutConsulter) return null;

  return (
    <>
      {info && <div className="alerte alerte-succes">{t(info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      {/* Alertes de l'initiative (calculées, aucune table) : retards,
          jalons proches, risques critiques, budget dépassé. */}
      <section className="carte">
        <div className="section-entete">
          <h3>{t('suivi.blocAlertes')}</h3>
          {alertes.length > 0 && (
            <span className={`badge ${alertes.some((a) => a.niveau === 'ROUGE') ? 'badge-rouge' : 'badge-orange'}`}>
              {alertes.length}
            </span>
          )}
        </div>
        {alertes.length === 0
          ? <p className="texte-muet">{t('suivi.alertesVide')}</p>
          : <ListeAlertes alertes={alertes} avecLiens={false} />}
      </section>

      {/* Fil de commentaires : immuable — on publie, on supprime, on ne
          modifie jamais (la ligne reste dans le journal des accès). */}
      <section className="carte">
        <div className="section-entete">
          <h3>{t('suivi.commentaires.titre')}</h3>
          {commentaires.length > 0 && <span className="badge badge-gris">{commentaires.length}</span>}
        </div>
        <p className="texte-muet">{t('suivi.commentaires.sousTitre')}</p>
        {commentaires.length === 0 && <p className="texte-muet">{t('suivi.commentaires.vide')}</p>}
        <ul className="alerte-liste">
          {commentaires.map((c) => (
            <li key={c.idCommentaire} className="commentaire-item">
              <span className="commentaire-tete">
                <strong>{c.auteur?.nomComplet ?? t('suivi.commentaires.auteurInconnu')}</strong>
                <span className="texte-muet">{formaterDateHeure(c.dateCreation, langue)}</span>
                {(c.auteur?.idUser === user?.idUser || peutSupprimerCommentaire) && (
                  <button
                    type="button"
                    className="btn btn-petit btn-danger"
                    onClick={() => supprimerCommentaire(c)}
                    aria-label={`${t('suivi.commentaires.supprimer')} : ${c.contenu.slice(0, 40)}`}
                  >
                    {t('suivi.commentaires.supprimer')}
                  </button>
                )}
              </span>
              <p className="commentaire-corps">{c.contenu}</p>
            </li>
          ))}
        </ul>
        {peutCommenter && !archivee && (
          <form className="suivi-form suivi-form-commentaire" onSubmit={publierCommentaire}>
            <textarea
              rows={3}
              maxLength={4000}
              value={nouveauCommentaire}
              onChange={(e) => setNouveauCommentaire(e.target.value)}
              placeholder={t('suivi.commentaires.champ')}
              aria-label={t('suivi.commentaires.champ')}
            />
            <div className="suivi-form-actions">
              <button type="submit" className="btn btn-primaire" disabled={!nouveauCommentaire.trim()}>
                {t('suivi.commentaires.publier')}
              </button>
            </div>
          </form>
        )}
      </section>

      {/* Registre des risques : score = probabilité × impact (1-25),
          quatre bandes de santé, statut et échéance. Écriture réservée à
          RISQUE_GERER (19). */}
      <section className="carte">
        <div className="section-entete">
          <h3>{t('suivi.risques.titre')}</h3>
          {risques.length > 0 && (
            <span className={risques.some((r) => r.niveau === 'ROUGE') ? 'badge badge-rouge' : 'badge badge-vert'}>
              {risques.length}
            </span>
          )}
          <Link to={`/risques?initiative=${idInitiative}`} className="btn btn-petit btn-secondaire">
            {t('risques.titre')}
          </Link>
        </div>
        {risques.length === 0 && <p className="texte-muet">{t('suivi.risques.vide')}</p>}
        <div className="etape-liste">
          {risques.map((risque) => (
            <div key={risque.idRisque} className="etape-item">
              {editionRisque?.idRisque === risque.idRisque ? (
                <form className="suivi-form" onSubmit={soumettreRisque}>
                  {champsRisque(editionRisque, setEditionRisque)}
                  <div className="suivi-form-actions">
                    <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ok')}</button>
                    <button type="button" className="btn btn-secondaire btn-petit" onClick={() => setEditionRisque(null)}>
                      {t('suivi.risques.annuler')}
                    </button>
                  </div>
                </form>
              ) : (
                <>
                  <div className="etape-texte">
                    <strong>{risque.titre}</strong>
                    <small>{risque.description}</small>
                    <span>
                      <SanteBadge sante={{ niveau: risque.niveau }} />
                      <span className="badge badge-bleu">
                        {t('monEspace.score')} {risque.score}/25
                      </span>
                      <span className={`badge badge-${statutRisque(risque)}`}>
                        {t(`suivi.risques.statut.${risque.statut}`)}
                      </span>
                      <span className="badge badge-gris">
                        {t('suivi.risques.probabilite')} {risque.probabilite} · {t('suivi.risques.impact')} {risque.impact}
                      </span>
                    </span>
                    {risque.echeance && (
                      <span className="texte-muet">
                        {t('suivi.risques.champEcheance')} : {risque.echeance}
                      </span>
                    )}
                    {risque.planAction && <small>{t('suivi.risques.champPlan')} : {risque.planAction}</small>}
                    <span className="texte-muet">
                      {t('risques.colResponsable')} : {risque.responsable?.nomComplet ?? '—'}
                    </span>
                  </div>
                  {peutGererRisques && !archivee && (
                    <div className="etape-actions">
                      <button
                        type="button"
                        className="btn btn-petit btn-secondaire"
                        onClick={() => setEditionRisque({
                          idRisque: risque.idRisque,
                          titre: risque.titre,
                          description: risque.description,
                          probabilite: String(risque.probabilite),
                          impact: String(risque.impact),
                          statut: risque.statut ?? 'ouverte',
                          echeance: risque.echeance ?? '',
                          planAction: risque.planAction ?? '',
                          idResponsable: risque.responsable?.idUser ?? ''
                        })}
                      >
                        {t('initiativeDetail.editer')}
                      </button>
                      <button type="button" className="btn btn-petit btn-danger" onClick={() => supprimerRisque(risque)}>
                        {t('initiativeDetail.supprimer')}
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
        {peutGererRisques && !archivee && (
          <form className="suivi-form" onSubmit={soumettreRisque}>
            {champsRisque(nouveauRisque, setNouveauRisque)}
            <div className="suivi-form-actions">
              <button type="submit" className="btn btn-primaire">{t('suivi.risques.creer')}</button>
            </div>
          </form>
        )}
      </section>

      {/* Dépenses de l'initiative : information financière — lecture et
          écriture réservées à DEPENSE_SAISIR (20). Le registre complet
          (filtres, totaux, export) vit sur sa propre page. */}
      {peutSaisirDepenses && (
        <section className="carte">
          <div className="section-entete">
            <h3>{t('suivi.depenses.titre')}</h3>
            {Array.isArray(depenses) && depenses.length > 0 && (
              <span className="badge badge-gris">
                {t('suivi.depenses.total', { montant: formaterMontant(totalDepenses, langue) })}
              </span>
            )}
            <Link to={`/depenses?initiative=${idInitiative}`} className="btn btn-petit btn-secondaire">
              {t('depenses.titre')}
            </Link>
          </div>
          {!Array.isArray(depenses) || depenses.length === 0
            ? <p className="texte-muet">{t('suivi.depenses.vide')}</p>
            : (
              <div className="table-enveloppe">
                <table className="table">
                  <thead>
                    <tr>
                      <th>{t('depenses.colDate')}</th>
                      <th>{t('depenses.colLibelle')}</th>
                      <th>{t('depenses.colMontant')}</th>
                      <th>{t('depenses.colCategorie')}</th>
                      <th>{t('depenses.colSaisisseur')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {depenses.map((d) => (
                      <tr key={d.idDepense}>
                        <td>{d.dateDepense}</td>
                        <td>{d.libelle}</td>
                        <td className="depenses-montant">{formaterMontant(d.montant, langue)}</td>
                        <td>{d.categorie ?? '—'}</td>
                        <td title={d.saisisseur?.email ?? ''}>{d.saisisseur?.nomComplet ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          {!archivee && (
            <form className="suivi-form" onSubmit={soumettreDepense}>
              <input value={nouvelleDepense.libelle} required maxLength={150}
                onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, libelle: e.target.value })}
                placeholder={t('suivi.depenses.champLibelle')}
                aria-label={t('suivi.depenses.champLibelle')} />
              <input type="number" min="0.01" step="0.01" required
                value={nouvelleDepense.montant}
                onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, montant: e.target.value })}
                placeholder={t('suivi.depenses.champMontant')}
                aria-label={t('suivi.depenses.champMontant')} />
              <input type="date" required
                value={nouvelleDepense.dateDepense}
                onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, dateDepense: e.target.value })}
                aria-label={t('suivi.depenses.champDate')} />
              <input value={nouvelleDepense.categorie} maxLength={50}
                onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, categorie: e.target.value })}
                placeholder={t('suivi.depenses.champCategorie')}
                aria-label={t('suivi.depenses.champCategorie')} />
              <div className="suivi-form-actions">
                <button type="submit" className="btn btn-primaire">{t('suivi.depenses.creer')}</button>
              </div>
            </form>
          )}
        </section>
      )}
    </>
  );
}