package ma.it6.portefeuille.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Détecte le type MIME réel d'un fichier à partir de ses premiers octets
 * (« magic bytes ») plutôt que du type déclaré par le client.
 *
 * <p>Le type déclaré dans l'en-tête HTTP peut être falsifié : un exécutable
 * renommé en {@code .pdf} déclare {@code application/pdf}. La vérification
 * des signatures réelles bloque ce contournement avant tout stockage.</p>
 *
 * <p>Types reconnus : PDF, PNG, JPEG, GIF, ZIP (xlsx/docx distingués par les
 * entrées de l'archive), OLE2 (xls/doc anciens) et texte (CSV). Retourne
 * {@code null} lorsque la signature est inconnue : l'appelant refuse alors
 * le fichier.</p>
 */
public final class DetecteurTypeMime {

    /** PDF. */
    public static final String PDF = "application/pdf";
    /** PNG. */
    public static final String PNG = "image/png";
    /** JPEG. */
    public static final String JPEG = "image/jpeg";
    /** GIF. */
    public static final String GIF = "image/gif";
    /** Excel moderne (xlsx). */
    public static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Word moderne (docx). */
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    /** OLE2 : Excel et Word anciens (xls, doc). */
    public static final String OLE2 = "application/x-ole2";
    /** Texte brut (CSV). */
    public static final String TEXTE = "text/plain";

    private DetecteurTypeMime() {
    }

    /**
     * Détecte le type MIME du contenu.
     *
     * @param octets contenu du fichier
     * @return type MIME canonique détecté, ou {@code null} si inconnu
     */
    public static String detecter(byte[] octets) {
        if (octets == null || octets.length == 0) {
            return null;
        }
        if (commencePar(octets, 0x25, 0x50, 0x44, 0x46)) { // %PDF
            return PDF;
        }
        if (commencePar(octets, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (commencePar(octets, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (commencePar(octets, 0x47, 0x49, 0x46, 0x38)) { // GIF8
            return GIF;
        }
        if (commencePar(octets, 0x50, 0x4B, 0x03, 0x04)) { // ZIP
            return typeZip(octets);
        }
        if (commencePar(octets, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) { // OLE2
            return OLE2;
        }
        if (estTexte(octets)) {
            return TEXTE;
        }
        return null;
    }

    /** Vérifie que les premiers octets correspondent à la signature. */
    private static boolean commencePar(byte[] octets, int... signature) {
        if (octets.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((octets[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Distingue xlsx (entrée « xl/ ») de docx (entrée « word/ ») dans un ZIP.
     * Retourne {@code null} pour toute autre archive ZIP.
     */
    private static String typeZip(byte[] octets) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(octets))) {
            ZipEntry entree;
            int compteur = 0;
            while ((entree = zip.getNextEntry()) != null && compteur < 20) {
                String nom = entree.getName();
                if (nom.startsWith("xl/")) {
                    return XLSX;
                }
                if (nom.startsWith("word/")) {
                    return DOCX;
                }
                compteur++;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Texte : aucun caractère de contrôle (hors tabulation, saut de ligne,
     * retour chariot) dans les premiers Ko — CSV et texte brut.
     */
    private static boolean estTexte(byte[] octets) {
        int limite = Math.min(octets.length, 8192);
        for (int i = 0; i < limite; i++) {
            int b = octets[i] & 0xFF;
            boolean imprimable = b == 0x09 || b == 0x0A || b == 0x0D
                    || (b >= 0x20 && b != 0x7F);
            if (!imprimable) {
                return false;
            }
        }
        return true;
    }
}
