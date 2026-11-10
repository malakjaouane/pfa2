package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Notification envoyée (historique immuable, 4D).
 * Enregistre chaque envoi d’alerte par email ou WhatsApp.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notification")
    private Long idNotification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal; // 'email', 'whatsapp'

    @Column(name = "sujet", length = 255)
    private String sujet;

    @Column(name = "corps", columnDefinition = "TEXT", nullable = false)
    private String corps;

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut; // 'envoyee', 'echec'
}
