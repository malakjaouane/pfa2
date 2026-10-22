package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question adressée à l'assistant IA.
 *
 * @param question texte libre (1 à 1000 caractères)
 * @param langue   code de langue {@code fr|en|ar|es} — la réponse est
 *                 rédigée dans cette langue (défaut : français)
 */
public record AssistantIaRequest(
        @NotBlank(message = "La question est obligatoire")
        @Size(max = 1000, message = "La question ne peut pas dépasser 1000 caractères")
        String question,
        @Size(max = 5, message = "Code de langue invalide")
        String langue) {
}
