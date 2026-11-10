import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';

/**
 * Écran « Changer mon mot de passe » (utilisateur déjà connecté).
 *
 * <p>POST /api/auth/changer-mot-de-passe : le backend vérifie le mot de
 * passe actuel, applique la politique (8 caractères, lettre + chiffre,
 * différent de l'email) puis ré-hash le nouveau mot de passe en BCrypt.</p>
 *
 * <p>Compte marqué « changement obligatoire » (compte admin créé hors
 * profil dev) : un bandeau l'annonce, et une fois le nouveau mot de passe
 * enregistré le profil est rechargé (/auth/me) puis l'utilisateur est
 * redirigé vers le tableau de bord — l'obligation est levée.</p>
 */
export default function MotDePassePage() {
  const { t } = useI18n();
  const { user, rafraichir } = useAuth();
  const navigate = useNavigate();
  const [actuel, setActuel] = useState('');
  const [nouveau, setNouveau] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [afficher, setAfficher] = useState(false);
  const [erreur, setErreur] = useState('');
  const [succes, setSucces] = useState('');
  const [envoi, setEnvoi] = useState(false);

  const obligation = Boolean(user?.doitChangerMotDePasse);

  const soumettre = async (e) => {
    e.preventDefault();
    setErreur('');
    setSucces('');

    if (nouveau !== confirmation) {
      setErreur({ cle: 'motDePasse.erreurConfirmation' });
      return;
    }

    setEnvoi(true);
    try {
      await api.post('/auth/changer-mot-de-passe', {
        motDePasseActuel: actuel,
        nouveauMotDePasse: nouveau
      });
      setSucces(t('motDePasse.succes'));
      setActuel('');
      setNouveau('');
      setConfirmation('');
      if (obligation) {
        // Le marqueur est levé côté base : on recharge le profil puis on
        // quitte l'écran imposé (RequireAuth ne bloque plus l'accès).
        await rafraichir();
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('motDePasse.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('motDePasse.sousTitre')}
          </p>
        </div>
      </header>

      {obligation && (
        <div className="mb-4 rounded-md border border-info/50 bg-info/50 p-3 text-sm text-info" role="alert">
          {t('motDePasse.obligatoire')}
        </div>
      )}
      {succes && (
        <div className="mb-4 rounded-md border border-success/50 bg-success/50 p-3 text-sm text-success">
          {succes}
        </div>
      )}
      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {afficherMessage(t, erreur)}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader>
          <CardTitle className="text-lg font-semibold">{t('motDePasse.titre')}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="space-y-4" onSubmit={soumettre}>
            <div className="space-y-2">
              <Label htmlFor="motDePasseActuel">{t('motDePasse.actuel')}</Label>
              <Input
                id="motDePasseActuel"
                type={afficher ? 'text' : 'password'}
                value={actuel}
                onChange={(e) => setActuel(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="motDePasseNouveau">{t('motDePasse.nouveau')}</Label>
              <Input
                id="motDePasseNouveau"
                type={afficher ? 'text' : 'password'}
                value={nouveau}
                onChange={(e) => setNouveau(e.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="motDePasseConfirmation">{t('motDePasse.confirmation')}</Label>
              <Input
                id="motDePasseConfirmation"
                type={afficher ? 'text' : 'password'}
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            <div className="flex items-start gap-2">
              <input
                id="afficherMotDePasse"
                type="checkbox"
                checked={afficher}
                onChange={(e) => setAfficher(e.target.checked)}
                className="mt-0.5 rounded-md border-input bg-background focus:ring-ring"
              />
              <Label htmlFor="afficherMotDePasse" className="text-sm leading-none cursor-pointer select-none">
                {t('motDePasse.afficher')}
              </Label>
            </div>
            <p className="text-xs text-muted-foreground">{t('motDePasse.regle')}</p>
            <Button type="submit" disabled={envoi} className="w-full">
              {envoi ? t('motDePasse.enCours') : t('motDePasse.enregistrer')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
