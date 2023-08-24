package fi.livi.rata.avoindata.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
public abstract class MockMvcBaseTest extends BaseTest {

    @Autowired
    protected MockMvc mockMvc;

    protected ResultActions getJson(final URI url) throws Exception {
        final ResultActions resultActions = this.mockMvc.perform(
                get(url).accept(MediaType.parseMediaType("application/json;charset=UTF-8")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE));

        return resultActions;
    }

    protected ResultActions getJson(final String url) throws Exception {
        return getJson(url, "v1");
    }

    protected ResultActions getJson(final String url, final String apiVersion) throws Exception {
        final ResultActions resultActions = this.mockMvc.perform(
                get("/api/" + apiVersion + "/" + url).accept(MediaType.parseMediaType("application/json;charset=UTF-8")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        return resultActions;
    }

    protected ResultActions getGeoJson(final String url) throws Exception {
        final ResultActions resultActions = this.mockMvc.perform(
                get("/api/v1" + url).accept(MediaType.parseMediaType("application/vnd.geo+json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").exists());
//                .andExpect(content().contentType("application/vnd.geo+json")); // does not work for some reason

        return resultActions;
    }

    protected void assertLength(final String url, final int length) throws Exception {
        final ResultActions r1 = getJson(url);
        r1.andExpect(jsonPath("$.length()").value(length));
    }

    protected void assertException(final String url, final String exception) throws Exception {
        getJson(url).andExpect(jsonPath("$.code").value(exception));
    }
}
