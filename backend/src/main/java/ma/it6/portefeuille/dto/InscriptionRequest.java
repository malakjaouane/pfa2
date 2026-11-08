package ma.it6.portefeuille.dto;

/** Requete d'inscription : cree une organisation + son premier administrateur. */
public record InscriptionRequest(String email, String motDePasse, String nom, String prenom, String nomOrganisation) {}
