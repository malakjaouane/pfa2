package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.Size;

/**
 * Demande de résumé d'une initiative par l'assistant IA.
 *
 * @param langue code de langue {@code fr|en|ar|es} (défaut : français)
 */
public record ResumeIaRequest(
        @Size(max = 5, message = "Code de langue invalide")
        String langue) {
}
