package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.MembreInitiativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Droits par initiative (Phase 2C).
 *
 * <p>Un utilisateur peut modifier une initiative s'il en est le
 * <strong>responsable</strong> ou un <strong>membre contributeur /
 * responsable</strong> (table {@code membre_initiative}). Un simple
 * <strong>lecteur</strong> ne peut que consulter.</p>
 */
@Service
public class MembreInitiativeService {

    private final InitiativeRepository initiativeRepository;
    private final MembreInitiativeRepository membreRepository;

    public MembreInitiativeService(InitiativeRepository initiativeRepository,
                                   MembreInitiativeRepository membreRepository) {
        this.initiativeRepository = initiativeRepository;
        this.membreRepository = membreRepository;
    }

    /** L'utilisateur peut-il modifier cette initiative ? */
    @Transactional(readOnly = true)
    public boolean peutModifier(Long idUser, Long idInitiative) {
        if (idUser == null || idInitiative == null) {
            return false;
        }
        Initiative initiative = initiativeRepository.findById(idInitiative).orElse(null);
        if (initiative == null) {
            return false;
        }
        // Le responsable de l'initiative peut toujours la modifier.
        if (initiative.getResponsable() != null
                && initiative.getResponsable().getIdUser().equals(idUser)) {
            return true;
        }
        // Sinon, un membre contributeur/responsable peut modifier.
        return membreRepository.peutModifier(idUser, idInitiative);
    }

    /** L'utilisateur est-il membre de l'initiative (quelque rôle que ce soit) ? */
    @Transactional(readOnly = true)
    public boolean estMembre(Long idUser, Long idInitiative) {
        if (idUser == null || idInitiative == null) {
            return false;
        }
        return membreRepository.estMembre(idUser, idInitiative);
    }
}
