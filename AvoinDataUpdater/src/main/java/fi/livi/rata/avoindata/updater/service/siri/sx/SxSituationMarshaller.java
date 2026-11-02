package fi.livi.rata.avoindata.updater.service.siri.sx;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxReportType;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxSituation;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxTarget;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxText;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.AffectedOperatorStructure;
import uk.org.siri.siri21.AffectedRouteStructure;
import uk.org.siri.siri21.AffectedStopPlaceStructure;
import uk.org.siri.siri21.AffectedVehicleJourneyStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.DataFrameRefStructure;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ReportTypeEnumeration;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.SituationSourceStructure;
import uk.org.siri.siri21.SituationSourceTypeEnumeration;
import uk.org.siri.siri21.SituationVersion;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

/** Builds the SIRI-SX document from situations. Only translation, no decisions. */
public class SxSituationMarshaller {

    private final SiriWritingService siriWritingService;
    private final String producerRef;

    public SxSituationMarshaller(final SiriWritingService siriWritingService, final String producerRef) {
        this.siriWritingService = siriWritingService;
        this.producerRef = producerRef;
    }

    public Siri marshal(final List<SxSituation> situations, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(now.withZoneSameInstant(DateProvider.ZONE_ID_HKI));
        if (!situations.isEmpty()) {
            final SituationExchangeDeliveryStructure.Situations container = new SituationExchangeDeliveryStructure.Situations();
            for (final SxSituation situation : situations) {
                container.getPtSituationElements().add(marshalSituation(situation));
            }
            delivery.setSituations(container);
        }

        siri.getServiceDelivery().getSituationExchangeDeliveries().add(delivery);
        return siri;
    }

    public byte[] marshalToBytes(final Siri siri) {
        return siriWritingService.marshalToBytes(siri);
    }

    private PtSituationElement marshalSituation(final SxSituation situation) {
        final PtSituationElement element = new PtSituationElement();

        element.setCreationTime(situation.creationTime());
        final RequestorRef participantRef = new RequestorRef();
        participantRef.setValue(producerRef);
        element.setParticipantRef(participantRef);
        final SituationNumber number = new SituationNumber();
        number.setValue(situation.situationNumber());
        element.setSituationNumber(number);
        final SituationVersion version = new SituationVersion();
        version.setValue(BigInteger.valueOf(situation.version()));
        element.setVersion(version);
        element.setVersionedAtTime(situation.versionedAtTime());

        final SituationSourceStructure source = new SituationSourceStructure();
        source.setSourceType(SituationSourceTypeEnumeration.DIRECT_REPORT);
        element.setSource(source);

        element.setProgress(situation.closed() ? WorkflowStatusEnumeration.CLOSED : WorkflowStatusEnumeration.OPEN);
        final HalfOpenTimestampOutputRangeStructure validity = new HalfOpenTimestampOutputRangeStructure();
        validity.setStartTime(situation.startTime());
        validity.setEndTime(situation.endTime());
        element.getValidityPeriods().add(validity);

        element.setUndefinedReason("");
        element.setReportType(situation.reportType() == SxReportType.INCIDENT
                ? ReportTypeEnumeration.INCIDENT : ReportTypeEnumeration.GENERAL);
        for (final SxText text : situation.summaries()) {
            element.getSummaries().add(text(text));
        }
        for (final SxText text : situation.descriptions()) {
            element.getDescriptions().add(text(text));
        }
        element.setAffects(affects(situation.target()));
        return element;
    }

    private static DefaultedTextStructure text(final SxText text) {
        final DefaultedTextStructure out = new DefaultedTextStructure();
        // The SIRI 2.0 schema accepts only upper case language codes (FI, SV, EN).
        out.setLang(text.language().toUpperCase(Locale.ROOT));
        out.setValue(text.text());
        return out;
    }

    private static AffectsScopeStructure affects(final SxTarget target) {
        final AffectsScopeStructure affects = new AffectsScopeStructure();
        switch (target) {
            case final SxTarget.StopPlaces stopPlaces -> {
                final AffectsScopeStructure.StopPlaces container = new AffectsScopeStructure.StopPlaces();
                for (final String id : stopPlaces.stopPlaceIds()) {
                    final StopPlaceRef ref = new StopPlaceRef();
                    ref.setValue(id);
                    final AffectedStopPlaceStructure stopPlace = new AffectedStopPlaceStructure();
                    stopPlace.setStopPlaceRef(ref);
                    container.getAffectedStopPlaces().add(stopPlace);
                }
                affects.setStopPlaces(container);
            }
            case final SxTarget.Journey journey -> {
                final DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
                dataFrameRef.setValue(journey.dataFrameRef().value());
                final FramedVehicleJourneyRefStructure framedRef = new FramedVehicleJourneyRefStructure();
                framedRef.setDataFrameRef(dataFrameRef);
                framedRef.setDatedVehicleJourneyRef(journey.serviceJourneyId().value());

                final AffectedVehicleJourneyStructure affected = new AffectedVehicleJourneyStructure();
                affected.setFramedVehicleJourneyRef(framedRef);
                if (journey.operatorRef() != null) {
                    final OperatorRefStructure operatorRef = new OperatorRefStructure();
                    operatorRef.setValue(journey.operatorRef().value());
                    final AffectedOperatorStructure operator = new AffectedOperatorStructure();
                    operator.setOperatorRef(operatorRef);
                    affected.setOperator(operator);
                }
                if (journey.lineId() != null) {
                    final LineRef lineRef = new LineRef();
                    lineRef.setValue(journey.lineId().value());
                    affected.setLineRef(lineRef);
                }
                // The profile requires a Route element, even an empty one.
                affected.getRoutes().add(new AffectedRouteStructure());

                final AffectsScopeStructure.VehicleJourneys container = new AffectsScopeStructure.VehicleJourneys();
                container.getAffectedVehicleJourneies().add(affected);
                affects.setVehicleJourneys(container);
            }
        }
        return affects;
    }
}
