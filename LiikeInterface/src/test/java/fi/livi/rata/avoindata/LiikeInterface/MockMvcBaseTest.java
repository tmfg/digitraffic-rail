package fi.livi.rata.avoindata.LiikeInterface;

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

    protected ResultActions getJson(URI url) throws Exception {
        final ResultActions resultActions = this.mockMvc.perform(
                get(url).accept(MediaType.parseMediaType("application/json;charset=UTF-8"))).andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"));

        return resultActions;
    }

    protected ResultActions getJson(String url) throws Exception {
        final ResultActions resultActions = this.mockMvc.perform(
                get( url).accept(MediaType.parseMediaType("application/json;charset=UTF-8"))).andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"));

        return resultActions;
    }

    protected void assertLength(String url, int length) throws Exception {
        final ResultActions r1 = getJson(url);
        r1.andExpect(jsonPath("$.length()").value(length));
    }

    protected void assertException(String url, String exception) throws Exception {
        getJson(url).andExpect(jsonPath("$.code").value(exception));
    }
}
