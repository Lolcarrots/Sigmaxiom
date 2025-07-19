#!/bin/bash

if [ $# -eq 0 ]; then
    echo "Error: No virtual environment path provided" >&2
    echo "Usage: $0 <path_to_virtual_environment>" >&2
    exit 1
fi

VENV_PATH="$1"

if [ ! -d "$VENV_PATH" ]; then
    echo "Error: Virtual environment not found at: $VENV_PATH" >&2
    exit 1
fi

if [ ! -f "$VENV_PATH/bin/activate" ]; then
    echo "Error: No activate script found. Is this a valid virtual environment?" >&2
    exit 1
fi

source "$VENV_PATH/bin/activate"

echo "Using Python: $(which python)"
echo "Python version: $(python --version)"
echo "Working directory: $(pwd)"
echo "Virtual environment: $VENV_PATH"

pip install -q ipykernel

WORK_DIR="$(pwd)"

CONNECTION_FILE="$WORK_DIR/kernel-$(uuidgen).json"
echo "Connection file: $CONNECTION_FILE"

(python -m ipykernel_launcher --ip=127.0.0.1 --f="$CONNECTION_FILE" > /dev/null 2>&1 &)
KERNEL_PID=$!
echo "KERNEL_PID=$KERNEL_PID"

echo "Waiting for connection file..."
timeout_seconds=15
end_time=$((SECONDS + timeout_seconds))

while [ ! -f "$CONNECTION_FILE" ]; do
    if [ $SECONDS -gt $end_time ]; then
        echo "Error: Timeout waiting for connection file: $CONNECTION_FILE" >&2
        if ps -p $KERNEL_PID > /dev/null; then
            kill $KERNEL_PID
        fi
        exit 1
    fi
    sleep 0.2
done

echo "Connection file created successfully"

echo "KERNEL_CONNECTION_FILE=$CONNECTION_FILE"

exit 0