package ma.it6.portefeuille.dto;

import java.time.LocalDate;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Formulaire de creation d'initiative (4G : creation depuis texte libre
 * par l'assistant IA). Les champs sont facultatifs : l'utilisateur peut
 * les completer avant de confirmer la creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiativeFormeDto {

    private String code;
    private String nom;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFinPrevue;
    private java.math.BigDecimal budget;
    private Integer avancement;
    private Long idEntite;
    private Long idResponsable;
    private Long idStatut;
    private Long idPriorite;
    private Long idObjectif;
}
