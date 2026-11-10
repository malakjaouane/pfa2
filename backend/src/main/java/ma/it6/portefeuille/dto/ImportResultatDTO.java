package ma.it6.portefeuille.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Résultat d'un import d'initiatives (CSV ou Excel).
 *
 * <p>En mode strict, si au moins une ligne est en erreur, aucune initiative
 * n'est importée et la liste {@code erreurs} détaille chaque refus ligne par
 * ligne. En mode « ignorer les lignes en erreur », seules les lignes valides
 * sont importées et les erreurs sont rapportées sans bloquer l'ensemble.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultatDTO {

    /** Nombre total de lignes de données lues (en-tête exclue). */
    private Integer totalLignes;

    /** Nombre de lignes effectivement importées. */
    private Integer lignesImportees;

    /** Nombre de lignes ignorées (erreurs) — uniquement en mode ignorer. */
    private Integer lignesIgnorees;

    /** Erreurs détectées, une par ligne erronée. */
    @Builder.Default
    private List<ErreurLigneDTO> erreurs = new ArrayList<>();

    /** Mode d'import utilisé (strict / ignorer). */
    private String mode;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErreurLigneDTO {
        /** Numéro de la ligne dans le fichier (en-tête = ligne 1). */
        private Integer ligne;
        /** Message d'erreur lisible. */
        private String message;
    }
}
