package ma.it6.portefeuille.dto;

/** Reinitialisation de mot de passe a partir d'un jeton a usage unique. */
public record ReinitialiserMotDePasseRequest(String jeton, String nouveauMotDePasse) {}
