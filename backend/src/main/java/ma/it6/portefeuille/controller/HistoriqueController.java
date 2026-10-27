package ma.it6.portefeuille.controller;

import ma.it6.portefeuille.dto.HistoriqueModificationResponse;
import ma.it6.portefeuille.dto.UserSummaryDto;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.service.HistoriqueModificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Journal global des modifications — lecture seule.
 *
 * <p>Lecture ouverte à {@code INITIATIVE_CONSULTER} ; aucune création,
 * modification ni suppression n'est possible : aucun POST/PUT/PATCH/DELETE
 * n'est déclaré (un envoi de ces verbes renvoie 405). Le journal est
 * immuable par conception — la preuve d'audit ne se retouche pas.</p>
 *
 * <p>La permission de lecture est {@code INITIATIVE_CONSULTER} — la même que
 * la consultation des initiatives : aucun nouveau droit n'est créé.</p>
 *
 * <p>L'export CSV ({@code GET /export}) porte sur <strong>toutes</strong>
 * les lignes des filtres courants, pas sur l'affichage de la page en cours.</p>
 */
@RestController
@RequestMapping("/api/historique")
public class HistoriqueController {

    /** Séparateur de colonnes CSV — point-virgule : l'Excel FR l'ouvre tel quel. */
    private static final String SEPARATEUR = ";";
    private static final DateTimeFormatter FORMATEUR_CSV =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);

    private final HistoriqueModificationService historiqueService;

    public HistoriqueController(HistoriqueModificationService historiqueService) {
        this.historiqueService = historiqueService;
    }

    /**
     * Une page du journal global, filtrée (recherche, type d'objet, champ,
     * auteur, période, initiative, vue Actives|Archives) et triée.
     *
     * @param direction {@code asc} (plus ancien d'abord) ou {@code desc}
     *                  (plus récent d'abord, par défaut)
     * @param page      numéro de page (0-indexé), 0 par défaut
     * @param size      taille de page, plafonnée à
     *                  {@value HistoriqueModificationService#TAILLE_MAXIMALE}
     * @return la page + le compte total hors pagination dans {@code X-Total-Count}
     */
    @GetMapping
    public ResponseEntity<List<HistoriqueModificationResponse>> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String champ,
            @RequestParam(required = false) Long utilisateur,
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Boolean archives,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String direction) {

        validerPeriode(du, au);
        validerType(type);
        boolean triAsc = "asc".equalsIgnoreCase(direction == null ? "" : direction.trim());
        validerDirection(direction);

        HistoriqueModificationService.FiltreHistorique filtre = new HistoriqueModificationService.FiltreHistorique(
                recherche, normaliser(type), normaliser(champ), utilisateur, initiative, du, au,
                Boolean.TRUE.equals(archives));
        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? HistoriqueModificationService.TAILLE_PAR_DEFAUT
                : Math.min(size, HistoriqueModificationService.TAILLE_MAXIMALE);

        HistoriqueModificationService.PageJournal resultat =
                historiqueService.lister(filtre, numeroPage, taille, triAsc);

        // Compte total HORS pagination : c'est ce dont la pagination du
        // tableau a besoin pour savoir s'il reste des pages (même convention
        // que la liste des initiatives).
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultat.total()))
                .body(resultat.lignes());
    }

    /**
     * Auteurs réellement présents dans le journal (liste déroulante de filtre).
     * La liste est limitée aux personnes ayant déjà agi : aucun droit n'est
     * ouvert au-delà de la simple lecture du journal.
     */
    @GetMapping("/auteurs")
    public ResponseEntity<List<UserSummaryDto>> auteurs() {
        return ResponseEntity.ok(historiqueService.auteurs());
    }

    /**
     * Export CSV complet des lignes filtrées — toutes les pages, pas
     * seulement l'affichage courant. Mêmes paramètres de filtre que la
     * lecture (sans pagination ni tri).
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String champ,
            @RequestParam(required = false) Long utilisateur,
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Boolean archives) {

        validerPeriode(du, au);
        validerType(type);

        HistoriqueModificationService.FiltreHistorique filtre = new HistoriqueModificationService.FiltreHistorique(
                recherche, normaliser(type), normaliser(champ), utilisateur, initiative, du, au,
                Boolean.TRUE.equals(archives));

        byte[] csv = construireCsv(historiqueService.exporter(filtre));

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        entetes.setContentDisposition(ContentDisposition.attachment()
                .filename("historique-modifications.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(entetes).body(csv);
    }

    // ------------------------------------------------------------------
    // Validation et internes
    // ------------------------------------------------------------------

    private static void validerPeriode(LocalDate du, LocalDate au) {
        if (du != null && au != null && du.isAfter(au)) {
            throw new BadRequestException("Période invalide : la date « du » est postérieure à la date « au ».");
        }
    }

    /** Type d'objet : le filtre pilote le champ {@code type_objet} en base. */
    private static void validerType(String type) {
        String valeur = normaliser(type);
        if (valeur != null && !HistoriqueModificationService.TYPES.contains(valeur)) {
            throw new BadRequestException("Type d'objet inconnu : « " + valeur
                    + " » (valeurs admises : "
                    + String.join(", ", HistoriqueModificationService.TYPES) + ").");
        }
    }

    private static void validerDirection(String direction) {
        String normalisee = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
        if (!normalisee.isEmpty()
                && !normalisee.equals("asc") && !normalisee.equals("desc")) {
            throw new BadRequestException("Direction de tri inconnue : « " + direction.trim()
                    + " » (valeurs admises : asc, desc).");
        }
    }

    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /**
     * Construction du fichier CSV : en-tête BOM (Excel ouvre le UTF-8 sans
     * initiative), lignes séparées par « ; », guillemets doublés quand la
     * valeur contient le séparateur, un guillemet ou un retour à la ligne.
     */
    private static byte[] construireCsv(List<HistoriqueModificationResponse> lignes) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(SEPARATEUR,
                        "Date", "Objet", "Id objet", "Champ", "Ancienne valeur",
                        "Nouvelle valeur", "Commentaire", "Origine", "Auteur", "Initiative"))
                .append('\n');
        for (HistoriqueModificationResponse ligne : lignes) {
            csv.append(cellule(ligne.dateChangement() == null ? "" : FORMATEUR_CSV.format(ligne.dateChangement())))
                    .append(SEPARATEUR).append(cellule(ligne.typeObjet()))
                    .append(SEPARATEUR).append(cellule(ligne.idObjet() == null ? "" : String.valueOf(ligne.idObjet())))
                    .append(SEPARATEUR).append(cellule(ligne.champ()))
                    .append(SEPARATEUR).append(cellule(ligne.ancienneValeur()))
                    .append(SEPARATEUR).append(cellule(ligne.nouvelleValeur()))
                    .append(SEPARATEUR).append(cellule(ligne.commentaire()))
                    .append(SEPARATEUR).append(cellule(ligne.automatique() ? "automatique" : "manuelle"))
                    .append(SEPARATEUR).append(cellule(ligne.utilisateur() == null ? "" : ligne.utilisateur().nomComplet()))
                    .append(SEPARATEUR).append(cellule(ligne.initiative() == null ? "" : ligne.initiative().code()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Champ CSV : vide si absent, guillemets doublés si nécessaire. */
    private static String cellule(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return "";
        }
        boolean echappement = valeur.contains(SEPARATEUR)
                || valeur.contains("\"")
                || valeur.contains("\n")
                || valeur.contains("\r");
        if (!echappement) {
            return valeur;
        }
        return "\"" + valeur.replace("\"", "\"\"") + "\"";
    }
}
