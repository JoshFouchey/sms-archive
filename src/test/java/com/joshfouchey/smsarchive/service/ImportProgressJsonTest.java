package com.joshfouchey.smsarchive.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportProgressJsonTest {

    @Test
    void progressJsonIncludesLiveCounters() {
        ImportService.ImportProgress progress = new ImportService.ImportProgress(UUID.randomUUID(), 1000);
        progress.setBytesRead(450);
        progress.incProcessedMessages();
        progress.incProcessedMessages();
        progress.incProcessedMessages();
        progress.incImportedMessages();
        progress.incImportedMessages();
        progress.incDuplicateMessages();

        JsonNode json = JsonMapper.builder().build().valueToTree(progress);

        assertThat(json.path("bytesRead").asLong(-1)).isEqualTo(450);
        assertThat(json.path("processedMessages").asLong(-1)).isEqualTo(3);
        assertThat(json.path("importedMessages").asLong(-1)).isEqualTo(2);
        assertThat(json.path("duplicateMessages").asLong(-1)).isEqualTo(1);
        assertThat(json.path("percentBytes").asInt(-1)).isEqualTo(45);
        assertThat(json.has("duplicateMessagesAtomic")).isFalse();
    }
}
