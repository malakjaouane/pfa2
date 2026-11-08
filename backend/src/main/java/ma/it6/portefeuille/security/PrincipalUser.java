package ma.it6.portefeuille.security;

import lombok.Getter;
import ma.it6.portefeuille.entity.RolePermission;
import ma.it6.portefeuille.entity.Utilisateur;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utilisateur Spring Security : enveloppe l'entité Utilisateur et expose
 * ses autorisations (permissions fonctionnelles + rôle).
 */
@Getter
public class PrincipalUser implements UserDetails {

    private final Utilisateur utilisateur;
    private final Set<GrantedAuthority> autorites;

    public PrincipalUser(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
        this.autorites = new HashSet<>();
        if (utilisateur.getRole() != null) {
            autorites.add(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().getCode()));
            if (utilisateur.getRole().getRolePermissions() != null) {
                for (RolePermission rp : utilisateur.getRole().getRolePermissions()) {
                    if (rp.getPermission() != null) {
                        autorites.add(new SimpleGrantedAuthority(rp.getPermission().getCode()));
                    }
                }
            }
        }
    }

    public Long getIdOrganisation() {
        return utilisateur.getIdOrganisation();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return autorites;
    }

    @Override
    public String getPassword() {
        return utilisateur.getMotDePasse();
    }

    @Override
    public String getUsername() {
        return utilisateur.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(utilisateur.getActif());
    }
}