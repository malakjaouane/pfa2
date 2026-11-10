import { useEffect, useState } from 'react';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { useToast } from '../components/ui/use-toast';

/**
 * Gestion des rôles et de leurs permissions (administration).
 */
export default function RolesPage() {
  const { t } = useI18n();
  const ref = useReferentiels();
  const { toast } = useToast();
  const [roles, setRoles] = useState([]); // détail avec permissions
  const [permissions, setPermissions] = useState([]);
  const [selection, setSelection] = useState({}); // idRole -> Set<idPermission>

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
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
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
      toast({
        title: afficherMessage(t, { cle: 'roles.permissionsEnregistrees', params: { role: ref.role(role.code, role.libelle) } }),
        variant: 'success'
      });
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('roles.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('roles.sousTitre')}
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {roles.map((role) => (
          <Card key={role.idRole} className="p-[18px] shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <div>
                <h3 className="text-base font-semibold text-foreground">
                  {ref.role(role.code, role.libelle)}
                </h3>
                <Badge variant="secondary" className="ml-2">
                  {role.code}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="pt-0">
              <p className="mb-3 text-xs text-muted-foreground">
                {t('roles.permissionsActuelles', { nombre: role.permissions.length })}
              </p>
              <div className="space-y-1">
                {permissions.map((perm) => {
                  const coche = selection[role.idRole]?.has(perm.idPermission);
                  return (
                    <label
                      key={perm.idPermission}
                      className="flex items-center gap-2 cursor-pointer select-none rounded-md border border-transparent p-1.5 hover:bg-muted transition-colors"
                    >
                      <input
                        type="checkbox"
                        checked={coche}
                        onChange={() => basculer(role.idRole, perm.idPermission)}
                        className="rounded border-input text-primary focus:ring-ring"
                      />
                      <span className="text-sm">
                        {ref.permission(perm.code, perm.libelle)}
                      </span>
                      <small className="ml-1 text-[11px] text-muted-foreground">
                        {perm.code}
                      </small>
                    </label>
                  );
                })}
              </div>
              <div className="mt-4 flex justify-end">
                <Button
                  type="button"
                  onClick={() => enregistrer(role)}
                  className="w-full sm:w-auto"
                >
                  {t('roles.enregistrer')}
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
