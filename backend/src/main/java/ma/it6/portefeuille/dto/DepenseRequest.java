package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de création / modification d'une dépense.
 *
 * <p>Le montant doit être strictement positif (contrainte CHECK côté
 * base) et la date est obligatoire — c'est elle qui alimente les filtres
 * de période du registre.</p>
 */
public record DepenseRequest(
        @NotBlank(message = "Le libellé de la dépense est obligatoire")
        @Size(max = 150, message = "Le libellé ne peut pas dépasser 150 caractères")
        String libelle,
        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être strictement positif")
        BigDecimal montant,
        @NotNull(message = "La date de la dépense est obligatoire")
        LocalDate dateDepense,
        @Size(max = 50, message = "La catégorie ne peut pas dépasser 50 caractères")
        String categorie
) {
}
