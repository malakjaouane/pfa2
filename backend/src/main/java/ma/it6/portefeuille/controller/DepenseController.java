package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.DepenseRequest;
import ma.it6.portefeuille.dto.DepenseResponse;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import ma.it6.portefeuille.service.DepenseService;
import ma.it6.portefeuille.util.CsvUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * API REST des dépenses d'initiative — suivi budgétaire opérationnel.
 *
 * <p><strong>Lecture ET écriture soumises à {@code DEPENSE_SAISIR}</strong> :
 * une dépense est une information financière, y'en sa simple consultation.
 * Le contrôle est posé dans {@code SecurityConfig} pour tous les verbes.</p>
 *
 * <p>Garde-fous : initiative archivée → 400 sur écriture ; montant strictement
 * positif, libellé et date obligatoires (validation du contrôleur + service).</p>
 */
@RestController
@RequestMapping("/api")
public class DepenseController {

    private final DepenseService depenseService;
    private final CustomUserDetailsService userDetailsService;

    public DepenseController(DepenseService depenseService,
                             CustomUserDetailsService userDetailsService) {
        this.depenseService = depenseService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Registre global, filtré (initiative, catégorie, période, recherche) et
     * paginé. Les totaux (compte + montant) voyagent dans les en-têtes.
     */
    @GetMapping("/depenses")
    public ResponseEntity<List<DepenseResponse>> lister(
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? DepenseService.TAILLE_PAR_DEFAUT
                : Math.min(size, DepenseService.TAILLE_MAXIMALE);

        DepenseService.PageDepenses resultat =
                depenseService.lister(initiative, categorie, du, au, recherche, numeroPage, taille);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultat.total()))
                .header("X-Total-Montant", montant(resultat.montantTotal()))
                .body(resultat.lignes());
    }

    /** Registre d'une initiative (plus récentes d'abord), hors pagination. */
    @GetMapping("/initiatives/{idInitiative}/depenses")
    public ResponseEntity<List<DepenseResponse>> listerParInitiative(
            @PathVariable Long idInitiative) {
        return ResponseEntity.ok(depenseService.listerParInitiative(idInitiative));
    }

    /** Export CSV de la sélection filtrée — toutes les lignes, pas la page. */
    @GetMapping("/depenses/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) Long initiative,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche) {

        List<DepenseResponse> lignes = depenseService
                .lister(initiative, categorie, du, au, recherche, 0, DepenseService.TAILLE_MAXIMALE)
                .lignes();

        List<List<String>> contenu = lignes.stream()
                .map(l -> List.of(
                        l.dateDepense() == null ? "" : l.dateDepense().toString(),
                        l.codeInitiative() == null ? "" : l.codeInitiative(),
                        l.nomInitiative() == null ? "" : l.nomInitiative(),
                        l.libelle(),
                        montant(l.montant()),
                        l.categorie() == null ? "" : l.categorie(),
                        l.saisisseur() == null ? "" : l.saisisseur().nomComplet()))
                .toList();
        byte[] csv = CsvUtil.construire(
                List.of("Date", "Initiative", "Nom initiative", "Libellé", "Montant",
                        "Catégorie", "Saisie par"),
                contenu);

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        entetes.setContentDisposition(ContentDisposition.attachment()
                .filename("depenses.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(entetes).body(csv);
    }

    @PostMapping("/initiatives/{idInitiative}/depenses")
    public ResponseEntity<DepenseResponse> creer(@PathVariable Long idInitiative,
                                                 @Valid @RequestBody DepenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depenseService.creer(idInitiative, request, idSaisisseurCourant()));
    }

    @PutMapping("/depenses/{idDepense}")
    public ResponseEntity<DepenseResponse> modifier(@PathVariable Long idDepense,
                                                    @Valid @RequestBody DepenseRequest request) {
        return ResponseEntity.ok(depenseService.modifier(idDepense, request));
    }

    @DeleteMapping("/depenses/{idDepense}")
    public ResponseEntity<Void> supprimer(@PathVariable Long idDepense) {
        depenseService.supprimer(idDepense);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    private static String montant(BigDecimal valeur) {
        return valeur == null ? "0.00" : valeur.toPlainString();
    }

    /**
     * Identifiant du compte connecté — la route est déjà
     * {@code authenticated()}. Le filtre JWT pose l'e-mail (String) comme
     * principal : on recharge le compte, comme MonEspaceService.
     */
    private Long idSaisisseurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof PrincipalUser principal) {
            return principal.getUtilisateur().getIdUser();
        }
        if (authentication.getPrincipal() instanceof String courant
                && !"anonymousUser".equals(courant)) {
            // Rechargement par e-mail (le JWT ne porte que l'e-mail). Si le
            // compte a disparu entre-temps : saisisseur anonyme, comme au fil.
            PrincipalUser recharge = userDetailsService.chargerParEmail(courant);
            return recharge == null ? null : recharge.getUtilisateur().getIdUser();
        }
        return null;
    }
}
