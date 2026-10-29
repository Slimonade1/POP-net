package dk.pop.status.temp.view.example;

import java.util.List;

public record ExampleService(String name, String slug, Integer latencyMs, List<Incident> incidents) {

    public boolean hasLatency() {
        return latencyMs != null;
    }
}
