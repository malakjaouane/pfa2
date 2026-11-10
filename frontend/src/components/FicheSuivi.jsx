import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import ListeAlertes from './ListeAlertes';
import SanteBadge from './SanteBadge';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Badge } from '../components/ui/badge';

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

const statutRisque = (r) =>
  r.statut === 'close' ? 'gris' : r.statut === 'attenuation' ? 'ambre' : 'bleu';

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
      <Input
        value={form.titre}
        required
        maxLength={150}
        onChange={(e) => setForm({ ...form, titre: e.target.value })}
        placeholder={t('suivi.risques.champTitre')}
        aria-label={t('suivi.risques.champTitre')}
      />
      <Input
        value={form.description}
        required
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder={t('suivi.risques.champDescription')}
        aria-label={t('suivi.risques.champDescription')}
      />
      <select
        aria-label={t('suivi.risques.probabilite')}
        value={form.probabilite}
        onChange={(e) => setForm({ ...form, probabilite: e.target.value })}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {NOTES.map((n) => <option key={n} value={n}>{t('suivi.risques.probabilite')} : {n}</option>)}
      </select>
      <select
        aria-label={t('suivi.risques.impact')}
        value={form.impact}
        onChange={(e) => setForm({ ...form, impact: e.target.value })}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {NOTES.map((n) => <option key={n} value={n}>{t('suivi.risques.impact')} : {n}</option>)}
      </select>
      <select
        aria-label={t('suivi.risques.champStatut')}
        value={form.statut}
        onChange={(e) => setForm({ ...form, statut: e.target.value })}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {['ouverte', 'attenuation', 'close'].map((s) => (
          <option key={s} value={s}>{t(`suivi.risques.statut.${s}`)}</option>
        ))}
      </select>
      <input
        type="date"
        aria-label={t('suivi.risques.champEcheance')}
        title={t('suivi.risques.champEcheance')}
        value={form.echeance}
        onChange={(e) => setForm({ ...form, echeance: e.target.value })}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
      <Input
        value={form.planAction}
        onChange={(e) => setForm({ ...form, planAction: e.target.value })}
        placeholder={t('suivi.risques.champPlan')}
        aria-label={t('suivi.risques.champPlan')}
      />
      <select
        aria-label={t('risques.colResponsable')}
        value={form.idResponsable}
        onChange={(e) => setForm({ ...form, idResponsable: e.target.value })}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
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

  if (!peutConsulter) return null;

  return (
    <>
      {info && (
        <div className="mb-3 rounded-md border border-success/50 bg-success/50 p-3 text-sm text-success">
          {t(info)}
        </div>
      )}
      {erreur && (
        <div className="mb-3 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {erreur}
        </div>
      )}

      {/* Alertes de l'initiative (calculées, aucune table) : retards,
          jalons proches, risques critiques, budget dépassé. */}
      <section className="mb-4 rounded-xl border border-input bg-card p-[18px] shadow-sm">
        <div className="flex items-center justify-between mb-3 section-entete">
          <h3 className="text-base font-semibold text-foreground">{t('suivi.blocAlertes')}</h3>
          {alertes.length > 0 && (
            <Badge
              variant={alertes.some((a) => a.niveau === 'ROUGE') ? 'destructive' : 'secondary'}
              className={`border-transparent ${
                alertes.some((a) => a.niveau === 'ROUGE') ? 'bg-destructive/15 text-destructive' : 'bg-amber/15 text-amber-800'
              }`}
            >
              {alertes.length}
            </Badge>
          )}
        </div>
        {alertes.length === 0
          ? <p className="text-sm text-muted-foreground">{t('suivi.alertesVide')}</p>
          : <ListeAlertes alertes={alertes} avecLiens={false} />}
      </section>

      {/* Fil de commentaires : immuable — on publie, on supprime, on ne
          modifie jamais (la ligne reste dans le journal des accès). */}
      <section className="mb-4 rounded-xl border border-input bg-card p-[18px] shadow-sm">
        <div className="flex items-center justify-between mb-3 section-entete">
          <h3 className="text-base font-semibold text-foreground">{t('suivi.commentaires.titre')}</h3>
          {commentaires.length > 0 && <Badge variant="secondary">{commentaires.length}</Badge>}
        </div>
        <p className="text-sm text-muted-foreground">{t('suivi.commentaires.sousTitre')}</p>
        {commentaires.length === 0 && <p className="text-sm text-muted-foreground">{t('suivi.commentaires.vide')}</p>}
        <ul className="space-y-3">
          {commentaires.map((c) => (
            <li key={c.idCommentaire} className="commentaire-item p-3 rounded-lg border border-muted/30 bg-muted/50">
              <span className="commentaire-tete flex items-center gap-2 mb-1">
                <strong>{c.auteur?.nomComplet ?? t('suivi.commentaires.auteurInconnu')}</strong>
                <span className="text-xs text-muted-foreground">{formaterDateHeure(c.dateCreation, langue)}</span>
                {(c.auteur?.idUser === user?.idUser || peutSupprimerCommentaire) && (
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    onClick={() => supprimerCommentaire(c)}
                    aria-label={`${t('suivi.commentaires.supprimer')} : ${c.contenu.slice(0, 40)}`}
                  >
                    {t('suivi.commentaires.supprimer')}
                  </Button>
                )}
              </span>
              <p className="commentaire-corps text-sm text-foreground">{c.contenu}</p>
            </li>
          ))}
        </ul>
        {peutCommenter && !archivee && (
          <form className="mt-4 space-y-2 suivi-form suivi-form-commentaire" onSubmit={publierCommentaire}>
            <textarea
              rows={3}
              maxLength={4000}
              value={nouveauCommentaire}
              onChange={(e) => setNouveauCommentaire(e.target.value)}
              placeholder={t('suivi.commentaires.champ')}
              aria-label={t('suivi.commentaires.champ')}
              className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
            <div className="suivi-form-actions flex items-center justify-end">
              <Button
                type="submit"
                disabled={!nouveauCommentaire.trim()}
              >
                {t('suivi.commentaires.publier')}
              </Button>
            </div>
          </form>
        )}
      </section>

      {/* Registre des risques : score = probabilité × impact (1-25),
          quatre bandes de santé, statut et échéance. Écriture réservée à
          RISQUE_GERER (19). */}
      <section className="mb-4 rounded-xl border border-input bg-card p-[18px] shadow-sm">
        <div className="flex items-center justify-between mb-3 section-entete">
          <h3 className="text-base font-semibold text-foreground">{t('suivi.risques.titre')}</h3>
          {risques.length > 0 && (
            <Badge
              variant={risques.some((r) => r.niveau === 'ROUGE') ? 'destructive' : 'success'}
              className={`border-transparent ${
                risques.some((r) => r.niveau === 'ROUGE') ? 'bg-destructive/15 text-destructive' : 'bg-success/15 text-success'
              }`}
            >
              {risques.length}
            </Badge>
          )}
          <Link
            to={`/risques?initiative=${idInitiative}`}
            className="text-xs text-primary hover:underline"
          >
            {t('risques.titre')}
          </Link>
        </div>
        {risques.length === 0 && <p className="text-sm text-muted-foreground">{t('suivi.risques.vide')}</p>}
        <div className="space-y-3">
          {risques.map((risque) => (
            <div key={risque.idRisque} className="etape-item p-3 rounded-lg border border-muted/30 bg-muted/50">
              {editionRisque?.idRisque === risque.idRisque ? (
                <form className="space-y-3 suivi-form" onSubmit={soumettreRisque}>
                  {champsRisque(editionRisque, setEditionRisque)}
                  <div className="suivi-form-actions flex items-center justify-end gap-2">
                    <Button type="submit">{t('initiativeDetail.ok')}</Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setEditionRisque(null)}
                    >
                      {t('suivi.risques.annuler')}
                    </Button>
                  </div>
                </form>
              ) : (
                <>
                  <div className="etape-texte mb-3">
                    <strong>{risque.titre}</strong>
                    <small className="block mt-1 text-muted-foreground">{risque.description}</small>
                    <span className="flex flex-wrap gap-2 mt-2">
                      <SanteBadge sante={{ niveau: risque.niveau }} />
                      <Badge variant="secondary" className={`border-transparent bg-muted/50 text-muted-foreground`}>
                        {t('monEspace.score')} {risque.score}/25
                      </Badge>
                      <Badge
                        variant="secondary"
                        className={`border-transparent ${
                          risque.statut === 'close' ? 'bg-muted/50 text-muted-foreground' :
                          risque.statut === 'attenuation' ? 'bg-amber/15 text-amber-800' : 'bg-blue/15 text-blue-800'
                        }`}
                      >
                        {t(`suivi.risques.statut.${risque.statut}`)}
                      </Badge>
                      <Badge variant="secondary" className={`border-transparent bg-muted/50 text-muted-foreground`}>
                        {t('suivi.risques.probabilite')} {risque.probabilite} · {t('suivi.risques.impact')} {risque.impact}
                      </Badge>
                    </span>
                    {risque.echeance && (
                      <span className="text-xs text-muted-foreground block mt-1">
                        {t('suivi.risques.champEcheance')} : {risque.echeance}
                      </span>
                    )}
                    {risque.planAction && (
                      <small className="text-muted-foreground block mt-1">
                        {t('suivi.risques.champPlan')} : {risque.planAction}
                      </small>
                    )}
                    <span className="text-xs text-muted-foreground block mt-1">
                      {t('risques.colResponsable')} : {risque.responsable?.nomComplet ?? '—'}
                    </span>
                  </div>
                  {peutGererRisques && !archivee && (
                    <div className="etape-actions flex items-center gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
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
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        onClick={() => supprimerRisque(risque)}
                      >
                        {t('initiativeDetail.supprimer')}
                      </Button>
                    </div>
                  )}
                </>
              )}
            </div>
          ))}
          {peutGererRisques && !archivee && (
            <form className="mt-4 space-y-2 suivi-form" onSubmit={soumettreRisque}>
              {champsRisque(nouveauRisque, setNouveauRisque)}
              <div className="suivi-form-actions flex items-center justify-end">
                <Button type="submit">{t('suivi.risques.creer')}</Button>
              </div>
            </form>
          )}
        </div>
      </section>

      {/* Dépenses de l'initiative : information financière — lecture et
          écriture réservées à DEPENSE_SAISIR (20). Le registre complet
          (filtres, totaux, export) vit sur sa propre page. */}
      {peutSaisirDepenses && (
        <section className="mb-4 rounded-xl border border-input bg-card p-[18px] shadow-sm">
          <div className="flex items-center justify-between mb-3 section-entete">
            <h3 className="text-base font-semibold text-foreground">{t('suivi.depenses.titre')}</h3>
            {Array.isArray(depenses) && depenses.length > 0 && (
              <Badge variant="secondary" className="border-transparent bg-muted/50 text-muted-foreground">
                {t('suivi.depenses.total', { montant: formaterMontant(totalDepenses, langue) })}
              </Badge>
            )}
            <Link
              to={`/depenses?initiative=${idInitiative}`}
              className="text-xs text-primary hover:underline"
            >
              {t('depenses.titre')}
            </Link>
          </div>
          {!Array.isArray(depenses) || depenses.length === 0
            ? <p className="text-sm text-muted-foreground">{t('suivi.depenses.vide')}</p>
            : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm border-collapse">
                  <thead>
                    <tr className="border-b border-muted/30">
                      {[
                        t('depenses.colDate'),
                        t('depenses.colLibelle'),
                        t('depenses.colMontant'),
                        t('depenses.colCategorie'),
                        t('depenses.colSaisisseur')
                      ].map((libelle) => (
                        <th key={libelle} className="text-left pb-2 text-xs text-muted-foreground font-medium">
                          {libelle}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {depenses.map((d) => (
                      <tr key={d.idDepense} className="border-b border-muted/10 hover:bg-muted/50">
                        <td className="py-2 text-xs text-foreground">{d.dateDepense}</td>
                        <td className="py-2 text-xs text-foreground">{d.libelle}</td>
                        <td className="py-2 text-xs text-right font-mono">
                          {formaterMontant(d.montant, langue)}
                        </td>
                        <td className="py-2 text-xs text-muted-foreground">
                          {d.categorie ?? '—'}
                        </td>
                        <td className="py-2 text-xs text-muted-foreground" title={d.saisisseur?.email ?? ''}>
                          {d.saisisseur?.nomComplet ?? '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          {!archivee && (
            <form className="mt-4 space-y-2 suivi-form" onSubmit={soumettreDepense}>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <Input
                  value={nouvelleDepense.libelle}
                  required
                  maxLength={150}
                  onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, libelle: e.target.value })}
                  placeholder={t('suivi.depenses.champLibelle')}
                  aria-label={t('suivi.depenses.champLibelle')}
                />
                <Input
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={nouvelleDepense.montant}
                  onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, montant: e.target.value })}
                  placeholder={t('suivi.depenses.champMontant')}
                  aria-label={t('suivi.depenses.champMontant')}
                />
                <Input
                  type="date"
                  required
                  value={nouvelleDepense.dateDepense}
                  onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, dateDepense: e.target.value })}
                  aria-label={t('suivi.depenses.champDate')}
                />
                <Input
                  value={nouvelleDepense.categorie}
                  maxLength={50}
                  onChange={(e) => setNouvelleDepense({ ...nouvelleDepense, categorie: e.target.value })}
                  placeholder={t('suivi.depenses.champCategorie')}
                  aria-label={t('suivi.depenses.champCategorie')}
                />
                <Button type="submit" className="mt-auto">{t('suivi.depenses.creer')}</Button>
              </div>
            </form>
          )}
        </section>
      )}
    </>
  );
}
