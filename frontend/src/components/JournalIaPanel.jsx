import { useEffect, useState } from 'react';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { Badge } from './ui/badge';
import { EmptyState } from './ui/empty-state';
import { Skeleton } from './ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from './ui/table';
import { useToast } from './ui/use-toast';
import { IconAssistant } from './Icons';

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/** Couleur du pastille de statut : OK vert, erreur rouge, indisponible gris. */
const CLASSES_STATUT = {
  OK: 'bg-success/15 text-success border-transparent',
  ERREUR: 'bg-destructive/10 text-destructive border-transparent',
  NON_DISPONIBLE: 'bg-muted text-muted-foreground border-transparent'
};

const CLE_STATUT = {
  OK: 'activite.journalIa.statut.ok',
  ERREUR: 'activite.journalIa.statut.erreur',
  NON_DISPONIBLE: 'activite.journalIa.statut.indisponible'
};

const CLE_TYPE = {
  QUESTION: 'activite.journalIa.type.question',
  RESUME: 'activite.journalIa.type.resume'
};

/**
 * 4ᵉ onglet « Activité et sécurité » : journal des requêtes adressées à
 * l'assistant IA (GET /api/ia/journal, réservé aux administrateurs).
 */
export default function JournalIaPanel() {
  const { t, langue } = useI18n();
  const { toast } = useToast();
  const [requete, setRequete] = useState(null);
  const [chargement, setChargement] = useState(true);

  useEffect(() => {
    let vivant = true;
    (async () => {
      try {
        const { data } = await api.get('/ia/journal');
        if (vivant) setRequete(data);
      } catch (err) {
        if (vivant) toast({ title: await messageErreur(err), variant: 'destructive' });
      } finally {
        if (vivant) setChargement(false);
      }
    })();
    return () => {
      vivant = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (chargement) {
    return (
      <div className="space-y-2" aria-hidden="true">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-3/4" />
      </div>
    );
  }

  const lignes = requete ?? [];

  if (lignes.length === 0) {
    return (
      <EmptyState
        icon={<IconAssistant size={40} />}
        titre={t('activite.journalIa.vide')}
      />
    );
  }

  return (
    <div className="rounded-lg border border-input bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('activite.journalIa.col.date')}</TableHead>
            <TableHead>{t('activite.journalIa.col.type')}</TableHead>
            <TableHead>{t('activite.journalIa.col.demande')}</TableHead>
            <TableHead>{t('activite.journalIa.col.statut')}</TableHead>
            <TableHead>{t('activite.journalIa.col.modele')}</TableHead>
            <TableHead className="text-right">{t('activite.journalIa.col.duree')}</TableHead>
            <TableHead>{t('activite.journalIa.col.utilisateur')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {lignes.map((l) => (
            <TableRow key={l.id}>
              <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                {formaterDate(l.dateRequete, langue)}
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{t(CLE_TYPE[l.type] ?? 'activite.journalIa.type.question')}</Badge>
              </TableCell>
              <TableCell className="max-w-[320px]">
                <span className="block truncate text-sm" title={l.reponse ? `${l.demande}\n\n${l.reponse}` : l.demande}>
                  {l.demande}
                </span>
                {l.initiativeCode && (
                  <Badge variant="outline" className="mt-1 text-[10px]">{l.initiativeCode}</Badge>
                )}
              </TableCell>
              <TableCell>
                <Badge variant="outline" className={CLASSES_STATUT[l.statut] ?? CLASSES_STATUT.NON_DISPONIBLE}>
                  {t(CLE_STATUT[l.statut] ?? 'activite.journalIa.statut.indisponible')}
                </Badge>
              </TableCell>
              <TableCell className="text-sm text-muted-foreground">{l.modele}</TableCell>
              <TableCell className="text-right text-sm tabular-nums text-muted-foreground">
                {l.dureeMs} ms
              </TableCell>
              <TableCell className="text-sm text-muted-foreground">{l.utilisateurEmail}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
