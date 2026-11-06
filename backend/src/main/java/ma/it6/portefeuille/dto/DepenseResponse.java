package ma.it6.portefeuille.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dépense renvoyée par l'API.
 *
 * @param saisisseur {@code null} si le compte a été supprimé
 */
public record DepenseResponse(
        Long idDepense,
        Long idInitiative,
        String codeInitiative,
        String nomInitiative,
        String libelle,
        BigDecimal montant,
        LocalDate dateDepense,
        String categorie,
        LocalDateTime dateCreation,
        UserSummaryDto saisisseur
) {
}
