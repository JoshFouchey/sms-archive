package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import jakarta.persistence.EntityNotFoundException;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(MediaController.class)
@Import(MediaControllerTest.StubConfig.class)
class MediaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class StubConfig {
        @Bean
        MediaService mediaService() {
            return new MediaService(null, null, null, null) { // override methods; repositories not used
                @Override
                public Page<MessagePart> getImages(Long contactId, int page, int size) {
                    if (contactId != null) {
                        throw new EntityNotFoundException("Contact not found: " + contactId);
                    }
                    Message msg1 = new Message();
                    msg1.setId(10L);
                    msg1.setProtocol(MessageProtocol.SMS);
                    msg1.setDirection(MessageDirection.INBOUND);
                    msg1.setTimestamp(Instant.now());
                    MessagePart part1 = new MessagePart();
                    part1.setId(100L);
                    part1.setMessage(msg1);
                    part1.setContentType("image/jpeg");
                    part1.setFilePath(null);

                    Message msg2 = new Message();
                    msg2.setId(11L);
                    msg2.setProtocol(MessageProtocol.MMS);
                    msg2.setDirection(MessageDirection.OUTBOUND);
                    msg2.setTimestamp(Instant.now());
                    MessagePart part2 = new MessagePart();
                    part2.setId(101L);
                    part2.setMessage(msg2);
                    part2.setContentType("image/png");
                    part2.setFilePath("media\\messages\\uuid\\part0.png");
                    return new PageImpl<>(List.of(part1, part2));
                }
            };
        }
    }

    @Test
    @WithMockUser
    void getImages_handlesNullAndNormalizesPaths() throws Exception {
        mockMvc.perform(get("/api/media/images"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].filePath").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(101L))
                .andExpect(jsonPath("$[1].filePath").value("media/messages/uuid/part0.png"));
    }

    @Test
    @WithMockUser
    void getImages_invalidContact_returns404() throws Exception {
        mockMvc.perform(get("/api/media/images").param("contactId", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Contact not found: 9999"));
    }
}
