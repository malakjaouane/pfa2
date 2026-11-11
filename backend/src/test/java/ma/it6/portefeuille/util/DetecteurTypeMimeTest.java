package ma.it6.portefeuille.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests du scan MIME serveur (magic bytes) : chaque type autorisé doit être
 * reconnu par sa signature, tout contenu inconnu doit être refusé.
 */
class DetecteurTypeMimeTest {

    private static byte[] texte(String valeur) {
        return valeur.getBytes(StandardCharsets.UTF_8);
    }

    /** Construit une archive ZIP contenant les entrées données. */
    private static byte[] zipAvec(String... entrees) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (String entree : entrees) {
                zip.putNextEntry(new ZipEntry(entree));
                zip.write(texte("contenu"));
                zip.closeEntry();
            }
            zip.finish();
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("PDF : signature « %PDF » détectée")
    void pdf() {
        assertThat(DetecteurTypeMime.detecter(texte("%PDF-1.7 faux fichier")))
                .isEqualTo(DetecteurTypeMime.PDF);
    }

    @Test
    @DisplayName("PNG : signature 89 50 4E 47 détectée")
    void png() {
        assertThat(DetecteurTypeMime.detecter(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01 }))
                .isEqualTo(DetecteurTypeMime.PNG);
    }

    @Test
    @DisplayName("JPEG : signature FF D8 FF détectée")
    void jpeg() {
        assertThat(DetecteurTypeMime.detecter(
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 }))
                .isEqualTo(DetecteurTypeMime.JPEG);
    }

    @Test
    @DisplayName("GIF : signature « GIF8 » détectée")
    void gif() {
        assertThat(DetecteurTypeMime.detecter(texte("GIF89a image")))
                .isEqualTo(DetecteurTypeMime.GIF);
    }

    @Test
    @DisplayName("XLSX : ZIP avec entrée « xl/ » détecté")
    void xlsx() {
        assertThat(DetecteurTypeMime.detecter(zipAvec("xl/workbook.xml", "xl/feuille1.xml")))
                .isEqualTo(DetecteurTypeMime.XLSX);
    }

    @Test
    @DisplayName("DOCX : ZIP avec entrée « word/ » détecté")
    void docx() {
        assertThat(DetecteurTypeMime.detecter(zipAvec("[Content_Types].xml", "word/document.xml")))
                .isEqualTo(DetecteurTypeMime.DOCX);
    }

    @Test
    @DisplayName("OLE2 (xls/doc anciens) : signature D0 CF 11 E0 détectée")
    void ole2() {
        assertThat(DetecteurTypeMime.detecter(new byte[] {
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 }))
                .isEqualTo(DetecteurTypeMime.OLE2);
    }

    @Test
    @DisplayName("CSV : texte sans caractère de contrôle détecté")
    void texte() {
        assertThat(DetecteurTypeMime.detecter(texte("code;libelle;montant\nMOB;Test;100\n")))
                .isEqualTo(DetecteurTypeMime.TEXTE);
    }

    @Test
    @DisplayName("Contenu inconnu (binaire) : refusé (null)")
    void inconnu() {
        assertThat(DetecteurTypeMime.detecter(new byte[] { 0x00, 0x01, 0x02, 0x03 })).isNull();
    }

    @Test
    @DisplayName("ZIP non Office : refusé (null)")
    void zipNonOffice() {
        assertThat(DetecteurTypeMime.detecter(zipAvec("autre/fichier.txt"))).isNull();
    }

    @Test
    @DisplayName("Contenu vide : refusé (null)")
    void vide() {
        assertThat(DetecteurTypeMime.detecter(new byte[0])).isNull();
    }
}
