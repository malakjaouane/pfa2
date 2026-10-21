package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.HistoriqueStatutResponse;
import ma.it6.portefeuille.dto.UserSummaryDto;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.service.HistoriqueStatutService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Journal global des transitions de statut — <strong>lecture seule</strong>.
 *
 * <p>Aucun POST/PUT/PATCH/DELETE n'est déclaré ici (et la règle d'URL en
 * SecurityConfig n'ouvre que GET) : les anciennes entrées ne peuvent ni être
 * modifiées ni être supprimées depuis l'interface. Seule l'écriture interne du
 * service, appelée dans la transaction du changement de statut, alimente le
 * journal.</p>
 *
 * <p>La permission exigée est {@code INITIATIVE_CONSULTER} — la même que la
 * consultation des initiatives : aucun nouveau droit n'est créé.</p>
 */
@RestController
@RequestMapping("/api/historique-statuts")
public class HistoriqueStatutController {

    private final HistoriqueStatutService historiqueStatutService;

    public HistoriqueStatutController(HistoriqueStatutService historiqueStatutService) {
        this.historiqueStatutService = historiqueStatutService;
    }

    /**
     * Une page du journal global, filtrée (recherche code/nom, statut,
     * utilisateur, période, initiative) et triée.
     *
     * @param direction {@code asc} (plus ancien d'abord) ou {@code desc}
     *                  (plus récent d'abord, par défaut)
     * @param page      numéro de page (0-indexé), 0 par défaut
     * @param size      taille de page, plafonnée à
     *                  {@value HistoriqueStatutService#TAILLE_MAXIMALE}
     * @return la page + le compte total hors pagination dans {@code X-Total-Count}
     */
    @GetMapping
    public ResponseEntity<List<HistoriqueStatutResponse>> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Long utilisateur,
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String direction) {

        if (du != null && au != null && du.isAfter(au)) {
            throw new BadRequestException("Période invalide : la date « du » est postérieure à la date « au ».");
        }
        boolean triAsc = "asc".equalsIgnoreCase(direction == null ? "" : direction.trim());
        String directionNormalisee = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
        if (!directionNormalisee.isEmpty()
                && !directionNormalisee.equals("asc") && !directionNormalisee.equals("desc")) {
            throw new BadRequestException("Direction de tri inconnue : « " + direction.trim()
                    + " » (valeurs admises : asc, desc).");
        }

        HistoriqueStatutService.FiltreHistorique filtre =
                new HistoriqueStatutService.FiltreHistorique(recherche, statut, utilisateur, initiative, du, au);
        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? HistoriqueStatutService.TAILLE_PAR_DEFAUT
                : Math.min(size, HistoriqueStatutService.TAILLE_MAXIMALE);

        List<HistoriqueStatutResponse> lignes = historiqueStatutService.lister(filtre, numeroPage, taille, triAsc);

        // Compte total HORS pagination : c'est ce dont la pagination du
        // tableau a besoin pour savoir s'il reste des pages (même convention
        // que la liste des initiatives).
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(historiqueStatutService.compter(filtre)))
                .body(lignes);
    }

    /**
     * Auteurs réellement présents dans le journal (liste déroulante de filtre).
     * La liste est limitée aux personnes ayant déjà agi : aucun droit n'est
     * ouvert au-delà de la simple lecture du journal.
     */
    @GetMapping("/auteurs")
    public ResponseEntity<List<UserSummaryDto>> auteurs() {
        return ResponseEntity.ok(historiqueStatutService.auteurs());
    }
}
