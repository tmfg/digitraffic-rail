package fi.livi.rata.avoindata.updater.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Callable;

@Component
public class SimpleTransactionManager {

    @Transactional
    public <T> T executeInTransaction(final Callable<T> callable) throws Exception {
        return callable.call();
    }
}
