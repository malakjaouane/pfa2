package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Ligne du journal des requêtes IA, vue administrative.
 *
 * @param id               identifiant de la requête
 * @param dateRequete      horodatage
 * @param type             {@code QUESTION} ou {@code RESUME}
 * @param demande          question posée ou libellé du résumé (tronqué)
 * @param reponse          réponse du modèle ( {@code null} si échec)
 * @param statut           {@code OK} | {@code ERREUR} | {@code NON_DISPONIBLE}
 * @param modele           modèle appelé
 * @param dureeMs          durée d'exécution
 * @param initiativeCode   code de l'initiative (résumés uniquement)
 * @param utilisateurEmail auteur de la requête
 */
public record JournalIaResponse(
        Long id,
        LocalDateTime dateRequete,
        String type,
        String demande,
        String reponse,
        String statut,
        String modele,
        Long dureeMs,
        String initiativeCode,
        String utilisateurEmail) {
}
