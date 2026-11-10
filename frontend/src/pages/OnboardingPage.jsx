import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '../components/ui/card';

/**
 * Assistant d'installation en 3 étapes (Phase 2B5) :
 *  1. votre organisation (créee a l'inscription),
 *  2. votre première initiative,
 *  3. invitation d'un collegue.
 *
 * A la fin, le drapeau localStorage onboardingDone est leve et
 * l'utilisateur est redirige vers le tableau de bord.
 *
 * Migree en Tailwind + shadcn/ui (Phase 3). Les cles i18n sont conservees
 * telles quelles pour les tests vitest (t: (k) => k).
 */
export default function OnboardingPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [etape, setEtape] = useState(1);
  const [termine, setTermine] = useState(false);
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState('');

  // --- Etape 2 : premiere initiative
  const [init, setInit] = useState({ code: '', nom: '', description: '' });
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], priorites: [] });

  // --- Etape 3 : invitation
  const [invite, setInvite] = useState({ email: '', idRole: '' });
  const [roles, setRoles] = useState([]);

  const set = (objet, champ) => (e) => setObjet(objet, champ, e.target.value);
  const setObjet = (objet, champ, valeur) => {
    if (objet === 'init') setInit((f) => ({ ...f, [champ]: valeur }));
    else setInvite((f) => ({ ...f, [champ]: valeur }));
  };

  const suivant = () => setEtape((e) => Math.min(e + 1, 3));
  const precedent = () => setEtape((e) => Math.max(e - 1, 1));

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/priorites'),
      api.get('/roles')
    ])
      .then(([s, e, p, r]) => {
        setReferentiels({
          statuts: s.data,
          entites: e.data,
          priorites: p.data
        });
        setRoles(r.data);
      })
      .catch(() => {});
  }, []);

  const creerInitiative = async (e) => {
    e.preventDefault();
    setErreur('');
    if (!init.code || !init.nom) {
      setErreur(t('onboarding.initiative') + ' et ' + t('onboarding.initiativeDesc') + ' sont obligatoires.');
      return;
    }
    setChargement(true);
    try {
      const statut = referentiels.statuts[0];
      const entite = referentiels.entites[0];
      const priorite = referentiels.priorites[0];
      await api.post('/api/initiatives', {
        code: init.code,
        nom: init.nom,
        description: init.description || null,
        budget: 0,
        avancement: 0,
        dateDebut: null,
        dateFinPrevue: null,
        idEntite: entite ? Number(entite.idEntite) : null,
        idStatut: statut ? Number(statut.idStatut) : null,
        idPriorite: priorite ? Number(priorite.idPriorite) : null,
        idObjectif: null
      });
      setEtape(3);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  const inviter = async (e) => {
    e.preventDefault();
    setErreur('');
    if (!invite.email || !invite.idRole) {
      setErreur(t('onboarding.invite') + ' et ' + t('onboarding.role') + ' sont obligatoires.');
      return;
    }
    setChargement(true);
    try {
      await api.post('/api/auth/inviter', {
        email: invite.email.trim().toLowerCase(),
        idRole: Number(invite.idRole)
      });
      setTermine(true);
      localStorage.setItem('onboardingDone', '1');
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  const allerDashboard = () => navigate('/dashboard', { replace: true });

  const titre = t('onboarding.titre');
  const sousTitre = t('onboarding.sousTitre');

  if (termine) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background p-4">
        <Card className="w-full max-w-md shadow-xl">
          <CardHeader className="text-center">
            <CardTitle>{t('onboarding.termine')}</CardTitle>
            <CardDescription>{t('onboarding.bravo')}</CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <p>{t('onboarding.etape3Desc')}</p>
            <Button
              className="mt-4"
              onClick={allerDashboard}
            >
              {t('onboarding.allerDashboard')}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const etapeCourante = [
    { numero: 1, titre: t('onboarding.etape1'), texte: t('onboarding.etape1Desc') },
    { numero: 2, titre: t('onboarding.etape2'), texte: t('onboarding.etape2Desc') },
    { numero: 3, titre: t('onboarding.etape3'), texte: t('onboarding.etape3Desc') }
  ][etape - 1];

  return (
    <div className="min-h-screen bg-background p-4">
      <header className="mb-6">
        <div>
          <h1 className="text-2xl font-semibold">{titre}</h1>
          <p className="text-muted-foreground">{sousTitre}</p>
        </div>
      </header>

      {erreur && (
        <div className="rounded-md border border-destructive/50 bg-destructive/50 p-3 text-destructive text-sm mb-4">
          {erreur}
        </div>
      )}

      <div className="flex items-center justify-between mb-6">
        {[1, 2, 3].map((n) => (
          <div
            key={n}
            className={`flex items-center space-x-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              n === etape
                ? 'bg-primary text-primary-foreground'
                : n < etape
                  ? 'bg-muted text-muted-foreground'
                  : 'bg-background text-muted-foreground'
            }`}
          >
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
              n === etape
                ? 'bg-primary-foreground text-primary'
                : n < etape
                  ? 'bg-primary text-primary-foreground'
                  : 'border border-input'
            }`}>{n}</span>
            <span>{etapeCourante.numero === n ? etapeCourante.titre : ''}</span>
          </div>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{etapeCourante.titre}</CardTitle>
          <CardDescription>{etapeCourante.texte}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Etape 1 : organisation (deja creee a l'inscription) */}
          {etape === 1 && (
            <>
              <p>{t('onboarding.etape1Desc')}</p>
              <p className="text-sm text-muted-foreground"><em>{user?.email}</em></p>
            </>
          )}

          {/* Etape 2 : creer la premiere initiative */}
          {etape === 2 && (
            <form onSubmit={creerInitiative} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="code">{t('onboarding.initiative')}</Label>
                  <Input
                    id="code"
                    value={init.code}
                    onChange={set('init', 'code')}
                    placeholder="DIG-CLI-001"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="nom">{t('initiativeForm.nom')}</Label>
                  <Input
                    id="nom"
                    value={init.nom}
                    onChange={set('init', 'nom')}
                    placeholder="Ex. Digitalisation client"
                    required
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="description">{t('initiativeForm.description')}</Label>
                <textarea
                  id="description"
                  value={init.description}
                  onChange={set('init', 'description')}
                  rows={3}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                />
              </div>
              <div className="flex items-center justify-between space-y-0 md:space-y-0 md:flex-row">
                <Button type="button" variant="outline" disabled={chargement} onClick={precedent}>
                  {t('initiativeForm.annuler')}
                </Button>
                <Button type="submit" className="ml-4" disabled={chargement}>
                  {chargement ? t('initiativeForm.enregistrement') : t('onboarding.creerInit')}
                </Button>
              </div>
            </form>
          )}

          {/* Etape 3 : inviter un collegue */}
          {etape === 3 && (
            <form onSubmit={inviter} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="inviteEmail">{t('onboarding.invite')}</Label>
                  <Input
                    id="inviteEmail"
                    type="email"
                    value={invite.email}
                    onChange={set('invite', 'email')}
                    placeholder="colleague@example.com"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="inviteRole">{t('onboarding.role')}</Label>
                  <select
                    id="inviteRole"
                    value={invite.idRole}
                    onChange={set('invite', 'idRole')}
                    required
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <option value="">—</option>
                    {roles.map((r) => (
                      <option key={r.idRole} value={r.idRole}>{r.libelle || r.code}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex items-center justify-between space-y-0 md:space-y-0 md:flex-row">
                <Button type="button" variant="outline" onClick={precedent}>
                  {t('initiativeForm.annuler')}
                </Button>
                <Button type="submit" className="ml-4" disabled={chargement}>
                  {chargement ? t('onboarding.enCours') : t('onboarding.suivant')}
                </Button>
              </div>
            </form>
          )}
        </CardContent>
        <CardFooter className="flex items-center justify-between space-y-0 md:space-y-0 md:flex-row">
          {etape > 1 && (
            <Button type="button" variant="outline" onClick={precedent}>
              {t('initiativeForm.annuler')}
            </Button>
          )}
          {etape < 3 && (
            <Button type="button" onClick={suivant}>
              {t('onboarding.suivant')}
            </Button>
          )}
        </CardFooter>
      </Card>
    </div>
  );
}
