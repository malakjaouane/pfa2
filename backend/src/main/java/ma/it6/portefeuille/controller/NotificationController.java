package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.AlerteDto;
import ma.it6.portefeuille.dto.NotificationResponse;
import ma.it6.portefeuille.service.AlerteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cloche de notifications : le compte voit les alertes du portefeuille
 * recalculées à la volée — aucune table, aucun compteur à synchroniser.
 *
 * <p>{@code total} est simplement le nombre d'alertes ; la frise les
 * déroule. Accessible à tout utilisateur connecté — le contenu s'adapte aux
 * permissions (demandes et budget selon les droits).</p>
 */
@RestController
public class NotificationController {

    private final AlerteService alerteService;

    public NotificationController(AlerteService alerteService) {
        this.alerteService = alerteService;
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<NotificationResponse> notifications() {
        List<AlerteDto> alertes = alerteService.toutes();
        return ResponseEntity.ok(new NotificationResponse(alertes.size(), alertes));
    }
}
