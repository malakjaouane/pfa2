import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { IconCadenas } from '../components/Icons';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';

const formVide = { code: '', libelle: '', description: '' };

/**
 * Objectifs stratégiques : consultation du
 * référentiel, création / modification, et initiatives contribuant à
 * chaque objectif (décompte calculé depuis la base réelle).
 */
export default function ObjectifsPage() {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const peutGerer = hasPermission('INITIATIVE_MODIFIER');

  const [objectifs, setObjectifs] = useState([]);
  const [initiatives, setInitiatives] = useState([]);
  const [form, setForm] = useState(formVide);
  const [nouveau, setNouveau] = useState(false);
  const [edition, setEdition] = useState(null); // idObjectif
  const [chargement, setChargement] = useState(true);
  const [soumission, setSoumission] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');
  // Objectifs cochés dans la barre de suppression en lot.
  const [selection, setSelection] = useState([]);

  const charger = () => {
    Promise.all([
      api.get('/objectifs'),
      api.get('/initiatives'),
      api.get('/initiatives?archive=true')
    ])
      .then(([obj, init, archivees]) => {
        setObjectifs(obj.data);
        // Le serveur compte TOUTES les initiatives rattachées — y compris
        // les archivées — pour refuser (400) la suppression d'un objectif.
        // La liste par défaut exclut les archivées : on cumule les deux
        // appels pour que la case soit désactivée aussi sur ces objectifs.
        setInitiatives([...init.data, ...archivees.data]);
        // Le rechargement repart d'une sélection vide : on ne supprime
        // jamais des cartes qui ne sont plus affichées.
        setSelection([]);
      })
      .catch((err) => setErreur(messageErreur(err)))
      .finally(() => setChargement(false));
  };

  useEffect(() => {
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rattachees = useMemo(() => {
    const m = new Map();
    initiatives.forEach((i) => {
      if (i.objectif) {
        const liste = m.get(i.objectif.idObjectif) ?? [];
        liste.push(i);
        m.set(i.objectif.idObjectif, liste);
      }
    });
    return m;
  }, [initiatives]);

  /**
   * Un objectif se coche seulement s'il est relié à zéro initiative :
   * la clé étrangère `initiative.id_objectif` est en
   * `ON DELETE SET NULL`, le serveur refuserait en 400 — on désactive
   * la case plutôt que de proposer une suppression qui échoue.
   */
  const supprimable = (o) => (rattachees.get(o.idObjectif) ?? []).length === 0;

  const objectifsAffichees = objectifs.filter(supprimable);
  // Cartes verrouillées (reliées à des initiatives) — affichées avec un
  // cadenas pour que le refus soit explicable d'un coup d'œil.
  const verrouillees = objectifs.length - objectifsAffichees.length;
  const toutesCochees = objectifsAffichees.length > 0
    && objectifsAffichees.every((o) => selection.includes(o.idObjectif));

  const basculer = (id) =>
    setSelection((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const toutSelectionner = () =>
    setSelection(toutesCochees ? [] : objectifsAffichees.map((o) => o.idObjectif));

  /**
   * Suppression en lot depuis la barre de sélection : une seule
   * confirmation, puis un `DELETE` par objectif choisi — les garde-fous
   * serveur (droits `INITIATIVE_MODIFIER`, objectif référencé) restent
   * appliqués carte à carte. Un échec partiel est annoncé avec le
   * détail du premier refus.
   */
  const supprimerSelection = async () => {
    const cibles = objectifs.filter((o) => selection.includes(o.idObjectif) && supprimable(o));
    if (cibles.length === 0) return;
    if (!window.confirm(t('objectifs.confirmSupprimerSelection', { n: cibles.length }))) return;

    let supprimes = 0;
    const echecs = [];
    for (const o of cibles) {
      try {
        await api.delete(`/objectifs/${o.idObjectif}`);
        supprimes += 1;
      } catch (err) {
        echecs.push(`${o.code} — ${afficherMessage(t, messageErreur(err))}`);
      }
    }

    setSelection([]);
    setEdition(null);
    if (echecs.length === 0) {
      setErreur('');
      setInfo({ cle: 'objectifs.info.supprimees', params: { n: supprimes } });
    } else {
      if (supprimes > 0) setInfo({ cle: 'objectifs.info.supprimees', params: { n: supprimes } });
      setErreur({
        cle: 'objectifs.erreurPartielle',
        params: { n: supprimes, echecs: echecs.length, detail: echecs[0] }
      });
    }
    charger();
  };

  const enregistrer = async (e) => {
    e.preventDefault();
    setSoumission(true);
    setErreur('');
    try {
      if (edition) {
        await api.put(`/objectifs/${edition}`, form);
        setInfo({ cle: 'objectifs.info.misAJour' });
      } else {
        await api.post('/objectifs', form);
        setInfo({ cle: 'objectifs.info.cree' });
      }
      setForm(formVide);
      setNouveau(false);
      setEdition(null);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setSoumission(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('objectifs.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('objectifs.sousTitre')}
          </p>
        </div>
        {peutGerer && (
          <Button
            type="button"
            onClick={() => { setNouveau(!nouveau); setEdition(null); setForm(formVide); }}
          >
            {nouveau ? t('objectifs.fermer') : t('objectifs.nouveau')}
          </Button>
        )}
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

      {nouveau && peutGerer && (
        <Card className="mb-6 p-[18px] shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg font-semibold">{t('objectifs.form.titreNouveau')}</CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <form className="space-y-4" onSubmit={enregistrer}>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="objectifCode">{t('objectifs.form.code')}</Label>
                  <Input
                    id="objectifCode"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    required
                    placeholder={t('objectifs.form.codePlaceholder')}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="objectifLibelle">{t('objectifs.form.libelle')}</Label>
                  <Input
                    id="objectifLibelle"
                    value={form.libelle}
                    onChange={(e) => setForm({ ...form, libelle: e.target.value })}
                    required
                    placeholder={t('objectifs.form.libellePlaceholder')}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="objectifDescription">{t('objectifs.form.description')}</Label>
                <textarea
                  id="objectifDescription"
                  rows={2}
                  className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </div>
              <div className="flex items-center justify-end gap-2">
                <Button type="button" variant="secondary" onClick={() => setNouveau(false)}>
                  {t('objectifs.annuler')}
                </Button>
                <Button type="submit" disabled={soumission}>
                  {soumission ? t('objectifs.enregistrement') : t('objectifs.creer')}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Barre de suppression en lot : présente dès l'arrivée sur la page
          (bouton inactif sans sélection) pour que l'action soit trouvable —
          les objectifs reliés à des initiatives affichent un cadenas et le
          bandeau ci-dessous explique pourquoi rien n'est sélectionnable. */}
      {peutGerer && (
        <Card className="mb-6 p-[18px] shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 text-sm">
              <label className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={toutesCochees}
                  onChange={toutSelectionner}
                  disabled={objectifsAffichees.length === 0}
                  aria-label={t('objectifs.selectionnerTout')}
                  className="rounded border-input text-primary focus:ring-ring"
                />
                {t('objectifs.selectionnerTout')}
              </label>
              <span className="text-muted-foreground">
                {' — '}
                {t('objectifs.selectionnees', { n: selection.length })}
                {verrouillees > 0 && ` — ${t('objectifs.verrouillees', { n: verrouillees })}`}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setSelection([])}
                disabled={selection.length === 0}
              >
                {t('objectifs.annulerSelection')}
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={supprimerSelection}
                disabled={selection.length === 0}
              >
                {t('objectifs.supprimerSelection', { n: selection.length })}
              </Button>
            </div>
          </div>
        </Card>
      )}
      {peutGerer && verrouillees > 0 && objectifsAffichees.length === 0 && (
        <div className="mb-4 rounded-md border border-info/50 bg-info/50 p-3 text-sm text-info">
          {t('objectifs.toutesVerrouillees', { n: verrouillees })}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {chargement ? (
          <div className="col-span-full flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('objectifs.chargement')}
          </div>
        ) : objectifs.length === 0 ? (
          <div className="col-span-full text-center py-12 text-sm text-muted-foreground">
            {t('objectifs.aucun')}
          </div>
        ) : (
          objectifs.map((o) => {
            const liste = rattachees.get(o.idObjectif) ?? [];
            const enEdition = edition === o.idObjectif;
            return (
              <Card key={o.idObjectif} className="p-[18px] shadow-sm">
                {enEdition && peutGerer ? (
                  <form className="space-y-4" onSubmit={enregistrer}>
                    <CardHeader>
                      <CardTitle className="text-lg font-semibold">
                        {t('objectifs.form.titreModifier')}
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-0 space-y-3">
                      <div className="space-y-2">
                        <Label htmlFor="editObjectifCode">{t('objectifs.form.code')}</Label>
                        <Input
                          id="editObjectifCode"
                          value={form.code}
                          onChange={(e) => setForm({ ...form, code: e.target.value })}
                          required
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="editObjectifLibelle">{t('objectifs.form.libelle')}</Label>
                        <Input
                          id="editObjectifLibelle"
                          value={form.libelle}
                          onChange={(e) => setForm({ ...form, libelle: e.target.value })}
                          required
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="editObjectifDescription">{t('objectifs.form.description')}</Label>
                        <textarea
                          id="editObjectifDescription"
                          rows={2}
                          className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                          value={form.description}
                          onChange={(e) => setForm({ ...form, description: e.target.value })}
                        />
                      </div>
                    </CardContent>
                    <div className="flex items-center justify-end gap-2 pt-4">
                      <Button type="button" variant="secondary" onClick={() => setEdition(null)}>
                        {t('objectifs.annuler')}
                      </Button>
                      <Button type="submit" disabled={soumission} className="ml-2">
                        {t('objectifs.enregistrer')}
                      </Button>
                    </div>
                  </form>
                ) : (
                  <>
                    <div className="flex flex-row items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Badge variant="secondary">{o.code}</Badge>
                        <h3 className="text-base font-semibold text-foreground">{o.libelle}</h3>
                      </div>
                      <div className="flex items-center gap-2">
                        {supprimable(o) ? (
                          <input
                            type="checkbox"
                            checked={selection.includes(o.idObjectif)}
                            onChange={() => basculer(o.idObjectif)}
                            aria-label={t('objectifs.caseSelection', { libelle: o.libelle })}
                            className="mt-0.5 rounded border-input text-primary focus:ring-ring"
                          />
                        ) : (
                          <Badge variant="secondary" title={t('objectifs.caseLiee', { n: liste.length })}>
                            <IconCadenas size={13} /> {t('objectifs.verrouillee')}
                          </Badge>
                        )}
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setEdition(o.idObjectif);
                            setForm({ code: o.code, libelle: o.libelle, description: o.description ?? '' });
                            setNouveau(false);
                          }}
                        >
                          {t('objectifs.modifier')}
                        </Button>
                      </div>
                    </div>
                    {o.description && <p className="mt-2 text-sm text-muted-foreground">{o.description}</p>}
                    <div className="mt-3 pt-3 border-t border-muted/30">
                      <div className="flex items-baseline gap-1 mb-1">
                        <span className="text-xl font-semibold text-foreground">{liste.length}</span>
                        <span className="text-sm text-muted-foreground">
                          {t(liste.length > 1 ? 'objectifs.kpi.initiativesRattachees' : 'objectifs.kpi.initiativeRattachee')}
                        </span>
                      </div>
                      <ul className="text-sm text-muted-foreground space-y-1">
                        {liste.length === 0 && <li>{t('objectifs.aucuneInitiative')}</li>}
                        {liste.map((i) => (
                          <li key={i.idInitiative} className="flex justify-between">
                            <Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link>
                            <span>{i.statut?.libelle}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </>
                )}
              </Card>
            );
          })
        )}
      </div>
    </div>
  );
}
