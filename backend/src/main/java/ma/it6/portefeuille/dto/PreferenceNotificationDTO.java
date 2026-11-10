package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Préférences de notification d’un utilisateur (4D).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenceNotificationDTO {

    private Long idPreferenceNotification;
    private Long idUtilisateur;
    private Boolean emailActif;
    private String frequence; // 'aucune', 'quotidienne', 'hebdomadaire'
    private Boolean whatsappActif;
    private String telephone;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
}
