package ma.it6.portefeuille.service;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.entity.Depense;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import ma.it6.portefeuille.security.OrganisationContext;

import static org.springframework.http.HttpStatus.*;

/**
 * Export "comite de pilotage" (4F).
 *
 * Genere un document (PDF ou PowerPoint) regroupant :
 * <ul>
 *   <li>Portefeuille : nombre d'initiatives par statut, par entite, par
 *       responsable.</li>
 *   <li>Retards : initiatives en retard avec date de fin prevue.</li>
 *   <li>Risques critiques : score >= 15 (bande rouge).</li>
 *   <li>Budget : total budgete vs total depense REEL (registre des
 *       depenses), avec ecart. Le "consomme" calcule au prorata de
 *       l'avancement est clairement marque comme estimation.</li>
 * </ul>
 *
 * Toutes les donnees sont filtrees par organisation courante (contexte
 * {@link OrganisationContext}).
 */
@Service
public class ExportComitePiloteService {

    private final InitiativeRepository initiativeRepository;
    private final DepenseRepository depenseRepository;
    private final RisqueRepository risqueRepository;

    public ExportComitePiloteService(InitiativeRepository initiativeRepository,
                                     DepenseRepository depenseRepository,
                                     RisqueRepository risqueRepository) {
        this.initiativeRepository = initiativeRepository;
        this.depenseRepository = depenseRepository;
        this.risqueRepository = risqueRepository;
    }

    /**
     * Exporte le comite de pilotage au format demande (PDF ou PPTX).
     *
     * @param format "pdf" ou "pptx"
     * @return octets du document genere
     */
    public byte[] exporter(String format) {
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Aucune organisation dans le contexte de requete.");
        }

        List<Initiative> initiatives = initiativeRepository.findByIdOrganisation(idOrganisation);
        List<Depense> depenses = depenseRepository.findAll().stream()
                .filter(d -> idOrganisation.equals(d.getIdOrganisation()))
                .collect(Collectors.toList());
        List<Risque> risques = risqueRepository.findAll().stream()
                .filter(r -> idOrganisation.equals(r.getIdOrganisation()))
                .collect(Collectors.toList());

