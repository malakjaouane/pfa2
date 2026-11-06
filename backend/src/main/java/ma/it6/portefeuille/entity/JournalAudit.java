package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Journal d'audit des actions HTTP : QUI a fait QUOI (verbe + route) depuis
 * OÙ (IP), quel que soit le résultat (statut HTTP).
 *
 * <p><b>Immuable</b> : le filtre d'audit n'écrit que de l'INSERT, aucune
 * mise à jour ni suppression n'est exposée. Contrairement aux autres
 * entités, cette table n'a <b>pas</b> de colonnes {@code created_at} /
 * {@code updated_at} / {@code version} : sa date de rédaction est portée
 * par {@code date_action} (écrite par le filtre, pas par JPA).</p>
 */
@Entity
@Table(name = "journal_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_audit")
    private Long idAudit;

    /** Date/heure de l'action (écrite par le filtre). */
    @Column(name = "date_action", nullable = false)
    private LocalDateTime dateAction;

    /** Auteur de l'action, anonymisé si le compte part (SET NULL). */
    @Column(name = "id_utilisateur")
    private Long idUtilisateur;

    /**
     * E-mail capturé dans le corps de la requête (login) ou dans le jeton.
     * Volontairement dénormalisé : l'audit reste lisible même après la
     * suppression du compte.
     */
    @Column(name = "email", length = 150)
    private String email;

    /** VERBE HTTP (POST / PUT / PATCH / DELETE). */
    @Column(name = "methode", nullable = false, length = 10)
    private String methode;

    /** Route : chemin + query, tronqué à 300 caractères. */
    @Column(name = "route", nullable = false, length = 300)
    private String route;

    /** Code HTTP retourné par l'application (la sécurité rejette hors filtre). */
    @Column(name = "statut_http", nullable = false)
    private Integer statutHttp;

    @Column(name = "adresse_ip", nullable = false, length = 45)
    private String adresseIp;

    /**
     * Corps de la requête, filtré (mots de passe masqués) et tronqué à
     * 300 caractères. {@code null} si la route n'est pas une route API.
     */
    @Column(name = "corps", length = 300)
    private String corps;
}
