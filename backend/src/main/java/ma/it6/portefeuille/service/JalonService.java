package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.JalonRequest;
import ma.it6.portefeuille.dto.JalonResponse;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.StatutJalon;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.JalonMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.StatutJalonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Gestion des jalons d'une initiative (RM09 : un jalon appartient à une
 * seule initiative). La date de réalisation peut être vide tant que le
 * jalon n'est pas atteint (RM15).
 */
@Service
public class JalonService {

    public static final String STATUT_ATTEINT = "Atteint";

    private final JalonRepository jalonRepository;
    private final InitiativeRepository initiativeRepository;
    private final StatutJalonRepository statutJalonRepository;
    private final HistoriqueModificationService historiqueService;

    public JalonService(JalonRepository jalonRepository,
                        InitiativeRepository initiativeRepository,
                        StatutJalonRepository statutJalonRepository,
                        HistoriqueModificationService historiqueService) {
        this.jalonRepository = jalonRepository;
        this.initiativeRepository = initiativeRepository;
        this.statutJalonRepository = statutJalonRepository;
        this.historiqueService = historiqueService;
    }

    @Transactional(readOnly = true)
    public List<JalonResponse> lister(Long idInitiative) {
        verifierInitiative(idInitiative);
        return jalonRepository.findByInitiativeIdInitiativeOrderByDatePrevueAsc(idInitiative).stream()
                .map(JalonMapper::toResponse)
                .toList();
    }

    /**
     * Création d'un jalon. Le statut par défaut est « À atteindre ».
     */
    @Transactional
    public JalonResponse creer(Long idInitiative, JalonRequest request) {
        Initiative initiative = verifierInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : ajout de jalon refusé.");
        }
        verifierDateDansIntervalle(initiative, request.datePrevue());

        Jalon jalon = Jalon.builder()
                .nom(request.nom().trim())
                .description(request.description())
                .datePrevue(request.datePrevue())
                .dateRealisation(request.dateRealisation())
                .initiative(initiative)
                .statutJalon(resoudreStatut(request.idStatutJalon()))
                .build();
        appliquerRegleStatut(jalon);

        Jalon enregistre = jalonRepository.save(jalon);

        // Journal : la création du jalon laisse une trace dans le fil de la
        // fiche (nouvelle valeur = le nom du jalon).
        historiqueService.ecrire(HistoriqueModificationService.TYPE_JALON,
                enregistre.getIdJalon(), initiative,
                HistoriqueModificationService.CHAMP_CREATION,
                null, enregistre.getNom(),
                "Création du jalon", HistoriqueModificationService.auteurCourant(), false);

