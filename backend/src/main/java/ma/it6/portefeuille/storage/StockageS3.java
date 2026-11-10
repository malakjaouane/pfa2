package ma.it6.portefeuille.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stockage des pièces jointes sur Amazon S3.
 *
 * <p>Sélectionné lorsque {@code stockage.type=s3}. Nécessite les propriétés
 * {@code stockage.s3.bucket}, {@code stockage.s3.region} et les identifiants
 * AWS (variables d'environnement {@code AWS_ACCESS_KEY_ID} /
 * {@code AWS_SECRET_ACCESS_KEY} ou profil IAM). Si aucune de ces propriétés
 * n'est définie, le bean n'est pas créé et le système de fichiers local est
 * utilisé.</p>
 */
@Component
@ConditionalOnProperty(
        name = "stockage.type",
        havingValue = "s3",
        matchIfMissing = false)
public class StockageS3 implements StockageFichier {

    private final S3Client s3;
    private final String bucket;

    public StockageS3(
            @Value("${stockage.s3.bucket}") String bucket,
            @Value("${stockage.s3.region:eu-west-3}") String region) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();
    }

    @Override
    public String stocker(String nomFichier, byte[] contenu, String typeMime) {
        String cle = UUID.randomUUID().toString();
        PutObjectRequest requete = PutObjectRequest.builder()
                .bucket(bucket)
                .key(cle)
                .contentType(typeMime)
                .build();
        s3.putObject(requete, RequestBody.fromBytes(contenu));
        return cle;
    }

    @Override
    public byte[] recuperer(String cleStockage) {
        GetObjectRequest requete = GetObjectRequest.builder()
                .bucket(bucket)
                .key(cleStockage)
                .build();
        try (InputStream flux = s3.getObject(requete)) {
            return flux.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible de lire le fichier S3 " + cleStockage, e);
        }
    }

    @Override
    public void supprimer(String cleStockage) {
        DeleteObjectRequest requete = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(cleStockage)
                .build();
        s3.deleteObject(requete);
    }
}
