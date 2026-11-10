package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Préférences de notification d’un utilisateur (4D).
 * Détermine quels canaux (email, WhatsApp) et quelle fréquence
 * (aucune, quotidienne, hebdomadaire) le compte souhaite recevoir.
 */
@Entity
@Table(name = "preference_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenceNotification extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preference_notification")
    private Long idPreferenceNotification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "email_actif", nullable = false)
    private Boolean emailActif = false;

    @Column(name = "frequence", nullable = false, length = 20)
    private String frequence = "aucune"; // 'aucune', 'quotidienne', 'hebdomadaire'

    @Column(name = "whatsapp_actif", nullable = false)
    private Boolean whatsappActif = false;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
