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

  const charger = () => {
    api.get('/users').then((res) => setUtilisateurs(res.data)).catch((err) => setErreur(messageErreur(err)));
  };

  useEffect(() => {
    charger();
    api.get('/roles').then((res) => setRoles(res.data)).catch(() => {});
    api.get('/referentiels/entites').then((res) => setEntites(res.data)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

      <div className="carte">
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
              {utilisateurs.map((u) => (
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
