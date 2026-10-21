import { useEffect, useState } from 'react';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';

const vide = { nom: '', prenom: '', email: '', motDePasse: '', telephone: '', actif: true, idRole: '', idEntite: '' };

/**
 * Gestion des utilisateurs (administration).
 */
export default function UsersPage() {
  const { t } = useI18n();
  const ref = useReferentiels();
  const [utilisateurs, setUtilisateurs] = useState([]);
  const [roles, setRoles] = useState([]);
  const [entites, setEntites] = useState([]);
  const [form, setForm] = useState(vide);
  const [editionId, setEditionId] = useState(null);
  const [modal, setModal] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');
  const [critere, setCritere] = useState({ recherche: '', role: '', entite: '' });

  const charger = () => {
    api.get('/users').then((res) => setUtilisateurs(res.data)).catch((err) => setErreur(messageErreur(err)));
  };

  useEffect(() => {
    charger();
    api.get('/roles').then((res) => setRoles(res.data)).catch(() => {});
    api.get('/referentiels/entites').then((res) => setEntites(res.data)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Normalisation d'une recherche : insensible à la casse et aux accents. */
  const normaliser = (v) => (v ?? '').toString().toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  /** Forme purement numérique (pour comparer des téléphones saisies formatés). */
  const chiffresSeuls = (v) => (v ?? '').toString().replace(/\D/g, '');

  /**
   * Filtre combiné (ET) appliqué sur TOUS les utilisateurs renvoyés par l'API :
   * `GET /users` retourne la liste complète (`findAllByOrderByIdUserAsc`),
   * sans pagination ni troncature — la recherche porte donc sur l'ensemble
   * des comptes accessibles, pas seulement sur des lignes affichées.
   * Trois critères : nom complet, e-mail ou téléphone / rôle / entité.
   */
  const utilisateursFiltres = utilisateurs.filter((u) => {
    const q = normaliser(critere.recherche).trim();
    if (q) {
      const trouveNom = normaliser(`${u.prenom} ${u.nom}`).includes(q);
      const trouveEmail = normaliser(u.email).includes(q);
      // Comparaison par chiffres uniquement si la requête est formatée comme
      // un téléphone ("06 22", "+212…") : sinon « it6 » d'un e-mail déclencherait
      // une fausse correspondance sur les numéros (tous commencent par 06).
      const requeteTelephone = /^[+\d\s().\-/]+$/.test(q);
      const trouveTel = requeteTelephone
        ? chiffresSeuls(u.telephone).includes(chiffresSeuls(q))
        : normaliser(u.telephone).includes(q);
      if (!trouveNom && !trouveEmail && !trouveTel) return false;
    }
    if (critere.role && String(u.role?.idRole) !== critere.role) return false;
    if (critere.entite && String(u.entite?.idEntite) !== critere.entite) return false;
    return true;
  });

  const champCritere = (champ) => (e) => setCritere((c) => ({ ...c, [champ]: e.target.value }));
  const reinitialiser = () => setCritere({ recherche: '', role: '', entite: '' });

  const ouvrirCreation = () => {
    setEditionId(null);
    setForm(vide);
    setModal(true);
  };

  const ouvrirEdition = (u) => {
    setEditionId(u.idUser);
    setForm({
      nom: u.nom,
      prenom: u.prenom,
      email: u.email,
      motDePasse: '',
      telephone: u.telephone ?? '',
      actif: u.actif,
      idRole: u.role?.idRole ?? '',
      idEntite: u.entite?.idEntite ?? ''
    });
    setModal(true);
  };

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');
    const corps = {
      ...form,
      actif: Boolean(form.actif),
      idRole: Number(form.idRole),
      idEntite: form.idEntite ? Number(form.idEntite) : null
    };
    try {
      if (editionId) {
        await api.put(`/users/${editionId}`, corps);
      } else {
        await api.post('/users', corps);
      }
      setModal(false);
      setInfo(editionId
        ? { cle: 'users.utilisateurModifie' }
        : { cle: 'users.utilisateurCree' });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('users.titre')}</h2>
          <p>{t('users.sousTitre')}</p>
        </div>
        <button className="btn btn-primaire" onClick={ouvrirCreation}>{t('users.nouvelUtilisateur')}</button>
      </header>

      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      {/* Recherche + filtres (rôle, entité) sur l'ensemble des utilisateurs. */}
      <div className="carte filtre-bar" role="search">
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('users.recherchePlaceholder')}
            aria-label={t('users.recherchePlaceholder')}
            value={critere.recherche}
            onChange={champCritere('recherche')}
          />
          <select aria-label={t('users.ariaFiltreRole')} value={critere.role} onChange={champCritere('role')}>
            <option value="">{t('users.tousRoles')}</option>
            {roles.map((r) => (
              <option key={r.idRole} value={r.idRole}>{ref.role(r.code, r.libelle)}</option>
            ))}
          </select>
          <select aria-label={t('users.ariaFiltreEntite')} value={critere.entite} onChange={champCritere('entite')}>
            <option value="">{t('users.tousEntites')}</option>
            {entites.map((e) => (
              <option key={e.idEntite} value={e.idEntite}>{e.nom}</option>
            ))}
          </select>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>{t('users.reinitialiser')}</button>
        </div>
      </div>

      <div className="carte">
        {utilisateursFiltres.length === 0 ? (
          <p className="texte-muet">{t('users.aucunResultat')}</p>
        ) : (
        <div className="table-enveloppe">
          <table className="table">
            <thead>
              <tr>
                <th>{t('users.colNom')}</th>
                <th>{t('users.colEmail')}</th>
                <th>{t('users.colTelephone')}</th>
                <th>{t('users.colRole')}</th>
                <th>{t('users.colEntite')}</th>
                <th>{t('users.colActif')}</th>
                <th>{t('users.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {utilisateursFiltres.map((u) => (
                <tr key={u.idUser}>
                  <td>{u.prenom} {u.nom}</td>
                  <td>{u.email}</td>
                  <td>{u.telephone ?? t('users.aucun')}</td>
                  <td><span className="badge badge-bleu">{ref.role(u.role?.code, u.role?.libelle)}</span></td>
                  <td>{u.entite?.nom ?? t('users.aucun')}</td>
                  <td>{u.actif ? <span className="badge badge-vert">{t('users.oui')}</span> : <span className="badge badge-rouge">{t('users.non')}</span>}</td>
                  <td><button className="btn btn-petit btn-secondaire" onClick={() => ouvrirEdition(u)}>{t('users.modifier')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        )}
      </div>

      {modal && (
        <div className="modal-fond" onClick={() => setModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{editionId ? t('users.modifierTitre') : t('users.creerTitre')}</h3>
            {erreur && <div className="alerte alerte-erreur">{erreur}</div>}
            <form className="formulaire" onSubmit={gererSoumission}>
              <div className="form-grille">
                <div className="champ">
                  <label>{t('users.champPrenom')}</label>
                  <input value={form.prenom} onChange={set('prenom')} required />
                </div>
                <div className="champ">
                  <label>{t('users.champNom')}</label>
                  <input value={form.nom} onChange={set('nom')} required />
                </div>
                <div className="champ">
                  <label>{t('users.champEmail')}</label>
                  <input type="email" value={form.email} onChange={set('email')} required />
                </div>
                <div className="champ">
                  <label>{t('users.champMotDePasse')} {editionId ? t('users.optionnel') : '*'}</label>
                  <input type="password" value={form.motDePasse} onChange={set('motDePasse')}
                    required={!editionId} disabled={false} placeholder={editionId ? t('users.motDePassePlaceholder') : ''} />
                </div>
                <div className="champ">
                  <label>{t('users.champTelephone')}</label>
                  <input value={form.telephone} onChange={set('telephone')} />
                </div>
                <div className="champ">
                  <label htmlFor="user-role">{t('users.champRole')}</label>
                  <select id="user-role" value={form.idRole} onChange={set('idRole')} required>
                    <option value="">{t('users.aucun')}</option>
                    {roles.map((r) => <option key={r.idRole} value={r.idRole}>{ref.role(r.code, r.libelle)}</option>)}
                  </select>
                </div>
                <div className="champ">
                  <label htmlFor="user-entite">{t('users.champEntite')}</label>
                  <select id="user-entite" value={form.idEntite} onChange={set('idEntite')}>
                    <option value="">{t('users.aucun')}</option>
                    {entites.map((e) => <option key={e.idEntite} value={e.idEntite}>{e.nom}</option>)}
                  </select>
                </div>
                <div className="champ">
                  <label htmlFor="user-actif">{t('users.champCompteActif')}</label>
                  <select id="user-actif" value={form.actif ? 'true' : 'false'} onChange={(e) => setForm((f) => ({ ...f, actif: e.target.value === 'true' }))}>
                    <option value="true">{t('users.oui')}</option>
                    <option value="false">{t('users.non')}</option>
                  </select>
                </div>
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-secondaire" onClick={() => setModal(false)}>{t('users.annuler')}</button>
                <button type="submit" className="btn btn-primaire">{t('users.enregistrer')}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
