package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.ConnexionResponse;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.service.HistoriqueConnexionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Journal des tentatives de connexion (lecture, suppression d'une ligne,
 * purge totale).
 *
 * <p><strong>Tout accès est réservé aux administrateurs</strong> :
 * SecurityConfig impose {@code UTILISATEUR_GERER} sur
 * {@code /api/historique-connexions/**}, tous verbes confondus — le journal
 * expose e-mails et adresses IP, données de contrôle d'accès. Aucun nouveau
 * droit n'est créé ; aucun POST/PUT n'est déclaré (405 ailleurs).</p>
 */
@RestController
@RequestMapping("/api/historique-connexions")
public class HistoriqueConnexionController {

    private final HistoriqueConnexionService historiqueConnexionService;

    public HistoriqueConnexionController(HistoriqueConnexionService historiqueConnexionService) {
        this.historiqueConnexionService = historiqueConnexionService;
    }

    /**
     * Une page du journal des connexions, filtrée (recherche e-mail/IP/nom,
     * résultat, période) et triée du plus récent au plus ancien.
     *
     * @param du  début de période (jour inclus)
     * @param au  fin de période (jour inclus)
     * @param page numéro de page (0-indexé), 0 par défaut
     * @param size taille de page, plafonnée à
     *             {@value HistoriqueConnexionService#TAILLE_MAXIMALE}
     * @return la page + le compte total hors pagination dans {@code X-Total-Count}
     */
    @GetMapping
    public ResponseEntity<List<ConnexionResponse>> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Boolean succes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (du != null && au != null && du.isAfter(au)) {
            throw new BadRequestException("Période invalide : la date « du » est postérieure à la date « au ».");
        }

        HistoriqueConnexionService.FiltreConnexion filtre =
                new HistoriqueConnexionService.FiltreConnexion(recherche, succes, du, au);
        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? HistoriqueConnexionService.TAILLE_PAR_DEFAUT
                : Math.min(size, HistoriqueConnexionService.TAILLE_MAXIMALE);

        List<ConnexionResponse> lignes =
                historiqueConnexionService.lister(filtre, numeroPage, taille);

        // Compte total HORS pagination (même convention que l'historique
        // des statuts et la liste des initiatives).
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(historiqueConnexionService.compter(filtre)))
                .body(lignes);
    }

    /**
     * Suppression d'une ligne du journal — administrateurs uniquement
     * (droit existant {@code UTILISATEUR_GERER}, imposé par SecurityConfig).
     *
     * @return {@code 204} si la ligne existait, {@code 404} sinon
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        historiqueConnexionService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Purge TOUT le journal des connexions — administrateurs uniquement
     * (droit existant {@code UTILISATEUR_GERER}, imposé par SecurityConfig
     * sur {@code DELETE /api/historique-connexions}).
     *
     * @return {@code 204} une fois la table vidée
     */
    @DeleteMapping
    public ResponseEntity<Void> purgerTout() {
        historiqueConnexionService.purgerTout();
        return ResponseEntity.noContent().build();
    }
}
