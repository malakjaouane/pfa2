package ma.it6.portefeuille.dto;

/**
 * Réponse de l'assistant IA.
 *
 * @param reponse texte rédigé par le modèle, sourcé sur le portefeuille
 * @param modele  identifiant du modèle utilisé (traçabilité)
 * @param dureeMs durée d'exécution en millisecondes
 */
public record AssistantIaResponse(
        String reponse,
        String modele,
        Long dureeMs) {
}
