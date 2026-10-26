package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Ligne de l'historique des connexions (page administrative).
 *
 * @param idConnexion  identifiant de la tentative
 * @param dateConnexion horodatage de la tentative
 * @param email        e-mail saisi lors de la tentative (trace conservée
 *                     même si le compte n'existe plus)
 * @param utilisateur  compte rattaché, {@code null} si inconnu ou supprimé
 * @param adresseIp    adresse IP du client (ou du reverse-proxy)
 * @param succes       {@code true} si la connexion a abouti
 * @param motif        motif d'échec, {@code null} si succès
 */
public record ConnexionResponse(
        Long idConnexion,
        LocalDateTime dateConnexion,
        String email,
        UserSummaryDto utilisateur,
        String adresseIp,
        boolean succes,
        String motif
) {
}
