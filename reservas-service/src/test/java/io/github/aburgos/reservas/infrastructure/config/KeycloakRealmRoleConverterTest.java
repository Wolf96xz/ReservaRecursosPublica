package io.github.aburgos.reservas.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
                .subject("u").issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private List<String> autoridades(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void conviertePrefijaYPasaAMayusculasLosRolesDeRealm() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("gestor", "Ciudadano"))));

        assertThat(autoridades(jwt)).containsExactlyInAnyOrder("ROLE_GESTOR", "ROLE_CIUDADANO");
    }

    @Test
    void sinClaimRealmAccessNoConcedeNingunRol() {
        assertThat(autoridades(jwt(Map.of()))).isEmpty();
    }

    @Test
    void realmAccessSinRolesNoConcedeNingunRol() {
        assertThat(autoridades(jwt(Map.of("realm_access", Map.of())))).isEmpty();
    }

    @Test
    void formatoInesperadoNoConcedeRolesNiFalla() {
        assertThat(autoridades(jwt(Map.of("realm_access", "gestor")))).isEmpty();
        assertThat(autoridades(jwt(Map.of("realm_access", Map.of("roles", "gestor"))))).isEmpty();
    }

    @Test
    void ignoraLosRolesQueNoSonTexto() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("gestor", 42))));

        assertThat(autoridades(jwt)).containsExactly("ROLE_GESTOR");
    }

    @Test
    void losRolesDeClienteNoSeConsideranRolesDeRealm() {
        Jwt jwt = jwt(Map.of("resource_access", Map.of("reservas-frontend", Map.of("roles", List.of("admin")))));

        assertThat(autoridades(jwt)).isEmpty();
    }
}
