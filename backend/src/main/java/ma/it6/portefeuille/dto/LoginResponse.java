package ma.it6.portefeuille.dto;

import java.util.List;

/**
 * Réponse d'authentification : jeton JWT + informations de l'utilisateur
 * connecté (profil, entité, permissions).
 *
 * @param doitChangerMotDePasse {@code true} = changement de mot de passe
 *                              obligatoire avant toute autre action
 *                              (compte créé hors profil dev).
 */
public record LoginResponse(
        String token,
        String type,
        Long idUser,
        String nom,
        String prenom,
        String email,
        RoleDto role,
        EntiteDto entite,
        List<String> permissions,
        Boolean doitChangerMotDePasse
) {
}