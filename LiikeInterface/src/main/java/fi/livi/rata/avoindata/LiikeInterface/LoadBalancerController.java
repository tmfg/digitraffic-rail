package fi.livi.rata.avoindata.LiikeInterface;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Controller
public class LoadBalancerController {
    @PersistenceContext
    private EntityManager entityManager;

    @RequestMapping("/avoin/healthcheck")
    @ResponseBody
    String healthcheck() {
        final Object result = entityManager.createNativeQuery("select 1 from dual").getSingleResult();

        return "OK";
    }

    @RequestMapping(value = "/maintenance/avoin", produces = "text/plain; charset=utf-8")
    @ResponseBody
    String maintenanceOff() {
        return "0";
    }
}
