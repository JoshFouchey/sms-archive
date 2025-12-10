# Historical Conversation Navigation - Backend Implementation

## Summary
Backend APIs to support historical exploration of conversations with 80k+ messages without loading everything at once.

## What Was Built

### 1. Timeline Index API
**Endpoint:** `GET /api/conversations/{conversationId}/timeline`

Returns a hierarchical year/month structure with:
- Message counts per year and month
- First/last message ID and timestamp per month
- Enables quick jumps to any time period

**Response Example:**
```json
{
  "conversationId": 1,
  "years": [
    {
      "year": 2017,
      "count": 1250,
      "months": [
        {
          "month": 7,
          "count": 42,
          "firstMessageId": 118,
          "lastMessageId": 156,
          "firstMessageTimestamp": "2017-07-06T18:15:52.811Z",
          "lastMessageTimestamp": "2017-07-06T20:09:27.030Z"
        }
      ]
    }
  ]
}
```

### 2. Date-Range Message Query
**Endpoint:** `GET /api/conversations/{conversationId}/messages?dateFrom=&dateTo=&page=&size=&sort=`

Fetch messages within a specific date range:
- `dateFrom` and `dateTo` accept ISO timestamps or dates (e.g., `2017-07-01`)
- Standard pagination (page/size/sort)
- If no date range provided, behaves like the existing endpoint

### 3. Message Context API (Already Implemented)
**Endpoint:** `GET /api/messages/{messageId}/context?before=25&after=25`

Fetch messages before/after a specific message for contextual viewing.

## Backend Files Created/Modified

### Created:
- `ConversationTimelineDto.java` - DTO for timeline response with nested year/month buckets

### Modified:
- `MessageRepository.java` - Added queries:
  - `getConversationTimeline()` - Native SQL to group by year/month
  - `findByConversationAndDateRange()` - JPA query for date-filtered messages
  
- `ConversationService.java` - Added methods:
  - `getConversationTimeline()` - Build timeline DTO from repository data
  - `getConversationMessagesByDateRange()` - Fetch messages by date with pagination
  
- `ConversationController.java` - Added endpoints:
  - `GET /{conversationId}/timeline` - Timeline index
  - Enhanced `GET /{conversationId}/messages` to accept optional `dateFrom`/`dateTo` params

## Frontend TypeScript API

Added to `frontend/src/services/api.ts`:

### Types:
```typescript
export interface ConversationTimeline {
  conversationId: number;
  years: YearBucket[];
}

export interface YearBucket {
  year: number;
  count: number;
  months: MonthBucket[];
}

export interface MonthBucket {
  month: number;
  count: number;
  firstMessageId: number | null;
  lastMessageId: number | null;
  firstMessageTimestamp: string | null;
  lastMessageTimestamp: string | null;
}
```

### Functions:
```typescript
// Fetch timeline index for a conversation
getConversationTimeline(conversationId: number): Promise<ConversationTimeline>

// Fetch messages by date range
getConversationMessagesByDateRange(
  conversationId: number,
  dateFrom: string | null,
  dateTo: string | null,
  page?: number,
  size?: number,
  sort?: "asc" | "desc"
): Promise<PagedResponse<Message>>
```

## How to Use

### 1. Load Timeline Index on Conversation Open
```typescript
const timeline = await getConversationTimeline(conversationId);
// Use timeline.years to build a sidebar navigation
```

### 2. Jump to a Specific Month
```typescript
const month = timeline.years[0].months[0];
const messages = await getConversationMessagesByDateRange(
  conversationId,
  month.firstMessageTimestamp,
  month.lastMessageTimestamp,
  0, // page
  100, // size
  "asc" // chronological
);
```

### 3. Jump to a Specific Message (from search)
```typescript
const context = await getMessageContext(messageId, 25, 25);
// Show context.before (reversed), context.center (highlighted), context.after
// Link to full conversation: router.push(`/conversations/${context.conversationId}?at=${messageId}`)
```

## Performance Characteristics

### Timeline Index Query
- Single aggregation query grouping by year/month
- Fast even with 80k+ messages (uses timestamp index)
- Small response size (typically < 5KB for years of data)

### Date-Range Query
- Uses indexed `conversation_id` + `timestamp` filter
- Returns paginated results (default 50-100 messages)
- Efficient for any date range

### Memory Usage
- Timeline: ~1KB per year in memory
- Date-range page: ~50-150KB per 100 messages
- No need to load entire conversation

## Next Steps (Frontend UI)

1. **Sidebar Timeline Navigator**
   - Collapsible year/month tree
   - Message counts per bucket
   - Click to jump to that month

2. **Virtual Scroller**
   - Render only visible messages
   - Bidirectional infinite scroll
   - Keep 3-5 pages in memory max

3. **URL State**
   - `/conversations/:id?at=messageId` - jump to specific message
   - `/conversations/:id?date=2017-07` - jump to specific month

4. **Sticky Date Headers**
   - Show current visible date as you scroll
   - Jump-to-date picker

## Build Status
✅ Backend compiles successfully
✅ Frontend compiles successfully
✅ Ready for branch testing

