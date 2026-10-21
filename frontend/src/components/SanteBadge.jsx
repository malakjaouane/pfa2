import { useI18n } from '../i18n/I18nContext';

/**
 * Badge de santé automatique d'une initiative (calculé par le backend) :
 * feu vert, vigilance (orange) ou à risque (rouge), avec le motif affiché
 * (ex. « 60 % du temps écoulé pour 30 % d'avancement »).
 *
 * @param {object} sante      champ `sante` d'une InitiativeResponse
 *                            ({ niveau, raison, parametres }) ; null = sans objet
 * @param {boolean} avecRaison affiche le motif sous le badge (fiche, dashboard)
 */
export default function SanteBadge({ sante, avecRaison = false }) {
  const { t } = useI18n();
  if (!sante?.niveau) return null;
  const libelle = t(`sante.${sante.niveau.toLowerCase()}`);
  const raison = sante.raison
    ? t(`sante.raison.${sante.raison}`, sante.parametres ?? {})
    : '';
  return (
    <span className={`sante sante-${sante.niveau.toLowerCase()}`}>
      <span className="sante-puce" aria-hidden="true" />
      <span className="sante-libelle">{libelle}</span>
      {avecRaison && raison && <span className="sante-raison">{raison}</span>}
    </span>
  );
}
