#!/bin/bash
# Test script to verify HTTP compression is working

echo "=== Testing HTTP Compression ==="
echo ""

# Replace with your actual server URL
SERVER_URL="${1:-http://localhost:8080}"
TEST_ENDPOINT="/api/conversations"

echo "Testing endpoint: $SERVER_URL$TEST_ENDPOINT"
echo ""

# Test WITHOUT compression
echo "1. Request WITHOUT Accept-Encoding header:"
RESPONSE1=$(curl -s -w "\n%{size_download}" -o /dev/null "$SERVER_URL$TEST_ENDPOINT" 2>/dev/null || echo "0")
SIZE1=$(echo "$RESPONSE1" | tail -1)
echo "   Response size: $SIZE1 bytes"
echo ""

# Test WITH compression
echo "2. Request WITH Accept-Encoding: gzip header:"
RESPONSE2=$(curl -s -H "Accept-Encoding: gzip" -w "\n%{size_download}" -o /dev/null "$SERVER_URL$TEST_ENDPOINT" 2>/dev/null || echo "0")
SIZE2=$(echo "$RESPONSE2" | tail -1)
echo "   Response size: $SIZE2 bytes (compressed)"
echo ""

# Calculate savings
if [ "$SIZE1" -gt 0 ] && [ "$SIZE2" -gt 0 ]; then
    SAVINGS=$((100 - (SIZE2 * 100 / SIZE1)))
    echo "✓ Compression working!"
    echo "  Reduction: $SAVINGS%"
    echo "  Saved: $((SIZE1 - SIZE2)) bytes"
else
    echo "✗ Could not connect to server or endpoint not found"
    echo "  Make sure the server is running at: $SERVER_URL"
fi
echo ""
echo "To test on your server:"
echo "  ./scripts/test-compression.sh https://your-server.com"
