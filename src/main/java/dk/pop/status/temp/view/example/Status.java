package dk.pop.status.temp.view.example;

import java.util.Collection;

public enum Status {
    WORKING('o', "Working", "operational", null),
    SICK('s', "Having problems", "sick", "triangle-alert"),
    DOWN('d', "Down", "down", "circle-x");

    private final char code;
    private final String label;
    private final String css;
    private final String icon;

    Status(char code, String label, String css, String icon) {
        this.code = code;
        this.label = label;
        this.css = css;
        this.icon = icon;
    }

    public char code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String css() {
        return css;
    }

    public String icon() {
        return icon;
    }

    public static Status worst(Collection<Status> statuses) {
        return statuses.stream().max(Enum::compareTo).orElse(WORKING);
    }
}
