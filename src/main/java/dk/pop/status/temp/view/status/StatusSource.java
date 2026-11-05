package dk.pop.status.temp.view.status;

import java.time.ZonedDateTime;
import java.util.List;

public interface StatusSource {
    List<ServiceGroup> groups(ZonedDateTime now);

    List<Integer> latencies(ServiceView service, Range range, ZonedDateTime now);

    Integer latencyNow(ServiceView service, ZonedDateTime now);
}
