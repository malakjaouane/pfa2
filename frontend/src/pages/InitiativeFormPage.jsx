import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';

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
 * Création / modification d'une initiative.\n * En création, l'initiative démarre au statut « À planifier » (cycle de vie).\n */
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

  if (chargement) return <div className="center-page">{t('initiativeForm.chargement')}</div>;

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{edition ? t('initiativeForm.titreModification') : t('initiativeForm.titreCreation')}</h2>
          <p>{edition ? t('initiativeForm.sousTitreModification') : t('initiativeForm.sousTitreCreation')}</p>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      <form className="carte formulaire" onSubmit={gererSoumission}>
        <div className="form-grille">
          <div className="champ">
            <label>{t('initiativeForm.code')}</label>
            <input value={form.code} onChange={set('code')} placeholder={t('initiativeForm.codePlaceholder')} required />
          </div>
          <div className="champ">
            <label>{t('initiativeForm.nom')}</label>
            <input value={form.nom} onChange={set('nom')} placeholder={t('initiativeForm.nomPlaceholder')} required />
          </div>
          <div className="champ champ-plein">
            <label>{t('initiativeForm.description')}</label>
            <textarea value={form.description} onChange={set('description')} rows={3} placeholder={t('initiativeForm.descriptionPlaceholder')} />
          </div>
          <div className="champ">
            <label htmlFor="if-entite">{t('initiativeForm.entite')}</label>
            <select id="if-entite" value={form.idEntite} onChange={set('idEntite')} required>
              <option value="">—</option>
              {referentiels.entites.map((en) => (
                <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>
              ))}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="if-responsable">{t('initiativeForm.responsable')}</label>
            <select id="if-responsable" value={form.idResponsable} onChange={set('idResponsable')}>
              <option value="">—</option>
              {referentiels.responsables.map((r) => (
                <option key={r.idUser} value={r.idUser}>{r.nomComplet}</option>
              ))}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="if-statut">{t('initiativeForm.statut')}</label>
            {edition ? (
              <select id="if-statut" value={form.idStatut} onChange={set('idStatut')}>
                {referentiels.statuts.map((st) => (
                  <option key={st.idStatut} value={st.idStatut}>{ref.statut(st.libelle)}</option>
                ))}
              </select>
            ) : (
              <input id="if-statut" value={ref.statut('À planifier')} disabled />
            )}
          </div>
          <div className="champ">
            <label htmlFor="if-priorite">{t('initiativeForm.priorite')}</label>
            <select id="if-priorite" value={form.idPriorite} onChange={set('idPriorite')} required>
              <option value="">—</option>
              {referentiels.priorites.map((p) => (
                <option key={p.idPriorite} value={p.idPriorite}>{ref.priorite(p.libelle)}</option>
              ))}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="if-objectif">{t('initiativeForm.objectif')}</label>
            <select id="if-objectif" value={form.idObjectif} onChange={set('idObjectif')}>
              <option value="">—</option>
              {referentiels.objectifs.map((o) => (
                <option key={o.idObjectif} value={o.idObjectif}>{o.libelle}</option>
              ))}
            </select>
          </div>
          <div className="champ">
            <label>{t('initiativeForm.dateDebut')}</label>
            <input type="date" value={form.dateDebut} onChange={set('dateDebut')} />
          </div>
          <div className="champ">
            <label>{t('initiativeForm.dateFinPrevue')}</label>
            <input type="date" value={form.dateFinPrevue} onChange={set('dateFinPrevue')} />
          </div>
          <div className="champ">
            <label>{t('initiativeForm.budget')}</label>
            <input type="number" min="0" step="0.01" value={form.budget} onChange={set('budget')} placeholder="0" />
          </div>
          <div className="champ">
            <label htmlFor="if-avancement">{t('initiativeForm.avancement')}</label>
            <input id="if-avancement" type="number" min="0" max="100" value={form.avancement}
              onChange={set('avancement')} placeholder="0" disabled={etapesPilotent}
              aria-describedby={etapesPilotent ? 'if-avancement-aide' : undefined}
              onInvalid={() => setErreur({ cle: 'initiativeForm.erreurAvancement' })} />
            {etapesPilotent && (
              <p className="aide-champ" id="if-avancement-aide">{t('initiativeForm.avancementEtapes')}</p>
            )}
          </div>
        </div>

        <div className="form-actions">
          <button type="button" className="btn btn-secondaire" onClick={() => navigate(-1)}>{t('initiativeForm.annuler')}</button>
          <button type="submit" className="btn btn-primaire" disabled={soumission}>
            {soumission ? t('initiativeForm.enregistrement') : edition ? t('initiativeForm.enregistrerModifications') : t('initiativeForm.creer')}
          </button>
        </div>
      </form>
    </div>
  );
}
