package ma.it6.portefeuille.dto;

import java.util.List;

/**
 * Vue « notifications » du compte courant : le compteur de la cloche est
 * simplement {@code total}, la liste est recalculée à chaque appel.
 */
public record NotificationResponse(
        int total,
        List<AlerteDto> alertes
) {
}
