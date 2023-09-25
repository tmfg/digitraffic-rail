package fi.livi.rata.avoindata.server.config;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import fi.livi.digitraffic.common.aop.TransactionLoggerAspect;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Aspect
public class AopConfiguration {

    @Bean
    public TransactionLoggerAspect transactionLoggerAspect(@Value("${dt.logging.transaction.limit}") final int limit) { return new TransactionLoggerAspect(limit); }
}
