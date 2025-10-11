package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.service.MediaService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(MediaController.class)
class MediaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MediaService mediaService;

    @Test
    void getImages_handlesNullAndNormalizesPaths() throws Exception {
        // Message 1 with null filePath
        Message msg1 = new Message();
        msg1.setId(10L);
        msg1.setProtocol(MessageProtocol.SMS);
        msg1.setDirection(MessageDirection.INBOUND);
        msg1.setSender("+15551234567");
        msg1.setRecipient("me");
        msg1.setTimestamp(Instant.now());

        MessagePart part1 = new MessagePart();
        part1.setId(100L);
        part1.setMessage(msg1);
        part1.setContentType("image/jpeg");
        part1.setFilePath(null); // triggers null branch

        // Message 2 with backslash filePath
        Message msg2 = new Message();
        msg2.setId(11L);
        msg2.setProtocol(MessageProtocol.MMS);
        msg2.setDirection(MessageDirection.OUTBOUND);
        msg2.setSender("me");
        msg2.setRecipient("+15557654321");
        msg2.setTimestamp(Instant.now());

        MessagePart part2 = new MessagePart();
        part2.setId(101L);
        part2.setMessage(msg2);
        part2.setContentType("image/png");
        part2.setFilePath("media\\messages\\uuid\\part0.png");

        Mockito.when(mediaService.getImages(isNull(), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(List.of(part1, part2)));

        mockMvc.perform(get("/api/media/images"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].filePath").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(101L))
                .andExpect(jsonPath("$[1].filePath").value("media/messages/uuid/part0.png"));
    }
}
