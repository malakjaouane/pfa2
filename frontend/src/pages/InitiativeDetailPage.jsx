import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { badgeTypeJournal, libelleType, texteEvenement } from '../i18n/journal';
import { useReferentiels } from '../i18n/referentiels';
import StatutBadge from '../components/StatutBadge';
import SanteBadge from '../components/SanteBadge';
import ProgressBar from '../components/ProgressBar';
import FicheSuivi from '../components/FicheSuivi';
import { IconTarget, IconAlert, IconAssistant } from '../components/Icons';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import { useToast } from '../components/ui/use-toast';

const etapeVide = { nom: '', description: '', ordre: '', dateDebut: '', dateFinPrevue: '', avancement: 0, idStatutEtape: '' };
const jalonVide = { nom: '', description: '', datePrevue: '', dateRealisation: '', idStatutJalon: '' };

/** Taille de page du fil de fiche : « Charger plus » remonte la suivante. */
const TAILLE_HISTORIQUE = 10;

/**
 * Fiche détaillée d'une initiative : informations, étapes et jalons.
 * Le suivi (alertes, commentaires, risques, dépenses) vit dans FicheSuivi.
 */
export default function InitiativeDetailPage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const { t, langue } = useI18n();
  const ref = useReferentiels();
  const peutConsulter = hasPermission('INITIATIVE_CONSULTER');
  const peutEtapes = hasPermission('ETAPE_GERER');
  const peutJalons = hasPermission('JALON_GERER');
  const peutModifier = hasPermission('INITIATIVE_MODIFIER');
  const peutArchiver = hasPermission('INITIATIVE_ARCHIVER');
  const peutIa = hasPermission('IA_UTILISER');

  const [initiative, setInitiative] = useState(null);
  const [etapes, setEtapes] = useState([]);
  const [jalons, setJalons] = useState([]);
  const [historique, setHistorique] = useState([]);
  const [historiqueTotal, setHistoriqueTotal] = useState(0);
  const [historiquePage, setHistoriquePage] = useState(0);
  const [historiqueType, setHistoriqueType] = useState('');
  const [statutsEtape, setStatutsEtape] = useState([]);
  const [statutsJalon, setStatutsJalon] = useState([]);
  const { toast } = useToast();

  // Éditeurs inline
  const [nouvelleEtape, setNouvelleEtape] = useState(etapeVide);
  const [editionEtape, setEditionEtape] = useState(null); // { idEtape, ... }
  const [nouveauJalon, setNouveauJalon] = useState(jalonVide);
  const [editionJalon, setEditionJalon] = useState(null);

  // Résumé IA (permission IA_UTILISER) — POST /api/ia/resume/{id}
  const [resumeIa, setResumeIa] = useState('');
  const [resumeEnCours, setResumeEnCours] = useState(false);

  const genererResume = async () => {
    setResumeEnCours(true);
    try {
      const { data } = await api.post(`/ia/resume/${id}`, { langue });
      setResumeIa(data.reponse);
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    } finally {
      setResumeEnCours(false);
    }
  };

  /**
   * Fil paginé du journal de l'initiative (page/size + X-Total-Count) :
   * page 0 = recharge (filtre courant), page suivante = « Charger plus »
   * (on ajoute à la suite sans repartir de zéro). Le backend filtre les
   * types ∈ {initiative, etape, jalon} — 400 au-delà, donc jamais.
   */
  const chargerHistorique = (page = 0, type = historiqueType) => {
    const params = new URLSearchParams({ page: String(page), size: String(TAILLE_HISTORIQUE) });
    if (type) params.set('type', type);
    api
      .get(`/initiatives/${id}/historique?${params.toString()}`)
      .then((res) => {
        const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
        setHistoriqueTotal(Number.isNaN(compte) ? res.data.length : compte);
        setHistorique((precedent) => (page === 0 ? res.data : [...precedent, ...res.data]));
        setHistoriquePage(page);
      })
      .catch(() => {});
  };

  const charger = () => {
    api
      .get(`/initiatives/${id}`)
      .then((res) => setInitiative(res.data))
      .catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
    // Les étapes et jalons se lisent avec INITIATIVE_CONSULTER : le visiteur
    // doit voir le suivi. Seules les écritures exigent ETAPE_GERER /
    // JALON_GERER.
    if (peutConsulter) {
      api.get(`/initiatives/${id}/etapes`).then((res) => setEtapes(res.data)).catch(() => {});
      api.get(`/initiatives/${id}/jalons`).then((res) => setJalons(res.data)).catch(() => {});
      // Journal des modifications : même permission que la fiche elle-même.
      chargerHistorique();
    }
  };

  useEffect(() => {
    Promise.all([api.get('/referentiels/statuts-etape'), api.get('/referentiels/statuts-jalon')])
      .then(([se, sj]) => {
        setStatutsEtape(se.data);
        setStatutsJalon(sj.data);
      })
      .catch(() => {});
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (!initiative) {
    return (
      <div className="mx-auto w-full max-w-[1240px] py-12 flex items-center justify-center">
        <span className="text-sm text-muted-foreground">{t('initiativeDetail.chargement')}</span>
      </div>
    );
  }

  const formatBudget = (valeur) =>
    new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 }).format(valeur ?? 0);

  const archiver = async () => {
    if (!window.confirm(t('initiativeDetail.confirmArchiver', { nom: initiative.nom }))) return;
    try {
      await api.patch(`/initiatives/${initiative.idInitiative}/archive`);
      toast({ title: t('initiativeDetail.archivee'), variant: 'success' });
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  // ---------------- Étapes ----------------

  const enregistrerEtape = async (form) => {
    const corps = {
      nom: form.nom,
      description: form.description,
      ordre: form.ordre === '' ? null : Number(form.ordre),
      dateDebut: form.dateDebut || null,
      dateFinPrevue: form.dateFinPrevue || null,
      avancement: Number(form.avancement) || 0,
      idStatutEtape: form.idStatutEtape ? Number(form.idStatutEtape) : null
    };
    if (editionEtape) {
      await api.put(`/etapes/${editionEtape.idEtape}`, corps);
    } else {
      await api.post(`/initiatives/${id}/etapes`, corps);
    }
    setNouvelleEtape(etapeVide);
    setEditionEtape(null);
    charger();
  };

  const soumettreEtape = (e) => {
    e.preventDefault();
    const cible = editionEtape ?? nouvelleEtape;
    enregistrerEtape(cible).catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
  };

  // ---------------- Jalons ----------------

  const enregistrerJalon = async (form) => {
    const corps = {
      nom: form.nom,
      description: form.description,
      datePrevue: form.datePrevue || null,
      dateRealisation: form.dateRealisation || null,
      idStatutJalon: form.idStatutJalon ? Number(form.idStatutJalon) : null
    };
    if (editionJalon) {
      await api.put(`/jalons/${editionJalon.idJalon}`, corps);
    } else {
      await api.post(`/initiatives/${id}/jalons`, corps);
    }
    setNouveauJalon(jalonVide);
    setEditionJalon(null);
    charger();
  };

  const soumettreJalon = (e) => {
    e.preventDefault();
    const cible = editionJalon ?? nouveauJalon;
    enregistrerJalon(cible).catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
  };

  // ---------------- Suppression / réordonnancement ----------------

  const supprimerEtape = async (etape) => {
    if (!window.confirm(t('initiativeDetail.confirmSupprimerEtape', { nom: etape.nom }))) return;
    try {
      await api.delete(`/etapes/${etape.idEtape}`);
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  const supprimerJalon = async (jalon) => {
    if (!window.confirm(t('initiativeDetail.confirmSupprimerJalon', { nom: jalon.nom }))) return;
    try {
      await api.delete(`/jalons/${jalon.idJalon}`);
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  /** Échange une étape avec sa voisine (ordre réaffecté par le serveur). */
  const deplacerEtape = async (index, delta) => {
    const ids = etapes.map((e) => e.idEtape);
    const cible = index + delta;
    if (cible < 0 || cible >= ids.length) return;
    [ids[index], ids[cible]] = [ids[cible], ids[index]];
    try {
      await api.put(`/initiatives/${id}/etapes/ordre`, ids);
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  /**
   * Valeur par défaut selon le statut (RM18) : « Terminée » 100 %,
   * « À faire » 0 %, « En cours » 50 % — ce dernier reste modifiable
   * librement (0-100) après avoir choisi le statut.
   */
  const changerStatutEtape = (e, form, setForm) => {
    const idStatut = Number(e.target.value);
    const libelle = statutsEtape.find((s) => s.idStatutEtape === idStatut)?.libelle;
    let avancement = form.avancement;
    if (libelle === 'Terminée') avancement = 100;
    else if (libelle === 'En cours') avancement = 50;
    else if (libelle === 'À faire') avancement = 0;
    setForm({ ...form, idStatutEtape: e.target.value, avancement });
  };

  /**
   * RM18 (double sens) : un nombre clairement saisi détermine le statut —
   * 1-99 -> « En cours », 100 -> « Terminée », 0 -> « À faire ». Le champ
   * vidé ne change rien : c'est la valeur par défaut du statut qui
   * s'applique à l'enregistrement (0 / 50 / 100).
   */
  const changerAvancementEtape = (e, form, setForm) => {
    const brut = e.target.value;
    let idStatutEtape = form.idStatutEtape;
    if (brut !== '') {
      const valeur = Number(brut);
      if (!Number.isNaN(valeur)) {
        const libelle = valeur === 0 ? 'À faire' : valeur === 100 ? 'Terminée' : 'En cours';
        idStatutEtape = statutsEtape.find((s) => s.libelle === libelle)?.idStatutEtape ?? idStatutEtape;
      }
    }
    setForm({ ...form, avancement: brut, idStatutEtape });
  };

  const etapesTerminees = etapes.filter((e) => e.statutEtape?.libelle === 'Terminée').length;
  const jalonsAtteints = jalons.filter((j) => j.statutJalon?.libelle === 'Atteint').length;

  return (
    <div className="mx-auto w-full max-w-[1240px]">

      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">
            <Link to="/initiatives">← {t('initiativeDetail.retourListe')}</Link>
          </p>
          <h1 className="text-[22px] font-semibold tracking-tight text-foreground">{initiative.nom}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('initiativeDetail.code')} {initiative.code} · {initiative.entite?.nom ?? '—'}
            {initiative.archive && (
              <Badge variant="secondary" className="ml-2">{t('initiativeDetail.archiveeBadge')}</Badge>
            )}
            {initiative.objectif && (
              <Badge variant="secondary" className="ml-2 flex items-center gap-1">
                <IconTarget size={12} /> {initiative.objectif.libelle}
              </Badge>
            )}
          </p>
        </div>
        <div className="flex items-end gap-2">
          {peutIa && !initiative.archive && (
            <Button
              type="button"
              variant="secondary"
              onClick={genererResume}
              disabled={resumeEnCours}
            >
              <IconAssistant size={16} />
              {resumeEnCours ? t('initiativeDetail.resumeEnCours') : t('initiativeDetail.resumeIa')}
            </Button>
          )}
          {peutModifier && !initiative.archive && (
            <Link
              to={`/initiatives/${initiative.idInitiative}/modifier`}
              className="text-sm font-medium text-primary hover:underline"
            >
              {t('initiativeDetail.modifier')}
            </Link>
          )}
          {peutArchiver && !initiative.archive && ['Terminée', 'Annulée'].includes(initiative.statut?.libelle) && (
            <Button type="button" variant="destructive" size="sm" onClick={archiver}>
              {t('initiativeDetail.archiver')}
            </Button>
          )}
        </div>
      </header>

      {resumeIa && (
        <Card className="mb-6 p-[18px] shadow-sm carte-resume-ia">
          <CardContent className="pt-0">
            <div className="flex items-start gap-2">
              <IconAssistant size={18} />
              <div>
                <h3 className="text-base font-semibold text-foreground mb-1">
                  {t('initiativeDetail.resumeTitre')}
                </h3>
                <p className="text-sm text-foreground">{resumeIa}</p>
                <p className="mt-2 text-xs text-muted-foreground">{t('assistant.legal')}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardContent className="pt-0">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 fiche-grille">
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.statut')}
              </span>
              <StatutBadge libelle={initiative.statut?.libelle} />
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('sante.colonne')}
              </span>
              <SanteBadge sante={initiative.sante} avecRaison />
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.priorite')}
              </span>
              <strong className="text-sm">{ref.priorite(initiative.priorite?.libelle) ?? '—'}</strong>
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.objectifStrategique')}
              </span>
              <strong className="text-sm">
                {initiative.objectif ? `${initiative.objectif.libelle} (${initiative.objectif.code})` : t('initiativeDetail.nonRattache')}
              </strong>
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.responsable')}
              </span>
              <strong className="text-sm">{initiative.responsable?.nomComplet ?? t('initiativeDetail.nonAffecte')}</strong>
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.dates')}
              </span>
              <strong className="text-sm">{initiative.dateDebut ?? '—'} → {initiative.dateFinPrevue ?? '—'}</strong>
            </div>
            <div>
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.budget')}
              </span>
              <strong className="text-sm">{formatBudget(initiative.budget)}</strong>
              <small className="text-xs text-muted-foreground block mt-0.5">
                {t('initiativeDetail.budgetAide')}
              </small>
            </div>
            <div className="lg:col-span-3">
              <span className="text-xs font-medium text-muted-foreground libelle block mb-1">
                {t('initiativeDetail.avancement')}
              </span>
              <ProgressBar valeur={initiative.avancement} large />
              {etapes.length > 0 && (
                <small className="text-xs text-muted-foreground block mt-1">
                  {t('initiativeDetail.avancementCalcule')}
                </small>
              )}
            </div>
            {initiative.enRetard && (
              <div className="lg:col-span-3">
                <Badge variant="destructive" className="border-transparent bg-destructive/15 text-destructive flex items-center gap-1">
                  <IconAlert size={12} /> {t('initiativeDetail.enRetard')}
                </Badge>
              </div>
            )}
          </div>
          {initiative.description && (
            <p className="mt-3 text-sm text-muted-foreground">{initiative.description}</p>
          )}
        </CardContent>
      </Card>

      <section className="mb-6">
        <Card className="p-[18px] shadow-sm">
          <CardContent className="pt-0 pb-0">
            <div className="flex items-center justify-between gap-3 section-entete">
              <div>
                <h3 className="text-base font-semibold text-foreground">
                  {t('initiativeDetail.etapesTitre', { nombre: etapes.length })}
                </h3>
                {etapes.length > 0 && (
                  <Badge variant="success" className="border-transparent bg-success/15 text-success">
                    {t('initiativeDetail.etapesTerminees', { faites: etapesTerminees, total: etapes.length })}
                  </Badge>
                )}
              </div>
            </div>
          </CardContent>
          <CardContent className="pt-0">
            {etapes.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('initiativeDetail.aucuneEtape')}</p>
            ) : (
              <div className="etape-liste">
                {etapes.map((etape, index) => (
                  <div key={etape.idEtape} className="etape-item">
                    {editionEtape?.idEtape === etape.idEtape ? (
                      <form className="formulaire-inline" onSubmit={soumettreEtape}>
                        <div className="flex flex-col sm:flex-row gap-2 items-end">
                          <Input
                            value={editionEtape.nom}
                            onChange={(e) => setEditionEtape({ ...editionEtape, nom: e.target.value })}
                            required
                            placeholder={t('initiativeDetail.nom')}
                            className="flex-1"
                          />
                          <Input
                            type="number"
                            min="0"
                            max="100"
                            value={editionEtape.avancement}
                            onChange={(e) => changerAvancementEtape(e, editionEtape, setEditionEtape)}
                            placeholder="%"
                            className="w-20"
                          />
                          <select
                            aria-label={t('initiativeDetail.ariaStatutEtape')}
                            value={editionEtape.idStatutEtape}
                            onChange={(e) => changerStatutEtape(e, editionEtape, setEditionEtape)}
                            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring w-32"
                          >
                            {statutsEtape.map((s) => (
                              <option key={s.idStatutEtape} value={s.idStatutEtape}>
                                {ref.statutEtape(s.libelle)}
                              </option>
                            ))}
                          </select>
                          <Button type="submit" className="ml-1">
                            {t('initiativeDetail.ok')}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setEditionEtape(null)}
                          >
                            {t('initiativeDetail.annuler')}
                          </Button>
                        </div>
                      </form>
                    ) : (
                      <>
                        <div className="etape-texte">
                          <strong>{etape.ordre}. {etape.nom}</strong>
                          {etape.description && <small>{etape.description}</small>}
                          <span className="texte-muet block mt-0.5">
                            {etape.dateDebut ?? '—'} → {etape.dateFinPrevue ?? '—'} · {ref.statutEtape(etape.statutEtape?.libelle)}
                          </span>
                        </div>
                        <ProgressBar valeur={etape.avancement} />
                        {peutEtapes && (
                          <div className="etape-actions">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => setEditionEtape({
                                idEtape: etape.idEtape,
                                ...etape,
                                idStatutEtape: etape.statutEtape?.idStatutEtape ?? ''
                              })}
                            >
                              {t('initiativeDetail.editer')}
                            </Button>
                            <Button
                              type="button"
                              variant="destructive"
                              size="sm"
                              onClick={() => supprimerEtape(etape)}
                            >
                              {t('initiativeDetail.supprimer')}
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => deplacerEtape(index, -1)}
                              disabled={index === 0}
                              title={t('initiativeDetail.monter')}
                              aria-label={t('initiativeDetail.monter')}
                            >
                              ↑
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => deplacerEtape(index, 1)}
                              disabled={index === etapes.length - 1}
                              title={t('initiativeDetail.descendre')}
                              aria-label={t('initiativeDetail.descendre')}
                            >
                              ↓
                            </Button>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                ))}
              </div>
            )}
            {peutEtapes && (
              <form className="formulaire-inline ajout mt-4" onSubmit={soumettreEtape}>
                <div className="flex flex-col sm:flex-row gap-2 items-end">
                  <Input
                    value={nouvelleEtape.nom}
                    onChange={(e) => setNouvelleEtape({ ...nouvelleEtape, nom: e.target.value })}
                    required
                    placeholder={t('initiativeDetail.nouvelleEtape')}
                    className="flex-1"
                  />
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    value={nouvelleEtape.avancement}
                    onChange={(e) => changerAvancementEtape(e, nouvelleEtape, setNouvelleEtape)}
                    placeholder="%"
                    className="w-20"
                  />
                  <select
                    aria-label={t('initiativeDetail.ariaStatutEtape')}
                    value={nouvelleEtape.idStatutEtape}
                    onChange={(e) => changerStatutEtape(e, nouvelleEtape, setNouvelleEtape)}
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring w-32"
                  >
                    <option value="">{t('initiativeDetail.statut')}</option>
                    {statutsEtape.map((s) => (
                      <option key={s.idStatutEtape} value={s.idStatutEtape}>{ref.statutEtape(s.libelle)}</option>
                    ))}
                  </select>
                  <Button type="submit">{t('initiativeDetail.ajouter')}</Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </section>

      <section className="mb-6">
        <Card className="p-[18px] shadow-sm">
          <CardContent className="pt-0 pb-0">
            <div className="flex items-center justify-between gap-3 section-entete">
              <div>
                <h3 className="text-base font-semibold text-foreground">
                  {t('initiativeDetail.jalonsTitre', { nombre: jalons.length })}
                </h3>
                {jalons.length > 0 && (
                  <Badge variant="secondary" className="border-transparent bg-blue/15 text-blue-800">
                    {t('initiativeDetail.jalonsAtteints', { faites: jalonsAtteints, total: jalons.length })}
                  </Badge>
                )}
              </div>
            </div>
          </CardContent>
          <CardContent className="pt-0">
            {jalons.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('initiativeDetail.aucunJalon')}</p>
            ) : (
              <div className="etape-liste">
                {jalons.map((jalon) => (
                  <div key={jalon.idJalon} className="etape-item">
                    {editionJalon?.idJalon === jalon.idJalon ? (
                      <form className="formulaire-inline" onSubmit={soumettreJalon}>
                        <div className="flex flex-col sm:flex-row gap-2 items-end">
                          <Input
                            value={editionJalon.nom}
                            onChange={(e) => setEditionJalon({ ...editionJalon, nom: e.target.value })}
                            required
                            placeholder={t('initiativeDetail.nom')}
                            className="flex-1"
                          />
                          <Input
                            type="date"
                            aria-label={t('initiativeDetail.ariaDatePrevue')}
                            value={editionJalon.datePrevue}
                            onChange={(e) => setEditionJalon({ ...editionJalon, datePrevue: e.target.value })}
                            className="w-40"
                          />
                          <select
                            aria-label={t('initiativeDetail.ariaStatutJalon')}
                            value={editionJalon.idStatutJalon}
                            onChange={(e) => setEditionJalon({ ...editionJalon, idStatutJalon: e.target.value })}
                            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring w-32"
                          >
                            {statutsJalon.map((s) => (
                              <option key={s.idStatutJalon} value={s.idStatutJalon}>
                                {ref.statutJalon(s.libelle)}
                              </option>
                            ))}
                          </select>
                          <Button type="submit" className="ml-1">
                            {t('initiativeDetail.ok')}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setEditionJalon(null)}
                          >
                            {t('initiativeDetail.annuler')}
                          </Button>
                        </div>
                      </form>
                    ) : (
                      <>
                        <div className="etape-texte">
                          <strong>{jalon.nom}</strong>
                          {jalon.enRetard && (
                            <Badge variant="destructive" className="border-transparent bg-destructive/15 text-destructive ml-2">
                              {t('initiativeDetail.enRetardJalon')}
                            </Badge>
                          )}
                          {jalon.description && <small>{jalon.description}</small>}
                          <span className="texte-muet block mt-0.5">
                            {t('initiativeDetail.jalonPrevu', {
                              date: jalon.datePrevue ?? '—',
                              statut: ref.statutJalon(jalon.statutJalon?.libelle) ?? ''
                            })}
                            {jalon.dateRealisation ? t('initiativeDetail.jalonRealise', { date: jalon.dateRealisation }) : ''}
                          </span>
                        </div>
                        {peutJalons && (
                          <div className="etape-actions">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => setEditionJalon({
                                idJalon: jalon.idJalon,
                                ...jalon,
                                idStatutJalon: jalon.statutJalon?.idStatutJalon ?? ''
                              })}
                            >
                              {t('initiativeDetail.editer')}
                            </Button>
                            <Button
                              type="button"
                              variant="destructive"
                              size="sm"
                              onClick={() => supprimerJalon(jalon)}
                            >
                              {t('initiativeDetail.supprimer')}
                            </Button>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                ))}
              </div>
            )}
            {peutJalons && (
              <form className="formulaire-inline ajout mt-4" onSubmit={soumettreJalon}>
                <div className="flex flex-col sm:flex-row gap-2 items-end">
                  <Input
                    value={nouveauJalon.nom}
                    onChange={(e) => setNouveauJalon({ ...nouveauJalon, nom: e.target.value })}
                    required
                    placeholder={t('initiativeDetail.nouveauJalon')}
                    className="flex-1"
                  />
                  <Input
                    type="date"
                    aria-label={t('initiativeDetail.ariaDatePrevue')}
                    value={nouveauJalon.datePrevue}
                    onChange={(e) => setNouveauJalon({ ...nouveauJalon, datePrevue: e.target.value })}
                    className="w-40"
                  />
                  <select
                    aria-label={t('initiativeDetail.ariaStatutJalon')}
                    value={nouveauJalon.idStatutJalon}
                    onChange={(e) => setNouveauJalon({ ...nouveauJalon, idStatutJalon: e.target.value })}
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring w-32"
                  >
                    <option value="">{t('initiativeDetail.statut')}</option>
                    {statutsJalon.map((s) => (
                      <option key={s.idStatutJalon} value={s.idStatutJalon}>{ref.statutJalon(s.libelle)}</option>
                    ))}
                  </select>
                  <Button type="submit">{t('initiativeDetail.ajouter')}</Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </section>

      {/* Suivi de l'initiative : alertes calculées, fil de commentaires,
          registre des risques (score p × i) et dépenses — permissions
          dédiées (18/19/20) portées par le composant lui-même. */}
      <FicheSuivi idInitiative={Number(id)} archivee={Boolean(initiative.archive)} />

      {/* Journal des modifications : qui a changé quoi, quand et pourquoi —
          toutes les lignes de l'initiative (initiative, étapes, jalons),
          filtrables par type et paginées « Charger plus ». Lisible par tous
          ceux qui peuvent voir la fiche (INITIATIVE_CONSULTER), y compris le
          Visiteur : c'est le suivi, pas une écriture. */}
      <Card className="mb-6 p-[18px] shadow-sm">
        <CardContent className="pt-0 pb-0">
          <div className="flex items-center justify-between gap-3 section-entete">
            <div>
              <h3 className="text-base font-semibold text-foreground">{t('initiativeDetail.historiqueTitre')}</h3>
              {historiqueTotal > 0 && (
                <Badge variant="secondary">{historiqueTotal}</Badge>
              )}
            </div>
          </div>
        </CardContent>
        <CardContent className="pt-0">
          {/* Les commandes (filtres + « Voir tout ») restent affichées même
              quand le filtre courant ne ramène rien : sinon la pastille
              active devient une impasse — aucun moyen de revenir à
              « Tous ». Elles ne disparaissent que sur une fiche sans aucun
              historique et sans filtre posé. */}
          {(historiqueTotal > 0 || historiqueType) && (
            <>
              <div className="mb-4 flex items-center justify-between gap-3 section-entete-actions">
                <p className="text-sm text-muted-foreground historique-resume">
                  {t('initiativeDetail.historiqueResume')}
                </p>
                <Link
                  to={`/historique?initiative=${id}`}
                  className="text-xs text-primary hover:underline"
                >
                  {t('initiativeDetail.voirToutHistorique')}
                </Link>
              </div>
              <div className="historique-filtres mb-4">
                <div
                  className="vue-toggle flex items-center gap-1 border border-input rounded-md p-1"
                  role="group"
                  aria-label={t('initiativeDetail.historiqueAriaFiltre')}
                >
                  {[
                    ['', t('initiativeDetail.historiqueFiltreTous')],
                    ['initiative', t('historique.type.initiative')],
                    ['etape', t('initiativeDetail.historiqueFiltreEtapes')],
                    ['jalon', t('initiativeDetail.historiqueFiltreJalons')]
                  ].map(([cle, libelle]) => (
                    <button
                      key={cle || 'tous'}
                      type="button"
                      className={`px-3 py-1.5 text-xs rounded-md transition-colors ${
                        historiqueType === cle
                          ? 'bg-primary text-primary-foreground'
                          : 'text-muted-foreground hover:bg-muted'
                      }`}
                      onClick={() => {
                        setHistoriqueType(cle);
                        chargerHistorique(0, cle);
                      }}
                    >
                      {libelle}
                    </button>
                  ))}
                </div>
              </div>
            </>
          )}
          {historique.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">
              {historiqueType
                ? t('initiativeDetail.aucunHistoriqueFiltre')
                : t('initiativeDetail.aucunHistorique')}
            </p>
          ) : (
            <>
              <ol className="historique space-y-3">
                {historique.map((ev) => (
                  <li key={ev.id} className="historique-item">
                    <span className="historique-date text-xs text-muted-foreground">
                      {new Date(ev.dateChangement).toLocaleString(langue, {
                        day: '2-digit', month: 'short', year: 'numeric',
                        hour: '2-digit', minute: '2-digit'
                      })}
                    </span>
                    <span className="historique-transition ml-2 flex items-center gap-1">
                      <Badge variant="secondary" className={`border-transparent ${
                        badgeTypeJournal(ev.typeObjet) === 'badge-vert' ? 'bg-success/15 text-success' :
                        badgeTypeJournal(ev.typeObjet) === 'badge-rouge' ? 'bg-destructive/15 text-destructive' :
                        badgeTypeJournal(ev.typeObjet) === 'badge-bleu' ? 'bg-blue/15 text-blue-800' :
                        'bg-muted/50 text-muted-foreground'
                      }`}
                      >
                        {libelleType(t, ev.typeObjet)}
                      </Badge>{' '}
                      {texteEvenement(t, ev)}
                    </span>
                    {ev.commentaire && (
                      <span className="historique-commentaire ml-2 text-xs text-muted-foreground">
                        {ev.commentaire}
                      </span>
                    )}
                    <span className="historique-auteur ml-2 text-xs text-muted-foreground">
                      {ev.utilisateur?.nomComplet ?? t('initiativeDetail.auteurInconnu')}
                      {ev.automatique ? ` · ${t('historique.origineAutomatique')}` : ''}
                    </span>
                  </li>
                ))}
              </ol>
              {historique.length < historiqueTotal && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="mt-4 historique-plus"
                  onClick={() => chargerHistorique(historiquePage + 1)}
                  disabled={historiquePage + 1 >= Math.ceil(historiqueTotal / TAILLE_HISTORIQUE)}
                >
                  {t('initiativeDetail.historiqueChargerPlus')}
                </Button>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
