import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { badgeTypeJournal, libelleType, texteEvenement } from '../i18n/journal';
import ListeAlertes from '../components/ListeAlertes';
import SanteBadge from '../components/SanteBadge';
import StatutBadge from '../components/StatutBadge';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Badge } from '../components/ui/badge';

/** Montants en dirhams, sans décimale d'affichage (l'export garde l'exact). */
const formaterMontant = (valeur, langue) =>
  new Intl.NumberFormat(langue, { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
    .format(Number(valeur ?? 0));

const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleDateString(langue, { day: '2-digit', month: 'short', year: 'numeric' });

const formaterDateHeure = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/**
 * « Mon espace » : l'agrégat du compte connecté — profil, chiffres
 * budgétaires (selon BUDGET_CONSULTER / DEPENSE_SAISIR), initiatives
 * pilotées, jalons des 14 prochains jours, risques ouverts, alertes,
 * demandes à évaluer (DEMANDE_EVALUER) et activité récente.
 *
 * Une seule requête (`GET /api/mon-espace`) : le serveur assemble déjà ce
 * que le compte a le droit de voir — les champs sans droits arrivent null
 * et les sections correspondantes disparaissent.
 */
export default function MonEspacePage() {
  const { t, langue } = useI18n();
  const [donnees, setDonnees] = useState(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  useEffect(() => {
    api.get('/mon-espace')
      .then((res) => setDonnees(res.data))
      .catch((err) => setErreur(messageErreur(err)))
      .finally(() => setChargement(false));
  }, []);

  if (chargement) {
    return (
      <div className="mx-auto w-full max-w-[1240px] py-12 flex items-center justify-center">
        <span className="text-sm text-muted-foreground">{t('common.chargement')}</span>
      </div>
    );
  }

  const d = donnees;
  const budgetConnue = d?.budgetTotal !== null && d?.budgetTotal !== undefined;
  const depensesConnues = d?.depensesTotales !== null && d?.depensesTotales !== undefined;
  const reste = budgetConnue
    ? Number(d.budgetTotal) - Number(depensesConnues ? d.depensesTotales : 0)
    : null;

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('monEspace.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('monEspace.sousTitre')}
          </p>
        </div>
      </header>

      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {erreur}
        </div>
      )}

      {d && (
        <>
          <Card className="mb-6 p-[18px] shadow-sm">
            <CardContent className="pt-0">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                <div className="flex flex-col gap-2">
                  <h3 className="text-base font-semibold text-foreground">
                    {d.compte?.nomComplet ?? d.compte?.email}
                  </h3>
                  <p className="text-sm text-muted-foreground">{d.compte?.email}</p>
                  {d.role && <Badge variant="secondary">{d.role}</Badge>}
                </div>
                {budgetConnue && (
                  <div className="flex flex-col gap-3 md:flex-row items-end">
                    <div className="flex flex-col gap-1">
                      <span className="text-xs text-muted-foreground">{t('monEspace.budget')}</span>
                      <strong className="text-lg">{formaterMontant(d.budgetTotal, langue)}</strong>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-xs text-muted-foreground">{t('monEspace.depenses')}</span>
                      <strong className="text-lg">
                        {depensesConnues ? formaterMontant(d.depensesTotales, langue) : t('monEspace.sansBudget')}
                      </strong>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-xs text-muted-foreground">{t('monEspace.reste')}</span>
                      <strong className={`text-lg ${reste !== null && reste < 0 ? 'text-destructive' : ''}`}>
                        {formaterMontant(reste ?? 0, langue)}
                      </strong>
                    </div>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-[18px] shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-base font-semibold text-foreground">{t('monEspace.jalons')}</h3>
                {d.jalonsProches?.length > 0 && (
                  <Badge variant="secondary">{d.jalonsProches.length}</Badge>
                )}
              </div>
              {d.jalonsProches?.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('monEspace.jalonsVide')}</p>
              ) : (
                <ul className="space-y-2">
                  {d.jalonsProches.map((j) => (
                    <li key={j.idJalon} className="flex items-center gap-2">
                      <Link to={`/initiatives/${j.idInitiative}`}>{j.nom}</Link>
                      <Badge
                        variant={j.enRetard ? 'destructive' : 'secondary'}
                        className={`border-transparent ${
                          j.enRetard ? 'bg-destructive/15 text-destructive' : 'bg-orange/15 text-orange-800'
                        }`}
                      >
                        {j.enRetard ? t('monEspace.jalonRetard') : formaterDate(j.datePrevue, langue)}
                      </Badge>
                      {j.enRetard && (
                        <span className="ml-1 text-xs text-muted-foreground">
                          {formaterDate(j.datePrevue, langue)}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card className="p-[18px] shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-base font-semibold text-foreground">{t('monEspace.alertes')}</h3>
                {d.alertes?.length > 0 && (
                  <Badge variant="secondary">{d.alertes.length}</Badge>
                )}
              </div>
              <ListeAlertes alertes={d.alertes ?? []} />
            </Card>

            <Card className="p-[18px] shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-base font-semibold text-foreground">{t('monEspace.pilotees')}</h3>
                {d.initiativesPilotees?.length > 0 && (
                  <Badge variant="secondary">{d.initiativesPilotees.length}</Badge>
                )}
              </div>
              {d.initiativesPilotees?.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('monEspace.piloteesVide')}</p>
              ) : (
                <ul className="space-y-2">
                  {d.initiativesPilotees.map((i) => (
                    <li key={i.idInitiative} className="flex items-center gap-2">
                      <Link to={`/initiatives/${i.idInitiative}`}>
                        <strong>{i.code}</strong> {i.nom}
                      </Link>
                      <StatutBadge libelle={i.statut?.libelle} />
                      <SanteBadge sante={i.sante} />
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card className="p-[18px] shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-base font-semibold text-foreground">{t('monEspace.risques')}</h3>
                {d.mesRisques?.length > 0 && (
                  <Badge variant="secondary">{d.mesRisques.length}</Badge>
                )}
              </div>
              {d.mesRisques?.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('monEspace.risquesVide')}</p>
              ) : (
                <ul className="space-y-2">
                  {d.mesRisques.map((r) => (
                    <li key={r.idRisque} className="flex items-center gap-2">
                      <Link to={`/initiatives/${r.idInitiative}`}>{r.titre}</Link>
                      <SanteBadge sante={{ niveau: r.niveau }} />
                      <span className="ml-1 text-xs text-muted-foreground">
                        {t('monEspace.score')} {r.score}/25
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            {d.demandesEnAttente?.length > 0 && (
              <Card className="p-[18px] shadow-sm">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-base font-semibold text-foreground">{t('monEspace.demandes')}</h3>
                  <Badge variant="secondary">{d.demandesEnAttente.length}</Badge>
                </div>
                <ul className="space-y-2">
                  {d.demandesEnAttente.map((dem) => (
                    <li key={dem.idDemande} className="flex items-center gap-2">
                      <Link to="/demandes">{dem.titre}</Link>
                      <span className="ml-1 text-xs text-muted-foreground">
                        {formaterDate(dem.dateSoumission, langue)}
                      </span>
                    </li>
                  ))}
                </ul>
              </Card>
            )}

            <Card className="p-[18px] shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-base font-semibold text-foreground">{t('monEspace.activite')}</h3>
                {d.activiteRecente?.length > 0 && (
                  <Badge variant="secondary">{d.activiteRecente.length}</Badge>
                )}
              </div>
              {d.activiteRecente?.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('monEspace.activiteVide')}</p>
              ) : (
                <ol className="space-y-2">
                  {d.activiteRecente.map((ev) => (
                    <li key={ev.id} className="flex items-start gap-2 text-sm">
                      <span className="shrink-0 text-muted-foreground">
                        {formaterDateHeure(ev.dateChangement, langue)}
                      </span>
                      <span className="historique-transition">
                        <Badge variant="secondary" className={`border-transparent ${
                          badgeTypeJournal(ev.typeObjet) === 'badge-vert' ? 'bg-success/15 text-success' :
                          badgeTypeJournal(ev.typeObjet) === 'badge-rouge' ? 'bg-destructive/15 text-destructive' :
                          badgeTypeJournal(ev.typeObjet) === 'badge-bleu' ? 'bg-blue/15 text-blue-800' :
                          'bg-muted/50 text-muted-foreground'
                        }`}
                        >
                          {libelleType(t, ev.typeObjet)}
                        </Badge>{' '}
                        {texteEvenement(t, ev)}
                        {ev.initiative && (
                          <Link to={`/initiatives/${ev.initiative.idInitiative}`} className="ml-1 text-xs text-muted-foreground hover:underline">
                            · {ev.initiative.code}
                          </Link>
                        )}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
