package ma.it6.portefeuille.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ma.it6.portefeuille.dto.ImportResultatDTO;
import ma.it6.portefeuille.service.ImportInitiativeService;

/**
 * Import d'initiatives depuis un fichier CSV (séparateur point-virgule) ou
 * Excel (.xlsx).
 *
 * <p>Routes :</p>
 * <ul>
 *   <li>{@code GET /api/initiatives/import/modele} — modèle CSV
 *       téléchargeable (colonnes requises + ligne d'exemple) ;</li>
 *   <li>{@code POST /api/initiatives/import} — upload du fichier avec
 *       option « ignorer les erreurs » (réservé à
 *       {@code INITIATIVE_CREER}).</li>
 * </ul>
 *
 * <p>Réponse :</p>
 * <ul>
 *   <li>201 si l'import réussit (ou lignes valides importées en mode
 *       « ignorer les erreurs ») ;</li>
 *   <li>400 en mode strict si au moins une ligne est en erreur (le corps
 *       contient le rapport détaillé ligne par ligne) ou si la limite du
 *       plan est dépassée.</li>
 * </ul>
 *
 * <p>Isolée par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api")
public class ImportInitiativeController {

    private final ImportInitiativeService importService;

    public ImportInitiativeController(ImportInitiativeService importService) {
        this.importService = importService;
    }

    /**
     * Modèle CSV d'import : colonnes requises + optionnelles, avec une
     * ligne d'exemple. Même droit que l'import (INITIATIVE_CREER).
     *
     * @return fichier CSV téléchargeable (BOM UTF-8, séparateur « ; »)
     */
    @GetMapping("/initiatives/import/modele")
    public ResponseEntity<byte[]> modele() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=modele-import-initiatives.csv")
                .body(importService.modele());
    }

    /**
     * Importe des initiatives depuis un fichier CSV ou Excel.
     *
     * @param fichier          fichier à importer (non vide)
     * @param ignorerErreurs   si true, saute les lignes en erreur et importe
     *                         seulement les lignes valides
     * @param email            e-mail du compte connecté (principal JWT)
     * @return résultat détaillé de l'import
     */
    @PostMapping("/initiatives/import")
    public ResponseEntity<ImportResultatDTO> importer(
            @RequestParam MultipartFile fichier,
            @RequestParam(defaultValue = "false") boolean ignorerErreurs,
            @AuthenticationPrincipal String email) {
        ImportResultatDTO resultat = importService.importer(fichier, ignorerErreurs);

        boolean strict = !"ignorer_erreurs".equals(resultat.getMode());
        boolean aErreurs = resultat.getErreurs() != null && !resultat.getErreurs().isEmpty();

        if (strict && aErreurs) {
            return ResponseEntity.badRequest().body(resultat);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(resultat);
    }
}