        try {
            if ("pdf".equalsIgnoreCase(format)) {
                return exporterPdf(initiatives, depenses, risques);
            } else if ("pptx".equalsIgnoreCase(format)) {
                return exporterPptx(initiatives, depenses, risques);
            } else {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Format non supporte : " + format + " (valeurs attendues : pdf, pptx).");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur lors de la generation du document : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // PDF (Apache PDFBox)
    // ------------------------------------------------------------------

    private byte[] exporterPdf(List<Initiative> initiatives,
                               List<Depense> depenses,
                               List<Risque> risques) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 16);
            stream.beginText();
            stream.newLineAtOffset(50, 750);
            stream.showText("Comite de pilotage - Rapport");
            stream.endText();

            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(0, -30);
            stream.showText("Date : " + LocalDate.now());
            stream.newLineAtOffset(0, -20);
            stream.showText("Organisation : " + OrganisationContext.getId());
            stream.endText();

            // Initiatives par statut
            stream.newLineAtOffset(0, -40);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
            stream.showText("Initiatives par statut :");
            stream.endText();
            Map<String, Long> parStatut = initiatives.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getStatut() != null ? i.getStatut().getLibelle() : "Non defini",
                            Collectors.counting()));
            stream.setFont(PDType1Font.HELVETICA, 12);
            for (Map.Entry<String, Long> e : parStatut.entrySet()) {
                stream.newLineAtOffset(0, -20);
                stream.showText("  " + e.getKey() + " : " + e.getValue());
            }
            stream.endText();

            // Retards
            stream.newLineAtOffset(0, -40);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
            stream.showText("Initiatives en retard :");
            stream.endText();
            LocalDate aujourdHui = LocalDate.now();
            List<Initiative> enRetard = initiatives.stream()
                    .filter(i -> i.getDateFinPrevue() != null
                            && i.getDateFinPrevue().isBefore(aujourdHui)
                            && (i.getAvancement() == null || i.getAvancement() < 100))
                    .sorted(Comparator.comparing(i -> i.getDateFinPrevue() != null ? i.getDateFinPrevue() : LocalDate.MIN))
                    .collect(Collectors.toList());
            stream.setFont(PDType1Font.HELVETICA, 12);
            for (Initiative i : enRetard) {
                stream.newLineAtOffset(0, -20);
                stream.showText("  " + i.getCode() + " - " + i.getNom()
                        + " (fin prevue : " + i.getDateFinPrevue() + ")");
            }
            stream.endText();

            // Risques critiques
            stream.newLineAtOffset(0, -40);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
            stream.showText("Risques critiques (score >= 15) :");
            stream.endText();
            List<Risque> critiques = risques.stream()
                    .filter(r -> r.getProbabilite() != null && r.getImpact() != null
                            && r.getProbabilite() * r.getImpact() >= 15)
                    .collect(Collectors.toList());
            stream.setFont(PDType1Font.HELVETICA, 12);
            for (Risque r : critiques) {
                stream.newLineAtOffset(0, -20);
                stream.showText("  " + r.getTitre() + " - score "
                        + (r.getProbabilite() * r.getImpact()) + "/25");
            }
            stream.endText();

            // Budget
            stream.newLineAtOffset(0, -40);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
            stream.showText("Budget :");
            stream.endText();
            Double budgetTotal = initiatives.stream()
                    .map(i -> i.getBudget() != null ? i.getBudget().doubleValue() : 0.0)
                    .reduce(0.0, Double::sum);
            Double depensesTotales = depenses.stream()
                    .map(d -> d.getMontant() != null ? d.getMontant().doubleValue() : 0.0)
                    .reduce(0.0, Double::sum);
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(0, -20);
            stream.showText("  Budget total : " + String.format("%.2f", budgetTotal) + " MAD");
            stream.newLineAtOffset(0, -20);
            stream.showText("  Depenses reelles : " + String.format("%.2f", depensesTotales) + " MAD");
            stream.newLineAtOffset(0, -20);
            stream.showText("  Ecart : " + String.format("%.2f", budgetTotal - depensesTotales) + " MAD");
            stream.newLineAtOffset(0, -20);
            stream.showText("  (Le montant \"consomme\" calcule au prorata de l'avancement est");
            stream.newLineAtOffset(0, -15);
            stream.showText("   une estimation. Les depenses reelles proviennent du registre.)");
            stream.endText();

            stream.close();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // PowerPoint (Apache POI)
    // ------------------------------------------------------------------

    private byte[] exporterPptx(List<Initiative> initiatives,
                                List<Depense> depenses,
                                List<Risque> risques) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextShape title = slide.createTextBox();
            title.setAnchor(new Rectangle(50, 30, 600, 40));
            title.setText("Comite de pilotage - Rapport");

            Map<String, Long> parStatut = initiatives.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getStatut() != null ? i.getStatut().getLibelle() : "Non defini",
                            Collectors.counting()));
            StringBuilder sb = new StringBuilder("Initiatives par statut :\n");
            parStatut.forEach((k, v) -> sb.append("  ").append(k).append(" : ").append(v).append("\n"));

            LocalDate aujourdHui = LocalDate.now();
            List<Initiative> enRetard = initiatives.stream()
                    .filter(i -> i.getDateFinPrevue() != null
                            && i.getDateFinPrevue().isBefore(aujourdHui)
                            && (i.getAvancement() == null || i.getAvancement() < 100))
                    .collect(Collectors.toList());
            sb.append("\nInitiatives en retard :\n");
            enRetard.forEach(i -> sb.append("  ").append(i.getCode()).append(" - ")
                    .append(i.getNom()).append("\n"));

            List<Risque> critiques = risques.stream()
                    .filter(r -> r.getProbabilite() != null && r.getImpact() != null
                            && r.getProbabilite() * r.getImpact() >= 15)
                    .collect(Collectors.toList());
            sb.append("\nRisques critiques (score >= 15) :\n");
            critiques.forEach(r -> sb.append("  ").append(r.getTitre()).append(" - score ")
                    .append(r.getProbabilite() * r.getImpact()).append("/25\n"));

            Double budgetTotal = initiatives.stream()
                    .map(i -> i.getBudget() != null ? i.getBudget().doubleValue() : 0.0)
                    .reduce(0.0, Double::sum);
            Double depensesTotales = depenses.stream()
                    .map(d -> d.getMontant() != null ? d.getMontant().doubleValue() : 0.0)
                    .reduce(0.0, Double::sum);
            sb.append("\nBudget :\n");
            sb.append("  Budget total : ").append(String.format("%.2f", budgetTotal)).append(" MAD\n");
            sb.append("  Depenses reelles : ").append(String.format("%.2f", depensesTotales)).append(" MAD\n");
            sb.append("  Ecart : ").append(String.format("%.2f", budgetTotal - depensesTotales)).append(" MAD\n");
            sb.append("  (Le montant \"consomme\" calcule au prorata de l'avancement est\n");
            sb.append("   une estimation. Les depenses reelles proviennent du registre.)");

            XSLFTextShape body = slide.createTextBox();
            body.setAnchor(new Rectangle(50, 80, 700, 400));
            body.setText(sb.toString());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ppt.write(baos);
            return baos.toByteArray();
        }
    }
}
