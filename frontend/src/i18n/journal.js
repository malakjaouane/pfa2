/**
 * Helpers i18n du journal des modifications (historique.js).
 *
 * <p>Le backend stocke des CODES (`statut`, `etape`, `motDePasse`…) pour
 * rester traduisibles et normalisés ; l'UI les traduit ici. Si la clé
 * manque dans la langue active, on retombe sur le code brut plutôt que
 * d'afficher une clé i18n illisible (c'est la garantie « jamais de
 * clé-tech dans l'écran »).</p>
 */

/** Libellé traduit d'un champ journalisé (ex. `champ=budget` → « Budget »). */
export const libelleChamp = (t, code) => {
  if (!code) return '';
  const cle = `historique.champ.${code}`;
  const libelle = t(cle);
  return libelle === cle ? code : libelle;
};

/** Libellé traduit d'un type d'objet journalisé (ex. `etape` → « Étape »). */
export const libelleType = (t, code) => {
  if (!code) return '';
  const cle = `historique.type.${code}`;
  const libelle = t(cle);
  return libelle === cle ? code : libelle;
};

/** Suffixe de classe du badge de type (Étape bleu, Jalon ambre, reste gris). */
export const badgeTypeJournal = (type) =>
  (type === 'etape' ? 'badge-bleu' : type === 'jalon' ? 'badge-ambre' : 'badge-gris');

/**
 * Libellé d'une ligne : création, suppression, transition (statut ou
 * archivage) ou diff de champ traduit — les codes ne sortent jamais bruts.
 *
 * @param t  fonction de traduction de la langue active
 * @param ev ligne du journal (HistoriqueModificationResponse)
 */
export const texteEvenement = (t, ev) => {
  if (ev.champ === 'creation') {
    return t('initiativeDetail.evtCreation', { valeur: ev.nouvelleValeur ?? '' });
  }
  if (ev.champ === 'suppression') {
    return t('initiativeDetail.evtSuppression', { valeur: ev.ancienneValeur ?? '' });
  }
  if (!ev.ancienneValeur) {
    return ev.champ === 'statut'
      ? t('initiativeDetail.transitionCreation', { statut: ev.nouvelleValeur ?? '' })
      : t('initiativeDetail.evtCreation', { valeur: ev.nouvelleValeur ?? '' });
  }
  if (!ev.nouvelleValeur) {
    return t('initiativeDetail.evtSuppression', { valeur: ev.ancienneValeur });
  }
  if (ev.champ === 'statut' || ev.champ === 'archive') {
    return t('initiativeDetail.transition', { de: ev.ancienneValeur, vers: ev.nouvelleValeur });
  }
  return t('initiativeDetail.evtModification', {
    champ: libelleChamp(t, ev.champ), de: ev.ancienneValeur, vers: ev.nouvelleValeur
  });
};
