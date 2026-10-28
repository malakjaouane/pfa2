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
import { IconTarget, IconAlert, IconAssistant } from '../components/Icons';

const etapeVide = { nom: '', description: '', ordre: '', dateDebut: '', dateFinPrevue: '', avancement: 0, idStatutEtape: '' };
const jalonVide = { nom: '', description: '', datePrevue: '', dateRealisation: '', idStatutJalon: '' };
const risqueVide = { description: '', probabilite: '2', impact: '2', planAction: '', idResponsable: '' };

/** Taille de page du fil de fiche : « Charger plus » remonte la suivante. */
const TAILLE_HISTORIQUE = 10;

/** Couleur du badge de santé d'un risque (codes renvoyés par le serveur). */
const badgeSanteRisque = (niveau) =>
  niveau === 'ROUGE' ? 'badge badge-rouge' : niveau === 'ORANGE' ? 'badge badge-ambre' : 'badge badge-vert';

/**
 * Fiche détaillée d'une initiative : informations, étapes et jalons.
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
  const [risques, setRisques] = useState([]);
  const [responsables, setResponsables] = useState([]);
  const [historique, setHistorique] = useState([]);
  const [historiqueTotal, setHistoriqueTotal] = useState(0);
  const [historiquePage, setHistoriquePage] = useState(0);
  const [historiqueType, setHistoriqueType] = useState('');
  const [statutsEtape, setStatutsEtape] = useState([]);
  const [statutsJalon, setStatutsJalon] = useState([]);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  // Éditeurs inline
  const [nouvelleEtape, setNouvelleEtape] = useState(etapeVide);
  const [editionEtape, setEditionEtape] = useState(null); // { idEtape, ... }
  const [nouveauJalon, setNouveauJalon] = useState(jalonVide);
  const [editionJalon, setEditionJalon] = useState(null);
  const [nouveauRisque, setNouveauRisque] = useState(risqueVide);
  const [editionRisque, setEditionRisque] = useState(null); // { idRisque, ... }

  // Résumé IA (permission IA_UTILISER) — POST /api/ia/resume/{id}
  const [resumeIa, setResumeIa] = useState('');
  const [resumeEnCours, setResumeEnCours] = useState(false);

  const genererResume = async () => {
    setErreur('');
    setResumeEnCours(true);
    try {
      const { data } = await api.post(`/ia/resume/${id}`, { langue });
      setResumeIa(data.reponse);
    } catch (err) {
      setErreur(messageErreur(err));
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
      .catch((err) => setErreur(messageErreur(err)));
    // Les étapes et jalons se lisent avec INITIATIVE_CONSULTER : le visiteur
    // doit voir le suivi. Seules les écritures exigent ETAPE_GERER /
    // JALON_GERER.
    if (peutConsulter) {
      api.get(`/initiatives/${id}/etapes`).then((res) => setEtapes(res.data)).catch(() => {});
      api.get(`/initiatives/${id}/jalons`).then((res) => setJalons(res.data)).catch(() => {});
      // Registre des risques : lecture avec INITIATIVE_CONSULTER, comme la fiche.
      api.get(`/initiatives/${id}/risques`).then((res) => setRisques(res.data)).catch(() => {});
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
    // Le sélecteur « responsable du plan d'action » a besoin des comptes :
    // on ne le charge que si l'utilisateur peut écrire (INITIATIVE_MODIFIER).
    if (peutModifier) {
      api.get('/referentiels/responsables').then((r) => setResponsables(r.data)).catch(() => {});
    }
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (erreur) return <div className="alerte alerte-erreur">{erreur}</div>;
  if (!initiative) return <div className="center-page">{t('initiativeDetail.chargement')}</div>;

  const archiver = async () => {
    if (!window.confirm(t('initiativeDetail.confirmArchiver', { nom: initiative.nom }))) return;
    try {
      await api.patch(`/initiatives/${initiative.idInitiative}/archive`);
      setInfo({ cle: 'initiativeDetail.archivee' });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
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
    enregistrerEtape(cible).catch((err) => setErreur(messageErreur(err)));
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
    enregistrerJalon(cible).catch((err) => setErreur(messageErreur(err)));
  };

  // ---------------- Risques ----------------

  const enregistrerRisque = async (form) => {
    const corps = {
      description: form.description,
      probabilite: Number(form.probabilite),
      impact: Number(form.impact),
      planAction: form.planAction || null,
      idResponsable: form.idResponsable ? Number(form.idResponsable) : null
    };
    if (editionRisque) {
      await api.put(`/risques/${editionRisque.idRisque}`, corps);
    } else {
      await api.post(`/initiatives/${id}/risques`, corps);
    }
    setNouveauRisque(risqueVide);
    setEditionRisque(null);
    charger();
  };

  const soumettreRisque = (e) => {
    e.preventDefault();
    const cible = editionRisque ?? nouveauRisque;
    enregistrerRisque(cible).catch((err) => setErreur(messageErreur(err)));
  };

  const supprimerRisque = async (risque) => {
    if (!window.confirm(t('initiativeDetail.confirmSupprimerRisque', { nom: risque.description }))) return;
    try {
      await api.delete(`/risques/${risque.idRisque}`);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  /** Libellé d'une note 1-3 (Faible / Moyen / Élevé). */
  const libelleNote = (n) =>
    n === 3 ? t('initiativeDetail.noteEleve') : n === 2 ? t('initiativeDetail.noteMoyen') : t('initiativeDetail.noteFaible');

  /** Champs communs aux formulaires d'ajout et d'édition d'un risque. */
  const champsRisque = (form, setForm) => (
    <>
      <input value={form.description} required
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder={t('initiativeDetail.descriptionRisque')}
        aria-label={t('initiativeDetail.descriptionRisque')} />
      <select aria-label={t('initiativeDetail.probabilite')} value={form.probabilite}
        onChange={(e) => setForm({ ...form, probabilite: e.target.value })}>
        {[1, 2, 3].map((n) => <option key={n} value={n}>{t('initiativeDetail.probabilite')} : {libelleNote(n)}</option>)}
      </select>
      <select aria-label={t('initiativeDetail.impact')} value={form.impact}
        onChange={(e) => setForm({ ...form, impact: e.target.value })}>
        {[1, 2, 3].map((n) => <option key={n} value={n}>{t('initiativeDetail.impact')} : {libelleNote(n)}</option>)}
      </select>
      <input value={form.planAction}
        onChange={(e) => setForm({ ...form, planAction: e.target.value })}
        placeholder={t('initiativeDetail.planAction')}
        aria-label={t('initiativeDetail.planAction')} />
      <select aria-label={t('initiativeDetail.responsableRisque')} value={form.idResponsable}
        onChange={(e) => setForm({ ...form, idResponsable: e.target.value })}>
        <option value="">{t('initiativeDetail.responsableRisque')}</option>
        {responsables.map((u) => <option key={u.idUser} value={u.idUser}>{u.nomComplet}</option>)}
      </select>
    </>
  );

  // ---------------- Suppression / réordonnancement ----------------

  const supprimerEtape = async (etape) => {
    if (!window.confirm(t('initiativeDetail.confirmSupprimerEtape', { nom: etape.nom }))) return;
    try {
      await api.delete(`/etapes/${etape.idEtape}`);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const supprimerJalon = async (jalon) => {
    if (!window.confirm(t('initiativeDetail.confirmSupprimerJalon', { nom: jalon.nom }))) return;
    try {
      await api.delete(`/jalons/${jalon.idJalon}`);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
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
      setErreur(messageErreur(err));
    }
  };

  /**
   * Valeur par défaut selon le statut (RM18) : « Terminée » 100 %,
   * « À faire » 0 %, « En cours » 50 % — ce dernier reste modifiable
   * librement (0-100) après avoir choisi le statut.
   */
  const changerStatutEtape = (e, form, setForm) => {
    const id = Number(e.target.value);
    const libelle = statutsEtape.find((s) => s.idStatutEtape === id)?.libelle;
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

  const formatBudget = (valeur) =>
    new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 }).format(valeur ?? 0);

  return (
    <div className="page">
      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}

      <header className="page-entete">
        <div>
          <p className="texte-muet"><Link to="/initiatives">← {t('initiativeDetail.retourListe')}</Link></p>
          <h2>{initiative.nom}</h2>
          <p className="texte-muet">
            {t('initiativeDetail.code')} {initiative.code} · {initiative.entite?.nom ?? '—'}
            {initiative.archive && <span className="badge badge-gris">{t('initiativeDetail.archiveeBadge')}</span>}
            {initiative.objectif && <span className="badge badge-violet"><IconTarget size={12} /> {initiative.objectif.libelle}</span>}
          </p>
        </div>
        <div className="actions">
          {peutIa && !initiative.archive && (
            <button
              type="button"
              className="btn btn-secondaire"
              onClick={genererResume}
              disabled={resumeEnCours}
            >
              <IconAssistant size={16} />
              {resumeEnCours ? t('initiativeDetail.resumeEnCours') : t('initiativeDetail.resumeIa')}
            </button>
          )}
          {peutModifier && !initiative.archive && (
            <Link to={`/initiatives/${initiative.idInitiative}/modifier`} className="btn btn-primaire">{t('initiativeDetail.modifier')}</Link>
          )}
          {peutArchiver && !initiative.archive && ['Terminée', 'Annulée'].includes(initiative.statut?.libelle) && (
            <button className="btn btn-danger" onClick={archiver}>{t('initiativeDetail.archiver')}</button>
          )}
        </div>
      </header>

      {resumeIa && (
        <section className="carte carte-resume-ia">
          <h3>
            <IconAssistant size={18} />
            {t('initiativeDetail.resumeTitre')}
          </h3>
          <p>{resumeIa}</p>
          <p className="texte-muet">{t('assistant.legal')}</p>
        </section>
      )}

      <section className="carte">
        <div className="fiche-grille">
          <div>
            <span className="libelle">{t('initiativeDetail.statut')}</span>
            <StatutBadge libelle={initiative.statut?.libelle} />
          </div>
          <div>
            <span className="libelle">{t('sante.colonne')}</span>
            <SanteBadge sante={initiative.sante} avecRaison />
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.priorite')}</span>
            <strong>{ref.priorite(initiative.priorite?.libelle) ?? '—'}</strong>
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.objectifStrategique')}</span>
            <strong>{initiative.objectif ? `${initiative.objectif.libelle} (${initiative.objectif.code})` : t('initiativeDetail.nonRattache')}</strong>
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.responsable')}</span>
            <strong>{initiative.responsable?.nomComplet ?? t('initiativeDetail.nonAffecte')}</strong>
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.dates')}</span>
            <strong>{initiative.dateDebut ?? '—'} → {initiative.dateFinPrevue ?? '—'}</strong>
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.budget')}</span>
            <strong>{formatBudget(initiative.budget)}</strong>
            <small className="texte-muet">{t('initiativeDetail.budgetAide')}</small>
          </div>
          <div>
            <span className="libelle">{t('initiativeDetail.avancement')}</span>
            <ProgressBar valeur={initiative.avancement} large />
            {etapes.length > 0 && (
              <small className="texte-muet">{t('initiativeDetail.avancementCalcule')}</small>
            )}
          </div>
          {initiative.enRetard && (
            <div className="champ-plein">
              <span className="badge badge-rouge"><IconAlert size={12} /> {t('initiativeDetail.enRetard')}</span>
            </div>
          )}
        </div>
        {initiative.description && <p className="texte-muet">{initiative.description}</p>}
      </section>

      <section className="carte">
        <div className="section-entete">
          <h3>{t('initiativeDetail.etapesTitre', { nombre: etapes.length })}</h3>
          {etapes.length > 0 && (
            <span className="badge badge-vert">
              {t('initiativeDetail.etapesTerminees', { faites: etapesTerminees, total: etapes.length })}
            </span>
          )}
        </div>
        {etapes.length === 0 && <p className="texte-muet">{t('initiativeDetail.aucuneEtape')}</p>}
        <div className="etape-liste">
          {etapes.map((etape, index) => (
            <div key={etape.idEtape} className="etape-item">
              {editionEtape?.idEtape === etape.idEtape ? (
                <form className="formulaire-inline" onSubmit={soumettreEtape}>
                  <input value={editionEtape.nom} onChange={(e) => setEditionEtape({ ...editionEtape, nom: e.target.value })} required placeholder={t('initiativeDetail.nom')} />
                  <input type="number" min="0" max="100" value={editionEtape.avancement}
                    onChange={(e) => changerAvancementEtape(e, editionEtape, setEditionEtape)} placeholder="%" />
                  <select aria-label={t('initiativeDetail.ariaStatutEtape')} value={editionEtape.idStatutEtape}
                    onChange={(e) => changerStatutEtape(e, editionEtape, setEditionEtape)}>
                    {statutsEtape.map((s) => <option key={s.idStatutEtape} value={s.idStatutEtape}>{ref.statutEtape(s.libelle)}</option>)}
                  </select>
                  <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ok')}</button>
                  <button type="button" className="btn btn-secondaire btn-petit" onClick={() => setEditionEtape(null)}>{t('initiativeDetail.annuler')}</button>
                </form>
              ) : (
                <>
                  <div className="etape-texte">
                    <strong>{etape.ordre}. {etape.nom}</strong>
                    {etape.description && <small>{etape.description}</small>}
                    <span className="texte-muet">
                      {etape.dateDebut ?? '—'} → {etape.dateFinPrevue ?? '—'} · {ref.statutEtape(etape.statutEtape?.libelle)}
                    </span>
                  </div>
                  <ProgressBar valeur={etape.avancement} />
                  {peutEtapes && (
                    <div className="etape-actions">
                      <button className="btn btn-petit btn-secondaire" onClick={() => setEditionEtape({ idEtape: etape.idEtape, ...etape, idStatutEtape: etape.statutEtape?.idStatutEtape ?? '' })}>{t('initiativeDetail.editer')}</button>
                      <button className="btn btn-petit btn-danger" onClick={() => supprimerEtape(etape)}>{t('initiativeDetail.supprimer')}</button>
                      <button className="btn btn-petit btn-secondaire" onClick={() => deplacerEtape(index, -1)} disabled={index === 0} title={t('initiativeDetail.monter')} aria-label={t('initiativeDetail.monter')}>↑</button>
                      <button className="btn btn-petit btn-secondaire" onClick={() => deplacerEtape(index, 1)} disabled={index === etapes.length - 1} title={t('initiativeDetail.descendre')} aria-label={t('initiativeDetail.descendre')}>↓</button>
                    </div>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
        {peutEtapes && (
          <form className="formulaire-inline ajout" onSubmit={soumettreEtape}>
            <input value={nouvelleEtape.nom} onChange={(e) => setNouvelleEtape({ ...nouvelleEtape, nom: e.target.value })} required placeholder={t('initiativeDetail.nouvelleEtape')} />
            <input type="number" min="0" max="100" value={nouvelleEtape.avancement}
              onChange={(e) => changerAvancementEtape(e, nouvelleEtape, setNouvelleEtape)} placeholder="%" />
            <select aria-label={t('initiativeDetail.ariaStatutEtape')} value={nouvelleEtape.idStatutEtape} onChange={(e) => changerStatutEtape(e, nouvelleEtape, setNouvelleEtape)}>
              <option value="">{t('initiativeDetail.statut')}</option>
              {statutsEtape.map((s) => <option key={s.idStatutEtape} value={s.idStatutEtape}>{ref.statutEtape(s.libelle)}</option>)}
            </select>
            <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ajouter')}</button>
          </form>
        )}
      </section>

      <section className="carte">
        <div className="section-entete">
          <h3>{t('initiativeDetail.jalonsTitre', { nombre: jalons.length })}</h3>
          {jalons.length > 0 && (
            <span className="badge badge-bleu">
              {t('initiativeDetail.jalonsAtteints', { faites: jalonsAtteints, total: jalons.length })}
            </span>
          )}
        </div>
        {jalons.length === 0 && <p className="texte-muet">{t('initiativeDetail.aucunJalon')}</p>}
        <div className="etape-liste">
          {jalons.map((jalon) => (
            <div key={jalon.idJalon} className="etape-item">
              {editionJalon?.idJalon === jalon.idJalon ? (
                <form className="formulaire-inline" onSubmit={soumettreJalon}>
                  <input value={editionJalon.nom} onChange={(e) => setEditionJalon({ ...editionJalon, nom: e.target.value })} required placeholder={t('initiativeDetail.nom')} />
                  <input type="date" aria-label={t('initiativeDetail.ariaDatePrevue')} value={editionJalon.datePrevue} onChange={(e) => setEditionJalon({ ...editionJalon, datePrevue: e.target.value })} />
                  <select aria-label={t('initiativeDetail.ariaStatutJalon')} value={editionJalon.idStatutJalon}
                    onChange={(e) => setEditionJalon({ ...editionJalon, idStatutJalon: e.target.value })}>
                    {statutsJalon.map((s) => <option key={s.idStatutJalon} value={s.idStatutJalon}>{ref.statutJalon(s.libelle)}</option>)}
                  </select>
                  <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ok')}</button>
                  <button type="button" className="btn btn-secondaire btn-petit" onClick={() => setEditionJalon(null)}>{t('initiativeDetail.annuler')}</button>
                </form>
              ) : (
                <>
                  <div className="etape-texte">
                    <strong>{jalon.nom}</strong>
                    {jalon.enRetard && <span className="badge badge-rouge">{t('initiativeDetail.enRetardJalon')}</span>}
                    {jalon.description && <small>{jalon.description}</small>}
                    <span className="texte-muet">
                      {t('initiativeDetail.jalonPrevu', { date: jalon.datePrevue ?? '—', statut: ref.statutJalon(jalon.statutJalon?.libelle) ?? '' })}
                      {jalon.dateRealisation ? t('initiativeDetail.jalonRealise', { date: jalon.dateRealisation }) : ''}
                    </span>
                  </div>
                  {peutJalons && (
                    <div className="etape-actions">
                      <button className="btn btn-petit btn-secondaire" onClick={() => setEditionJalon({ idJalon: jalon.idJalon, ...jalon, idStatutJalon: jalon.statutJalon?.idStatutJalon ?? '' })}>{t('initiativeDetail.editer')}</button>
                      <button className="btn btn-petit btn-danger" onClick={() => supprimerJalon(jalon)}>{t('initiativeDetail.supprimer')}</button>
                    </div>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
        {peutJalons && (
          <form className="formulaire-inline ajout" onSubmit={soumettreJalon}>
            <input value={nouveauJalon.nom} onChange={(e) => setNouveauJalon({ ...nouveauJalon, nom: e.target.value })} required placeholder={t('initiativeDetail.nouveauJalon')} />
            <input type="date" aria-label={t('initiativeDetail.ariaDatePrevue')} value={nouveauJalon.datePrevue} onChange={(e) => setNouveauJalon({ ...nouveauJalon, datePrevue: e.target.value })} />
            <select aria-label={t('initiativeDetail.ariaStatutJalon')} value={nouveauJalon.idStatutJalon} onChange={(e) => setNouveauJalon({ ...nouveauJalon, idStatutJalon: e.target.value })}>
              <option value="">{t('initiativeDetail.statut')}</option>
              {statutsJalon.map((s) => <option key={s.idStatutJalon} value={s.idStatutJalon}>{ref.statutJalon(s.libelle)}</option>)}
            </select>
            <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ajouter')}</button>
          </form>
        )}
      </section>

      {/* Registre des risques : description, probabilité et impact notés
          1-3, responsable du plan d'action — la santé pondérée (score/9)
          est recalculée par le serveur à chaque affichage. Lecture pour
          tous les lecteurs de la fiche, écriture avec INITIATIVE_MODIFIER. */}
      <section className="carte">
        <div className="section-entete">
          <h3>{t('initiativeDetail.risquesTitre', { nombre: risques.length })}</h3>
          {risques.length > 0 && (
            <span className={risques.some((r) => r.niveau === 'ROUGE') ? 'badge badge-rouge' : 'badge badge-vert'}>
              {t('initiativeDetail.risquesCritiques', { nombre: risques.filter((r) => r.niveau === 'ROUGE').length })}
            </span>
          )}
        </div>
        {risques.length === 0 && <p className="texte-muet">{t('initiativeDetail.aucunRisque')}</p>}
        <div className="etape-liste">
          {risques.map((risque) => (
            <div key={risque.idRisque} className="etape-item">
              {editionRisque?.idRisque === risque.idRisque ? (
                <form className="formulaire-inline" onSubmit={soumettreRisque}>
                  {champsRisque(editionRisque, setEditionRisque)}
                  <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ok')}</button>
                  <button type="button" className="btn btn-secondaire btn-petit" onClick={() => setEditionRisque(null)}>{t('initiativeDetail.annuler')}</button>
                </form>
              ) : (
                <>
                  <div className="etape-texte">
                    <strong>{risque.description}</strong>
                    <span>
                      <span className="badge badge-bleu">{t('initiativeDetail.probabilite')} : {libelleNote(risque.probabilite)}</span>
                      <span className="badge badge-ambre">{t('initiativeDetail.impact')} : {libelleNote(risque.impact)}</span>
                      <span className={badgeSanteRisque(risque.niveau)}>
                        {t('initiativeDetail.santeRisque', { score: risque.score })}
                      </span>
                    </span>
                    {risque.planAction && <small>{t('initiativeDetail.planAction')} : {risque.planAction}</small>}
                    <span className="texte-muet">
                      {t('initiativeDetail.responsableRisque')} : {risque.responsable?.nomComplet ?? t('initiativeDetail.nonAffecte')}
                    </span>
                  </div>
                  {peutModifier && (
                    <div className="etape-actions">
                      <button className="btn btn-petit btn-secondaire"
                        onClick={() => setEditionRisque({
                          idRisque: risque.idRisque,
                          description: risque.description,
                          probabilite: String(risque.probabilite),
                          impact: String(risque.impact),
                          planAction: risque.planAction ?? '',
                          idResponsable: risque.responsable?.idUser ?? ''
                        })}>
                        {t('initiativeDetail.editer')}
                      </button>
                      <button className="btn btn-petit btn-danger" onClick={() => supprimerRisque(risque)}>{t('initiativeDetail.supprimer')}</button>
                    </div>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
        {peutModifier && (
          <form className="formulaire-inline ajout" onSubmit={soumettreRisque}>
            {champsRisque(nouveauRisque, setNouveauRisque)}
            <button type="submit" className="btn btn-primaire btn-petit">{t('initiativeDetail.ajouter')}</button>
          </form>
        )}
      </section>

      {/* Journal des modifications : qui a changé quoi, quand et pourquoi —
          toutes les lignes de l'initiative (initiative, étapes, jalons),
          filtrables par type et paginées « Charger plus ». Lisible par tous
          ceux qui peuvent voir la fiche (INITIATIVE_CONSULTER), y compris le
          Visiteur : c'est le suivi, pas une écriture. */}
      <section className="carte">
        <div className="section-entete">
          <h3>{t('initiativeDetail.historiqueTitre')}</h3>
          {historiqueTotal > 0 && (
            <span className="badge badge-gris">{historiqueTotal}</span>
          )}
        </div>
        {/* Les commandes (filtres + « Voir tout ») restent affichées même
            quand le filtre courant ne ramène rien : sinon la pastille
            active devient une impasse — aucun moyen de revenir à
            « Tous ». Elles ne disparaissent que sur une fiche sans aucun
            historique et sans filtre posé. */}
        {(historiqueTotal > 0 || historiqueType) && (
          <>
            <div className="section-entete-actions">
              <p className="texte-muet historique-resume">{t('initiativeDetail.historiqueResume')}</p>
              {/* Le journal complet vit sur sa propre page : cette fiche ne
                  garde qu'un fil de lecture, avec un filtre déjà posé sur lui. */}
              <Link
                to={`/historique?initiative=${id}`}
                className="btn btn-petit btn-secondaire"
              >
                {t('initiativeDetail.voirToutHistorique')}
              </Link>
            </div>
            <div className="historique-filtres">
              <div
                className="vue-toggle"
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
                    className={historiqueType === cle ? 'actif' : ''}
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
          <p className="texte-muet">
            {historiqueType
              ? t('initiativeDetail.aucunHistoriqueFiltre')
              : t('initiativeDetail.aucunHistorique')}
          </p>
        ) : (
          <>
            <ol className="historique">
              {historique.map((ev) => (
                <li key={ev.id} className="historique-item">
                  <span className="historique-date">
                    {new Date(ev.dateChangement).toLocaleString(langue, {
                      day: '2-digit', month: 'short', year: 'numeric',
                      hour: '2-digit', minute: '2-digit'
                    })}
                  </span>
                  <span className="historique-transition">
                    <span className={`badge ${badgeTypeJournal(ev.typeObjet)}`}>
                      {libelleType(t, ev.typeObjet)}
                    </span>
                    {texteEvenement(t, ev)}
                  </span>
                  {ev.commentaire && (
                    <span className="historique-commentaire">{ev.commentaire}</span>
                  )}
                  <span className="historique-auteur">
                    {ev.utilisateur?.nomComplet ?? t('initiativeDetail.auteurInconnu')}
                    {ev.automatique ? ` · ${t('historique.origineAutomatique')}` : ''}
                  </span>
                </li>
              ))}
            </ol>
            {historique.length < historiqueTotal && (
              <button
                type="button"
                className="btn btn-petit btn-secondaire historique-plus"
                onClick={() => chargerHistorique(historiquePage + 1)}
                disabled={historiquePage + 1 >= Math.ceil(historiqueTotal / TAILLE_HISTORIQUE)}
              >
                {t('initiativeDetail.historiqueChargerPlus')}
              </button>
            )}
          </>
        )}
      </section>
    </div>
  );
}
