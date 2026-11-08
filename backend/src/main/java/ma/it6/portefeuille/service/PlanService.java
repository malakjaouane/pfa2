package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.Organisation;
import ma.it6.portefeuille.entity.Plan;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.PlanRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vérification des limites de plan (Phase 2D).
 *
 * <p>Avant toute création d'initiative ou d'utilisateur, le serveur
 * vérifie que l'organisation n'a pas atteint la limite de son plan
 * (gratuit ou pro). Une limite dépassée renvoie une erreur 400
 * avec un message explicite.</p>
 */
@Service
public class PlanService {

    private final OrganisationRepository organisationRepository;
    private final PlanRepository planRepository;
    private final InitiativeRepository initiativeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public PlanService(OrganisationRepository organisationRepository,
                       PlanRepository planRepository,
                       InitiativeRepository initiativeRepository,
                       UtilisateurRepository utilisateurRepository) {
        this.organisationRepository = organisationRepository;
        this.planRepository = planRepository;
        this.initiativeRepository = initiativeRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Plan de l'organisation (défaut 'gratuit' si introuvable). */
    @Transactional(readOnly = true)
    public Plan planDe(Long idOrganisation) {
        Organisation org = organisationRepository.findById(idOrganisation).orElse(null);
        String code = org == null ? "gratuit" : org.getPlan();
        Plan plan = planRepository.findByCode(code);
        return plan == null ? planRepository.findByCode("gratuit") : plan;
    }

    /** L'organisation peut-elle créer une nouvelle initiative ? */
    @Transactional(readOnly = true)
    public boolean peutCreerInitiative(Long idOrganisation) {
        Plan plan = planDe(idOrganisation);
        long nb = initiativeRepository.countByIdOrganisation(idOrganisation);
        return nb < plan.getNbInitiativesMax();
    }

    /** L'organisation peut-elle ajouter un nouvel utilisateur ? */
    @Transactional(readOnly = true)
    public boolean peutAjouterUtilisateur(Long idOrganisation) {
        Plan plan = planDe(idOrganisation);
        long nb = utilisateurRepository.countByIdOrganisation(idOrganisation);
        return nb < plan.getNbUtilisateursMax();
    }
}
