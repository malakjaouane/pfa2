package ma.it6.portefeuille.dto;

/** Acceptation d'une invitation : creation du compte invite. */
public record AccepterInvitationRequest(String jeton, String nom, String prenom, String motDePasse) {}
