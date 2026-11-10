import { useEffect, useState } from 'react';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import { useToast } from '../components/ui/use-toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '../components/ui/table';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '../components/ui/sheet';

const vide = { nom: '', prenom: '', email: '', motDePasse: '', telephone: '', actif: true, idRole: '', idEntite: '' };

/**
 * Gestion des utilisateurs (administration).
 */
export default function UsersPage() {
  const { t } = useI18n();
  const ref = useReferentiels();
  const { user, hasPermission } = useAuth();
  // La page est déjà réservée à UTILISATEUR_GERER (route + API) : le droit
  // est relu ici pour n'afficher la colonne que sur un compte qui l'a.
  const peutSupprimer = hasPermission('UTILISATEUR_GERER');
  const [utilisateurs, setUtilisateurs] = useState([]);
  const [roles, setRoles] = useState([]);
  const [entites, setEntites] = useState([]);
  const [form, setForm] = useState(vide);
  const [editionId, setEditionId] = useState(null);
  const [modalOuvert, setModalOuvert] = useState(false);
  const { toast } = useToast();
  const [critere, setCritere] = useState({ recherche: '', role: '', entite: '' });

  const charger = () => {
    api.get('/users').then((res) => setUtilisateurs(res.data)).catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
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
    setModalOuvert(true);
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
    setModalOuvert(true);
  };

  const gererSoumission = async (e) => {
    e.preventDefault();
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
      setModalOuvert(false);
      toast({ title: t(editionId ? 'users.utilisateurModifie' : 'users.utilisateurCree'), variant: 'success' });
      charger();
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  /**
   * Suppression définitive d'un compte (confirmation puis rechargement).
   * Le bouton est masqué sur son propre compte ; le serveur refuse aussi :
   * soi-même, le dernier administrateur actif et un compte qui porte
   * encore des demandes (400 avec message explicite).
   */
  const supprimer = (u) => {
    const nom = `${u.prenom} ${u.nom}`;
    if (!window.confirm(t('users.supprimerConfirmer', { nom }))) return;
    api.delete(`/users/${u.idUser}`)
      .then(() => {
        toast({ title: t('users.utilisateurSupprime'), variant: 'success' });
        charger();
      })
      .catch((err) => {
        toast({ title: messageErreur(err), variant: 'destructive' });
      });
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('users.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('users.sousTitre')}
          </p>
        </div>
        <Button type="button" onClick={ouvrirCreation}>
          {t('users.nouvelUtilisateur')}
        </Button>
      </header>

      {/* Recherche + filtres (rôle, entité) sur l'ensemble des utilisateurs. */}
      <Card className="mb-6 p-[18px] shadow-sm" role="search">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('users.recherchePlaceholder')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label htmlFor="filtreRecherche" className="text-xs font-medium text-muted-foreground">
                {t('users.recherchePlaceholder')}
              </Label>
              <Input
                id="filtreRecherche"
                type="search"
                placeholder={t('users.recherchePlaceholder')}
                aria-label={t('users.recherchePlaceholder')}
                value={critere.recherche}
                onChange={champCritere('recherche')}
              />
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('users.ariaFiltreRole')}
              </Label>
              <select
                aria-label={t('users.ariaFiltreRole')}
                value={critere.role}
                onChange={champCritere('role')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('users.tousRoles')}</option>
                {roles.map((r) => (
                  <option key={r.idRole} value={r.idRole}>{ref.role(r.code, r.libelle)}</option>
                ))}
              </select>
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('users.ariaFiltreEntite')}
              </Label>
              <select
                aria-label={t('users.ariaFiltreEntite')}
                value={critere.entite}
                onChange={champCritere('entite')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('users.tousEntites')}</option>
                {entites.map((e) => (
                  <option key={e.idEntite} value={e.idEntite}>{e.nom}</option>
                ))}
              </select>
            </div>
            <Button type="button" variant="secondary" onClick={reinitialiser}>
              {t('users.reinitialiser')}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        {utilisateursFiltres.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('users.aucunResultat')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('users.colNom')}</TableHead>
                <TableHead>{t('users.colEmail')}</TableHead>
                <TableHead>{t('users.colTelephone')}</TableHead>
                <TableHead>{t('users.colRole')}</TableHead>
                <TableHead>{t('users.colEntite')}</TableHead>
                <TableHead>{t('users.colActif')}</TableHead>
                <TableHead className="text-right">{t('users.colActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {utilisateursFiltres.map((u) => (
                <TableRow key={u.idUser}>
                  <TableCell>{u.prenom} {u.nom}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>{u.telephone ?? t('users.aucun')}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{ref.role(u.role?.code, u.role?.libelle)}</Badge>
                  </TableCell>
                  <TableCell>{u.entite?.nom ?? t('users.aucun')}</TableCell>
                  <TableCell>
                    {u.actif
                      ? <Badge variant="secondary" className="border-transparent bg-success/15 text-success">{t('users.oui')}</Badge>
                      : <Badge variant="secondary" className="border-transparent bg-destructive/15 text-destructive">{t('users.non')}</Badge>}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => ouvrirEdition(u)}
                    >
                      {t('users.modifier')}
                    </Button>
                    {peutSupprimer && user?.idUser !== u.idUser && (
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        onClick={() => supprimer(u)}
                        className="ml-1"
                      >
                        {t('users.supprimer')}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Sheet open={modalOuvert} onOpenChange={setModalOuvert}>
        <SheetTrigger asChild>
          <button type="button" />
        </SheetTrigger>
        <SheetContent className="sm:max-w-md">
          <SheetHeader>
            <SheetTitle>
              {editionId ? t('users.modifierTitre') : t('users.creerTitre')}
            </SheetTitle>
            <SheetDescription>
              {editionId ? t('users.modifierDescription') : t('users.creerDescription')}
            </SheetDescription>
          </SheetHeader>
          <form className="space-y-4" onSubmit={gererSoumission}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="userPrenom">{t('users.champPrenom')}</Label>
                <Input
                  id="userPrenom"
                  value={form.prenom}
                  onChange={set('prenom')}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="userNom">{t('users.champNom')}</Label>
                <Input
                  id="userNom"
                  value={form.nom}
                  onChange={set('nom')}
                  required
                />
              </div>
              <div className="md:col-span-2 space-y-2">
                <Label htmlFor="userEmail">{t('users.champEmail')}</Label>
                <Input
                  id="userEmail"
                  type="email"
                  value={form.email}
                  onChange={set('email')}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="userMotDePasse">
                  {t('users.champMotDePasse')} {editionId ? t('users.optionnel') : ' '}
                  <span className="text-destructive">{!editionId ? '*' : ''}</span>
                </Label>
                <Input
                  id="userMotDePasse"
                  type="password"
                  value={form.motDePasse}
                  onChange={set('motDePasse')}
                  required={!editionId}
                  placeholder={editionId ? t('users.motDePassePlaceholder') : ''}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="userTelephone">{t('users.champTelephone')}</Label>
                <Input
                  id="userTelephone"
                  value={form.telephone}
                  onChange={set('telephone')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="userRole">{t('users.champRole')}</Label>
                <select
                  id="userRole"
                  value={form.idRole}
                  onChange={set('idRole')}
                  required
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">{t('users.aucun')}</option>
                  {roles.map((r) => (
                    <option key={r.idRole} value={r.idRole}>{ref.role(r.code, r.libelle)}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="userEntite">{t('users.champEntite')}</Label>
                <select
                  id="userEntite"
                  value={form.idEntite}
                  onChange={set('idEntite')}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">{t('users.aucun')}</option>
                  {entites.map((e) => (
                    <option key={e.idEntite} value={e.idEntite}>{e.nom}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="userActif">{t('users.champCompteActif')}</Label>
                <select
                  id="userActif"
                  value={form.actif ? 'true' : 'false'}
                  onChange={(e) => setForm((f) => ({ ...f, actif: e.target.value === 'true' }))}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="true">{t('users.oui')}</option>
                  <option value="false">{t('users.non')}</option>
                </select>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-4">
              <Button type="button" variant="secondary" onClick={() => setModalOuvert(false)}>
                {t('users.annuler')}
              </Button>
              <Button type="submit" className="ml-2">
                {t('users.enregistrer')}
              </Button>
            </div>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
