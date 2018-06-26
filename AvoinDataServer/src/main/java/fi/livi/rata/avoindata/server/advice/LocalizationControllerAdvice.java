package fi.livi.rata.avoindata.server.advice;

import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.localization.PowerTypeCache;
import fi.livi.rata.avoindata.server.localization.TrainCategoryCache;
import fi.livi.rata.avoindata.server.localization.TrainTypeCache;
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
    private TrainCategoryCache trainCategoryCache;

    @Autowired
    private TrainTypeCache trainTypeCache;

    @Autowired
    private PowerTypeCache powerTypeCache;

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
            localizeComposition((Composition) entity);
        } else if (entity instanceof Train) {
            localizeTrain((Train) entity);
        }
    }

    public void localizeTrain(final Train train) {
        train.trainCategory = trainCategoryCache.get(train.trainCategoryId).name;
        train.trainType = trainTypeCache.get(train.trainTypeId).name;
    }

    private void localizeComposition(final Composition composition) {
        composition.trainCategory = trainCategoryCache.get(composition.trainCategoryId).name;
        composition.trainType = trainTypeCache.get(composition.trainTypeId).name;
        composition.journeySections.forEach(this::localizeJourneySection);
    }

    private void localizeJourneySection(final JourneySection journeySection) {
        journeySection.locomotives.forEach(this::localizeLocomotive);
    }

    private void localizeLocomotive(final Locomotive locomotive) {
        locomotive.powerType = powerTypeCache.get(locomotive.powerTypeAbbreviation).name;
    }
}
