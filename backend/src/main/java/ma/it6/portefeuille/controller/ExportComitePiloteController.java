package ma.it6.portefeuille.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.it6.portefeuille.service.ExportComitePiloteService;

/**
 * Export "comite de pilotage" (4F).
 *
 * <p>Route :</p>
 * <ul>
 *   <li>{@code GET /api/export/comite-pilote?format=pdf|pptx} — genere un
 *       rapport PDF ou PowerPoint contenant : portefeuille (initiatives par
 *       statut, entite, responsable), retards, risques critiques, budget
 *       reellement depense vs budget.</li>
 * </ul>
 *
 * <p>Isolée par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api/export")
public class ExportComitePiloteController {

    private final ExportComitePiloteService exportService;

    public ExportComitePiloteController(ExportComitePiloteService exportService) {
        this.exportService = exportService;
    }

    /**
     * Genere un rapport "comite de pilotage" au format demande.
     *
     * @param format "pdf" ou "pptx"
     * @param email  e-mail du compte connecte (principal JWT)
     * @return document PDF ou PowerPoint en telechargement
     */
    @GetMapping("/comite-pilote")
    public ResponseEntity<byte[]> exporter(
            @RequestParam String format,
            @AuthenticationPrincipal String email) {
        byte[] contenu = exportService.exporter(format);

        String contentType;
        String extension;
        if ("pdf".equalsIgnoreCase(format)) {
            contentType = MediaType.APPLICATION_PDF_VALUE;
            extension = "pdf";
        } else if ("pptx".equalsIgnoreCase(format)) {
            contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            extension = "pptx";
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Format non supporte : " + format);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=comite-pilote-" + extension)
                .body(contenu);
    }
}
