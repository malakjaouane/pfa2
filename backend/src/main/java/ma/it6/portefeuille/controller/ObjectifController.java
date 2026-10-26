package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.ObjectifDto;
import ma.it6.portefeuille.dto.ObjectifRequest;
import ma.it6.portefeuille.service.ObjectifService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST des objectifs stratégiques.
 */
@RestController
@RequestMapping("/api/objectifs")
public class ObjectifController {

    private final ObjectifService objectifService;

    public ObjectifController(ObjectifService objectifService) {
        this.objectifService = objectifService;
    }

    @GetMapping
    public ResponseEntity<List<ObjectifDto>> lister() {
        return ResponseEntity.ok(objectifService.lister());
    }

    @PostMapping
    public ResponseEntity<ObjectifDto> creer(@Valid @RequestBody ObjectifRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(objectifService.creer(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObjectifDto> modifier(@PathVariable Long id,
                                                @Valid @RequestBody ObjectifRequest request) {
        return ResponseEntity.ok(objectifService.modifier(id, request));
    }

    /**
     * Suppression définitive d'un objectif — le droit existant
     * {@code INITIATIVE_MODIFIER} s'applique (SecurityConfig, même règle
     * que la création et la modification : aucun nouveau droit créé).
     *
     * <p>Refus <strong>400</strong> si l'objectif est référencé par au
     * moins une initiative (la clé étrangère est en
     * {@code ON DELETE SET NULL} : le détachement silencieux perdrait
     * l'alignement stratégique), <strong>404</strong> s'il n'existe pas.</p>
     *
     * @return {@code 204} si l'objectif a été supprimé
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        objectifService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}