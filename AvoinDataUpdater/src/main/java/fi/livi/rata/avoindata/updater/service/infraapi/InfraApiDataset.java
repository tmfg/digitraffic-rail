package fi.livi.rata.avoindata.updater.service.infraapi;

import java.util.Locale;

/**
 * The Infra API datasets whose health is tracked separately. They differ by orders of magnitude in
 * both volume and criticality, so aggregating them into one figure hides outages.
 */
public enum InfraApiDataset {
    REITIT("reitit"),
    RAUTATIELIIKENNEPAIKAT("rautatieliikennepaikat"),
    LIIKENNEPAIKANOSAT("liikennepaikanosat"),
    RAIDEOSUUDET("raideosuudet"),
    LAITURIT("laiturit"),
    RADAT("radat"),
    OTHER("other");

    private final String token;

    InfraApiDataset(final String token) {
        this.token = token;
    }

    /** Metric key segment. Underscores only: a hyphen is a query-syntax operator in OpenSearch DSL. */
    public String metricKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static InfraApiDataset fromPath(final String path) {
        if (path == null) {
            return OTHER;
        }
        final String lowerCasePath = path.toLowerCase(Locale.ROOT);
        for (final InfraApiDataset dataset : values()) {
            if (dataset != OTHER && lowerCasePath.contains(dataset.token)) {
                return dataset;
            }
        }
        return OTHER;
    }
}
