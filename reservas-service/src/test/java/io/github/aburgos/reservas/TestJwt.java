package io.github.aburgos.reservas;

import io.github.aburgos.reservas.infrastructure.config.KeycloakRealmRoleConverter;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * JWT simulados para los tests, con la forma que emite Keycloak: el usuario en {@code sub} y los roles
 * de realm en {@code realm_access.roles}. Las autoridades se derivan con el conversor real de producción,
 * así los tests también cubren el mapeo de roles. No hace falta CSRF: la API es sin sesión.
 */
public final class TestJwt {

    private TestJwt() {
    }

    public static RequestPostProcessor ciudadano(String sub) {
        return conRoles(sub, "ciudadano");
    }

    public static RequestPostProcessor gestor(String sub) {
        return conRoles(sub, "gestor");
    }

    public static RequestPostProcessor admin(String sub) {
        return conRoles(sub, "admin");
    }

    /** Token válido pero sin ningún rol de realm. */
    public static RequestPostProcessor sinRoles(String sub) {
        return conRoles(sub);
    }

    private static RequestPostProcessor conRoles(String sub, String... roles) {
        return jwt()
                .jwt(token -> token.subject(sub).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }
}
