package ma.it6.portefeuille.dto;

/**
 * Disponibilité de l'assistant IA — exposée à tout utilisateur connecté
 * afin que l'interface affiche un état explicite (« non configuré »,
 * « désactivé ») au lieu d'une erreur muette.
 *
 * @param actif          interrupteur global actif ({@code IA_ACTIF})
 * @param cleRenseignee  une clé API est présente côté serveur
 * @param disponible     l'assistant peut répondre ({@code actif && clé})
 * @param modele         modèle configuré (traçabilité, même indisponible)
 */
public record StatutIaResponse(
        boolean actif,
        boolean cleRenseignee,
        boolean disponible,
        String modele) {
}
