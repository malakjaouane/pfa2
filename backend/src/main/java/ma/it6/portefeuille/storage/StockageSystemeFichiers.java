package ma.it6.portefeuille.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stockage des pièces jointes sur le système de fichiers local (défaut).
 *
 * <p>Les fichiers sont écrits dans le répertoire configuré par
 * {@code stockage.repertoire} (défaut {@code ./data/pieces-jointes}). Chaque
 * fichier reçoit un nom de stockage unique (UUID) pour éviter les collisions
 * et les problèmes de caractères spéciaux.</p>
 */
@Component
@org.springframework.context.annotation.Primary
public class StockageSystemeFichiers implements StockageFichier {

    private final Path repertoire;

    public StockageSystemeFichiers(
            @Value("${stockage.repertoire:./data/pieces-jointes}") String repertoire) {
        this.repertoire = Paths.get(repertoire);
        try {
            Files.createDirectories(this.repertoire);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible de créer le répertoire de stockage " + repertoire, e);
        }
    }

    @Override
    public String stocker(String nomFichier, byte[] contenu, String typeMime) {
        String cle = UUID.randomUUID().toString();
        Path cible = repertoire.resolve(cle);
        try {
            Files.write(cible, contenu);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible d'écrire le fichier " + cle, e);
        }
        return cle;
    }

    @Override
    public byte[] recuperer(String cleStockage) {
        Path cible = repertoire.resolve(cleStockage);
        try {
            return Files.readAllBytes(cible);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible de lire le fichier " + cleStockage, e);
        }
    }

    @Override
    public void supprimer(String cleStockage) {
        Path cible = repertoire.resolve(cleStockage);
        try {
            Files.deleteIfExists(cible);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible de supprimer le fichier " + cleStockage, e);
        }
    }
}
