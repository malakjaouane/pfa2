package ma.it6.portefeuille.storage;

/**
 * Abstraction du stockage des pièces jointes.
 *
 * <p>Deux implémentations sont disponibles, sélectionnées par la propriété
 * {@code stockage.type} :
 * <ul>
 *   <li>{@code systeme} (défaut) : {@link StockageSystemeFichiers}, répertoire
 *       local configuré par {@code stockage.repertoire} ;</li>
 *   <li>{@code s3} : {@link StockageS3}, bucket Amazon S3 configuré par
 *       {@code stockage.s3.*}. Désactivable en ne configurant pas les
 *       identifiants AWS.</li>
 * </ul>
 *
 * <p>Le binaire n'est jamais stocké en base de données : seule sa clé de
 * stockage, son nom, son type MIME et sa taille le sont dans la table
 * {@code piece_jointe}.</p>
 */
public interface StockageFichier {

    /**
     * Stocke un fichier et retourne sa clé de stockage.
     *
     * @param nomFichier nom original du fichier
     * @param contenu    octets du fichier
     * @param typeMime   type MIME du fichier
     * @return clé de stockage permettant de récupérer le fichier
     */
    String stocker(String nomFichier, byte[] contenu, String typeMime);

    /**
     * Récupère le contenu d'un fichier stocké.
     *
     * @param cleStockage clé de stockage retournée par {@link #stocker}
     * @return octets du fichier
     */
    byte[] recuperer(String cleStockage);

    /**
     * Supprime un fichier stocké.
     *
     * @param cleStockage clé de stockage du fichier à supprimer
     */
    void supprimer(String cleStockage);
}
