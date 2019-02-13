package fi.livi.rata.avoindata.server.advice;

import fi.livi.rata.avoindata.common.dao.localization.CompositionLocalizer;
import fi.livi.rata.avoindata.common.dao.localization.TrainLocalizer;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class LocalizationControllerAdvice implements ResponseBodyAdvice<Object> {
    @Autowired
    private TrainLocalizer trainLocalizer;

    @Autowired
    private CompositionLocalizer compositionLocalizer;

    @Override
    public boolean supports(final MethodParameter methodParameter, final Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(final Object body, final MethodParameter methodParameter, final MediaType mediaType,
                                  final Class<? extends HttpMessageConverter<?>> aClass, final ServerHttpRequest serverHttpRequest,
                                  final ServerHttpResponse serverHttpResponse) {
        if (body instanceof Iterable) {
            ((Iterable<?>) body).forEach(this::localizeEntity);
        } else {
            localizeEntity(body);
        }
        return body;
    }

    private void localizeEntity(final Object entity) {
        if (entity instanceof Composition) {
            compositionLocalizer.localize((Composition) entity);
        } else if (entity instanceof Train) {
            trainLocalizer.localize((Train) entity);
        }
    }
}
