import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import MotifZellige from '../components/MotifZellige';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';

/**
 * Page d'inscription (POST /api/auth/signup).
 *
 * Crée une organisation + son premier compte administrateur. Le compte est
 * créé avec l'adresse e-mail non vérifiée : un e-mail de vérification est
 * envoyé et le compte ne peut pas se connecter tant qu'il n'est pas confirmé.
 *
 * Migree en Tailwind + shadcn/ui (Phase 3).
 */
export default function InscriptionPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({
    nom: '', prenom: '', email: '', motDePasse: '', nomOrganisation: ''
  });
  const [succes, setSucces] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [afficherMdp, setAfficherMdp] = useState(false);
  const [erreur, setErreur] = useState('');

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');
    setSucces(false);

    if (form.motDePasse.length < 8) {
      setErreur(t('inscription.regle'));
      return;
    }
    if (form.motDePasse.toLowerCase() === form.email.toLowerCase().trim()) {
      setErreur(t('inscription.regle'));
      return;
    }

    setEnvoi(true);
    try {
      await api.post('/auth/signup', {
        email: form.email.trim().toLowerCase(),
        motDePasse: form.motDePasse,
        nom: form.nom.trim(),
        prenom: form.prenom.trim(),
        nomOrganisation: form.nomOrganisation.trim()
      });
      setSucces(true);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  const titre = t('inscription.titre');
  const sousTitre = t('inscription.sousTitre');
  const succesMsg = t('inscription.succes');
  const creer = t('inscription.creer');
  const enCours = t('inscription.enCours');
  const dejaCompte = t('inscription.dejaCompte');
  const seConnecter = t('inscription.seConnecter');

  if (succes) {
    return (
      <div className="login-page">
        <MotifZellige className="motif-fond-login" />
        <Card className="relative z-10 w-full max-w-md shadow-xl">
          <CardHeader className="text-center">
            <Link to="/login" className="inline-flex items-center text-sm text-muted-foreground hover:text-primary mb-4">
              ← {seConnecter}
            </Link>
            <div className="flex justify-end">
              <LanguageSwitcher />
            </div>
            <CardTitle>{titre}</CardTitle>
            <CardDescription>{sousTitre}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="rounded-md border border-success/50 bg-success/50 p-3 text-success text-sm mb-4">
              {succesMsg}
            </div>
            <Button
              type="button"
              className="w-full"
              onClick={() => navigate(location.state?.from?.pathname ?? '/login', { replace: true })}
            >
              {seConnecter}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="login-page">
      <MotifZellige className="motif-fond-login" />
      <Card className="relative z-10 w-full max-w-md shadow-xl">
        <CardHeader className="text-center">
          <Link to="/login" className="inline-flex items-center text-sm text-muted-foreground hover:text-primary mb-4">
            ← {t('login.retour')}
          </Link>
          <div className="flex justify-end">
            <LanguageSwitcher />
          </div>
          <CardTitle>{titre}</CardTitle>
          <CardDescription>{sousTitre}</CardDescription>
        </CardHeader>
        <CardContent>
          {erreur && (
            <div className="rounded-md border border-destructive/50 bg-destructive/50 p-3 text-destructive text-sm mb-4">
              {erreur}
            </div>
          )}
          <form className="space-y-4" onSubmit={gererSoumission}>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="prenom">{t('inscription.prenom')}</Label>
                <Input
                  id="prenom"
                  type="text"
                  name="prenom"
                  value={form.prenom}
                  onChange={set('prenom')}
                  placeholder={t('inscription.prenom')}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="nom">{t('inscription.nom')}</Label>
                <Input
                  id="nom"
                  type="text"
                  name="nom"
                  value={form.nom}
                  onChange={set('nom')}
                  placeholder={t('inscription.nom')}
                  required
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">{t('inscription.email')}</Label>
              <Input
                id="email"
                type="email"
                name="email"
                value={form.email}
                onChange={set('email')}
                placeholder="email@exemple.com"
                autoComplete="username"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="nomOrganisation">{t('inscription.nomOrganisation')}</Label>
              <Input
                id="nomOrganisation"
                type="text"
                name="nomOrganisation"
                value={form.nomOrganisation}
                onChange={set('nomOrganisation')}
                placeholder="Ma organisation"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="motDePasse">{t('inscription.motDePasse')}</Label>
              <Input
                id="motDePasse"
                type={afficherMdp ? 'text' : 'password'}
                name="motDePasse"
                value={form.motDePasse}
                onChange={set('motDePasse')}
                placeholder="••••••••"
                autoComplete="new-password"
                required
              />
              <span className="text-xs text-muted-foreground">{t('inscription.regle')}</span>
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
              {envoi ? enCours : creer}
            </Button>
            <div className="text-center text-sm">
              {dejaCompte} <Link to="/login" className="text-primary hover:underline font-medium">{seConnecter}</Link>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
