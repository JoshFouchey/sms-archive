# Import Directory Feature

## How It Works

1. The application monitors subdirectories under `import-drop/`
2. Each subdirectory name is matched against existing usernames
3. At configurable intervals (default: 5 minutes), it scans for `.xml` files in valid user directories
4. When a new file is detected (and old enough), it automatically starts an import **for that specific user**
5. After processing starts, the file is either deleted or moved to that user's `processed` subdirectory
## Directory Structure

1. The application monitors a designated directory for new XML files
2. At configurable intervals (default: 5 minutes), it scans for `.xml` files
3. When a new file is detected (and old enough), it automatically starts an import
4. After processing starts, the file is either deleted or moved to a `processed` subdirectory
5. Old processed files are automatically cleaned up based on retention settings

## Configuration

### Enable the Feature

In your `.env` file, set:
```bash
SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=true
```

### All Configuration Options

```bash
# Enable/disable the feature
SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=true
IMPORT_DROP_HOST_PATH=/opt/sms-archive/import-drop
# Directory to monitor (host path)
IMPORT_DROP_HOST_PATH=/path/to/your/import-drop

# How often to scan for files (in seconds)
# Default: 300 (5 minutes)
# Examples: 600 (10 min), 3600 (1 hour), 86400 (1 day)
SMSARCHIVE_IMPORT_DIRECTORY_SCAN_INTERVAL=300

# Delete files after import starts (true) or move to processed/ (false)
# Set to true if you don't want to keep the original files
SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=true

# Days to keep files in processed/ before auto-cleanup (only if deleteAfterImport=false)
# Set to 0 to never auto-delete
SMSARCHIVE_IMPORT_DIRECTORY_RETENTION_DAYS=7

# Minimum file age before processing (seconds)
# Files must exist for at least this long before being processed
# This ensures the file is fully copied/uploaded
SMSARCHIVE_IMPORT_DIRECTORY_FILE_AGE_THRESHOLD=30
```

### Recommended Settings by Use Case

#### Infrequent Use (Once a Month or Less)
```bash
SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=true
SMSARCHIVE_IMPORT_DIRECTORY_SCAN_INTERVAL=3600     # Check every hour
SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=true  # Don't keep files
SMSARCHIVE_IMPORT_DIRECTORY_FILE_AGE_THRESHOLD=60  # Wait 1 minute
```

#### Occasional Use (Weekly)
```bash
SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=true
SMSARCHIVE_IMPORT_DIRECTORY_SCAN_INTERVAL=600      # Check every 10 minutes
SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=true
SMSARCHIVE_IMPORT_DIRECTORY_FILE_AGE_THRESHOLD=30
```

#### Frequent Use with Archival
```bash
SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=true
SMSARCHIVE_IMPORT_DIRECTORY_SCAN_INTERVAL=300      # Check every 5 minutes
SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=false  # Keep in processed/
SMSARCHIVE_IMPORT_DIRECTORY_RETENTION_DAYS=30      # Keep for 30 days
SMSARCHIVE_IMPORT_DIRECTORY_FILE_AGE_THRESHOLD=30
```

### Docker Setup

The directory is automatically mounted in docker-compose.yml:
- Host path: `${IMPORT_DROP_HOST_PATH}`
- Container path: `/app/import-drop`

### 1. Create Your User Directory
- Files must exist for at least this duration before being considered ready
First, create a subdirectory named exactly as your username in the SMS Archive:
- No manual cleanup needed
### Pause Scanning
# Replace 'your-username' with your actual SMS Archive username
mkdir -p /opt/sms-archive/import-drop/your-username
```bash
curl -X POST http://localhost:8070/api/import-directory/pause
**Important:** The directory name must match your username exactly (case-sensitive).

### 2. Drop XML Files
- Temporarily disable scanning during maintenance
Copy your XML export files into your user directory:


# Local copy
cp ~/Downloads/sms-export.xml /opt/sms-archive/import-drop/your-username/

# Or for multiple files
cp ~/Downloads/*.xml /opt/sms-archive/import-drop/your-username/
```bash
cp ~/Downloads/sms-export-large.xml /path/to/import-drop/
```

Or use `scp` for remote servers:
scp sms-export-large.xml user@server:/opt/sms-archive/import-drop/your-username/
```bash
scp sms-export-large.xml user@server:/path/to/import-drop/
### 3. Monitor Progress

The application will automatically detect and import your files:
- ✅ Pause/resume via API
- ✅ Manual trigger for immediate processing
# Watch all import activity
docker-compose logs -f app | grep -i import

# Watch activity for specific user
docker-compose logs -f app | grep your-username
```bash
docker-compose logs -f app
```

