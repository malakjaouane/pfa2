import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { IconCadenas } from '../components/Icons';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';

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
  // Bascule « Actives | Archives » portée par l'URL (?archive=1) :
  // lien partagé, retour navigateur et rechargement automatique.
  const [searchParams, setSearchParams] = useSearchParams();
  const voirArchives = searchParams.get('archive') === '1' || searchParams.get('archive') === 'true';

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
  // Demandes cochées dans la barre de suppression en lot.
  const [selection, setSelection] = useState([]);

  const libelleStatut = (libelle) =>
    libelle && CLES_STATUT_DEMANDE[libelle] ? t(CLES_STATUT_DEMANDE[libelle]) : libelle;

  /**
   * Rechargement : demandes (avec la recherche libre éventuelle), entités
   * et priorités. `motCle` permet de forcer le mot-clé au moment de la
   * soumission du formulaire — l'état React n'est pas encore mis à jour.
   */
  const charger = (motCle) => {
    const q = (motCle ?? rechercheAppliquee).trim();
    // Les deux vues sont exclusives : archive=true bascule sur les
    // demandes archivées, sinon le serveur renvoie la liste active.
    const params = {};
    if (q) params.recherche = q;
    if (voirArchives) params.archive = 'true';
    Promise.all([
      api.get('/demandes', { params }),
      api.get('/referentiels/entites'),
      api.get('/referentiels/priorites')
    ])
      .then(([d, en, pr]) => {
        setDemandes(d.data);
        setEntites(en.data);
        setPriorites(pr.data);
        // Le rechargement repart d'une sélection vide : on ne supprime
        // jamais des lignes qui ne sont plus affichées.
        setSelection([]);
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

  // Rechargement au montage, puis à chaque bascule de vue : la liste
  // dépend de « voirArchives » (porté par l'URL ?archive=1). L'indicateur
  // de chargement est posé avant l'appel pour ne pas afficher, un instant,
  // les lignes de l'autre vue sous le nouveau titre.
  useEffect(() => {
    setChargement(true);
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [voirArchives]);

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

  /**
   * Bascule vers la vue « Archives » (ou retour aux « Actives ») :
   * l'URL change, l'effet ci-dessus recharge la liste avec le bon filtre.
   */
  const basculerVue = (archives) => {
    setSearchParams(archives ? { archive: '1' } : {}, { replace: false });
  };

  /**
   * Archivage (depuis la vue « Actives ») : la demande quitte la liste
   * active — seules les demandes terminées (« Acceptée » ou « Refusée »)
   * sont archivables, le serveur répond 400 sinon. Réversible via
   * « Restaurer » dans la vue « Archives ».
   */
  const archiver = async (d) => {
    if (!window.confirm(t('demandes.confirmArchiver', { titre: d.titre }))) return;
    try {
      await api.patch(`/demandes/${d.idDemande}/archive`);
      setInfo({ cle: 'demandes.infoArchivee' });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  /** Retour dans la liste active — aucune condition de statut. */
  const restaurer = async (d) => {
    try {
      await api.patch(`/demandes/${d.idDemande}/restaurer`);
      setInfo({ cle: 'demandes.infoRestauree' });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  /**
   * Une demande se coche si le compte peut l'évaluer ET si elle n'est pas
   * déjà transformée en initiative (le serveur refuserait en 400 : la
   * transformation lie les deux dossiers).
   */
  const supprimable = (d) => peutEvaluer && !d.initiativeLiee;

  const demandesAffichees = demandes.filter(supprimable);
  // Lignes verrouillées (transformées) — affichées avec un cadenas pour
  // que le refus soit explicable d'un coup d'œil.
  const verrouillees = demandes.length - demandesAffichees.length;
  const toutesCochees = demandesAffichees.length > 0
    && demandesAffichees.every((d) => selection.includes(d.idDemande));

  const basculer = (id) =>
    setSelection((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const toutSelectionner = () =>
    setSelection(toutesCochees ? [] : demandesAffichees.map((d) => d.idDemande));

  /**
   * Suppression en lot depuis la barre de sélection : une seule
   * confirmation, puis un `DELETE` par demande choisie — les garde-fous
   * serveur (transformée, droits) restent appliqués ligne à ligne. Un
   * échec partiel est annoncé avec le détail du premier refus, et les
   * demandes supprimées sont comptées.
   */
  const supprimerSelection = async () => {
    const cibles = demandes.filter((d) => selection.includes(d.idDemande) && supprimable(d));
    if (cibles.length === 0) return;
    if (!window.confirm(t('demandes.confirmSupprimerSelection', { n: cibles.length }))) return;

    let supprimees = 0;
    const echecs = [];
    for (const d of cibles) {
      try {
        await api.delete(`/demandes/${d.idDemande}`);
        supprimees += 1;
      } catch (err) {
        echecs.push(`${d.titre} — ${afficherMessage(t, messageErreur(err))}`);
      }
    }

    setSelection([]);
    if (echecs.length === 0) {
      setErreur('');
      setInfo({ cle: 'demandes.infoSupprimees', params: { n: supprimees } });
    } else {
      if (supprimees > 0) setInfo({ cle: 'demandes.infoSupprimees', params: { n: supprimees } });
      setErreur({
        cle: 'demandes.erreurPartielle',
        params: { n: supprimees, echecs: echecs.length, detail: echecs[0] }
      });
    }
    charger();
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('demandes.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('demandes.sousTitre')}
          </p>
        </div>
        <div className="flex items-end gap-3">
          {/* Bascule dans la page (aucune route ni lien de menu) :
              l'URL ?archive=1 documente la vue affichée. */}
          <div className="flex items-center gap-2 border border-input rounded-md p-1">
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${!voirArchives ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => basculerVue(false)}
            >
              {t('demandes.vueActives')}
            </button>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${voirArchives ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => basculerVue(true)}
            >
              {t('demandes.vueArchives')}
            </button>
          </div>
          {peutSoumettre && !voirArchives && (
            <Button
              type="button"
              onClick={() => (formVisible ? setFormVisible(false) : ouvrirNouvelle())}
            >
              {formVisible ? t('demandes.fermer') : t('demandes.nouvelle')}
            </Button>
          )}
        </div>
      </header>

      {info && (
        <div className="mb-4 rounded-md border border-success/50 bg-success/50 p-3 text-sm text-success">
          {afficherMessage(t, info)}
        </div>
      )}
      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {afficherMessage(t, erreur)}
        </div>
      )}

      {formVisible && (
        <Card className="mb-6 p-[18px] shadow-sm" onSubmit={enregistrer}>
          <CardHeader>
            <CardTitle className="text-lg font-semibold">
              {idEdition ? t('demandes.formModifierTitre') : t('demandes.formNouvelleTitre')}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2 space-y-2">
                <Label htmlFor="demTitre">{t('demandes.champTitre')} *</Label>
                <Input
                  id="demTitre"
                  value={form.titre}
                  onChange={set('titre')}
                  required
                  placeholder={t('demandes.placeholderTitre')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="demEntite">{t('demandes.champEntite')} *</Label>
                <select
                  id="demEntite"
                  value={form.idEntite}
                  onChange={set('idEntite')}
                  required
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {entites.map((en) => <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>)}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="demPriorite">{t('demandes.champPriorite')} *</Label>
                <select
                  id="demPriorite"
                  value={form.idPriorite}
                  onChange={set('idPriorite')}
                  required
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {priorites.map((p) => <option key={p.idPriorite} value={p.idPriorite}>{ref.priorite(p.libelle)}</option>)}
                </select>
              </div>
              <div className="md:col-span-2 space-y-2">
                <Label htmlFor="demDescription">{t('demandes.champDescription')}</Label>
                <textarea
                  id="demDescription"
                  rows={3}
                  className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={form.descriptionBesoin}
                  onChange={set('descriptionBesoin')}
                  placeholder={t('demandes.placeholderDescription')}
                />
              </div>
              <div className="md:col-span-2 space-y-2">
                <Label htmlFor="demJustification">{t('demandes.champJustification')}</Label>
                <textarea
                  id="demJustification"
                  rows={2}
                  className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={form.justificationMetier}
                  onChange={set('justificationMetier')}
                  placeholder={t('demandes.placeholderJustification')}
                />
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-4">
              <Button type="button" variant="secondary" onClick={() => setFormVisible(false)}>
                {t('demandes.annuler')}
              </Button>
              <Button type="submit" disabled={soumission} className="ml-2">
                {soumission ? t('demandes.enregistrement') : idEdition ? t('demandes.enregistrerModifications') : t('demandes.creer')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <form className="flex flex-wrap items-center gap-3" onSubmit={appliquerRecherche}>
          <div className="flex-1 min-w-[200px] space-y-2">
            <Label htmlFor="demRecherche">
              {t('demandes.recherchePlaceholder')}
            </Label>
            <Input
              id="demRecherche"
              type="search"
              placeholder={t('demandes.recherchePlaceholder')}
              aria-label={t('demandes.recherchePlaceholder')}
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
            />
          </div>
          <div className="flex items-end gap-2">
            <Button type="submit" disabled={chargement}>
              {t('demandes.appliquer')}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={effacerRecherche}
              disabled={!recherche && !rechercheAppliquee}
            >
              {t('demandes.reinitialiser')}
            </Button>
          </div>
        </form>
      </Card>

      {/* Barre de suppression en lot : présente dès l'arrivée sur la page
          (bouton inactif sans sélection) pour que l'action soit trouvable —
          les lignes transformées en initiative affichent un cadenas et le
          bandeau ci-dessous explique pourquoi rien n'est sélectionnable. */}
      {peutEvaluer && (
        <Card className="mb-6 p-[18px] shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">
              {t('demandes.selectionnees', { n: selection.length })}
              {verrouillees > 0 && (
                <>
                  {' — '}
                  {t('demandes.verrouillees', { n: verrouillees })}
                </>
              )}
            </span>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setSelection([])}
                disabled={selection.length === 0}
              >
                {t('demandes.annulerSelection')}
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={supprimerSelection}
                disabled={selection.length === 0}
              >
                {t('demandes.supprimerSelection', { n: selection.length })}
              </Button>
            </div>
          </div>
        </Card>
      )}
      {peutEvaluer && verrouillees > 0 && demandesAffichees.length === 0 && (
        <div className="mb-4 rounded-md border border-info/50 bg-info/50 p-3 text-sm text-info">
          {t('demandes.toutesVerrouillees', { n: verrouillees })}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        {chargement ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('demandes.chargement')}
          </div>
        ) : demandes.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t(voirArchives ? 'demandes.aucuneArchive' : 'demandes.aucune')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                {peutEvaluer && (
                  <TableHead className="w-12">
                    <input
                      type="checkbox"
                      checked={toutesCochees}
                      onChange={toutSelectionner}
                      disabled={demandesAffichees.length === 0}
                      aria-label={t('demandes.selectionnerTout')}
                      className="mt-0.5 rounded border-input text-primary focus:ring-ring"
                    />
                  </TableHead>
                )}
                <TableHead>{t('demandes.colTitre')}</TableHead>
                <TableHead>{t('demandes.colDemandeur')}</TableHead>
                <TableHead>{t('demandes.colEntite')}</TableHead>
                <TableHead>{t('demandes.colPriorite')}</TableHead>
                <TableHead>{t('demandes.colSoumission')}</TableHead>
                <TableHead>{t('demandes.colStatut')}</TableHead>
                <TableHead className="text-right">{t('demandes.colActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {demandes.map((d) => (
                <TableRow key={d.idDemande}>
                  {peutEvaluer && (
                    <TableCell className="w-12">
                      {supprimable(d) ? (
                        <input
                          type="checkbox"
                          checked={selection.includes(d.idDemande)}
                          onChange={() => basculer(d.idDemande)}
                          aria-label={t('demandes.caseSelection', { titre: d.titre })}
                          className="mt-0.5 rounded border-input text-primary focus:ring-ring"
                        />
                      ) : (
                        <Badge variant="secondary" title={t('demandes.caseNonSupprimable')}>
                          <IconCadenas size={13} /> {t('demandes.verrouillee')}
                        </Badge>
                      )}
                    </TableCell>
                  )}
                  <TableCell>
                    <strong>{d.titre}</strong>
                    {d.justificationMetier && (
                      <div className="mt-1 text-xs text-muted-foreground">
                        <small>{d.justificationMetier}</small>
                      </div>
                    )}
                    {d.motifRefus && (
                      <div className="mt-1 text-xs text-muted-foreground">
                        <small>{t('demandes.motifRefus', { motif: d.motifRefus })}</small>
                      </div>
                    )}
                    {d.initiativeLiee && (
                      <div className="mt-1">
                        <Link
                          to={`/initiatives/${d.initiativeLiee.idInitiative}`}
                          className="text-xs text-primary hover:underline"
                        >
                          {t('demandes.transformee', { code: d.initiativeLiee.code })}
                        </Link>
                      </div>
                    )}
                  </TableCell>
                  <TableCell>{d.demandeur?.nomComplet ?? '—'}</TableCell>
                  <TableCell>{d.entite?.nom ?? '—'}</TableCell>
                  <TableCell>{ref.priorite(d.priorite?.libelle) ?? '—'}</TableCell>
                  <TableCell>{d.dateSoumission ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant="secondary" className={`border-transparent ${
                      STYLES_DEMANDE[d.statut?.libelle] === 'badge-vert' ? 'bg-success/15 text-success' :
                      STYLES_DEMANDE[d.statut?.libelle] === 'badge-rouge' ? 'bg-destructive/15 text-destructive' :
                      STYLES_DEMANDE[d.statut?.libelle] === 'badge-ambre' ? 'bg-amber/15 text-amber-800' :
                      STYLES_DEMANDE[d.statut?.libelle] === 'badge-bleu' ? 'bg-blue/15 text-blue-800' :
                      'bg-muted/50 text-muted-foreground'
                    }`}
                    >
                      {libelleStatut(d.statut?.libelle)}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center gap-1">
                      {!d.archive && d.statut?.libelle === 'Brouillon' && peutSoumettre && (
                        <>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => ouvrirEdition(d)}
                          >
                            {t('demandes.modifier')}
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => action(d.idDemande, 'soumettre', { cle: 'demandes.infoSoumise' })}
                          >
                            {t('demandes.soumettre')}
                          </Button>
                        </>
                      )}
                      {!d.archive && d.statut?.libelle === 'Soumise' && peutEvaluer && (
                        <Button
                          type="button"
                          size="sm"
                          onClick={() => action(d.idDemande, 'evaluer', { cle: 'demandes.infoEnEvaluation' })}
                        >
                          {t('demandes.evaluer')}
                        </Button>
                      )}
                      {!d.archive && d.statut?.libelle === 'En évaluation' && peutEvaluer && (
                        <>
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => action(d.idDemande, 'accepter', { cle: 'demandes.infoAcceptee' })}
                          >
                            {t('demandes.accepter')}
                          </Button>
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            onClick={() => { setRefusPour(d.idDemande); setMotifRefus(''); }}
                          >
                            {t('demandes.refuser')}
                          </Button>
                          {refusPour === d.idDemande && (
                            <div className="flex items-end gap-2 ml-2">
                              <Input
                                value={motifRefus}
                                onChange={(e) => setMotifRefus(e.target.value)}
                                placeholder={t('demandes.placeholderMotifRefus')}
                                className="w-32 text-xs"
                              />
                              <Button
                                type="button"
                                variant="destructive"
                                size="sm"
                                onClick={() => confirmerRefus(d.idDemande)}
                              >
                                {t('demandes.confirmer')}
                              </Button>
                              <Button
                                type="button"
                                variant="secondary"
                                size="sm"
                                onClick={() => setRefusPour(null)}
                              >
                                {t('demandes.annuler')}
                              </Button>
                            </div>
                          )}
                        </>
                      )}
                      {!d.archive && d.statut?.libelle === 'Acceptée' && peutEvaluer && !d.initiativeLiee && (
                        <Button
                          type="button"
                          size="sm"
                          onClick={() => transformer(d)}
                        >
                          {t('demandes.transformer')}
                        </Button>
                      )}
                      {d.statut?.libelle === 'Acceptée' && d.initiativeLiee && (
                        <Link
                          to={`/initiatives/${d.initiativeLiee.idInitiative}`}
                          className="text-xs text-primary hover:underline"
                        >
                          {t('demandes.voirInitiative')}
                        </Link>
                      )}
                      {!d.archive && (d.statut?.libelle === 'Acceptée' || d.statut?.libelle === 'Refusée') && (
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          onClick={() => archiver(d)}
                        >
                          {t('demandes.archiver')}
                        </Button>
                      )}
                      {d.archive && (
                        <Button
                          type="button"
                          variant="primary"
                          size="sm"
                          onClick={() => restaurer(d)}
                        >
                          {t('demandes.restaurer')}
                        </Button>
                      )}
                      {peutEvaluer && (
                        <Button
                          type="button"
                          variant="destructive"
                          size="sm"
                          onClick={() => supprimer(d)}
                        >
                          {t('demandes.supprimer')}
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
