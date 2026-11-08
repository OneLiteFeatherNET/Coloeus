package net.onelitefeather.coloeus.paper;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

/** Creates {@link OfflinePlayer} stand-ins for the tests; only the UUID and the name are answered. */
final class FakeOfflinePlayer {

    private FakeOfflinePlayer() {
    }

    static OfflinePlayer of(UUID uuid, String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FakeOfflinePlayer[" + name + "]";
                    default -> throw new UnsupportedOperationException("not used by the tests: " + method.getName());
                });
    }
}
