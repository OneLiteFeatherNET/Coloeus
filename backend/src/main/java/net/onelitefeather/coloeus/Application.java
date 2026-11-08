package net.onelitefeather.coloeus;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * The bearer scheme is what Swagger UI's "Authorize" sends: an Entra ID access token for the Coloeus audience.
 * The generated document is META-INF/swagger/coloeus.yml, served anonymously under /swagger/coloeus.yml.
 */
@OpenAPIDefinition(
        info = @Info(title = "Coloeus"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Entra ID access token for the Coloeus audience. Roles: Coloeus.Client, Coloeus.Admin, Coloeus.Moderator.")
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