        return JalonMapper.toResponse(enregistre);
    }

    @Transactional
    public JalonResponse modifier(Long idJalon, JalonRequest request) {
        Jalon jalon = jalonRepository.findById(idJalon)
                .orElseThrow(() -> new ResourceNotFoundException("Jalon introuvable : " + idJalon));
        if (Boolean.TRUE.equals(jalon.getInitiative().getArchive())) {
            throw new BadRequestException("L'initiative est archivée : modification de jalon refusée.");
        }
        verifierDateDansIntervalle(jalon.getInitiative(), request.datePrevue());

        // Journal : valeurs figées avant application, pour le diff champ à
        // champ — il couvre aussi la réouverture (retrait du statut
        // « Atteint »), simple changement de statut comme un autre.
        var avant = valeursJournalisees(jalon);

        jalon.setNom(request.nom().trim());
        jalon.setDescription(request.description());
        jalon.setDatePrevue(request.datePrevue());
        jalon.setDateRealisation(request.dateRealisation());
        if (request.idStatutJalon() != null) {
            jalon.setStatutJalon(resoudreStatut(request.idStatutJalon()));
        }
        appliquerRegleStatut(jalon);

        Jalon enregistre = jalonRepository.save(jalon);

        historiqueService.diff(HistoriqueModificationService.TYPE_JALON,
                enregistre.getIdJalon(), enregistre.getInitiative(),
                avant, valeursJournalisees(enregistre),
                HistoriqueModificationService.auteurCourant(), false);

        return JalonMapper.toResponse(enregistre);
    }

    /**
     * Suppression d'un jalon : une erreur de saisie ne doit pas rester
     * définitive.
     */
    @Transactional
    public void supprimer(Long idJalon) {
        Jalon jalon = jalonRepository.findById(idJalon)
                .orElseThrow(() -> new ResourceNotFoundException("Jalon introuvable : " + idJalon));
        if (Boolean.TRUE.equals(jalon.getInitiative().getArchive())) {
            throw new BadRequestException("L'initiative est archivée : suppression de jalon refusée.");
        }
        String nomSupprime = jalon.getNom();
        Initiative initiative = jalon.getInitiative();
        jalonRepository.delete(jalon);

        // Journal : la trace de suppression survit à la disparition du
        // jalon (aucune clé étrangère sur celui-ci, lien sur l'initiative).
        historiqueService.ecrire(HistoriqueModificationService.TYPE_JALON,
                idJalon, initiative,
                HistoriqueModificationService.CHAMP_SUPPRESSION,
                nomSupprime, null,
                "Suppression du jalon", HistoriqueModificationService.auteurCourant(), false);
    }

    /**
     * Valeurs affichables des champs d'un jalon — le père du diff
     * journalisé : la même carte est construite avant et après la
     * modification.
     */
    private static java.util.Map<String, String> valeursJournalisees(Jalon j) {
        java.util.Map<String, String> valeurs = new java.util.LinkedHashMap<>();
        valeurs.put("nom", j.getNom());
        valeurs.put("description", j.getDescription());
        valeurs.put("datePrevue", j.getDatePrevue() == null ? null : j.getDatePrevue().toString());
        valeurs.put("dateRealisation", j.getDateRealisation() == null ? null : j.getDateRealisation().toString());
        valeurs.put("statut", j.getStatutJalon() == null ? null : j.getStatutJalon().getLibelle());
        return valeurs;
    }

    /**
     * RM15 : lorsqu'un jalon passe à « Atteint » sans date de réalisation,
     * celle-ci est renseignée à la date du jour. Un jalon non atteint
     * conserve une date de réalisation vide.
     */
    private void appliquerRegleStatut(Jalon jalon) {
        if (jalon.getStatutJalon() == null) {
            return;
        }
        boolean atteint = STATUT_ATTEINT.equals(jalon.getStatutJalon().getLibelle());
        if (atteint && jalon.getDateRealisation() == null) {
            jalon.setDateRealisation(LocalDate.now());
        }
        if (!atteint) {
            jalon.setDateRealisation(null);
        }
    }

    /** Un jalon doit se situer dans l'intervalle de son initiative. */
    private void verifierDateDansIntervalle(Initiative initiative, LocalDate datePrevue) {
        if (datePrevue == null) {
            return;
        }
        if (initiative.getDateDebut() != null && datePrevue.isBefore(initiative.getDateDebut())) {
            throw new BadRequestException("La date prévue du jalon (" + datePrevue
                    + ") ne peut pas précéder celle de l'initiative ("
                    + initiative.getDateDebut() + ").");
        }
        if (initiative.getDateFinPrevue() != null && datePrevue.isAfter(initiative.getDateFinPrevue())) {
            throw new BadRequestException("La date prévue du jalon (" + datePrevue
                    + ") ne peut pas dépasser celle de l'initiative ("
                    + initiative.getDateFinPrevue() + ").");
        }
    }

    private StatutJalon resoudreStatut(Long idStatutJalon) {
        if (idStatutJalon == null) {
            return statutJalonRepository.findAllByOrderByOrdreAsc().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Aucun statut de jalon disponible."));
        }
        return statutJalonRepository.findById(idStatutJalon)
                .orElseThrow(() -> new ResourceNotFoundException("Statut de jalon introuvable : " + idStatutJalon));
    }

    private Initiative verifierInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException("Initiative introuvable : " + idInitiative));
    }
}
