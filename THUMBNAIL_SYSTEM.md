# Thumbnail Generation System

## Overview
The thumbnail generation system has been refactored to support both import-time thumbnail creation and on-demand rebuilding of thumbnails for existing media.

## Architecture

### Components

#### 1. ThumbnailService
**Location:** `src/main/java/com/joshfouchey/smsarchive/service/ThumbnailService.java`

Core service responsible for thumbnail generation logic:
- **Supported formats:** JPEG, JPG, PNG, GIF, BMP
- **Unsupported formats with placeholder:** HEIC, HEIF
- **Thumbnail specs:** 400x400px, JPEG format, 80% quality
- **Methods:**
  - `createThumbnail(Path original, Path thumbDest, String contentType, boolean force)` - Main thumbnail creation
  - `createUnsupportedPlaceholder(Path thumbDest, String label)` - Generates placeholder for unsupported formats
  - `deriveThumbnailPath(Path originalPath, int seq)` - Derives thumbnail path from original
  - `isSupported(String contentType)` - Checks if format is supported
  - `isUnsupportedNeedsPlaceholder(String contentType)` - Checks if format needs placeholder

#### 2. ThumbnailJobProgress
**Location:** `src/main/java/com/joshfouchey/smsarchive/service/ThumbnailJobProgress.java`

Thread-safe progress tracking object:
- **Fields:**
  - `id` - UUID job identifier
  - `status` - PENDING, RUNNING, COMPLETED, FAILED
  - `totalParts` - Total number of parts to process
  - `processedParts` - Number of parts processed
  - `regeneratedThumbnails` - Count of regenerated thumbnails
  - `skippedThumbnails` - Count of skipped thumbnails
  - `errors` - List of error messages
  - `startedAt` / `finishedAt` - Timestamps

#### 3. ThumbnailRebuildJobService
**Location:** `src/main/java/com/joshfouchey/smsarchive/service/ThumbnailRebuildJobService.java`

Manages thumbnail rebuild jobs:
- Async job execution (uses existing `importTaskExecutor` or falls back to thread)
- Per-user scoping (respects authenticated user)
- Optional contact filtering
- Force regeneration option
- Progress tracking via UUID

**Key Methods:**
- `startJob(Long contactId, boolean force, int batchSize, boolean async)` - Start a rebuild job
- `getJob(UUID jobId)` - Get job progress

#### 4. MediaJobController
**Location:** `src/main/java/com/joshfouchey/smsarchive/controller/MediaJobController.java`

REST API endpoints for job management:
- `POST /api/media/jobs/rebuild-thumbnails` - Start a rebuild job
- `GET /api/media/jobs/{id}` - Get job status and progress

#### 5. MessagePartRepository (Enhanced)
**Location:** `src/main/java/com/joshfouchey/smsarchive/repository/MessagePartRepository.java`

New queries added:
- `findAllImagePartsByUser(User)` - Get all image parts for a user
- `findImagePartsByContactId(Long, User)` - Get image parts filtered by contact

#### 6. ImportService (Modified)
**Location:** `src/main/java/com/joshfouchey/smsarchive/service/ImportService.java`

**Changes:**
- Delegates thumbnail creation to `ThumbnailService`
- Removed inline thumbnail logic
- Removed `createUnsupportedThumbnail` method
- Removed `SUPPORTED_THUMB_TYPES` and `UNSUPPORTED_IMAGE_TYPES` constants

## API Usage

### Start a Thumbnail Rebuild Job

**Endpoint:** `POST /api/media/jobs/rebuild-thumbnails`

**Query Parameters:**
- `contactId` (optional) - Filter by contact ID
- `force` (default: false) - Regenerate existing thumbnails
- `batchSize` (default: 200) - Informational batch size
- `async` (default: true) - Run asynchronously

