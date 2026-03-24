package com.joshfouchey.smsarchive.event;

import com.joshfouchey.smsarchive.model.User;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a message import completes successfully.
 * Listeners can use this to trigger post-import processing (e.g., embedding new messages).
 */
public class ImportCompletedEvent extends ApplicationEvent {

    private final User user;
    private final long importedCount;

    public ImportCompletedEvent(Object source, User user, long importedCount) {
        super(source);
        this.user = user;
        this.importedCount = importedCount;
    }

    public User getUser() { return user; }
    public long getImportedCount() { return importedCount; }
}
