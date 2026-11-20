package com.joshfouchey.smsarchive.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/{id}")
        String get(@PathVariable Long id, @RequestParam String query) { return "ok"; }

        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        String post(@RequestBody Map<String, Object> body) { return "ok"; }

        @GetMapping("/probe/boom")
        String boom() { throw new IllegalStateException("internal detail"); }

        @GetMapping("/probe/unavailable")
        String unavailable() { throw new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE); }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void missingRequestParam_returns400WithParamName() throws Exception {
        mvc.perform(get("/probe/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("query")))
                .andExpect(jsonPath("$.path").value("/probe/1"));
    }

    @Test
    void pathVariableTypeMismatch_returns400() throws Exception {
        mvc.perform(get("/probe/abc").param("query", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void unsupportedContentType_returns415() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void wrongMethod_returns405WithAllowHeader() throws Exception {
        mvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    void unexpectedException_stillReturns500WithoutInternalDetail() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void frameworkServerError_isNotTreatedAsClientError() throws Exception {
        mvc.perform(get("/probe/unavailable"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
