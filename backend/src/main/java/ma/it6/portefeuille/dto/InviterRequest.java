package ma.it6.portefeuille.dto;

/** Invitation d'un collegue par un administrateur d'organisation. */
public record InviterRequest(String email, Long idRole) {}
