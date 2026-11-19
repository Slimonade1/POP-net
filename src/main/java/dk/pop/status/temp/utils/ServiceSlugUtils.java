package dk.pop.status.temp.utils;

import dk.pop.status.temp.domain.ServiceEntity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;

public final class ServiceSlugUtils {
    private ServiceSlugUtils() {
    }

    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public static Map<Long, String> slugs(Iterable<ServiceEntity> services) {
        Map<Long, String> result = new LinkedHashMap<>();
        var used = new HashSet<String>();
        StreamSupport.stream(services.spliterator(), false)
                .filter(service -> service.name() != null && !service.name().isBlank())
                .sorted(Comparator.comparing(ServiceEntity::id))
                .forEach(service -> {
                    String base = slug(service.name());
                    String candidate = base.isEmpty() ? "service-" + service.id() : base;
                    while (!used.add(candidate)) {
                        candidate += "-" + service.id();
                    }
                    result.put(service.id(), candidate);
                });
        return result;
    }
}
