package fi.livi.rata.avoindata.server.controller.api.metadata;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.Collection;

@RestController
@RequestMapping(value = WebConfig.CONTEXT_PATH + "metadata", produces = "application/json")
@Api(tags = "metadata",description = "Returns supporting metadata. For example list of stations")
public abstract class AMetadataController {
    protected void setCache(final HttpServletResponse response, Collection items) {
        CacheConfig.METADATA_CACHECONTROL.setCacheParameter(response, items);
    }
}
