package dk.pop.status.temp.view.status;

import java.util.List;

public record ServiceGroup(String name, List<ServiceView> services) {
}
