package io.github.aburgos.reservas.infrastructure.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte los roles de realm de Keycloak ({@code realm_access.roles}) en autoridades de Spring
 * ({@code ROLE_<NOMBRE_EN_MAYUSCULAS>}). Keycloak no los publica en {@code scope}, que es lo único que
 * Spring lee por defecto. Un token sin ese claim, o con un formato inesperado, no concede ningún rol.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(
                        "ROLE_" + ((String) rol).toUpperCase(Locale.ROOT)))
                .toList();
    }
}
