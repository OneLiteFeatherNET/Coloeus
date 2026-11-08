package net.onelitefeather.coloeus.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.onelitefeather.coloeus.account.AccountController;
import net.onelitefeather.coloeus.currency.CurrencyController;
import net.onelitefeather.coloeus.ledger.TransactionController;
import net.onelitefeather.coloeus.player.PlayerNameController;
import org.junit.jupiter.api.Test;

/**
 * Default deny for {@code /v1}: every route method of the service names the roles that may call it.
 */
class SecuredRoutesTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            CurrencyController.class, ClientController.class, PlayerNameController.class,
            AccountController.class, TransactionController.class);

    @Test
    void everyRouteOfTheServiceNamesItsRoles() {
        List<String> unsecured = CONTROLLERS.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(SecuredRoutesTest::isRoute)
                .filter(method -> !method.isAnnotationPresent(Secured.class))
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .toList();

        assertTrue(unsecured.isEmpty(), "every route must carry @Secured, unsecured: " + unsecured);
    }

    private static boolean isRoute(Method method) {
        return Stream.of(Get.class, Post.class, Put.class, Delete.class).anyMatch(method::isAnnotationPresent);
    }
}
