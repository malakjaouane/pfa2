package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.RisqueRequest;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.service.RisqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST des risques d'une initiative (registre par initiative).
 *
 * <p>Lecture : {@code INITIATIVE_CONSULTER} (rattrapée par le GET des
 * initiatives). Écriture : {@code INITIATIVE_MODIFIER} — gérer les
 * risques, c'est enrichir le dossier de l'initiative elle-même.</p>
 */
@RestController
@RequestMapping("/api")
public class RisqueController {

    private final RisqueService risqueService;

    public RisqueController(RisqueService risqueService) {
        this.risqueService = risqueService;
    }

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
