package fi.livi.rata.avoindata.common.domain.trainreadymessage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table
@Schema(name = "TrainRunningMessageRule", title = "TrainRunningMessageRule", description = "These rules define how TrainRunningMessages trigger actual times for TimeTableRows")
public class TrainRunningMessageRule {
    @Id
    public Long id;

    @Schema(description = "For which station is the actual time generated")
    public String timeTableRowStationShortCode;
    @Schema(description = "For which TimeTableRow is the actual time generated")
    public TimeTableRow.TimeTableRowType timeTableRowType;

    public String trainRunningMessageStationShortCode;
    public String trainRunningMessageNextStationShortCode;
    public String trainRunningMessageTrackSection;
    public TrainRunningMessageTypeEnum trainRunningMessageType ;

    @Schema(description = "TrainRunningMessages timestamp is adjusted for offset when generating actual time")
    public Integer offset;


    @Override
    public String toString() {
        return "TrainRunningMessageRule{" +
                "id=" + id +
                '}';
    }


}