package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requete d'inscription : cree une organisation + son premier administrateur.
 *
 * <p>Les contraintes sont declarees ici : sans elles, une requete
 * incomplete (champ absent ou vide) provoquait une NullPointerException
 * — une erreur 500 avec trace technique — au lieu d'une reponse 400
 * exploitable par le client.</p>
 *
 * <p>Les longueurs reprennent celles des colonnes (utilisateur : nom et
 * prenom 100, e-mail 255 ; organisation : nom 150).</p>
 */
public record InscriptionRequest(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail est invalide")
        @Size(max = 255, message = "L'e-mail est trop long")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 200, message = "Le mot de passe est trop long")
        String motDePasse,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom est trop long")
        String nom,

        @NotBlank(message = "Le prenom est obligatoire")
        @Size(max = 100, message = "Le prenom est trop long")
        String prenom,

        @NotBlank(message = "Le nom de l'organisation est obligatoire")
        @Size(max = 150, message = "Le nom de l'organisation est trop long")
        String nomOrganisation) {}
