package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corps de création / modification d'un risque.
 *
 * <p>Probabilité et impact sont bornés à 1-5 par la validation (messages
 * en français, affichés tels quels sous le formulaire). Le statut vaut
 * {@code ouverte} par défaut ; l'échéance est optionnelle.</p>
 */
public record RisqueRequest(
        @NotBlank(message = "Le titre du risque est obligatoire")
        @Size(max = 150, message = "Le titre du risque ne peut pas dépasser 150 caractères")
        String titre,
        @NotBlank(message = "La description du risque est obligatoire") String description,
        @Pattern(regexp = "ouverte|attenuation|close",
                message = "Le statut du risque doit valoir ouverte, attenuation ou close")
        String statut,
        LocalDate echeance,
        @NotNull(message = "La probabilité est obligatoire")
        @Min(value = 1, message = "La probabilité doit être notée de 1 à 5 (1 = Très faible, 5 = Très élevé)")
        @Max(value = 5, message = "La probabilité doit être notée de 1 à 5 (1 = Très faible, 5 = Très élevé)")
        Integer probabilite,
        @NotNull(message = "L'impact est obligatoire")
        @Min(value = 1, message = "L'impact doit être noté de 1 à 5 (1 = Très faible, 5 = Très élevé)")
        @Max(value = 5, message = "L'impact doit être noté de 1 à 5 (1 = Très faible, 5 = Très élevé)")
        Integer impact,
        String planAction,
        Long idResponsable
) {
}
