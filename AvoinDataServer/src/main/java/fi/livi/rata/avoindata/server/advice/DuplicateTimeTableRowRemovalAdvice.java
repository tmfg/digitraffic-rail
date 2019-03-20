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
import java.util.stream.Stream;

/*Hibernate duplicate removal*/
@ControllerAdvice
public class DuplicateTimeTableRowRemovalAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(final MethodParameter methodParameter, final Class<? extends HttpMessageConverter<?>> aClass) {
        try {
            String typeName = methodParameter.getNestedGenericParameterType().getTypeName();
            if (typeName.equals("java.util.List<fi.livi.rata.avoindata.common.domain.train.Train>") ||
                    typeName.equals("java.util.stream.Stream<fi.livi.rata.avoindata.common.domain.train.Train>")) {
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
        if (body instanceof List) {
            ((List<Train>) body).forEach(this::removeDuplicate);
        } else if (body instanceof Stream) {
            return ((Stream<Train>) body).map(s -> removeDuplicate(s));
        }

        return body;
    }

    private Train removeDuplicate(final Train train) {
        train.timeTableRows = Lists.newArrayList(Sets.newLinkedHashSet(train.timeTableRows));
        return train;
    }
}
