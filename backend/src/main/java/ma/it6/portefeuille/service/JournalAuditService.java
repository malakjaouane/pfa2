package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.JournalAuditResponse;
import ma.it6.portefeuille.entity.JournalAudit;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.repository.JournalAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Lecture du journal d'audit des actions HTTP — <strong>strictement en
 * lecture</strong> : aucune écriture, aucune mise à jour, aucune suppression
 * n'est exposée (le journal est une preuve, pas une donnée à corriger).
 *
 * <p>Les lignes sont produites par le filtre d'audit
 * ({@code AuditFilterConfig}) ; ce service ne fait que les présenter avec
 * des filtres (e-mail, méthode, route, période).</p>
 *
 * <p>Garde-fous :</p>
 * <ul>
 *   <li>méthode HTTP inconnue (ex. {@code GET}) → 400 ;</li>
 *   <li>période inversée ({@code du} &gt; {@code au}) → 400 ;</li>
 *   <li>journal vide renvoyé comme liste vide, jamais une erreur.</li>
 * </ul>
 */
@Service
public class JournalAuditService {

    /** Méthodes journalisables (le filtre ne retient que les écritures). */
    public static final Set<String> METHODES = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Taille de page par défaut et plafond. */
    public static final int TAILLE_PAR_DEFAUT = 25;
    public static final int TAILLE_MAXIMALE = 200;

    private final JournalAuditRepository journalRepository;

    public JournalAuditService(JournalAuditRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    /** Une page du journal, triée de la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public PageJournal lister(String email, String methode, String route,
                              LocalDate du, LocalDate au, int numeroPage, int taille) {
        verifierPeriode(du, au);
        Page<JournalAudit> page = journalRepository.rechercher(
                normaliser(email), methodeValide(methode), normaliser(route),
                debut(du), finExclusive(au),
                PageRequest.of(Math.max(0, numeroPage),
                        Math.min(Math.max(taille, 1), TAILLE_MAXIMALE)));
        return new PageJournal(page.map(JournalAuditService::toResponse).getContent(),
                page.getTotalElements());
    }

    /** Export complet des lignes filtrées (toutes les pages, sans pagination). */
    @Transactional(readOnly = true)
    public List<JournalAuditResponse> exporter(String email, String methode, String route,
                                               LocalDate du, LocalDate au) {
        verifierPeriode(du, au);
        return journalRepository.rechercher(
                        normaliser(email), methodeValide(methode), normaliser(route),
                        debut(du), finExclusive(au),
                        PageRequest.of(0, Integer.MAX_VALUE))
                .map(JournalAuditService::toResponse)
                .getContent();
    }

    /** Une page du journal : lignes + total hors pagination. */
    public record PageJournal(List<JournalAuditResponse> lignes, long total) {
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    /** Période inversée (du > au) → 400 plutôt qu'un résultat vide trompeur. */
    private static void verifierPeriode(LocalDate du, LocalDate au) {
        if (du != null && au != null && du.isAfter(au)) {
            throw new BadRequestException(
                    "La période est inversée : la date de début ne peut pas dépasser la date de fin.");
        }
    }

    /** Méthode absente ou inconnue → 400 plutôt qu'un filtre muet. */
    private static String methodeValide(String methode) {
        if (methode == null || methode.isBlank()) {
            return null;
        }
        String nettoye = methode.trim().toUpperCase();
        if (!METHODES.contains(nettoye)) {
            throw new BadRequestException(
                    "Méthode d'audit inconnue : " + methode
                            + " (valeurs acceptées : POST, PUT, PATCH, DELETE).");
        }
        return nettoye;
    }

    /** Jour de début → minuit inclus. */
    private static LocalDateTime debut(LocalDate du) {
        return du == null ? null : du.atStartOfDay();
    }

    /** Jour de fin → minuit du lendemain, exclus (jour inclus côté client). */
    private static LocalDateTime finExclusive(LocalDate au) {
        return au == null ? null : au.plusDays(1).atStartOfDay();
    }

    private static String normaliser(String valeur) {
        if (valeur == null) {
            return null;
        }
        String nettoye = valeur.trim();
        return nettoye.isEmpty() ? null : nettoye;
    }

    static JournalAuditResponse toResponse(JournalAudit a) {
        if (a == null) {
            return null;
        }
        return new JournalAuditResponse(
                a.getIdAudit(),
                a.getDateAction(),
                a.getIdUtilisateur(),
                a.getEmail(),
                a.getMethode(),
                a.getRoute(),
                a.getStatutHttp(),
                a.getAdresseIp(),
                a.getCorps());
    }
}
