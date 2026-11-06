package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Action HTTP du journal d'audit : QUI a fait QUOI depuis OÙ, avec le
 * statut HTTP rendu. Lecture seule, jamais exposée en écriture.
 *
 * @param corps corps filtré (mots de passe masqués) et tronqué ;
 *              {@code null} hors routes API
 */
public record JournalAuditResponse(
        Long idAudit,
        LocalDateTime dateAction,
        Long idUtilisateur,
        String email,
        String methode,
        String route,
        Integer statutHttp,
        String adresseIp,
        String corps
) {
}
