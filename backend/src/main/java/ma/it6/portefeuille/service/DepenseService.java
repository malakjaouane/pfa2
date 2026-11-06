package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.DepenseRequest;
import ma.it6.portefeuille.dto.DepenseResponse;
import ma.it6.portefeuille.entity.Depense;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dépenses déclaratives rattachées à une initiative — le volet opérationnel
 * du suivi budgétaire (contrôle de dépassement sur le tableau de bord).
 *
 * <p>Le portefeuille n'est pas un outil comptable : ces lignes servent à
 * suivre ce qui est dépensé, pas à produire des écritures.</p>
 *
 * <p>Garde-fous :</p>
 * <ul>
 *   <li>initiative inexistante → 404 ;</li>
 *   <li>initiative archivée → 400 sur toute écriture ;</li>
 *   <li>montant non strictement positif, date ou libellé manquant → 400
 *       (validation du contrôleur + contrôle ici) ;</li>
 *   <li>dépense inexistante → 404.</li>
 * </ul>
 *
 * <p><strong>Lecture soumise à {@code DEPENSE_SAISIR}</strong> : une
 * dépense est une information financière, même pour le registre global.
 * Le contrôle est posé dans {@code SecurityConfig} (tous verbes).</p>
 */
@Service
public class DepenseService {

    /** Taille de page par défaut du registre. */
    public static final int TAILLE_PAR_DEFAUT = 25;
    /** Plafond de taille de page. */
    public static final int TAILLE_MAXIMALE = 200;

    private final DepenseRepository depenseRepository;
    private final InitiativeRepository initiativeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public DepenseService(DepenseRepository depenseRepository,
                          InitiativeRepository initiativeRepository,
                          UtilisateurRepository utilisateurRepository) {
        this.depenseRepository = depenseRepository;
        this.initiativeRepository = initiativeRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Registre global, filtré (initiative, catégorie, période, recherche)
     * et paginé. Le total des montants de la <strong>même sélection</strong>
     * est renvoyé à côté des lignes (contrôle budgétaire instantané).
     *
     * @return la page courante + le total hors pagination + le montant total
     */
    @Transactional(readOnly = true)
    public PageDepenses lister(Long idInitiative, String categorie, LocalDate du, LocalDate au,
                               String recherche, int numeroPage, int taille) {
        int tailleEffective = Math.min(Math.max(taille, 1), TAILLE_MAXIMALE);
        Page<Depense> page = depenseRepository.rechercher(
                idInitiative, normaliser(categorie), du, au, normaliser(recherche),
                PageRequest.of(Math.max(0, numeroPage), tailleEffective,
                        Sort.by(Sort.Direction.DESC, "dateDepense")
                                .and(Sort.by(Sort.Direction.DESC, "idDepense"))));
        return new PageDepenses(
                page.map(DepenseService::toResponse).getContent(),
                page.getTotalElements(),
                totalFiltre(idInitiative, categorie, du, au, recherche));
    }

    /** Une page : lignes, compte total, montant total de la sélection. */
    public record PageDepenses(List<DepenseResponse> lignes, long total, BigDecimal montantTotal) {
    }

    /** Registre d'une initiative (plus récentes d'abord), hors pagination. */
    @Transactional(readOnly = true)
    public List<DepenseResponse> listerParInitiative(Long idInitiative) {
        Initiative initiative = verifierInitiative(idInitiative);
        return depenseRepository
                .findTop50ByInitiativeIdInitiativeOrderByDateDepenseDesc(initiative.getIdInitiative())
                .stream()
                .map(DepenseService::toResponse)
                .toList();
    }

    /** Montant total des dépenses d'une initiative (fil d'information). */
    @Transactional(readOnly = true)
    public BigDecimal totalInitiative(Long idInitiative) {
        Initiative initiative = verifierInitiative(idInitiative);
        return depenseRepository.totalParInitiative(initiative.getIdInitiative());
    }

    @Transactional
    public DepenseResponse creer(Long idInitiative, DepenseRequest request, Long idSaisisseur) {
        Initiative initiative = verifierInitiative(idInitiative);
        verifierActive(initiative, "ajout de dépense refusé.");
        valider(request);

        Depense depense = Depense.builder()
                .initiative(initiative)
                .libelle(request.libelle().trim())
                .montant(request.montant())
                .dateDepense(request.dateDepense())
                .categorie(normaliser(request.categorie()))
                .saisisseur(utilisateur(idSaisisseur))
                .build();
        return toResponse(depenseRepository.save(depense));
    }

    @Transactional
    public DepenseResponse modifier(Long idDepense, DepenseRequest request) {
        Depense depense = getDepense(idDepense);
        verifierActive(depense.getInitiative(), "modification de dépense refusée.");
        valider(request);

        depense.setLibelle(request.libelle().trim());
        depense.setMontant(request.montant());
        depense.setDateDepense(request.dateDepense());
        depense.setCategorie(normaliser(request.categorie()));
        return toResponse(depenseRepository.save(depense));
    }

    @Transactional
    public void supprimer(Long idDepense) {
        Depense depense = getDepense(idDepense);
        verifierActive(depense.getInitiative(), "suppression de dépense refusée.");
        depenseRepository.delete(depense);
    }

    // ------------------------------------------------------------------
    // Garde-fous et internes
    // ------------------------------------------------------------------

    private Depense getDepense(Long idDepense) {
        return depenseRepository.findById(idDepense)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense introuvable : " + idDepense));
    }

    private Initiative verifierInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Initiative introuvable : " + idInitiative));
    }

    private void verifierActive(Initiative initiative, String motifRefus) {
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : " + motifRefus);
        }
    }

    /** Contrôle métier redondant avec la validation : montant et date. */
    private static void valider(DepenseRequest request) {
        if (request.montant() == null || request.montant().signum() <= 0) {
            throw new BadRequestException("Le montant de la dépense doit être strictement positif.");
        }
        if (request.dateDepense() == null) {
            throw new BadRequestException("La date de la dépense est obligatoire.");
        }
    }

    /** Montant total des lignes retenues par les mêmes filtres que la page. */
    private BigDecimal totalFiltre(Long idInitiative, String categorie, LocalDate du, LocalDate au,
                                   String recherche) {
        BigDecimal total = depenseRepository.totalFiltre(idInitiative, normaliser(categorie), du, au,
                normaliser(recherche));
        return total == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                : total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Utilisateur utilisateur(Long idUtilisateur) {
        if (idUtilisateur == null) {
            return null;
        }
        return utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + idUtilisateur));
    }

    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    static DepenseResponse toResponse(Depense d) {
        if (d == null) {
            return null;
        }
        return new DepenseResponse(
                d.getIdDepense(),
                d.getInitiative() != null ? d.getInitiative().getIdInitiative() : null,
                d.getInitiative() != null ? d.getInitiative().getCode() : null,
                d.getInitiative() != null ? d.getInitiative().getNom() : null,
                d.getLibelle(),
                d.getMontant(),
                d.getDateDepense(),
                d.getCategorie(),
                d.getCreatedAt(),
                UtilisateurMapper.toSummary(d.getSaisisseur())
        );
    }
}
