import { useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { useToast } from '../components/ui/use-toast';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Badge } from '../components/ui/badge';

/**
 * Page « Import d'initiatives » (4B).
 *
 * Permet d'importer un fichier CSV (séparateur point-virgule) ou Excel
 * (.xlsx) contenant des initiatives. Le backend valide chaque ligne
 * (code dupliqué, budget négatif, dates incohérentes, responsable/
 * statut/entité/objectif inconnus) et renvoie un rapport ligne par ligne.
 *
 * En mode strict (défaut), toute erreur rejette l'import entier.
 * En mode « ignorer les erreurs », seules les lignes valides sont
 * importées et les erreurs sont rapportées.
 *
 * Un modèle CSV (colonnes requises + ligne d'exemple) est téléchargeable
 * depuis la page — même droit que l'import.
 */
export default function ImportInitiativesPage() {
  const { t, langue } = useI18n();
  const { toast } = useToast();
  const [fichier, setFichier] = useState(null);
  const [envoi, setEnvoiCreation] = useState(false);
  const [resultat, setResultat] = useState(null);

  const importer = async (e) => {
    e.preventDefault();
    if (!fichier) return;
    setEnvoiCreation(true);
    setResultat(null);
    const formData = new FormData();
    formData.append('fichier', fichier);
    try {
      const res = await api.post('/initiatives/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setResultat(res.data);
      toast({
        title: t('importation.succes', { n: res.data.lignesImportees }),
        variant: 'success',
      });
    } catch (err) {
      const rapport = err.response?.data;
      if (rapport && Array.isArray(rapport.erreurs)) {
        // Mode strict : le serveur renvoie le rapport ligne par ligne
        // (HTTP 400) — on l'affiche dans le panneau de résultat.
        setResultat(rapport);
        toast({ title: t('importation.refuse'), variant: 'destructive' });
      } else {
        toast({ title: messageErreur(err), variant: 'destructive' });
      }
    } finally {
      setEnvoiCreation(false);
    }
  };

  const telechargerModele = async () => {
    try {
      await telechargerFichier('/initiatives/import/modele', 'modele-import-initiatives.csv');
    } catch (err) {
      toast({ title: messageErreurBlob(err), variant: 'destructive' });
    }
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('importation.titre')}
          </h1>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('importation.sousTitre')}
          </p>
        </div>
        <Link to="/initiatives" className="text-sm font-medium text-primary hover:underline">
          {t('initiatives.titre')}
        </Link>
      </header>

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader>
          <CardTitle className="text-base font-semibold text-foreground">
            {t('importation.importerFichier')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={importer}>
            <div className="flex flex-col sm:flex-row gap-4">
              <Input
                type="file"
                accept=".csv,.xlsx,.xls"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    setFichier(e.target.files[0]);
                  }
                }}
                className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <Button
                type="submit"
                disabled={!fichier || envoiCreation}
              >
                {envoiCreation ? t('importation.enCours') : t('importation.importer')}
              </Button>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-muted-foreground">
                {t('importation.formats')}
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={telechargerModele}
              >
                {t('importation.modele')}
              </Button>
            </div>
            <p className="text-[11px] text-muted-foreground">
              {t('importation.modeleAide')}
            </p>
          </form>
        </CardContent>
      </Card>

      {resultat && (
        <Card className="mb-6 p-[18px] shadow-sm">
          <CardHeader>
            <CardTitle className="text-base font-semibold text-foreground">
              {t('importation.resultat')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col sm:flex-row gap-4 text-sm">
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{t('importation.total')}</Badge>
                  <span className="ml-1">{resultat.totalLignes}</span>
                </div>
                <div className="flex items-center gap-2 mt-2">
                  <Badge variant="success">{t('importation.importees', { n: resultat.lignesImportees })}</Badge>
                </div>
                <div className="flex items-center gap-2 mt-2">
                  {resultat.lignesIgnorees > 0 && (
                    <Badge variant="destructive">{t('importation.ignorees', { n: resultat.lignesIgnorees })}</Badge>
                  )}
                </div>
              </div>
              {resultat.erreurs && resultat.erreurs.length > 0 && (
                <div className="mt-4">
                  <h4 className="text-sm font-semibold text-destructive mb-2">
                    {t('importation.erreurs', { n: resultat.erreurs.length })}
                  </h4>
                  <ul className="text-xs text-destructive space-y-1 max-h-64 overflow-y-auto">
                    {resultat.erreurs.map((e) => (
                      <li key={e.ligne}>
                        {e.ligne}: {e.message}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}