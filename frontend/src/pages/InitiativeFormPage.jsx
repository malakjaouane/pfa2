import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';

const vide = (statutPlanifier) => ({
  code: '',
  nom: '',
  description: '',
  dateDebut: '',
  dateFinPrevue: '',
  budget: '',
  avancement: 0,
  idEntite: '',
  idResponsable: '',
  idStatut: statutPlanifier ?? '',
  idPriorite: '',
  idObjectif: ''
});

/**
 * Création / modification d'une initiative.
 * En création, l'initiative démarre au statut « À planifier » (cycle de vie).
 */
export default function InitiativeFormPage() {
  const { id } = useParams();
  const edition = Boolean(id);
  const navigate = useNavigate();
  const { t } = useI18n();
  const ref = useReferentiels();

  const [form, setForm] = useState(vide(null));
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], responsables: [], priorites: [], objectifs: [] });
  const [chargement, setChargement] = useState(edition);
  const [soumission, setSoumission] = useState(false);
  const [erreur, setErreur] = useState('');
  // Dès qu'une initiative possède des étapes, son avancement est recalculé
  // côté serveur (moyenne des étapes) : la saisie manuelle serait ignorée.
  // On grise alors le champ et on l'explique, plutôt que de laisser croire
  // qu'une valeur à 100 % clôture l'initiative.
  const [etapesPilotent, setEtapesPilotent] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/responsables'),
      api.get('/referentiels/priorites'),
      api.get('/objectifs')
    ])
      .then(([s, e, r, p, o]) => {
        const statuts = s.data;
        const planifier = statuts.find((st) => st.libelle === 'À planifier');
        setReferentiels({ statuts, entites: e.data, responsables: r.data, priorites: p.data, objectifs: o.data });
        setForm((f) => (f.idStatut ? f : { ...f, idStatut: planifier?.idStatut ?? '' }));
      })
      .catch((err) => setErreur(messageErreur(err)));

    if (edition) {
      api
        .get(`/initiatives/${id}`)
        .then((res) => {
          const i = res.data;
          setForm({
            code: i.code,
            nom: i.nom,
            description: i.description ?? '',
            dateDebut: i.dateDebut ?? '',
            dateFinPrevue: i.dateFinPrevue ?? '',
            budget: i.budget ?? '',
            avancement: i.avancement ?? 0,
            idEntite: i.entite?.idEntite ?? '',
            idResponsable: i.responsable?.idUser ?? '',
            idStatut: i.statut?.idStatut ?? '',
            idPriorite: i.priorite?.idPriorite ?? '',
            idObjectif: i.objectif?.idObjectif ?? ''
          });
        })
        .catch((err) => setErreur(messageErreur(err)))
        .finally(() => setChargement(false));

      api
        .get(`/initiatives/${id}/etapes`)
        .then((res) => setEtapesPilotent(Array.isArray(res.data) && res.data.length > 0))
        .catch(() => {});
    }
  }, [id, edition]);

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');
    // Contrôle explicite côté interface : le message s'affiche dans la page,
    // sans dépendre de la bulle de validation native du navigateur.
    const avancementSaisi = form.avancement === '' ? 0 : Number(form.avancement);
    if (!Number.isFinite(avancementSaisi) || avancementSaisi < 0 || avancementSaisi > 100) {
      setErreur({ cle: 'initiativeForm.erreurAvancement' });
      return;
    }
    setSoumission(true);
    const corps = {
      ...form,
      budget: form.budget === '' ? null : Number(form.budget),
      avancement: Number(form.avancement) || 0,
      dateDebut: form.dateDebut || null,
      dateFinPrevue: form.dateFinPrevue || null,
      idEntite: Number(form.idEntite),
      idResponsable: form.idResponsable ? Number(form.idResponsable) : null,
      idStatut: Number(form.idStatut),
      idPriorite: Number(form.idPriorite),
      idObjectif: form.idObjectif ? Number(form.idObjectif) : null
    };
    try {
      if (edition) {
        await api.put(`/initiatives/${id}`, corps);
        navigate(`/initiatives/${id}`);
      } else {
        const res = await api.post('/initiatives', corps);
        navigate(`/initiatives/${res.data.idInitiative}`);
      }
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setSoumission(false);
    }
  };

  if (chargement) {
    return (
      <div className="mx-auto w-full max-w-[1240px] py-12 flex items-center justify-center">
        <span className="text-sm text-muted-foreground">{t('initiativeForm.chargement')}</span>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {edition ? t('initiativeForm.titreModification') : t('initiativeForm.titreCreation')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {edition ? t('initiativeForm.sousTitreModification') : t('initiativeForm.sousTitreCreation')}
          </p>
        </div>
      </header>

      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {afficherMessage(t, erreur)}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader>
          <CardTitle className="text-lg font-semibold">
            {edition ? t('initiativeForm.titreModification') : t('initiativeForm.titreCreation')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0 space-y-4">
          <form className="space-y-4" onSubmit={gererSoumission}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="ifCode">{t('initiativeForm.code')}</Label>
                <Input
                  id="ifCode"
                  value={form.code}
                  onChange={set('code')}
                  placeholder={t('initiativeForm.codePlaceholder')}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifNom">{t('initiativeForm.nom')}</Label>
                <Input
                  id="ifNom"
                  value={form.nom}
                  onChange={set('nom')}
                  placeholder={t('initiativeForm.nomPlaceholder')}
                  required
                />
              </div>
              <div className="md:col-span-2 space-y-2">
                <Label htmlFor="ifDescription">{t('initiativeForm.description')}</Label>
                <textarea
                  id="ifDescription"
                  rows={3}
                  className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={form.description}
                  onChange={set('description')}
                  placeholder={t('initiativeForm.descriptionPlaceholder')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifEntite">{t('initiativeForm.entite')}</Label>
                <select
                  id="ifEntite"
                  value={form.idEntite}
                  onChange={set('idEntite')}
                  required
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {referentiels.entites.map((en) => (
                    <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifResponsable">{t('initiativeForm.responsable')}</Label>
                <select
                  id="ifResponsable"
                  value={form.idResponsable}
                  onChange={set('idResponsable')}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {referentiels.responsables.map((r) => (
                    <option key={r.idUser} value={r.idUser}>{r.nomComplet}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifStatut">{t('initiativeForm.statut')}</Label>
                {edition ? (
                  <select
                    id="ifStatut"
                    value={form.idStatut}
                    onChange={set('idStatut')}
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {referentiels.statuts.map((st) => (
                      <option key={st.idStatut} value={st.idStatut}>{ref.statut(st.libelle)}</option>
                    ))}
                  </select>
                ) : (
                  <Input
                    id="ifStatut"
                    value={ref.statut('À planifier')}
                    disabled
                    className="bg-muted/50"
                  />
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifPriorite">{t('initiativeForm.priorite')}</Label>
                <select
                  id="ifPriorite"
                  value={form.idPriorite}
                  onChange={set('idPriorite')}
                  required
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {referentiels.priorites.map((p) => (
                    <option key={p.idPriorite} value={p.idPriorite}>{ref.priorite(p.libelle)}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifObjectif">{t('initiativeForm.objectif')}</Label>
                <select
                  id="ifObjectif"
                  value={form.idObjectif}
                  onChange={set('idObjectif')}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">—</option>
                  {referentiels.objectifs.map((o) => (
                    <option key={o.idObjectif} value={o.idObjectif}>{o.libelle}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifDateDebut">{t('initiativeForm.dateDebut')}</Label>
                <Input
                  id="ifDateDebut"
                  type="date"
                  value={form.dateDebut}
                  onChange={set('dateDebut')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifDateFinPrevue">{t('initiativeForm.dateFinPrevue')}</Label>
                <Input
                  id="ifDateFinPrevue"
                  type="date"
                  value={form.dateFinPrevue}
                  onChange={set('dateFinPrevue')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifBudget">{t('initiativeForm.budget')}</Label>
                <Input
                  id="ifBudget"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.budget}
                  onChange={set('budget')}
                  placeholder="0"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifAvancement">{t('initiativeForm.avancement')}</Label>
                <Input
                  id="ifAvancement"
                  type="number"
                  min="0"
                  max="100"
                  value={form.avancement}
                  onChange={set('avancement')}
                  placeholder="0"
                  disabled={etapesPilotent}
                  aria-describedby={etapesPilotent ? 'ifAvancementAide' : undefined}
                />
                {etapesPilotent && (
                  <p id="ifAvancementAide" className="text-xs text-muted-foreground">
                    {t('initiativeForm.avancementEtapes')}
                  </p>
                )}
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-4">
              <Button type="button" variant="secondary" onClick={() => navigate(-1)}>
                {t('initiativeForm.annuler')}
              </Button>
              <Button
                type="submit"
                disabled={soumission}
                className="ml-2"
              >
                {soumission ? t('initiativeForm.enregistrement') : edition ? t('initiativeForm.enregistrerModifications') : t('initiativeForm.creer')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
