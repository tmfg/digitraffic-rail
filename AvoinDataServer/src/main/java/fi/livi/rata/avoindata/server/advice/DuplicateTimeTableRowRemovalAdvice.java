package fi.livi.rata.avoindata.server.advice;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/*Hibernate duplicate removal*/
@ControllerAdvice
public class DuplicateTimeTableRowRemovalAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(final MethodParameter methodParameter, final Class<? extends HttpMessageConverter<?>> aClass) {
        try {
            if (methodParameter.getNestedGenericParameterType().getTypeName().equals("java.util.List<fi.livi.rata.avoindata.common.domain.train.Train>")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Object beforeBodyWrite(final Object body, final MethodParameter methodParameter, final MediaType mediaType,
                                  final Class<? extends HttpMessageConverter<?>> aClass, final ServerHttpRequest serverHttpRequest,
                                  final ServerHttpResponse serverHttpResponse) {
        List<Train> trainList = (List<Train>) body;
        ((List<Train>) body).forEach(this::removeDuplicate);

        return trainList;
    }

    private void removeDuplicate(final Train train) {
        train.timeTableRows = Lists.newArrayList(Sets.newLinkedHashSet(train.timeTableRows));
    }
}
