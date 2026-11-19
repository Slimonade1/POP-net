package dk.pop.status.temp.utils;

import dk.pop.status.temp.domain.ServiceEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceSlugUtilsTest {
    @Test
    void duplicateNamesAndNamesMatchingGeneratedSuffixesHaveUniqueUrls() {
        var slugs = ServiceSlugUtils.slugs(List.of(
                service(3, "Print server 2"), service(2, "Print server"), service(1, "Print server")));

        assertThat(slugs).containsEntry(1L, "print-server")
                .containsEntry(2L, "print-server-2").containsEntry(3L, "print-server-2-3");
    }

    @Test
    void emptySlugsHaveAUsableFallbackAndBlankNamesAreSkipped() {
        var slugs = ServiceSlugUtils.slugs(List.of(service(1, ""), service(2, "!!!")));
        assertThat(slugs).containsOnlyKeys(2L).containsEntry(2L, "service-2");
    }

    private static ServiceEntity service(long id, String name) {
        return new ServiceEntity(id, name, "icmp://127.0.0.1", 3);
    }
}
