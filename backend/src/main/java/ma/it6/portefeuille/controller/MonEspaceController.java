package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.AlerteDto;
import ma.it6.portefeuille.dto.MonEspaceResponse;
import ma.it6.portefeuille.service.AlerteService;
import ma.it6.portefeuille.service.MonEspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * « Mon espace » : l'agrégat personnel du compte connecté — profil,
 * initiatives pilotées, jalons proches, risques ouverts, alertes,
 * activité récente et, selon les droits, demandes à évaluer et chiffres
 * budgétaires.
 *
 * <p>Visible par les trois profils (administrateur, responsable, visiteur)
 * : le contenu s'adapte aux permissions. Le bloc d'alertes de la fiche
 * d'initiative est exposé par {@link #alertesInitiative(Long)}.</p>
 */
@RestController
public class MonEspaceController {

    private final MonEspaceService monEspaceService;
    private final AlerteService alerteService;

    public MonEspaceController(MonEspaceService monEspaceService,
                               AlerteService alerteService) {
        this.monEspaceService = monEspaceService;
        this.alerteService = alerteService;
    }

    @GetMapping("/api/mon-espace")
    public ResponseEntity<MonEspaceResponse> monEspace() {
        return ResponseEntity.ok(monEspaceService.consulter());
    }

    /** Alertes d'une initiative (bloc de la fiche de suivi). */
    @GetMapping("/api/initiatives/{idInitiative}/alertes")
    public ResponseEntity<List<AlerteDto>> alertesInitiative(@PathVariable Long idInitiative) {
        return ResponseEntity.ok(alerteService.pourInitiative(idInitiative));
    }
}
