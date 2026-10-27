package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de création / modification d'un risque.
 *
 * <p>Probabilité et impact sont bornés à 1-3 par la validation (messages
 * en français, affichés tels quels sous le formulaire).</p>
 */
public record RisqueRequest(
        @NotBlank(message = "La description du risque est obligatoire") String description,
        @NotNull(message = "La probabilité est obligatoire")
        @Min(value = 1, message = "La probabilité doit être notée de 1 à 3 (1 = Faible, 3 = Élevé)")
        @Max(value = 3, message = "La probabilité doit être notée de 1 à 3 (1 = Faible, 3 = Élevé)")
        Integer probabilite,
        @NotNull(message = "L'impact est obligatoire")
        @Min(value = 1, message = "L'impact doit être noté de 1 à 3 (1 = Faible, 3 = Élevé)")
        @Max(value = 3, message = "L'impact doit être noté de 1 à 3 (1 = Faible, 3 = Élevé)")
        Integer impact,
        String planAction,
        Long idResponsable
) {
}
