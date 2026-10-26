package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.ObjectifDto;
import ma.it6.portefeuille.dto.ObjectifRequest;
import ma.it6.portefeuille.entity.ObjectifStrategique;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.ObjectifStrategiqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Objectifs stratégiques (alignement Jira Align) : consultation,
 * création, modification et suppression du référentiel auquel les
 * initiatives sont rattachées.
 */
@Service
@Transactional(readOnly = true)
public class ObjectifService {

    private final ObjectifStrategiqueRepository objectifRepository;
    private final InitiativeRepository initiativeRepository;

    public ObjectifService(ObjectifStrategiqueRepository objectifRepository,
                           InitiativeRepository initiativeRepository) {
        this.objectifRepository = objectifRepository;
        this.initiativeRepository = initiativeRepository;
    }

    public List<ObjectifDto> lister() {
        return objectifRepository.findAllByOrderByIdObjectifAsc().stream()
                .map(InitiativeMapper::toObjectifDto)
                .toList();
    }

    @Transactional
    public ObjectifDto creer(ObjectifRequest request) {
        validerCodeUnique(request.code(), null);
        ObjectifStrategique objectif = ObjectifStrategique.builder()
                .code(request.code().trim())
                .libelle(request.libelle().trim())
                .description(request.description())
                .build();
        return InitiativeMapper.toObjectifDto(objectifRepository.save(objectif));
    }

    @Transactional
    public ObjectifDto modifier(Long idObjectif, ObjectifRequest request) {
        ObjectifStrategique objectif = objectifRepository.findById(idObjectif)
                .orElseThrow(() -> new ResourceNotFoundException("Objectif stratégique introuvable : " + idObjectif));
        validerCodeUnique(request.code(), idObjectif);
        objectif.setCode(request.code().trim());
        objectif.setLibelle(request.libelle().trim());
        objectif.setDescription(request.description());
        return InitiativeMapper.toObjectifDto(objectifRepository.save(objectif));
    }

    private void validerCodeUnique(String code, Long idIgnore) {
        String codeTrim = code == null ? "" : code.trim();
        if (codeTrim.isBlank()) {
            throw new BadRequestException("Le code est obligatoire.");
        }
        boolean existe = idIgnore == null
                ? objectifRepository.existsByCode(codeTrim)
                : objectifRepository.existsByCodeAndIdObjectifNot(codeTrim, idIgnore);
        if (existe) {
            throw new BadRequestException("Le code « " + codeTrim + " » est déjà utilisé.");
        }
    }

    /**
     * Suppression définitive d'un objectif (droit existant
     * {@code INITIATIVE_MODIFIER}, imposé par SecurityConfig sur
     * {@code DELETE /api/objectifs/**} — aucun nouveau droit créé).
     *
     * <p>Garde-fou : un objectif <strong>référencé par au moins une
     * initiative</strong> est refusé en 400. La clé étrangère
     * {@code initiative.id_objectif} est en {@code ON DELETE SET NULL} :
     * sans ce garde-fou, MySQL détacherait silencieusement les
     * initiatives et le portefeuille perdrait son alignement
     * stratégique. On détache d'abord (champ « Objectif » de
     * l'initiative), on supprime ensuite.</p>
     *
     * <p>Une objectif introuvable lève {@link ResourceNotFoundException}
     * → 404.</p>
     */
    @Transactional
    public void supprimer(Long idObjectif) {
        ObjectifStrategique objectif = objectifRepository.findById(idObjectif)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Objectif stratégique introuvable : " + idObjectif));

        long initiatives = initiativeRepository.countByObjectif_IdObjectif(idObjectif);
        if (initiatives > 0) {
            throw new BadRequestException("Opération refusée : cet objectif est référencé par "
                    + initiatives + " initiative(s). Détachez-les d'abord (champ « Objectif » "
                    + "de chaque initiative) avant de le supprimer.");
        }

        objectifRepository.delete(objectif);
    }
}