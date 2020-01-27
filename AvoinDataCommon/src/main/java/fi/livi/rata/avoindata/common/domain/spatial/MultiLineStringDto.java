package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.List;
import java.util.stream.Collectors;

public final class MultiLineStringDto implements GeometryDto<List<List<List<Double>>>> {

    private final List<LineStringDto> lines;

    public MultiLineStringDto(final List<LineStringDto> lines) {
        this.lines = lines;
    }

    @Override
    public List<List<List<Double>>> getCoordinates() {
        return lines.stream().map(LineStringDto::getCoordinates).collect(Collectors.toList());
    }

    @Override
    public String getType() {
        return "MultiLineString";
    }
}
