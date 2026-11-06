package ma.it6.portefeuille.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de l'utilitaire CSV partagé : conventions d'export (séparateur
 * point-virgule, BOM UTF-8) et garde anti-formule (injection Excel).
 */
@DisplayName("CsvUtil — séparateur, BOM et garde anti-formule")
class CsvUtilTest {

    @Test
    @DisplayName("BOM UTF-8 en tête du fichier (Excel ouvre les accents)")
    void bomEnTete() {
        byte[] csv = CsvUtil.construire(List.of("Colonne"), List.of(List.of("valeur")));

        assertThat(csv).startsWith(0xEF, 0xBB, 0xBF);
        // Après le BOM : le contenu UTF-8 (une seule colonne : pas de séparateur).
        String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(contenu).startsWith("Colonne\nvaleur");
    }

    @Test
    @DisplayName("séparateur point-virgule entre les colonnes")
    void separateurPointVirgule() {
        byte[] csv = CsvUtil.construire(
                List.of("A", "B", "C"),
                List.of(List.of("1", "2", "3")));
        String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);

        assertThat(contenu).isEqualTo("A;B;C\n1;2;3\n");
    }

    @Test
    @DisplayName("guillemets doublés pour le séparateur, le guillemet et le retour ligne")
    void echappementGuillemets() {
        assertThat(CsvUtil.cellule("a;b")).isEqualTo("\"a;b\"");
        assertThat(CsvUtil.cellule("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvUtil.cellule("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvUtil.cellule("a\rb")).isEqualTo("\"a\rb\"");
        // Un guillemet simple, sans séparateur ni retour : pas de guillemets.
        assertThat(CsvUtil.cellule("plain")).isEqualTo("plain");
    }

    @Test
    @DisplayName("garde anti-formule : apostrophe devant = + - @ tab CR")
    void gardeAntiFormule() {
        assertThat(CsvUtil.cellule("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvUtil.cellule("+33612345678")).isEqualTo("'+33612345678");
        assertThat(CsvUtil.cellule("-2")).isEqualTo("'-2");
        assertThat(CsvUtil.cellule("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvUtil.cellule("\tx")).isEqualTo("'\tx");
        // \r déclenche l'apostrophe PUIS les guillemets (le champ contient un retour chariot).
        assertThat(CsvUtil.cellule("\rx")).isEqualTo("\"'\rx\"");
    }

    @Test
    @DisplayName("champ vide ou absent : cellule vide, jamais « null »")
    void champVide() {
        assertThat(CsvUtil.cellule(null)).isEmpty();
        assertThat(CsvUtil.cellule("")).isEmpty();

        byte[] csv = CsvUtil.construire(
                List.of("A", "B"),
                List.of(java.util.Arrays.asList((String) null, "")));
        String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(contenu).contains("A;B\n;\n");
    }

    @Test
    @DisplayName("liste de lignes vide : seul l'en-tête est écrit")
    void aucuneLigne() {
        byte[] csv = CsvUtil.construire(List.of("Date", "Objet"), List.of());
        String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);

        assertThat(contenu).isEqualTo("Date;Objet\n");
    }
}
