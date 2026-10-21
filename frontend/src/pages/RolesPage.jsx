import { useEffect, useState } from 'react';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';

/**
 * Gestion des rôles et de leurs permissions (administration).
 */
export default function RolesPage() {
  const { t } = useI18n();
  const ref = useReferentiels();
  const [roles, setRoles] = useState([]); // détail avec permissions
  const [permissions, setPermissions] = useState([]);
  const [selection, setSelection] = useState({}); // idRole -> Set<idPermission>
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  const charger = async () => {
    try {
      const [r, p] = await Promise.all([api.get('/roles'), api.get('/permissions')]);
      const details = await Promise.all(r.data.map((role) => api.get(`/roles/${role.idRole}`)));
      const rolesDetaille = details.map((d) => d.data);
      setRoles(rolesDetaille);
      setPermissions(p.data);
      const sel = {};
      rolesDetaille.forEach((role) => {
        sel[role.idRole] = new Set(role.permissions.map((perm) => perm.idPermission));
      });
      setSelection(sel);
      setErreur('');
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  useEffect(() => {
    charger();
  }, []);

  const basculer = (idRole, idPermission) => {
    setSelection((sel) => {
      const s = new Set(sel[idRole] ?? []);
      if (s.has(idPermission)) {
        s.delete(idPermission);
      } else {
        s.add(idPermission);
      }
      return { ...sel, [idRole]: s };
    });
  };

  const enregistrer = async (role) => {
    try {
      await api.put(`/roles/${role.idRole}/permissions`, { idsPermissions: [...(selection[role.idRole] ?? [])] });
      setInfo({ cle: 'roles.permissionsEnregistrees', params: { role: ref.role(role.code, role.libelle) } });
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('roles.titre')}</h2>
          <p>{t('roles.sousTitre')}</p>
        </div>
      </header>

      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      <div className="roles-grille">
        {roles.map((role) => (
          <div key={role.idRole} className="carte role-carte">
            <div className="role-entete">
              <h3>{ref.role(role.code, role.libelle)}</h3>
              <span className="badge badge-gris">{role.code}</span>
            </div>
            <p className="texte-muet">{t('roles.permissionsActuelles', { nombre: role.permissions.length })}</p>
            <div className="perm-liste">
              {permissions.map((perm) => {
                const coche = selection[role.idRole]?.has(perm.idPermission);
                return (
                  <label key={perm.idPermission} className={`perm-ligne ${coche ? 'perm-cochee' : ''}`}>
                    <input
                      type="checkbox"
                      checked={coche}
                      onChange={() => basculer(role.idRole, perm.idPermission)}
                    />
                    <span>{ref.permission(perm.code, perm.libelle)}</span>
                    <small>{perm.code}</small>
                  </label>
                );
              })}
            </div>
            <button className="btn btn-primaire btn-block" onClick={() => enregistrer(role)}>{t('roles.enregistrer')}</button>
          </div>
        ))}
      </div>
    </div>
  );
}