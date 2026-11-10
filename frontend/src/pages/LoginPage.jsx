import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import MotifZellige from '../components/MotifZellige';
import { messageErreur } from '../api';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';

/**
 * Page de connexion (POST /api/auth/login).
 *
 * Migree en Tailwind + shadcn/ui (Phase 3). Les cles i18n sont conservees
 * telles quelles afin que les tests vitest (t: (k) => k) continuent de
 * matcher les textes bruts (login.seConnecter, login.oublieLien…).
 */
export default function LoginPage() {
  const { login, user } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [afficherMdp, setAfficherMdp] = useState(false);
  const [erreur, setErreur] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [motDePasseOublie, setMotDePasseOublie] = useState(false);

  if (user) {
    navigate('/dashboard', { replace: true });
  }

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');

    // On lit directement les champs du formulaire (et non uniquement l'état
    // React) : le gestionnaire de mots de passe du navigateur peut remplir
    // les champs sans déclencher onChange, laissant l'état obsolète et
    // envoyant ainsi un mot de passe erroné. L'email est en outre débarrassé
    // des espaces parasites.
    const form = e.currentTarget;
    const emailSaisi = (form.elements.email?.value ?? email).trim();
    const mdpSaisi = form.elements.motDePasse?.value ?? motDePasse;
    setEmail(emailSaisi);

    setEnvoi(true);
    try {
      await login(emailSaisi, mdpSaisi);
      navigate(location.state?.from?.pathname ?? '/dashboard', { replace: true });
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <MotifZellige className="absolute inset-0 opacity-30 pointer-events-none" />
      <Card className="w-full max-w-md shadow-xl">
        <CardHeader className="text-center">
          <Link to="/" className="inline-flex items-center text-sm text-muted-foreground hover:text-primary mb-4">
            ← {t('login.retour')}
          </Link>
          <div className="flex justify-end">
            <LanguageSwitcher />
          </div>
          <CardTitle className="text-2xl">{t('login.titre') ?? 'IT6 Consulting'}</CardTitle>
          <CardDescription>{t('login.sousTitre')}</CardDescription>
          {t('login.compteCree') && <p className="text-xs text-muted-foreground mt-2">{t('login.compteCree')}</p>}
        </CardHeader>
        <CardContent>
          {erreur && (
            <div className="rounded-md border border-destructive/50 bg-destructive/50 p-3 text-destructive text-sm mb-4">
              {erreur}
            </div>
          )}
          <form className="space-y-4" onSubmit={gererSoumission}>
            <div className="space-y-2">
              <Label htmlFor="email">{t('login.email')}</Label>
              <Input
                id="email"
                type="email"
                name="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="email@it6.ma"
                autoComplete="username"
                required
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="motDePasse">{t('login.motDePasse')}</Label>
              </div>
              <Input
                id="motDePasse"
                type={afficherMdp ? 'text' : 'password'}
                name="motDePasse"
                value={motDePasse}
                onChange={(e) => setMotDePasse(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                required
              />
            </div>
            <div className="flex items-center space-x-2">
              <input
                id="afficherMdp"
                type="checkbox"
                checked={afficherMdp}
                onChange={(e) => setAfficherMdp(e.target.checked)}
                className="h-4 w-4 rounded border-input text-primary"
              />
              <Label htmlFor="afficherMdp" className="text-xs cursor-pointer select-none">
                {t('login.afficherMdp')}
              </Label>
            </div>
            <Button
              type="submit"
              className="w-full"
              disabled={envoi}
            >
              {envoi ? t('login.connexion') : t('login.seConnecter')}
            </Button>
            <button
              type="button"
              onClick={() => setMotDePasseOublie((v) => !v)}
              aria-expanded={motDePasseOublie}
              className="text-xs text-muted-foreground hover:text-primary underline-offset-4 hover:underline"
            >
              {t('login.oublieLien')}
            </button>
            {motDePasseOublie && (
              <div className="mt-3 p-3 rounded-md bg-primary/5 text-primary text-sm">
                {t('login.oublie')}
              </div>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
