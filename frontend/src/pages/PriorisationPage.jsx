import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import { useToast } from '../components/ui/use-toast';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Badge } from '../components/ui/badge';

/**
 * Page « Matrice de priorisation » (4C).
 *
 * Affiche les initiatives sur une grille 5×5 :
 * - Axe X : valeur métier (1 à 5, de gauche à droite).
 * - Axe Y : effort estimé (5 en haut à 1 en bas — un effort faible est mieux placé).
 * - Chaque cellule contient les initiatives qui ont ce couple (valeur, effort).
 * - La couleur de fond reflète le score de priorisation = valeur × 25 ÷ effort (1 à 25).
 *   Plus le score est élevé, plus l’initiative est prioritaire (forte valeur, faible effort).
 *
 * Le tri « Par score de priorisation » sur la page « Initiatives » est cohérent
 * avec cette même logique.
 */
export default function PriorisationPage() {
  const { t, langue } = useI18n();
  const { toast } = useToast();
  const ref = useReferentiels();
  const [initiatives, setInitiatives] = useState([]);
  const [chargement, setChargement] = useState(true);

  useEffect(() => {
    api.get('/initiatives')
      .then((res) => setInitiatives(res.data ?? []))
      .catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }))
      .finally(() => setChargement(false));
  }, []);

  // Matrice 5×5 : clés "valeur:effort" → liste d’initiatives.
  const matrice = useMemo(() => {
    const m = new Map();
    for (let v = 1; v <= 5; v++) {
      for (let e = 1; e <= 5; e++) {
        m.set(`${v}:${e}`, []);
      }
    }
    initiatives.forEach((i) => {
      const v = i.valeur;
      const e = i.effort;
      if (v != null && e != null && v >= 1 && v <= 5 && e >= 1 && e <= 5) {
        const cle = `${v}:${e}`;
        const liste = m.get(cle);
        if (liste) liste.push(i);
      }
    });
    return m;
  }, [initiatives]);

  // Score d’une cellule : valeur × 25 ÷ effort (arrondi à l’entier, 1 à 25).
  const scoreCellule = (v, e) => {
    if (v == null || e == null || e === 0) return 0;
    return Math.max(1, Math.min(25, Math.floor((v * 25) / e)));
  };

  const classeCellule = (score) => {
    if (!score) return 'bg-muted/20';
    if (score >= 20) return 'bg-destructive/25';
    if (score >= 15) return 'bg-destructive/15';
    if (score >= 10) return 'bg-amber/15';
    if (score >= 5) return 'bg-blue/10';
    return 'bg-muted/10';
  };

  const texteScore = (score) => {
    if (!score) return '—';
    if (score >= 20) return 'Critique';
    if (score >= 15) return 'Élevé';
    if (score >= 10) return 'Moyen';
    if (score >= 5) return 'Faible';
    return 'Très faible';
  };

  if (chargement) {
    return (
      <div className="mx-auto w-full max-w-[1240px] py-12 flex items-center justify-center">
        <span className="text-sm text-muted-foreground">{t('initiatives.chargement')}</span>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('priorisation.titre')}
          </h1>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('priorisation.sousTitre')}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          asChild
        >
          <Link to="/initiatives">{t('initiatives.titre')}</Link>
        </Button>
      </header>

      <p className="mb-6 text-sm text-muted-foreground max-w-[1240px]">
        {t('priorisation.legendeScore')}
      </p>

      <div className="grid grid-cols-6 gap-1">
        {/* En-tête des colonnes (valeur 1 à 5) */}
        <div className="col-span-1" />
        {[1, 2, 3, 4, 5].map((v) => (
          <div
            key={v}
            className="flex items-center justify-center py-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider"
          >
            {v}
          </div>
        ))}
      </div>

      {/* Lignes : effort 5 (en haut) à 1 (en bas) */}
      {[5, 4, 3, 2, 1].map((e) => (
        <div key={e} className="grid grid-cols-6 gap-1">
          {/* Etiquette d'effort (à gauche) */}
          <div className="flex items-center justify-end pr-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
            {e}
          </div>
          {[1, 2, 3, 4, 5].map((v) => {
            const liste = matrice.get(`${v}:${e}`) ?? [];
            const score = scoreCellule(v, e);
            return (
              <div
                key={`${v}:${e}`}
                className={`min-h-[80px] rounded-lg border border-muted/30 p-2 transition-colors hover:border-muted/50 ${classeCellule(score)}`}
                title={texteScore(score)}
              >
                <div className="flex items-end justify-between pb-1">
                  <span className="text-[10px] font-medium text-foreground/70">{score}</span>
                  <Badge
                    variant="secondary"
                    className={`border-transparent text-[10px] px-1.5 py-0.5 ${score >= 15 ? 'bg-destructive/30 text-destructive' : 'bg-muted/30 text-muted-foreground'}`}
                  >
                    {e}
                  </Badge>
                </div>
                <ul className="space-y-0.5 mt-1">
                  {liste.slice(0, 4).map((i) => (
                    <li
                      key={i.idInitiative}
                      className="flex items-center gap-1"
                    >
                      <Link
                        to={`/initiatives/${i.idInitiative}`}
                        className="text-[10px] text-primary hover:underline truncate"
                        title={i.nom}
                      >
                        {i.code}
                      </Link>
                    </li>
                  ))}
                  {liste.length > 4 && (
                    <li className="flex items-center gap-1">
                      <span className="text-[9px] text-muted-foreground italic">
                        +{liste.length - 4} …
                      </span>
                    </li>
                  )}
                </ul>
              </div>
            );
          })}
        </div>
      ))}

      <div className="mt-6 max-w-[1240px]">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          Légende
        </h3>
        <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-destructive/25" />
            {t('priorisation.colScore')} ≥ 20 : critique
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-destructive/15" />
            {t('priorisation.colScore')} ≥ 15 : élevé
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-amber/15" />
            {t('priorisation.colScore')} ≥ 10 : moyen
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-blue/10" />
            {t('priorisation.colScore')} ≥ 5 : faible
          </span>
        </div>
      </div>

      <div className="mt-6 max-w-[1240px] grid grid-cols-1 md:grid-cols-5 gap-4">
        {[1, 2, 3, 4, 5].map((v) => {
          const liste = [];
          for (let e = 1; e <= 5; e++) {
            liste.push(...(matrice.get(`${v}:${e}`) ?? []));
          }
          if (liste.length === 0) return null;
          const n = liste.length;
          return (
            <Card key={v} className="p-[18px] shadow-sm">
              <CardHeader>
                <CardTitle className="text-base font-semibold text-foreground">
                  {t('priorisation.colValeur')} {v}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground mb-3">
                  {n === 1
                    ? t('priorisation.initiatives', { n })
                    : t('priorisation.initiatives_plural', { n })}
                </p>
                <ul className="text-sm text-foreground space-y-1">
                  {liste.map((i) => (
                    <li key={i.idInitiative} className="flex items-center gap-1">
                      <Link
                        to={`/initiatives/${i.idInitiative}`}
                        className="text-primary hover:underline"
                      >
                        {i.code}
                      </Link>
                      <span className="ml-auto text-[10px] text-muted-foreground">
                        {ref?.priorite?.(i.priorite?.libelle) ?? ''}
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
