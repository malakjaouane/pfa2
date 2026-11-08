package ma.it6.portefeuille.dto;

/** Demande de reinitialisation de mot de passe (adresse e-mail uniquement). */
public record MotDePasseOublieRequest(String email) {}
