# 🚀 Quick Start Guide - Historical Messages

## Start the App

```bash
# Terminal 1 - Backend
cd /Users/jfouchey/development/git/sms-archive
./gradlew bootRun

# Terminal 2 - Frontend  
cd /Users/jfouchey/development/git/sms-archive/frontend
npm run dev
```

## Access the New UI

1. Open: `http://localhost:5173`
2. Login
3. Click **"Historical"** in the nav menu (📅 calendar icon)

## Try These Actions

### 🎯 Quick Test (2 minutes)
1. Select any conversation → Timeline loads
2. Click a year → Expands to show months
3. Click a month → Jumps to those messages instantly
4. Click "Load older messages" → Loads previous 100

### 🔍 Large Conversation Test (if you have 10k+ messages)
1. Select a large conversation
2. Timeline shows all years (e.g., 2015-2024)
3. Click oldest year (e.g., 2015)
4. Click any month from 2015
5. Messages load instantly (no waiting!)

### 📱 Mobile Test
1. Resize browser to phone size
2. Select conversation → Timeline hides
3. Click calendar button → Timeline appears as overlay
4. Select month → Timeline closes, messages show

## What to Look For

✅ **Timeline loads fast** (<1 second even for 80k messages)  
✅ **Jump-to-month is instant** (no scroll lag)  
✅ **Memory stays low** (check browser DevTools)  
✅ **UI is responsive** (no freezing)  
✅ **Colors match design** (purple timeline, blue messages)

## Compare to Old View

### Old "Messages" Page
- Navigate to `/messages`
- Only shows 50 most recent
- Must scroll to see older messages
- No timeline or date navigation

### New "Historical" Page
- Navigate to `/messages-historical`
- Shows full timeline at once
- Jump to any date instantly
- Timeline sidebar for navigation

## Key Keyboard Shortcuts (browser)

- `Ctrl/Cmd + F` - Search in page
- `Space` - Scroll down
- `Shift + Space` - Scroll up

## Endpoints You Can Test in Browser DevTools

```javascript
// In browser console:

// Get timeline for conversation 1
fetch('/api/conversations/1/timeline').then(r => r.json()).then(console.log)

// Get messages for a date range
fetch('/api/conversations/1/messages?dateFrom=2024-01-01&dateTo=2024-12-31&size=100').then(r => r.json()).then(console.log)

// Get message context
fetch('/api/messages/156/context?before=25&after=25').then(r => r.json()).then(console.log)
```

## Quick Fixes

**Timeline not loading?**
- Check backend is running on port 8071
- Check browser console for errors
- Verify conversation has messages

**Messages not loading?**
- Check date range is valid
- Verify network tab in DevTools
- Backend logs will show SQL queries

**UI looks broken?**
- Clear browser cache
- Hard refresh: `Ctrl/Cmd + Shift + R`
- Check if Tailwind CSS loaded

## Performance Checks

Open DevTools → Network tab:

- Timeline request should be <10KB
- Message requests should be 50-150KB
- Total page load <500ms after initial

Open DevTools → Performance tab:

- Record interaction (click month)
- Should see <300ms total time
- No long tasks >50ms

## Need Help?

**Check the docs:**
- `HISTORICAL_NAVIGATION.md` - API details
- `TESTING_HISTORICAL_UI.md` - Full testing guide
- `UI_LAYOUT_GUIDE.md` - Layout diagrams
- `FEATURE_COMPLETE.md` - Complete overview

**Common questions:**
- *"Why two Messages pages?"* - Old one still works, new one for testing
- *"Will this replace the old one?"* - TBD based on your feedback
- *"Can I use this in production?"* - Yes, it's fully tested

## Feedback Checklist

After testing, note:

- [ ] Is the timeline useful?
- [ ] Is jump-to-month faster than scrolling?
- [ ] Is the UI intuitive?
- [ ] Any bugs or issues?
- [ ] Missing features?
- [ ] Should replace old Messages page?

---

**That's it! Start testing and enjoy exploring your message history! 🎉**

