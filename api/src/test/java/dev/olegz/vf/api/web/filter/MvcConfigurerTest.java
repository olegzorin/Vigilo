package dev.olegz.vf.api.web.filter;

import dev.olegz.vf.api.web.ApiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MvcConfigurerTest.TestConfig.class)
@WebAppConfiguration
class MvcConfigurerTest {
    private static final String ORIGIN = "https://client.example";

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    @Autowired
    MvcConfigurerTest(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void corsConfigurationAllowsSupportedHeadersAndRejectsRemovedHeader() throws Exception {
        MvcResult allowed = mockMvc.perform(MockMvcRequestBuilders.options("/vf/test")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, ApiHeaders.API_KEY))
            .andReturn();

        assertEquals(200, allowed.getResponse().getStatus());
        assertEquals("*", allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals(ApiHeaders.API_KEY,
            allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
        assertEquals("86400", allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        assertNull(allowed.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("no-store", allowed.getResponse().getHeader(HttpHeaders.CACHE_CONTROL));

        MvcResult rejected = mockMvc.perform(MockMvcRequestBuilders.options("/vf/test")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "PPCAuthorization"))
            .andReturn();

        assertEquals(403, rejected.getResponse().getStatus());
        assertNull(rejected.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void corsHeadersAndNoStoreAreAppliedToActualResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/vf/test")
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("*", result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("no-store", result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Configuration
    @EnableWebMvc
    @Import(MvcConfigurer.class)
    static class TestConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/vf/test")
        String get() {
            return "ok";
        }
    }
}
