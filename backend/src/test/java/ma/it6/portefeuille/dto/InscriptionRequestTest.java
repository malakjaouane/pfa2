package ma.it6.portefeuille.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validation de la requete d'inscription.
 *
 * <p>Sans contraintes declarees, un champ absent provoquait une
 * NullPointerException dans le controleur (erreur 500) ; la requete doit
 * etre rejetee proprement (400) avant d'atteindre le service.</p>
 */
class InscriptionRequestTest {

    private static Validator validateur;

    @BeforeAll
    static void initialiser() {
        ValidatorFactory fabrique = Validation.buildDefaultValidatorFactory();
        validateur = fabrique.getValidator();
    }

    private static InscriptionRequest complete() {
        return new InscriptionRequest(
                "admin@it6.ma", "Admin@123", "Alaoui", "Karim", "IT6 Consulting");
    }

    @Test
    @DisplayName("requete complete : aucune violation")
    void requeteValide() {
        assertThat(validateur.validate(complete())).isEmpty();
    }

    @Test
    @DisplayName("e-mail absent : violation sur le champ email")
    void emailAbsent() {
        InscriptionRequest requete = new InscriptionRequest(
                null, "Admin@123", "Alaoui", "Karim", "IT6 Consulting");

        Set<ConstraintViolation<InscriptionRequest>> violations = validateur.validate(requete);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
    }

    @Test
    @DisplayName("e-mail invalide : violation sur le format")
    void emailInvalide() {
        InscriptionRequest requete = new InscriptionRequest(
                "pas-un-email", "Admin@123", "Alaoui", "Karim", "IT6 Consulting");

        assertThat(validateur.validate(requete))
                .anyMatch(v -> "email".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("champs vides (espaces) : chaque champ viole une contrainte")
    void champsVides() {
        InscriptionRequest requete = new InscriptionRequest(
                "   ", "   ", "  ", "  ", "   ");

        // Un compte exact serait fragile : l'e-mail rempli d'espaces viole
        // a la fois @NotBlank et @Email (6 violations au total).
        assertThat(validateur.validate(requete))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("email", "motDePasse", "nom", "prenom", "nomOrganisation");
    }

    @Test
    @DisplayName("tous les champs absents : 5 violations, aucune exception")
    void tousAbsents() {
        assertThat(validateur.validate(
                new InscriptionRequest(null, null, null, null, null))).hasSize(5);
    }

    @Test
    @DisplayName("valeur trop longue pour la colonne : violation @Size")
    void valeurTropLongue() {
        InscriptionRequest requete = new InscriptionRequest(
                "admin@it6.ma", "Admin@123", "x".repeat(101), "Karim", "IT6 Consulting");

        assertThat(validateur.validate(requete))
                .anyMatch(v -> "nom".equals(v.getPropertyPath().toString()));
    }
}
