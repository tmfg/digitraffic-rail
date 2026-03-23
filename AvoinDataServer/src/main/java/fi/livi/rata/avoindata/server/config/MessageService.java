package fi.livi.rata.avoindata.server.config;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final MessageSource messageSource;

    public MessageService(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(final String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }
}

