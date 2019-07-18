package fi.livi.rata.avoindata.server.advice;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.server.services.GeoJsonFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class GeoJsonControllerAdvice implements ResponseBodyAdvice<Object> {
    @Autowired
    private GeoJsonFormatter geoJsonFormatter;

    @Override
    public boolean supports(final MethodParameter methodParameter, final Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(final Object body, final MethodParameter methodParameter, final MediaType mediaType,
                                  final Class<? extends HttpMessageConverter<?>> aClass, final ServerHttpRequest serverHttpRequest,
                                  final ServerHttpResponse serverHttpResponse) {
        String path = serverHttpRequest.getURI().getPath();
        if (isAGeoJsonRequest(serverHttpRequest)) {
            if (path.contains("/train-locations")) {
                return geoJsonFormatter.wrapAsGeoJson(serverHttpResponse, (List<TrainLocation>) body, s -> new Double[]{s.location.getX(), s.location.getY()});
            } else if (path.contains("metadata/stations")) {
                return geoJsonFormatter.wrapAsGeoJson(serverHttpResponse, (List<Station>) body, s -> new Double[]{s.longitude.doubleValue(), s.latitude.doubleValue()});
            }
        }

        return body;
    }

    private boolean isAGeoJsonRequest(ServerHttpRequest serverHttpRequest) {
        return serverHttpRequest.getHeaders().getAccept().contains(MediaType.valueOf("application/vnd.geo+json"));
    }
}
