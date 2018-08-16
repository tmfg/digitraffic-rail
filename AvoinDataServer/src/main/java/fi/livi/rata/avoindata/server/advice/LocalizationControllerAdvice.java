package fi.livi.rata.avoindata.server.advice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import fi.livi.rata.avoindata.common.dao.localization.PowerTypeRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.OptionalUtil;

@ControllerAdvice
public class LocalizationControllerAdvice implements ResponseBodyAdvice<Object> {
    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @Autowired
    private PowerTypeRepository powerTypeRepository;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

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
        train.trainCategory = OptionalUtil.getName(trainCategoryRepository.findByIdCached(train.trainCategoryId));
        train.trainType = OptionalUtil.getName(trainTypeRepository.findByIdCached(train.trainTypeId));
    }

    private void localizeComposition(final Composition composition) {
        composition.trainCategory = OptionalUtil.getName(trainCategoryRepository.findByIdCached(composition.trainCategoryId));
        composition.trainType = OptionalUtil.getName(trainTypeRepository.findByIdCached(composition.trainTypeId));

        for (final JourneySection journeySection : composition.journeySections) {
            for (final Locomotive locomotive : journeySection.locomotives) {
                locomotive.powerType = OptionalUtil.getName(powerTypeRepository.findByAbbreviationCached(locomotive.powerTypeAbbreviation));
            }
        }
    }
}
