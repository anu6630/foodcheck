#!/bin/bash
set -e

# Change directory to backend root
cd "$(dirname "$0")/../.."

# 1. Download Karate JAR if not present
KARATE_JAR="tests/karate/karate.jar"
if [ ! -f "$KARATE_JAR" ]; then
    echo "Karate JAR not found. Downloading v1.4.1..."
    curl -L -o "$KARATE_JAR" "https://github.com/karatelabs/karate/releases/download/v1.4.1/karate-1.4.1.jar"
    echo "Karate JAR downloaded successfully."
else
    echo "Karate JAR already present."
fi

# 2. Setup mock environment variable so Gemini Vision API runs in mock mode for deterministic tests
export MOCK_GEMINI="true"
export DATABASE_URL="postgresql://postgres.mhnreqfbohczronjprzs:Plmokn%40123%24%24@aws-1-ap-northeast-2.pooler.supabase.com:5432/foodcheck"
export PYTHONPATH="."

# 3. Start FastAPI server in background
echo "Starting FastAPI server..."
.venv/bin/python src/main.py > fastapi.log 2>&1 &
SERVER_PID=$!

# Ensure server process is killed on script exit
cleanup() {
    echo "Stopping FastAPI server (PID: $SERVER_PID)..."
    kill $SERVER_PID || true
}
trap cleanup EXIT

# 4. Wait for server to become healthy
echo "Waiting for backend server to start on port 8999..."
MAX_ATTEMPTS=30
ATTEMPT=0
while ! curl -s http://localhost:8999/health >/dev/null; do
    sleep 0.5
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo "Backend server failed to start within time limit. FastAPI logs:"
        cat fastapi.log
        exit 1
    fi
done

echo "Backend server is healthy!"

# 5. Run Karate tests
echo "Executing Karate integration tests..."
TEST_STATUS=0
cd tests/karate
java -jar karate.jar features/ || TEST_STATUS=$?
cd ../..


if [ $TEST_STATUS -eq 0 ]; then
    echo "All Karate tests passed! 🎉"
else
    echo "Some Karate tests failed. Check report under target/ directory."
fi

exit $TEST_STATUS