**Request Example:**
```bash
# Rebuild all thumbnails for all contacts
curl -X POST "http://localhost:8080/api/media/jobs/rebuild-thumbnails?async=true&force=false" \
  -H "Authorization: Bearer <token>"

# Rebuild thumbnails for a specific contact, forcing regeneration
curl -X POST "http://localhost:8080/api/media/jobs/rebuild-thumbnails?contactId=123&force=true" \
  -H "Authorization: Bearer <token>"
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

### Check Job Status

**Endpoint:** `GET /api/media/jobs/{id}`

**Request Example:**
```bash
curl "http://localhost:8080/api/media/jobs/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <token>"
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "percentComplete": 45.5,
  "totalParts": 1000,
  "processedParts": 455,
  "regeneratedThumbnails": 400,
  "skippedThumbnails": 55,
  "errorCount": 0,
  "errors": [],
  "startedAt": "2025-10-25T10:30:00Z",
  "finishedAt": null
}
```

**Status Values:**
- `PENDING` - Job queued but not started
- `RUNNING` - Job in progress
- `COMPLETED` - Job finished successfully
- `FAILED` - Job encountered fatal error

## Error Handling

### Edge Cases Handled:
1. **Missing original file** - Records error, continues processing
2. **Unreadable file** - Records error, skips
3. **Unsupported format** - Generates placeholder if HEIC/HEIF
4. **Existing thumbnail** - Skips unless `force=true`
5. **Concurrent processing** - Thread-safe progress updates

### Error Tracking:
Errors are accumulated in `ThumbnailJobProgress.errors` list:
```json
{
  "errors": [
    "partId=123: Original file not found: /path/to/missing.jpg",
    "partId=456: Original file not readable: /path/to/locked.png"
  ]
}
```

## Testing

### Test Files Created:

1. **ThumbnailServiceTest**
   - Basic functionality tests implemented
   - Content type detection
   - Path derivation
   - TODO: Integration tests with actual image files

2. **ThumbnailJobProgressTest**
   - Progress tracking verification
   - Percent calculation
   - Status transitions
   - TODO: Concurrent update tests

### Running Tests:
```bash
./gradlew test --tests "com.joshfouchey.smsarchive.service.Thumbnail*"
```

## Migration Guide

### For Existing Installations:

1. **No database changes required** - Thumbnails are filesystem-based
2. **Rebuild thumbnails for existing media:**
   ```bash
   # Rebuild all thumbnails
   curl -X POST "http://localhost:8080/api/media/jobs/rebuild-thumbnails?force=true"
   
   # Monitor progress
   curl "http://localhost:8080/api/media/jobs/{jobId}"
   ```

### New Media Path Structure:
Thumbnails are stored alongside originals:
```
media/messages/
  ├── {contactId}/
  │   ├── part0.jpg          (original)
  │   ├── part0_thumb.jpg    (thumbnail)
  │   ├── part1.png
  │   ├── part1_thumb.jpg
  │   └── ...
  └── _nocontact/            (temporary during import)
```

## Performance Considerations

- **Async by default:** Jobs run in background threads
- **User-scoped:** Each user's media processed separately
- **Batch processing:** Processes all matching parts in single job
- **Memory efficient:** Streams through database results
- **Thread-safe:** Concurrent job tracking supported

## Future Enhancements

- [ ] Resume failed jobs from last successful part
- [ ] Scheduled thumbnail maintenance jobs
- [ ] Thumbnail size/quality configuration per user
- [ ] Parallel processing within single job
- [ ] Webhook notifications on job completion
- [ ] Admin API to view all jobs across users
- [ ] Cleanup orphaned thumbnails (no original)
- [ ] Incremental rebuild (only missing thumbnails)

## Configuration

### Application Properties:
```yaml
# Existing properties used by thumbnail system
smsarchive:
  media:
    root: media/messages  # Base path for media storage
  import:
    inline: false         # Use async task executor
```

### Task Executor:
The system reuses the existing `importTaskExecutor` bean. If not configured, falls back to creating daemon threads.

## Troubleshooting

### Job stuck in PENDING status
- Check if `importTaskExecutor` bean is properly configured
- Verify thread pool has available capacity
- Check application logs for task execution errors

### Thumbnails not generated
- Verify original files exist and are readable
- Check file permissions on media directory
- Ensure supported image format (JPEG, PNG, GIF, BMP)
- Review job error list for specific failures

### High memory usage during rebuild
- Reduce number of concurrent jobs
- Process by contact ID in smaller batches
- Increase JVM heap size if needed

### 404 on job status endpoint
- Job ID may be invalid or expired
- Jobs are stored in memory (lost on restart)
- Future: Consider persisting job state to database

## Security

- **Authentication required:** All endpoints respect Spring Security configuration
- **User isolation:** Jobs only process media for authenticated user
- **No directory traversal:** Paths validated and normalized
- **Rate limiting:** Consider adding for production (not implemented)

## Dependencies

All dependencies already present in project:
- `net.coobird.thumbnailator:thumbnailator` - Thumbnail generation
- `spring-boot-starter-web` - REST endpoints
- `spring-boot-starter-data-jpa` - Repository queries
- `lombok` - Code generation

