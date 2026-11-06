import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';

/**
 * Liste d'alertes calculées (AlerteDto) — composant partagé par la cloche
 * de notifications, « Mon espace » et le bloc d'alertes de la fiche de suivi.
 *
 * Le libellé vient toujours du serveur sous forme de `type` + `parametres` :
 * seuls les textes sont traduits ici (jamais le serveur n'assemble une
 * phrase). Niveau ROUGE en rouge, ORANGE en orange, avec lien de navigation
 * vers l'objet concerné quand il existe.
 *
 * @param {Array} alertes   liste AlerteDto (peut être vide ou absente)
 * @param {boolean} avecLiens affiche les liens vers l'objet concerné
 */
export default function ListeAlertes({ alertes, avecLiens = true }) {
  const { t, langue } = useI18n();

  if (!alertes || alertes.length === 0) {
    return <p className="texte-muet">{t('suivi.alerte.vide')}</p>;
  }

  const formaterDate = (v) => new Date(v).toLocaleDateString(langue, {
    day: '2-digit', month: 'short', year: 'numeric'
  });

  return (
    <ul className="alerte-liste">
      {alertes.map((a, i) => {
        const cible = a.idInitiative
          ? `/initiatives/${a.idInitiative}`
          : (a.idDemande ? '/demandes' : null);
        const niveau = a.niveau === 'ROUGE' ? 'ROUGE' : 'ORANGE';
        return (
          <li key={`${a.type}-${a.idInitiative ?? a.idDemande ?? i}`} className="alerte-item">
            <span className={`badge ${niveau === 'ROUGE' ? 'badge-rouge' : 'badge-orange'}`}>
              {t(`suivi.niveau.${niveau}`)}
            </span>
            <span className="alerte-message">{t(`suivi.alerte.${a.type}`, a.parametres ?? {})}</span>
            {a.libelle && <span className="texte-muet alerte-libelle">{a.libelle}</span>}
            {a.echeance && (
              <time className="texte-muet" dateTime={a.echeance}>
                {t('suivi.alerte.echeance', { date: formaterDate(a.echeance) })}
              </time>
            )}
            {avecLiens && cible && (
              <Link className="lien-bouton" to={cible}>{a.reference ?? '›'}</Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}
