package ma.it6.portefeuille.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.PieceJointeDTO;
import ma.it6.portefeuille.entity.Commentaire;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.PieceJointe;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.CommentaireRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.PieceJointeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.OrganisationContext;
import ma.it6.portefeuille.storage.StockageFichier;

/**
 * Tests unitaires du service de pièces jointes (4A) : règles de taille, de
 * type MIME (scan des magic bytes), de suppression et de téléchargement.
 */
class PieceJointeServiceTest {

    private static final Long ID_ORG = 1L;
    private static final Long ID_INITIATIVE = 10L;
    private static final Long ID_COMMENTAIRE = 20L;
    private static final Long ID_PJ = 30L;
    private static final String EMAIL = "admin@it6.ma";
    private static final String EMAIL_AUTRE = "autre@it6.ma";
    private static final long TAILLE_MAX = 1024L;

    private PieceJointeRepository pieceJointeRepository;
    private InitiativeRepository initiativeRepository;
    private CommentaireRepository commentaireRepository;
    private UtilisateurRepository utilisateurRepository;
    private StockageFichier stockage;
    private PieceJointeService service;

    private Utilisateur admin;
    private Utilisateur autre;

    @BeforeEach
    void setup() {
        pieceJointeRepository = mock(PieceJointeRepository.class);
        initiativeRepository = mock(InitiativeRepository.class);
        commentaireRepository = mock(CommentaireRepository.class);
        utilisateurRepository = mock(UtilisateurRepository.class);
        stockage = mock(StockageFichier.class);

        admin = Utilisateur.builder()
                .idUser(1L).nom("Admin").prenom("IT6").email(EMAIL).build();
        autre = Utilisateur.builder()
                .idUser(2L).nom("Autre").prenom("Test").email(EMAIL_AUTRE).build();
        when(utilisateurRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findByEmail(EMAIL_AUTRE)).thenReturn(Optional.of(autre));
        when(pieceJointeRepository.save(any(PieceJointe.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockage.stocker(anyString(), any(), anyString())).thenReturn("cle-test");

        service = new PieceJointeService(pieceJointeRepository, initiativeRepository,
                commentaireRepository, utilisateurRepository, stockage, TAILLE_MAX, "");
        OrganisationContext.setId(ID_ORG);
    }

    @AfterEach
    void nettoyerContexte() {
        OrganisationContext.clear();
    }

    private MockMultipartFile fichier(String nom, String typeMime, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, typeMime, contenu);
    }

    private MockMultipartFile fichierTexte(String nom, String typeMime, String contenu) {
        return fichier(nom, typeMime, contenu.getBytes(StandardCharsets.UTF_8));
    }

    /** Signature PNG minimale (magic bytes). */
    private static byte[] png() {
        return new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    }

    private PieceJointe pieceJointe(Utilisateur uploader) {
        return PieceJointe.builder()
                .idPieceJointe(ID_PJ)
                .nomFichier("fiche.pdf")
                .typeMime("application/pdf")
                .tailleOctets(16L)
                .cleStockage("cle-1")
                .utilisateur(uploader)
                .dateCreation(LocalDateTime.now())
                .build();
    }

    // ---------- Upload ----------

    @Test
    @DisplayName("Upload sur une initiative : DTO renvoyé et binaire stocké")
    void uploadInitiative() {
        when(initiativeRepository.findByIdInitiative(ID_INITIATIVE))
                .thenReturn(Optional.of(Initiative.builder().idInitiative(ID_INITIATIVE).build()));

        PieceJointeDTO dto = service.uploader(
                fichierTexte("fiche.pdf", "application/pdf", "%PDF-1.7 test"),
                ID_INITIATIVE, null, EMAIL);

        assertThat(dto.getNomFichier()).isEqualTo("fiche.pdf");
        assertThat(dto.getIdInitiative()).isEqualTo(ID_INITIATIVE);
        assertThat(dto.getUtilisateurEmail()).isEqualTo(EMAIL);
        verify(stockage).stocker(anyString(), any(), anyString());
        verify(pieceJointeRepository).save(any(PieceJointe.class));
    }

    @Test
    @DisplayName("Upload sur un commentaire : la cible commentaire est acceptée")
    void uploadCommentaire() {
        when(initiativeRepository.findByIdInitiative(ID_INITIATIVE))
                .thenReturn(Optional.of(Initiative.builder().idInitiative(ID_INITIATIVE).build()));
        when(commentaireRepository.findById(ID_COMMENTAIRE))
                .thenReturn(Optional.of(Commentaire.builder().idCommentaire(ID_COMMENTAIRE).build()));

        PieceJointeDTO dto = service.uploader(
                fichier("photo.png", "image/png", png()),
                ID_INITIATIVE, ID_COMMENTAIRE, EMAIL);

        assertThat(dto.getIdCommentaire()).isEqualTo(ID_COMMENTAIRE);
    }

    @Test
    @DisplayName("Fichier vide : 400")
    void fichierVide() {
        MockMultipartFile vide = fichier("vide.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.uploader(vide, ID_INITIATIVE, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Fichier trop lourd : 400")
    void fichierTropLourd() {
        byte[] gros = new byte[(int) TAILLE_MAX + 1];

        assertThatThrownBy(() -> service.uploader(
                fichier("gros.pdf", "application/pdf", gros), ID_INITIATIVE, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Type MIME hors liste (text/html) : 400")
    void mimeNonAutorise() {
        assertThatThrownBy(() -> service.uploader(
                fichierTexte("page.html", "text/html", "<html></html>"),
                ID_INITIATIVE, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Contenu GIF déclaré PDF : 400 (scan MIME serveur)")
    void contenuNonConforme() {
        assertThatThrownBy(() -> service.uploader(
                fichierTexte("fiche.pdf", "application/pdf", "GIF89a faux pdf"),
                ID_INITIATIVE, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        verify(stockage, never()).stocker(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Contenu binaire inconnu déclaré PDF : 400")
    void contenuInconnu() {
        assertThatThrownBy(() -> service.uploader(
                fichier("fiche.pdf", "application/pdf", new byte[] { 0x00, 0x01, 0x02 }),
                ID_INITIATIVE, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Initiative inconnue : 404")
    void initiativeInconnue() {
        when(initiativeRepository.findByIdInitiative(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploader(
                fichierTexte("fiche.pdf", "application/pdf", "%PDF-1.7 test"),
                999L, null, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Commentaire inconnu : 404")
    void commentaireInconnu() {
        when(initiativeRepository.findByIdInitiative(ID_INITIATIVE))
                .thenReturn(Optional.of(Initiative.builder().idInitiative(ID_INITIATIVE).build()));
        when(commentaireRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploader(
                fichierTexte("fiche.pdf", "application/pdf", "%PDF-1.7 test"),
                ID_INITIATIVE, 999L, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ---------- Suppression ----------

    @Test
    @DisplayName("Suppression par l'uploader : binaire et métadonnées supprimés")
    void suppressionParUploader() {
        PieceJointe pj = pieceJointe(admin);
        when(pieceJointeRepository.findByIdPieceJointe(ID_PJ)).thenReturn(Optional.of(pj));

        service.supprimer(ID_PJ, EMAIL, false);

        verify(stockage).supprimer("cle-1");
        verify(pieceJointeRepository).delete(pj);
    }

    @Test
    @DisplayName("Suppression par un autre compte non admin : 403")
    void suppressionNonAutorisee() {
        PieceJointe pj = pieceJointe(admin);
        when(pieceJointeRepository.findByIdPieceJointe(ID_PJ)).thenReturn(Optional.of(pj));

        assertThatThrownBy(() -> service.supprimer(ID_PJ, EMAIL_AUTRE, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
        verify(stockage, never()).supprimer(anyString());
    }

    @Test
    @DisplayName("Suppression par un administrateur : autorisée")
    void suppressionParAdmin() {
        PieceJointe pj = pieceJointe(admin);
        when(pieceJointeRepository.findByIdPieceJointe(ID_PJ)).thenReturn(Optional.of(pj));

        service.supprimer(ID_PJ, EMAIL_AUTRE, true);

        verify(stockage).supprimer("cle-1");
    }

    // ---------- Lecture ----------

    @Test
    @DisplayName("Téléchargement : le contenu du stockage est renvoyé")
    void recupererContenu() {
        PieceJointe pj = pieceJointe(admin);
        when(pieceJointeRepository.findByIdPieceJointe(ID_PJ)).thenReturn(Optional.of(pj));
        when(stockage.recuperer("cle-1")).thenReturn(new byte[] { 1, 2, 3 });

        assertThat(service.recuperer(ID_PJ)).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    @DisplayName("Pièce jointe inconnue : 404")
    void pieceJointeInconnue() {
        when(pieceJointeRepository.findByIdPieceJointe(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDTO(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }
}
