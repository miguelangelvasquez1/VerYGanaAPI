package com.verygana2.utils.pqrs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.User;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link RequesterNameResolver}: resuelve un nombre legible para
 * mostrar en emails/notificaciones de PQRS, sin importar el rol del usuario
 * (cada rol guarda su nombre en un campo distinto de UserDetails).
 */
@DisplayName("RequesterNameResolver")
class RequesterNameResolverTest {

    private final RequesterNameResolver resolver = new RequesterNameResolver();

    @Test
    @DisplayName("ConsumerDetails: concatena nombre + apellido")
    void consumerDetails_returnsFullName() {
        ConsumerDetails details = new ConsumerDetails();
        details.setName("Ana");
        details.setLastName("Gómez");

        User user = userWith(details);

        assertThat(resolver.resolve(user)).isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("CommercialDetails: usa el nombre de la empresa")
    void commercialDetails_returnsCompanyName() {
        CommercialDetails details = new CommercialDetails();
        details.setCompanyName("Tienda XYZ SAS");

        User user = userWith(details);

        assertThat(resolver.resolve(user)).isEqualTo("Tienda XYZ SAS");
    }

    @Test
    @DisplayName("GameDesignerDetails: concatena nombre + apellido")
    void gameDesignerDetails_returnsFullName() {
        GameDesignerDetails details = new GameDesignerDetails();
        details.setName("Carlos");
        details.setLastName("Ruiz");

        User user = userWith(details);

        assertThat(resolver.resolve(user)).isEqualTo("Carlos Ruiz");
    }

    @Test
    @DisplayName("rol no contemplado (ej. AdminDetails): cae al email como respaldo")
    void unmappedRole_fallsBackToEmail() {
        User user = userWith(new AdminDetails());
        user.setEmail("admin@verygana.com");

        assertThat(resolver.resolve(user)).isEqualTo("admin@verygana.com");
    }

    @Test
    @DisplayName("sin UserDetails asociado (null): cae al email como respaldo")
    void nullUserDetails_fallsBackToEmail() {
        User user = new User();
        user.setEmail("sin-perfil@verygana.com");

        assertThat(resolver.resolve(user)).isEqualTo("sin-perfil@verygana.com");
    }

    private User userWith(com.verygana2.models.userDetails.UserDetails details) {
        User user = new User();
        user.setUserDetails(details);
        return user;
    }
}
