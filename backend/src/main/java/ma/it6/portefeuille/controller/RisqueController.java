package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.RisqueRequest;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.mapper.RisqueMapper;
import ma.it6.portefeuille.service.RisqueService;
import ma.it6.portefeuille.util.CsvUtil;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * API REST des risques : registre par initiative <strong>et</strong>
 * registre global (toutes initiatives, filtré) — la vue « Risques » du menu.
 *
 * <p>Lecture : {@code INITIATIVE_CONSULTER}. Écriture : {@code RISQUE_GERER} —
 * noter et suivre un risque est un métier à part entière, distinct de la
 * simple modification de l'initiative.</p>
 */
@RestController
@RequestMapping("/api")
public class RisqueController {

    private static final DateTimeFormatter FORMATEUR_CSV =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private final RisqueService risqueService;

    public RisqueController(RisqueService risqueService) {
        this.risqueService = risqueService;
    }

    // ------------------------------------------------------------------
    // Registre global (page « Risques »)
    // ------------------------------------------------------------------

    /** Registre global paginé, filtré (initiative, statut, recherche). */
    @GetMapping("/risques")
    public ResponseEntity<List<RisqueResponse>> registreGlobal(
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0 ? 25 : Math.min(size, 200);
        Page<RisqueResponse> resultat =
                risqueService.listerGlobal(initiative, statut, recherche, numeroPage, taille);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultat.getTotalElements()))
                .body(resultat.getContent());
    }

    /** Export CSV de la sélection filtrée — toutes les lignes. */
    @GetMapping("/risques/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String recherche) {

        List<RisqueResponse> lignes = risqueService
                .listerGlobal(initiative, statut, recherche, 0, 200)
                .getContent();

        List<List<String>> contenu = lignes.stream()
                .map(r -> List.of(
                        r.codeInitiative() == null ? "" : r.codeInitiative(),
                        r.titre() == null ? "" : r.titre(),
                        r.description(),
                        r.statut() == null ? "" : r.statut(),
                        r.echeance() == null ? "" : FORMATEUR_CSV.format(r.echeance()),
                        String.valueOf(r.probabilite()),
                        String.valueOf(r.impact()),
                        String.valueOf(r.score()),
                        r.niveau(),
                        r.responsable() == null ? "" : r.responsable().nomComplet(),
                        r.planAction() == null ? "" : r.planAction()))
                .toList();
        byte[] csv = CsvUtil.construire(
                List.of("Initiative", "Titre", "Description", "Statut", "Échéance",
                        "Probabilité", "Impact", "Score", "Niveau", "Responsable", "Plan d'action"),
                contenu);

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        entetes.setContentDisposition(ContentDisposition.attachment()
                .filename("risques.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(entetes).body(csv);
    }

    // ------------------------------------------------------------------
    // Registre d'une initiative (fiche)
    // ------------------------------------------------------------------

    @GetMapping("/initiatives/{idInitiative}/risques")
    public ResponseEntity<List<RisqueResponse>> lister(@PathVariable Long idInitiative) {
        return ResponseEntity.ok(risqueService.lister(idInitiative));
    }

    @PostMapping("/initiatives/{idInitiative}/risques")
    public ResponseEntity<RisqueResponse> creer(@PathVariable Long idInitiative,
                                                @Valid @RequestBody RisqueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(risqueService.creer(idInitiative, request));
    }

    @PutMapping("/risques/{idRisque}")
    public ResponseEntity<RisqueResponse> modifier(@PathVariable Long idRisque,
                                                   @Valid @RequestBody RisqueRequest request) {
        return ResponseEntity.ok(risqueService.modifier(idRisque, request));
    }

    @DeleteMapping("/risques/{idRisque}")
    public ResponseEntity<Void> supprimer(@PathVariable Long idRisque) {
        risqueService.supprimer(idRisque);
        return ResponseEntity.noContent().build();
    }
}
