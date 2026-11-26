package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.joshfouchey.smsarchive.service.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Imports run inline in the test profile, so the job has finished when the upload request returns. */
@SpringBootTest
@AutoConfigureMockMvc
class ImportUploadTempFileTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    String token;

    @BeforeEach
    void setup() throws Exception {
        String username = "upload" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(body).get("accessToken").asString();
    }

    private static Set<Path> uploadTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("sms-stream"))
                    .collect(Collectors.toSet());
        }
    }

    private String upload(byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "export.xml", "text/xml", content);
        String body = mvc.perform(multipart("/import/stream").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("jobId").asString();
    }

    @Test
    void completedImport_deletesUploadTempFile() throws Exception {
        Set<Path> before = uploadTempFiles();
        String jobId = upload(new ClassPathResource("test-messages.xml").getContentAsByteArray());

        mvc.perform(get("/import/progress/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(uploadTempFiles()).isSubsetOf(before);
    }

    @Test
    void failedImport_deletesUploadTempFile() throws Exception {
        Set<Path> before = uploadTempFiles();
        String jobId = upload("<smses><sms address=".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/import/progress/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(uploadTempFiles()).isSubsetOf(before);
    }

    @Test
    void importStartFailure_deletesUploadTempFile() throws Exception {
        ImportService importService = mock(ImportService.class);
        AtomicReference<Path> received = new AtomicReference<>();
        when(importService.startImportAsync(any(Path.class), any())).thenAnswer(inv -> {
            received.set(inv.getArgument(0));
            throw new IllegalStateException("boom");
        });
        ImportController controller = new ImportController(importService);

        assertThatThrownBy(() -> controller.uploadStream(new MockMultipartFile("file", "x.xml", "text/xml", new byte[]{1})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(received.get()).isNotNull().doesNotExist();
    }
}
