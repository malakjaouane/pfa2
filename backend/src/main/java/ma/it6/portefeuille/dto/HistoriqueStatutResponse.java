package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Événement du journal des transitions de statut d'une initiative.
 *
 * @param ancienStatut statut avant la transition, {@code null} à la création
 * @param automatique  {@code true} si la transition a été déclenchée par le
 *                     moteur de règles (RM07-auto, RM17, alignement au
 *                     démarrage), {@code false} pour une action manuelle
 * @param utilisateur  auteur de la transition (peut être {@code null} si le
 *                     compte a été supprimé entre-temps)
 * @param initiative   rattachement de l'événement (alimente la page
 *                     « Historique des statuts »)
 */
public record HistoriqueStatutResponse(
        Long idHistorique,
        String ancienStatut,
        String nouveauStatut,
        String commentaire,
        LocalDateTime dateChangement,
        boolean automatique,
        UserSummaryDto utilisateur,
        InitiativeSummaryDto initiative
) {
}
