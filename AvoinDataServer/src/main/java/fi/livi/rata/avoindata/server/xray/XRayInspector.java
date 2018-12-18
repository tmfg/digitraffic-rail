package fi.livi.rata.avoindata.server.xray;

import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.spring.aop.AbstractXRayInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class XRayInspector extends AbstractXRayInterceptor {
    @Override
    protected Map<String, Map<String, Object>> generateMetadata(ProceedingJoinPoint proceedingJoinPoint, Subsegment subsegment) {
        return super.generateMetadata(proceedingJoinPoint, subsegment);
    }

    @Override
    @Pointcut("( @within(org.springframework.stereotype.Service) " +
            "|| @within(org.springframework.stereotype.Component) " +
            "|| @within(org.springframework.stereotype.Controller) " +
            "|| @within(org.springframework.stereotype.Indexed) " +
            "|| @within(org.springframework.stereotype.Repository)) " +
            "&& within(fi.livi.*)")
    public void xrayEnabledClasses() {}
}
