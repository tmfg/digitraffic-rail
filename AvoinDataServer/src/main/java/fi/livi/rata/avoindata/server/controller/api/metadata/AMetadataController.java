package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = WebConfig.CONTEXT_PATH + "metadata")
@Tag(name = "metadata", description = "Returns supporting metadata. For example list of stations")
public abstract class AMetadataController {
    protected void setCache(final HttpServletResponse response, Collection items) {
        CacheConfig.METADATA_CACHECONTROL.setCacheParameter(response, items);
    }
}
