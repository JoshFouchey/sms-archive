package com.joshfouchey.smsarchive.dto;

import java.util.List;

public class ConversationTimelineDto {
    private long conversationId;
    private List<YearBucket> years;

    public ConversationTimelineDto() {}

    public ConversationTimelineDto(long conversationId, List<YearBucket> years) {
        this.conversationId = conversationId;
        this.years = years;
    }

    public long getConversationId() {
        return conversationId;
    }

    public void setConversationId(long conversationId) {
        this.conversationId = conversationId;
    }

    public List<YearBucket> getYears() {
        return years;
    }

    public void setYears(List<YearBucket> years) {
        this.years = years;
    }

    public static class YearBucket {
        private int year;
        private long count;
        private List<MonthBucket> months;

        public YearBucket() {}

        public YearBucket(int year, long count, List<MonthBucket> months) {
            this.year = year;
            this.count = count;
            this.months = months;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public List<MonthBucket> getMonths() {
            return months;
        }

        public void setMonths(List<MonthBucket> months) {
            this.months = months;
        }
    }

    public static class MonthBucket {
        private int year;
        private int month; // 1-12
        private long count;
        private Long firstMessageId;
        private Long lastMessageId;

        public MonthBucket() {}

        public MonthBucket(int year, int month, long count, Long firstMessageId, Long lastMessageId) {
            this.year = year;
            this.month = month;
            this.count = count;
            this.firstMessageId = firstMessageId;
            this.lastMessageId = lastMessageId;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public Long getFirstMessageId() {
            return firstMessageId;
        }

        public void setFirstMessageId(Long firstMessageId) {
            this.firstMessageId = firstMessageId;
        }

        public Long getLastMessageId() {
            return lastMessageId;
        }

        public void setLastMessageId(Long lastMessageId) {
            this.lastMessageId = lastMessageId;
        }
    }
}

