package ma.it6.portefeuille.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fabrication de fichiers CSV destinés à l'export (Excel / LibreOffice).
 *
 * <p>Conventions, identiques pour tous les exports de l'application :</p>
 * <ul>
 *   <li>séparateur {@code ;} — le standard des tableurs francophones ;</li>
 *   <li>BOM UTF-8 en tête — sinon Excel ouvre en « ANSI » et casse les
 *       accents ;</li>
 *   <li>guillemets doublés pour les champs contenant séparateur, quote,
 *       saut de ligne ou retour chariot ;</li>
 *   <li><strong>garde anti-formule</strong> : un champ commençant par
 *       {@code =}, {@code +}, {@code -}, {@code @}, tabulation ou retour
 *       chariot reçoit une apostrophe de neutralisation — Excel sinon
 *       <em>exécuterait</em> la cellule (injection de formule).</li>
 * </ul>
 */
public final class CsvUtil {

    /** Séparateur de colonnes (standard Excel francophone). */
    public static final String SEPARATEUR = ";";

    /** BOM UTF-8 : sans lui, Excel casse les accents à l'ouverture. */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvUtil() {
    }

    /**
     * Construit le fichier complet : BOM + en-tête + lignes, chaque colonne
     * passant par {@link #cellule(String)}.
     *
     * @param entetes libellés de la première ligne
     * @param lignes  lignes de valeurs ({@code null} accepté = champ vide)
     */
    public static byte[] construire(List<String> entetes, List<List<String>> lignes) {
        StringBuilder csv = new StringBuilder();
        ajouterLigne(csv, entetes);
        if (lignes != null) {
            for (List<String> ligne : lignes) {
                ajouterLigne(csv, ligne);
            }
        }
        byte[] contenu = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] fichier = new byte[BOM.length + contenu.length];
        System.arraycopy(BOM, 0, fichier, 0, BOM.length);
        System.arraycopy(contenu, 0, fichier, BOM.length, contenu.length);
        return fichier;
    }

    /**
     * Une cellule : vide si absente, apostrophe de neutralisation si la
     * valeur commence par un caractère de formule, guillemets doublés si
     * nécessaire.
     */
    public static String cellule(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return "";
        }
        String protegee = commencementDeFormule(valeur) ? "'" + valeur : valeur;
        boolean aGuillemets = protegee.contains(SEPARATEUR)
                || protegee.contains("\"")
                || protegee.contains("\n")
                || protegee.contains("\r");
        if (!aGuillemets) {
            return protegee;
        }
        return "\"" + protegee.replace("\"", "\"\"") + "\"";
    }

    /** {@code true} si Excel interpréterait la valeur comme une formule. */
    private static boolean commencementDeFormule(String valeur) {
        char premier = valeur.charAt(0);
        return premier == '=' || premier == '+' || premier == '-'
                || premier == '@' || premier == '\t' || premier == '\r';
    }

    private static void ajouterLigne(StringBuilder csv, List<String> colonnes) {
        if (colonnes == null || colonnes.isEmpty()) {
            csv.append('\n');
            return;
        }
        for (int i = 0; i < colonnes.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATEUR);
            }
            csv.append(cellule(colonnes.get(i)));
        }
        csv.append('\n');
    }
}
