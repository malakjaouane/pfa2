package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.JournalAuditResponse;
import ma.it6.portefeuille.service.JournalAuditService;
import ma.it6.portefeuille.util.CsvUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Journal d'audit des actions HTTP — <strong>lecture seule</strong>.
 *
 * <p>Accès réservé aux administrateurs ({@code UTILISATEUR_GERER}). Le
 * journal est immuable : aucun {@code POST} / {@code PUT} / {@code PATCH} /
 * {@code DELETE} n'est déclaré — l'envoi de ces verbes renvoie 405. Les
 * lignes sont produites par le filtre d'audit ; ce contrôleur ne fait que
 * les présenter.</p>
 */
@RestController
@RequestMapping("/api/journal-audit")
public class JournalAuditController {

    private static final DateTimeFormatter FORMATEUR_CSV =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);

    private final JournalAuditService journalService;

    public JournalAuditController(JournalAuditService journalService) {
        this.journalService = journalService;
    }

    /** Une page du journal, filtrée (e-mail, méthode, route, période). */
    @GetMapping
    public ResponseEntity<List<JournalAuditResponse>> lister(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String methode,
            @RequestParam(required = false) String route,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? JournalAuditService.TAILLE_PAR_DEFAUT
                : Math.min(size, JournalAuditService.TAILLE_MAXIMALE);

        JournalAuditService.PageJournal resultat =
                journalService.lister(email, methode, route, du, au, numeroPage, taille);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultat.total()))
                .body(resultat.lignes());
    }

    /** Export CSV complet des lignes filtrées (toutes les pages). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String methode,
            @RequestParam(required = false) String route,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {

        List<JournalAuditResponse> lignes = journalService.exporter(email, methode, route, du, au);
        List<List<String>> contenu = lignes.stream()
                .map(l -> List.of(
                        l.dateAction() == null ? "" : FORMATEUR_CSV.format(l.dateAction()),
                        l.email() == null ? "" : l.email(),
                        l.methode(),
                        l.route(),
                        l.statutHttp() == null ? "" : String.valueOf(l.statutHttp()),
                        l.adresseIp() == null ? "" : l.adresseIp(),
                        l.corps() == null ? "" : l.corps()))
                .toList();
        byte[] csv = CsvUtil.construire(
                List.of("Date", "E-mail", "Méthode", "Route", "Statut HTTP", "Adresse IP", "Corps"),
                contenu);

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        entetes.setContentDisposition(ContentDisposition.attachment()
                .filename("journal-audit.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(entetes).body(csv);
    }
}
