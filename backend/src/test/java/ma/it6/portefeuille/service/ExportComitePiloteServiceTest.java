package ma.it6.portefeuille.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.entity.Depense;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import ma.it6.portefeuille.security.OrganisationContext;

/**
 * Tests de l'export « comité de pilotage » (4F).
 *
 * <p>Les repositories sont mockes ; {@link OrganisationContext} simule le
 * contexte de requete. Le test verifie les regles du brief :</p>
 * <ul>
 *   <li>depenses <b>reelles</b> du registre — jamais le prorata
 *       d'avancement, signale comme <b>estimation</b> ;</li>
 *   <li>formats PDF (magic %PDF) et PPTX (zip OOXML) ;</li>
 *   <li>isolation par organisation (depenses et risques des autres
 *       organisations exclus) ;</li>
 *   <li>format inconnu -> 400, contexte d'organisation absent -> 500.</li>
 * </ul>
 */
class ExportComitePiloteServiceTest {

    private InitiativeRepository initiativeRepository;
    private DepenseRepository depenseRepository;
    private RisqueRepository risqueRepository;
    private ExportComitePiloteService service;

    @BeforeEach
    void setup() {
        OrganisationContext.setId(1L);

        initiativeRepository = mock(InitiativeRepository.class);
        depenseRepository = mock(DepenseRepository.class);
        risqueRepository = mock(RisqueRepository.class);
        service = new ExportComitePiloteService(
                initiativeRepository, depenseRepository, risqueRepository);

        // Organisation 1 : 2 initiatives (dont 1 en retard), budget 6000,
        // 1 depense reelle de 150, 1 risque critique (5 x 4 = 20).
        when(initiativeRepository.findByIdOrganisation(1L)).thenReturn(List.of(
                initiative("PRJ-001", "Initiative en retard", "En cours",
                        LocalDate.now().minusDays(10), 40, "5000"),
                initiative("PRJ-002", "Initiative a l heure", "Terminee",
                        LocalDate.now().plusDays(30), 100, "1000")));

        // findAll retourne TOUT : c'est le service qui filtre par
        // organisation — les lignes « org 2 » ne doivent jamais apparaitre.
        when(depenseRepository.findAll()).thenReturn(List.of(
                depense(1L, "150"),
                depense(2L, "99999")));
        when(risqueRepository.findAll()).thenReturn(List.of(
                risque(1L, "Risque critique", 5, 4),
                risque(2L, "Risque isole org2", 5, 5)));
    }

    @AfterEach
    void tearDown() {
        OrganisationContext.clear();
    }

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------

    @Test
    void exporter_pdf_contient_portefeuille_retards_et_risques() throws Exception {
        byte[] octets = service.exporter("pdf");

        assertThat(octets).isNotEmpty();
        assertThat(new String(octets, 0, 4, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");

        String texte = textePdf(octets);
        assertThat(texte).contains("Comite de pilotage");
        assertThat(texte).contains("En cours : 1");
        assertThat(texte).contains("Terminee : 1");
        // Retards : PRJ-001 (fin prevue passee, avancement < 100) figure,
        // PRJ-002 (a l heure) non.
        assertThat(texte).contains("PRJ-001");
        assertThat(texte).doesNotContain("PRJ-002");
        // Risque critique 5 x 4 = 20/25.
        assertThat(texte).contains("Risque critique - score 20/25");
    }

    @Test
    void exporter_pdf_affiche_les_depenses_reelles_et_le_prorata_comme_estimation()
            throws Exception {
        String texte = textePdf(service.exporter("pdf"));

        // Budget total 5000 + 1000 ; depense reelle du registre = 150
        // (jamais le prorata 50 % de 5000 = 2500).
        assertThat(texte).contains("Budget total : 6000.00 MAD");
        assertThat(texte).contains("Depenses reelles : 150.00 MAD");
        assertThat(texte).contains("Ecart : 5850.00 MAD");
        assertThat(texte).doesNotContain("2500.00");
        // Le prorata reste annonce comme estimation.
        assertThat(texte).contains("une estimation");
    }

    @Test
    void exporter_pdf_isole_les_donnees_des_autres_organisations() throws Exception {
        String texte = textePdf(service.exporter("pdf"));

        // Depenses : seul le registre org 1 entre dans le total (150,
        // et non 150 + 99999 de l'organisation 2).
        assertThat(texte).contains("Depenses reelles : 150.00 MAD");
        // Risques : le risque critique de l'org 2 est exclu.
        assertThat(texte).doesNotContain("Risque isole org2");
        assertThat(texte).contains("Risque critique");
    }

    // ------------------------------------------------------------------
    // PowerPoint
    // ------------------------------------------------------------------

    @Test
    void exporter_pptx_est_un_zip_ooxml_avec_le_meme_contenu() throws Exception {
        byte[] octets = service.exporter("pptx");

        // En-tete ZIP OOXML : « PK ».
        assertThat(octets[0]).isEqualTo((byte) 0x50);
        assertThat(octets[1]).isEqualTo((byte) 0x4B);

        String texte = textePptx(octets);
        assertThat(texte).contains("Comite de pilotage");
        assertThat(texte).contains("Depenses reelles : 150.00 MAD");
        assertThat(texte).contains("une estimation");
        assertThat(texte).contains("PRJ-001");
        assertThat(texte).doesNotContain("Risque isole org2");
    }

    // ------------------------------------------------------------------
    // Erreurs
    // ------------------------------------------------------------------

    @Test
    void exporter_format_invalide_rejette_400() {
        assertThatThrownBy(() -> service.exporter("docx"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("pdf, pptx");
    }

    @Test
    void exporter_sans_organisation_rejette_500() {
        OrganisationContext.clear();

        assertThatThrownBy(() -> service.exporter("pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "statusCode", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Initiative initiative(String code, String nom, String libelleStatut,
                                  LocalDate fin, int avancement, String budget) {
        StatutInitiative statut = new StatutInitiative();
        statut.setLibelle(libelleStatut);
        Initiative i = new Initiative();
        i.setCode(code);
        i.setNom(nom);
        i.setStatut(statut);
        i.setDateFinPrevue(fin);
        i.setAvancement(avancement);
        i.setBudget(new BigDecimal(budget));
        i.setIdOrganisation(1L);
        return i;
    }

    private Depense depense(long idOrganisation, String montant) {
        Depense d = new Depense();
        d.setIdOrganisation(idOrganisation);
        d.setMontant(new BigDecimal(montant));
        return d;
    }

    private Risque risque(long idOrganisation, String titre,
                          int probabilite, int impact) {
        Risque r = new Risque();
        r.setIdOrganisation(idOrganisation);
        r.setTitre(titre);
        r.setProbabilite(probabilite);
        r.setImpact(impact);
        return r;
    }

    /** Texte extrait du PDF genere (PDFBox, robuste a la compression). */
    private String textePdf(byte[] octets) throws Exception {
        try (PDDocument doc = PDDocument.load(octets)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /** Texte extrait du PPTX genere (formes de texte des diapositives). */
    private String textePptx(byte[] octets) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(octets))) {
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape texte) {
                        sb.append(texte.getText()).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }
}
