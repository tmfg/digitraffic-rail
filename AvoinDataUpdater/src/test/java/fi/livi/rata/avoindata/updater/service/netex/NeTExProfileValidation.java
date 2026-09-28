package fi.livi.rata.avoindata.updater.service.netex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.entur.netex.validation.validator.NetexValidatorsRunner;
import org.entur.netex.validation.validator.Severity;
import org.entur.netex.validation.validator.SimpleValidationEntryFactory;
import org.entur.netex.validation.validator.ValidationReportEntry;
import org.entur.netex.validation.validator.XPathValidator;
import org.entur.netex.validation.validator.id.DefaultNetexIdRepository;
import org.entur.netex.validation.validator.id.ExternalReferenceValidator;
import org.entur.netex.validation.validator.id.NetexIdRepository;
import org.entur.netex.validation.validator.id.NetexIdUniquenessValidator;
import org.entur.netex.validation.validator.id.NetexReferenceValidator;
import org.entur.netex.validation.validator.id.ReferenceToValidEntityTypeValidator;
import org.entur.netex.validation.validator.id.VersionOnLocalNetexIdValidator;
import org.entur.netex.validation.validator.id.VersionOnRefToLocalNetexIdValidator;
import org.entur.netex.validation.validator.schema.NetexSchemaValidator;
import org.entur.netex.validation.validator.xpath.XPathRuleValidator;
import org.entur.netex.validation.validator.xpath.tree.PublicationDeliveryValidationTreeFactory;
import org.entur.netex.validation.xml.NetexXMLParser;

/**
 * Runs the Entur Nordic profile rules over a generated dataset. Catches the whole
 * class of faults that only show up in assembled XML — dangling references,
 * duplicate ids, missing mandatory elements — which mocked unit tests cannot see.
 */
final class NeTExProfileValidation {

    private static final String CODESPACE = "FTR";
    private static final int MAX_SCHEMA_ERRORS = 1000;

    private NeTExProfileValidation() {
    }

    /** Findings of ERROR severity or worse, formatted one per line. */
    static List<String> errors(final Map<String, String> files) {
        return findings(files).stream()
                .filter(e -> e.getSeverity() == Severity.ERROR || e.getSeverity() == Severity.CRITICAL)
                .map(e -> "%s: %s (%s)".formatted(e.getFileName(), e.getName(), e.getMessage()))
                .toList();
    }

    private static List<ValidationReportEntry> findings(final Map<String, String> files) {
        final NetexIdRepository idRepository = new DefaultNetexIdRepository();
        // ids of another codespace belong to its publisher, not to this dataset
        final ExternalReferenceValidator otherCodespace = refs -> refs.stream()
                .filter(r -> !r.getId().startsWith(CODESPACE + ":"))
                .collect(Collectors.toSet());

        final List<XPathValidator> xpath = List.of(
                new XPathRuleValidator(new PublicationDeliveryValidationTreeFactory()),
                new VersionOnLocalNetexIdValidator(),
                new VersionOnRefToLocalNetexIdValidator(),
                new ReferenceToValidEntityTypeValidator(),
                new NetexIdUniquenessValidator(idRepository),
                new NetexReferenceValidator(idRepository, List.of(otherCodespace)));

        final NetexValidatorsRunner runner = NetexValidatorsRunner.of()
                .withNetexXMLParser(new NetexXMLParser())
                .withNetexSchemaValidator(new NetexSchemaValidator(MAX_SCHEMA_ERRORS))
                .withXPathValidators(xpath)
                .withValidationReportEntryFactory(new SimpleValidationEntryFactory())
                .build();

        final String reportId = "test-" + System.nanoTime();
        final List<ValidationReportEntry> all = new ArrayList<>();
        for (final String name : sharedFileFirst(files)) {
            all.addAll(runner.validate(CODESPACE, reportId, name,
                    files.get(name).getBytes(StandardCharsets.UTF_8))
                    .getValidationReportEntries());
        }
        return all;
    }

    /** The shared file registers the ids the line files refer to, so it has to go first. */
    private static List<String> sharedFileFirst(final Map<String, String> files) {
        return files.keySet().stream()
                .sorted(Comparator.comparing((String n) -> !n.startsWith("_")).thenComparing(n -> n))
                .toList();
    }
}
