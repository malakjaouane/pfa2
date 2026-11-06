package ma.it6.portefeuille.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Alerte calculée à la volée (aucune table) : jalons en retard ou proches,
 * initiative en retard, risque critique ou échéance proche, demande en
 * attente d'évaluation, budget dépassé.
 *
 * <p>Le libellé n'est <strong>jamais</strong> formé côté serveur : seul le
 * {@code type} part vers le front, qui traduit
 * {@code suivi.alerte.<type>} avec les {@code parametres} fournis.</p>
 *
 * @param type          code d'alerte (ex. {@code jalonEnRetard})
 * @param niveau        {@code ROUGE} | {@code ORANGE} — code badge existant
 * @param reference     référence affichable (code initiative, nom du jalon…)
 * @param libelle       libellé court déjà prêt à afficher
 * @param echeance      date pilote de l'alerte (échéance / fin prévue)
 * @param parametres    paramètres d'interpolation du message traduit
 */
public record AlerteDto(
        String type,
        String niveau,
        Long idInitiative,
        Long idDemande,
        String reference,
        String libelle,
        LocalDate echeance,
        Map<String, Object> parametres
) {
}