Starting automatic import of file: /app/import-drop/alice/backup.xml for user: alice (size: 125 MB)
Import job 12345678-abcd-... started for user: alice, file: backup.xml
Deleted backup.xml after starting import for user: alice
Import job 12345678-abcd-... started for file: sms-export.xml
Moved sms-export.xml to processed directory
### 4. Check Import Status

### 5. Check Import Status

You can monitor the import progress using the existing API:

```bash
curl http://localhost:8070/api/import/progress/{jobId}
```

The job ID is logged when the import starts.

## File Stability Check

To prevent processing files that are still being uploaded/copied, the watcher:
- Checks if the file size is stable (unchanged for 5 seconds)
- Skips files that are currently growing
- Retries on the next scan (30 seconds later)

## Processed Files

After an import starts, files are moved to `import-drop/processed/`:
- Original filename is preserved
- If a file with the same name exists, a timestamp is appended
- This prevents reprocessing the same file
- You can safely delete files from `processed/` to free up space

## Troubleshooting

### Files aren't being processed

1. Check if the feature is enabled:
   ```bash
   docker-compose exec app env | grep IMPORT_DIRECTORY
   ```

2. Check the logs:
   ```bash
   docker-compose logs app | grep -i import
   ```

3. Verify the directory exists and is writable:
   ```bash
   docker-compose exec app ls -la /app/import-drop
   ```

### Permission issues

The app runs as user `10110:10110`. Ensure the host directory has proper permissions:

```bash
chown -R 10110:10110 /path/to/import-drop
chmod 755 /path/to/import-drop
```

## Disabling the Feature

To disable the import directory watcher:

1. Set in `.env`:
   ```bash
   SMSARCHIVE_IMPORT_DIRECTORY_ENABLED=false
   ```

2. Restart the application:
   ```bash
   docker-compose restart app
   ```

## Example Workflow

```bash
# 1. Enable the feature (if not already enabled)
## API Control
# 6. Clean up processed files (optional, if retention enabled)
You can control the import directory watcher via REST API:

### Get Status
```bash
curl http://localhost:8070/api/import-directory/status
```

Response:
```json
{
  "enabled": true,
  "status": "Scanning: enabled, Directory: /app/import-drop, Interval: 300s, Files in progress: 0"
}
```

### Pause Scanning
Temporarily stop scanning without disabling the feature:
```bash
curl -X POST http://localhost:8070/api/import-directory/pause
```
   ```
This is useful if you want to:
- Prepare multiple files without triggering imports
- Temporarily disable scanning during maintenance
- Reduce system load

### Resume Scanning
```bash
curl -X POST http://localhost:8070/api/import-directory/resume
```
   ```
### Trigger Immediate Scan
Force a scan without waiting for the next interval:
   ```
curl -X POST http://localhost:8070/api/import-directory/scan-now

This means the directory name doesn't match any username in the database:
This is useful after dropping a file if you don't want to wait for the next scheduled scan.
```bash
## File Handling
# Then ensure directory name matches exactly (case-sensitive)
### File Age Threshold

To prevent processing files that are still being uploaded/copied, the watcher:
- Only processes files older than the configured threshold (default: 30 seconds)
- Files must exist for at least this duration before being considered ready
- More reliable than checking file size stability

### Delete After Import (Recommended)

If `SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=true`:
- Files are **deleted immediately** after import starts
- Saves disk space
- No manual cleanup needed
- **Recommended for most users**
# You should see output like:
### Keep Processed Files
#   - Structure: Each user has subdirectory named by username
If `SMSARCHIVE_IMPORT_DIRECTORY_DELETE_AFTER_IMPORT=false`:
- Files are moved to `import-drop/username/processed/` subdirectory
- Original filename is preserved
- If a file with the same name exists, a timestamp is appended
- Prevents reprocessing the same file
- Automatic cleanup based on `SMSARCHIVE_IMPORT_DIRECTORY_RETENTION_DAYS`
curl -X POST http://localhost:8070/api/import-directory/scan-now
### Automatic Cleanup
# 5. Clean up processed files (optional)
When keeping processed files, old files are automatically deleted:
- Runs daily at 3:00 AM
- Deletes files older than the retention period
- Set `SMSARCHIVE_IMPORT_DIRECTORY_RETENTION_DAYS=0` to disable auto-cleanup
- You can manually delete files anytime from `processed/`

## Security Considerations

- **User Isolation:** Each user can only import to their own account via their username directory

- **Username Verification:** Invalid usernames (directories not matching existing users) are skipped and logged
- **Server Access Required:** Users must have SSH/file system access to drop files
- ✅ No reverse proxy size limits
- ✅ Can use `rsync`, `scp`, or direct server access
## Troubleshooting
- ✅ No need to configure Nginx Proxy Manager

## Security Considerations

- The import directory is only accessible via server file system access
- Users must have SSH/file system access to the server
- Files are processed using the same import service as web uploads
- The directory should NOT be exposed via web server


   ls -la /opt/sms-archive/import-drop/
