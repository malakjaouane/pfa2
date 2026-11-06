import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { badgeTypeJournal, libelleType, texteEvenement } from '../i18n/journal';
import ListeAlertes from '../components/ListeAlertes';
import SanteBadge from '../components/SanteBadge';
import StatutBadge from '../components/StatutBadge';

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
    return <div className="center-page">{t('common.chargement')}</div>;
  }

  const d = donnees;
  const budgetConnue = d?.budgetTotal !== null && d?.budgetTotal !== undefined;
  const depensesConnues = d?.depensesTotales !== null && d?.depensesTotales !== undefined;
  const reste = budgetConnue
    ? Number(d.budgetTotal) - Number(depensesConnues ? d.depensesTotales : 0)
    : null;

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('monEspace.titre')}</h2>
          <p>{t('monEspace.sousTitre')}</p>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      {d && (
        <>
          <section className="carte mon-espace-entete">
            <div className="mon-espace-compte">
              <h3>{d.compte?.nomComplet ?? d.compte?.email}</h3>
              <p className="texte-muet">{d.compte?.email}</p>
              {d.role && <span className="badge badge-bleu">{d.role}</span>}
            </div>
            {budgetConnue && (
              <div className="mon-espace-budget">
                <div className="mon-espace-chiffre">
                  <span className="texte-muet">{t('monEspace.budget')}</span>
                  <strong>{formaterMontant(d.budgetTotal, langue)}</strong>
                </div>
                <div className="mon-espace-chiffre">
                  <span className="texte-muet">{t('monEspace.depenses')}</span>
                  <strong>{depensesConnues ? formaterMontant(d.depensesTotales, langue) : t('monEspace.sansBudget')}</strong>
                </div>
                <div className="mon-espace-chiffre">
                  <span className="texte-muet">{t('monEspace.reste')}</span>
                  <strong className={reste !== null && reste < 0 ? 'texte-rouge' : ''}>
                    {formaterMontant(reste ?? 0, langue)}
                  </strong>
                </div>
              </div>
            )}
          </section>

          <div className="mon-espace-grille">
            <section className="carte">
              <div className="section-titre-icone">
                <h3>{t('monEspace.jalons')}</h3>
                {d.jalonsProches?.length > 0 && (
                  <span className="badge badge-gris">{d.jalonsProches.length}</span>
                )}
              </div>
              {d.jalonsProches?.length === 0 ? (
                <p className="texte-muet">{t('monEspace.jalonsVide')}</p>
              ) : (
                <ul className="mon-espace-liste">
                  {d.jalonsProches.map((j) => (
                    <li key={j.idJalon} className="mon-espace-item">
                      <Link to={`/initiatives/${j.idInitiative}`}>{j.nom}</Link>
                      <span className={`badge ${j.enRetard ? 'badge-rouge' : 'badge-orange'}`}>
                        {j.enRetard
                          ? t('monEspace.jalonRetard')
                          : formaterDate(j.datePrevue, langue)}
                      </span>
                      {j.enRetard && (
                        <span className="texte-muet">{formaterDate(j.datePrevue, langue)}</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="carte">
              <div className="section-titre-icone">
                <h3>{t('monEspace.alertes')}</h3>
                {d.alertes?.length > 0 && (
                  <span className="badge badge-gris">{d.alertes.length}</span>
                )}
              </div>
              <ListeAlertes alertes={d.alertes ?? []} />
            </section>

            <section className="carte">
              <div className="section-titre-icone">
                <h3>{t('monEspace.pilotees')}</h3>
                {d.initiativesPilotees?.length > 0 && (
                  <span className="badge badge-gris">{d.initiativesPilotees.length}</span>
                )}
              </div>
              {d.initiativesPilotees?.length === 0 ? (
                <p className="texte-muet">{t('monEspace.piloteesVide')}</p>
              ) : (
                <ul className="mon-espace-liste">
                  {d.initiativesPilotees.map((i) => (
                    <li key={i.idInitiative} className="mon-espace-item">
                      <Link to={`/initiatives/${i.idInitiative}`}>
                        <strong>{i.code}</strong> {i.nom}
                      </Link>
                      <StatutBadge libelle={i.statut?.libelle} />
                      <SanteBadge sante={i.sante} />
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="carte">
              <div className="section-titre-icone">
                <h3>{t('monEspace.risques')}</h3>
                {d.mesRisques?.length > 0 && (
                  <span className="badge badge-gris">{d.mesRisques.length}</span>
                )}
              </div>
              {d.mesRisques?.length === 0 ? (
                <p className="texte-muet">{t('monEspace.risquesVide')}</p>
              ) : (
                <ul className="mon-espace-liste">
                  {d.mesRisques.map((r) => (
                    <li key={r.idRisque} className="mon-espace-item">
                      <Link to={`/initiatives/${r.idInitiative}`}>
                        {r.titre}
                      </Link>
                      <SanteBadge sante={{ niveau: r.niveau }} />
                      <span className="texte-muet">
                        {t('monEspace.score')} {r.score}/25
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {d.demandesEnAttente?.length > 0 && (
              <section className="carte">
                <div className="section-titre-icone">
                  <h3>{t('monEspace.demandes')}</h3>
                  <span className="badge badge-orange">{d.demandesEnAttente.length}</span>
                </div>
                <ul className="mon-espace-liste">
                  {d.demandesEnAttente.map((dem) => (
                    <li key={dem.idDemande} className="mon-espace-item">
                      <Link to="/demandes">{dem.titre}</Link>
                      <span className="texte-muet">
                        {formaterDate(dem.dateSoumission, langue)}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <section className="carte">
              <div className="section-titre-icone">
                <h3>{t('monEspace.activite')}</h3>
                {d.activiteRecente?.length > 0 && (
                  <span className="badge badge-gris">{d.activiteRecente.length}</span>
                )}
              </div>
              {d.activiteRecente?.length === 0 ? (
                <p className="texte-muet">{t('monEspace.activiteVide')}</p>
              ) : (
                <ol className="historique">
                  {d.activiteRecente.map((ev) => (
                    <li key={ev.id} className="historique-item">
                      <span className="historique-date">
                        {formaterDateHeure(ev.dateChangement, langue)}
                      </span>
                      <span className="historique-transition">
                        <span className={`badge ${badgeTypeJournal(ev.typeObjet)}`}>
                          {libelleType(t, ev.typeObjet)}
                        </span>
                        {texteEvenement(t, ev)}
                        {ev.initiative && (
                          <Link to={`/initiatives/${ev.initiative.idInitiative}`}>
                            {' · '}{ev.initiative.code}
                          </Link>
                        )}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}