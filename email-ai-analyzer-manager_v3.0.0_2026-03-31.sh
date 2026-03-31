#!/bin/bash

# =============================================================================
# Email AI Analyzer - Docker Management Script
# =============================================================================
# Version: 3.0.0
# Created: 2026-03-31
# Author: Eyuce
# License: MIT
# Repository: https://github.com/eyuce/email-ai-analyzer
# =============================================================================
#
# DESCRIPTION:
#   Interactive menu-driven script for managing Email AI Analyzer application,
#   Ollama models, and MySQL database operations via Docker.
#
# FEATURES:
#   - Email AI Analyzer container management (start/stop/restart/logs)
#   - Ollama model management (list/download/load/unload GPU models)
#   - MySQL database operations (connect/query/browse tables)
#   - Multi-environment support (local/dev/prod)
#   - Secure credential handling via environment variables
#
# USAGE:
#   1. Copy this script to your project directory
#   2. Set environment variables in .env file or export them
#   3. Run: ./email-ai-analyzer-manager.sh
#
# REQUIRED ENVIRONMENT VARIABLES:
#   - DB_PASSWORD: Database password (change default: <change_me>)
#   - SECURITY_PASS: Application login password (change default: <change_me>)
#   - MYSQL_PASSWORD: MySQL root password (change default: <change_me>)
#   - SMTP_PASSWORD: Gmail app password for sending emails
#   - IMAP_PASSWORD: Gmail app password for receiving emails
#   - GROQ_API_KEY: Groq API key (if using Groq as LLM provider)
#
# OPTIONAL ENVIRONMENT VARIABLES:
#   - DEV_DB_PASSWORD: Dev environment DB password (default: change_me_dev)
#   - PROD_DB_PASSWORD: Prod environment DB password (default: change_me_prod)
#   - LOCAL_DB_PASSWORD: Local environment DB password (default: change_me_local)
#   - SECURITY_USER: Application login username (default: admin)
#   - MYSQL_USER: MySQL username (default: root)
#   - MYSQL_CONTAINER: MySQL container name (default: email_db)
#   - DEBUG_LOG_SECRETS: Enable debug logging (default: false)
#
# For full environment variable list, see the commented section below.
# =============================================================================
#
# CHANGELOG:
#   v3.0.0 (2026-03-31) - Security hardening for public release
#                         - Removed all hardcoded credentials
#                         - Added environment variable defaults
#                         - Added menu-submenu navigation
#                         - Added version tracking
#                         - MySQL credentials configurable
#   v2.2.0              - Added Ollama model management
#                         - Added MySQL database operations
#   v2.1.0              - Added LLM settings from database
#   v2.0.0              - Initial Docker management script
# =============================================================================
#
# IMPORTANT: Ollama/LLM settings are now loaded from database!
# - No SPRING_AI_OLLAMA_BASE_URL environment variable is used
# - Settings stored in: email_user.app_settings table
# - Fields: llm_url, llm_model, llm_temperature, system_prompt
# - Use menu options 8, 9, 10 to manage LLM settings
# =============================================================================
#
# Environment Variables (Copy to .env or export in your shell / container runtime)
# Spring Boot also reads standard SPRING_* and relaxed-binding names (EMAIL_IMAP_* etc.)
#
# Profile: local | dev | prod
# export SPRING_PROFILES_ACTIVE=dev
#
# MySQL (profile YAML uses DB_*; you can use SPRING_DATASOURCE_* instead)
# export DB_URL=jdbc:mysql://localhost:3306/email_db
# export DB_USER=email_user
# export DB_PASSWORD=<your_secure_password>
#
# Groq API (when LLM provider is groq in settings)
# export GROQ_API_KEY=<your_api_key_here>
# Optional override; default is https://api.groq.com/openai/v1
# export GROQ_API_URL=
#
# Email Credentials (Gmail App Password)
# export SMTP_PASSWORD='<your_gmail_app_password>'
# export IMAP_PASSWORD='<your_gmail_app_password>'
#
# Actuator / management UI basic auth
# export SECURITY_USER=admin
# export SECURITY_PASS=<your_secure_password>
# export SECURITY_LOG_CREDENTIALS_ON_STARTUP=true
#
# When true, logs may include plaintext secrets — debugging only
# export DEBUG_LOG_SECRETS=false
#
# Optional tuning
# export AI_TEST_CONNECT_TIMEOUT_SECONDS=10
# export AI_TEST_REQUEST_TIMEOUT_SECONDS=75
# export EMAIL_ANALYSIS_DEFAULT_DATE_RANGE_DAYS=1
# export EMAIL_ANALYSIS_DEFAULT_MAX_EMAILS=1000
# export EMAIL_IMAP_CONNECTION_TIMEOUT_MS=10000
# export EMAIL_IMAP_TIMEOUT_MS=20000
# export EMAIL_IMAP_WRITE_TIMEOUT_MS=20000
# export EMAIL_IMAP_SSL_TRUST=*
#
# Docker Compose / ops examples (optional)
# export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus,metrics
# export MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
# =============================================================================

IMAGE_NAME="guvenulu/email-ai-analyzer:latest"
CONTAINER_NAME="email-ai-app"
SCRIPT_VERSION="3.0.0"
SCRIPT_CREATED="2026-03-31"

# Load .env file if it exists
if [[ -f ".env" ]]; then
    echo "Loading environment variables from .env file..."
    set -a
    source ".env"
    set +a
fi

# Environment-specific configurations
declare -A ENV_DB_URLS
declare -A ENV_DB_USERS
declare -A ENV_DB_PASSWORDS

# Dev environment configuration (Ubuntu development)
ENV_DB_URLS["dev"]="jdbc:mysql://email_analyzer_db:3306/email_db"
ENV_DB_USERS["dev"]="email_user"
ENV_DB_PASSWORDS["dev"]="${DEV_DB_PASSWORD:-change_me_dev}"

# Prod environment configuration
ENV_DB_URLS["prod"]="jdbc:mysql://email_analyzer_db_prod:3306/email_db_prod"
ENV_DB_USERS["prod"]="email_user_prod"
ENV_DB_PASSWORDS["prod"]="${PROD_DB_PASSWORD:-change_me_prod}"

# Local environment configuration (for local development)
ENV_DB_URLS["local"]="jdbc:mysql://localhost:3306/email_db"
ENV_DB_USERS["local"]="email_user"
ENV_DB_PASSWORDS["local"]="${LOCAL_DB_PASSWORD:-change_me_local}"

# Default environment
DEFAULT_ENV="dev"

# Note: Ollama/LLM settings are now loaded from database (email_user.app_settings)
# No SPRING_AI_OLLAMA_BASE_URL environment variable is needed

# Global variable to track selected database
SELECTED_DB="${SELECTED_DB:-email_user}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-<change_me>}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-email_db}"

# Main menu state
MAIN_MENU=1

show_main_menu() {
    echo "================================"
    echo "  Email AI Analyzer - Main Menu"
    echo "================================"
    echo "Version: ${SCRIPT_VERSION} | Created: ${SCRIPT_CREATED}"
    echo "Current environment: ${CURRENT_ENV}"
    echo "================================"
    echo "1. Email AI Analyzer Management"
    echo "2. Ollama Model Management"
    echo "3. MySQL Database Management"
    echo "4. Show Version"
    echo "5. Exit"
    echo "================================"
    echo -n "Select an option (1-5): "
}

show_email_analyzer_menu() {
    echo "================================"
    echo "  Email AI Analyzer Management"
    echo "================================"
    echo "Current environment: ${CURRENT_ENV}"
    echo "================================"
    echo "1. Set environment (local/dev/prod)"
    echo "2. Start container only"
    echo "3. Pull new image and restart container"
    echo "4. Restart existing container"
    echo "5. Stop container"
    echo "6. View container logs"
    echo "7. Stop ALL Email AI Analyzer containers"
    echo "8. View LLM Settings (from database)"
    echo "9. Update LLM Settings (in database)"
    echo "10. Test AI Connection"
    echo "11. Remove ALL Email AI Analyzer images"
    echo "12. Check container status"
    echo "13. Back to Main Menu"
    echo "================================"
    echo -n "Select an option (1-13): "
}

show_ollama_menu() {
    echo "================================"
    echo "  Ollama Model Management"
    echo "================================"
    echo "Ollama Container: logilink_ollama"
    echo "================================"
    echo "1. List all Ollama models"
    echo "2. Download Ollama model"
    echo "3. Start Ollama model (load into GPU)"
    echo "4. Stop Ollama model (unload from GPU)"
    echo "5. View running Ollama models"
    echo "6. Restart Ollama with GPU"
    echo "7. Back to Main Menu"
    echo "================================"
    echo -n "Select an option (1-7): "
}

show_mysql_menu() {
    echo "================================"
    echo "  MySQL Database Management"
    echo "================================"
    echo "Container: ${MYSQL_CONTAINER} | Database: ${SELECTED_DB}"
    echo "================================"
    echo "1. Connect to MySQL"
    echo "2. Show databases"
    echo "3. Select database"
    echo "4. Show tables"
    echo "5. Run SQL query"
    echo "6. Show table structure"
    echo "7. Back to Main Menu"
    echo "================================"
    echo -n "Select an option (1-7): "
}

validate_menu_choice() {
    local choice="$1"
    local max="$2"
    if [[ -z "${choice}" ]]; then
        echo "ERROR: Please enter a number between 1-${max}."
        return 1
    fi
    if [[ ! "${choice}" =~ ^[0-9]+$ ]]; then
        echo "ERROR: Invalid input '${choice}'. Please enter a number between 1-${max}."
        return 1
    fi
    if [[ "${choice}" -lt 1 || "${choice}" -gt "${max}" ]]; then
        echo "ERROR: Invalid option '${choice}'. Please select a number between 1-${max}."
        return 1
    fi
    return 0
}

set_environment() {
    echo "Available environments:"
    echo "1. local (Local development - localhost MySQL)"
    echo "2. dev (Development - Docker MySQL)"
    echo "3. prod (Production - Separate MySQL)"
    echo -n "Select environment (1-3): "
    read -r env_choice

    case $env_choice in
        1)
            CURRENT_ENV="local"
            echo "Environment set to: local"
            ;;
        2)
            CURRENT_ENV="dev"
            echo "Environment set to: dev"
            ;;
        3)
            CURRENT_ENV="prod"
            echo "Environment set to: prod"
            echo "WARNING: Production environment selected!"
            ;;
        *)
            echo "ERROR: Invalid option '${env_choice}'. Please enter a number between 1-3."
            echo "Keeping current environment: ${CURRENT_ENV}"
            return 1
            ;;
    esac
}

start_container() {
    echo "Starting container for ${CURRENT_ENV} environment..."

    # Check if container exists
    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        # Check if container is already running
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            echo "Container is already running."
        else
            docker start "${CONTAINER_NAME}"
            echo "Container started successfully."
        fi
    else
        echo "Container does not exist. Creating new container..."
        create_and_run_container
        echo "Container created and started successfully."
    fi
}

create_and_run_container() {
    local db_url="${ENV_DB_URLS[$CURRENT_ENV]}"
    local db_user="${ENV_DB_USERS[$CURRENT_ENV]}"
    local db_password="${ENV_DB_PASSWORDS[$CURRENT_ENV]}"

    # Note: Ollama/LLM settings are loaded from database (email_user.app_settings)
    # No SPRING_AI_OLLAMA_BASE_URL environment variable is needed
    # The application reads llm_url, llm_model, llm_temperature from app_settings table

    # Build the docker run command with Spring profile
    # Memory limit: 16GB RAM with 16GB swap (prevents OOM kills)
    docker run -d \
        --name "${CONTAINER_NAME}" \
        -p 8081:8081 \
        --network email-net \
        --add-host=host.docker.internal:host-gateway \
        --memory=16g \
        --memory-swap=16g \
        --memory-swappiness=0 \
        --oom-kill-disable \
        -e SPRING_PROFILES_ACTIVE="${CURRENT_ENV}" \
        -e DB_URL="${db_url}" \
        -e DB_USER="${db_user}" \
        -e DB_PASSWORD="${db_password}" \
        -e GROQ_API_KEY="${GROQ_API_KEY:-}" \
        -e SMTP_PASSWORD="${SMTP_PASSWORD:-}" \
        -e IMAP_PASSWORD="${IMAP_PASSWORD:-}" \
        -e EMAIL_IMAP_SSL_TRUST="${EMAIL_IMAP_SSL_TRUST:-*}" \
        -e SECURITY_USER="${SECURITY_USER:-admin}" \
        -e SECURITY_PASS="${SECURITY_PASS:-<change_me>}" \
        -e SECURITY_LOG_CREDENTIALS_ON_STARTUP="${SECURITY_LOG_CREDENTIALS_ON_STARTUP:-true}" \
        -e DEBUG_LOG_SECRETS="${DEBUG_LOG_SECRETS:-false}" \
        -e AI_TEST_CONNECT_TIMEOUT_SECONDS="${AI_TEST_CONNECT_TIMEOUT_SECONDS:-10}" \
        -e AI_TEST_REQUEST_TIMEOUT_SECONDS="${AI_TEST_REQUEST_TIMEOUT_SECONDS:-75}" \
        -e EMAIL_ANALYSIS_DEFAULT_DATE_RANGE_DAYS="${EMAIL_ANALYSIS_DEFAULT_DATE_RANGE_DAYS:-1}" \
        -e EMAIL_ANALYSIS_DEFAULT_MAX_EMAILS="${EMAIL_ANALYSIS_DEFAULT_MAX_EMAILS:-1000}" \
        -e EMAIL_IMAP_CONNECTION_TIMEOUT_MS="${EMAIL_IMAP_CONNECTION_TIMEOUT_MS:-10000}" \
        -e EMAIL_IMAP_TIMEOUT_MS="${EMAIL_IMAP_TIMEOUT_MS:-20000}" \
        -e EMAIL_IMAP_WRITE_TIMEOUT_MS="${EMAIL_IMAP_WRITE_TIMEOUT_MS:-20000}" \
        -e MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:-health,info,prometheus,metrics}" \
        -e MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS="${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:-always}" \
        "${IMAGE_NAME}"
}

pull_and_restart() {
    echo "Pulling new image..."
    docker pull "${IMAGE_NAME}"

    echo "Stopping and removing existing container..."
    docker stop "${CONTAINER_NAME}" 2>/dev/null || true
    docker rm "${CONTAINER_NAME}" 2>/dev/null || true

    echo "Starting new container for ${CURRENT_ENV} environment..."
    create_and_run_container

    echo "New container started successfully."
}

restart_container() {
    echo "Recreating container for ${CURRENT_ENV} environment..."

    echo "Stopping and removing existing container..."
    docker stop "${CONTAINER_NAME}" 2>/dev/null || true
    docker rm "${CONTAINER_NAME}" 2>/dev/null || true

    echo "Starting new container..."
    create_and_run_container

    echo "Container recreated successfully."
}

stop_container() {
    echo "Stopping container..."
    docker stop "${CONTAINER_NAME}" 2>/dev/null || echo "Container was not running or does not exist."
}

view_logs() {
    echo "Showing last 100 lines of container logs..."
    docker logs --tail 100 "${CONTAINER_NAME}"
}

check_status() {
    echo "================================"
    echo "  Email AI Analyzer Status"
    echo "================================"

    # Check container status
    local container_status
    container_status=$(docker ps -a --filter "name=${CONTAINER_NAME}" --format '{{.Status}}' 2>/dev/null)

    if [[ -z "${container_status}" ]]; then
        echo "Container: NOT FOUND"
        echo ""
        echo "To create and start the container, select option 2."
    else
        echo "Container: ${CONTAINER_NAME}"
        echo "Status: ${container_status}"
        echo ""

        # Check if running
        if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Status}}' | grep -q "Up"; then
            echo "✓ Container is RUNNING"
            echo ""

            # Get memory stats
            local mem_stats
            mem_stats=$(docker stats --no-stream --format "table {{.MemUsage}}" "${CONTAINER_NAME}" 2>/dev/null | tail -1)
            if [[ -n "${mem_stats}" ]]; then
                echo "Memory Usage: ${mem_stats}"
                echo "Memory Limit: 16GB"
            fi
            echo ""

            echo "Access URLs:"
            echo "  - Local:      http://localhost:8081"
            echo "  - Production: https://my.logilink.tr"
            echo ""
            echo "Credentials:"
            echo "  - Username: ${SECURITY_USER:-admin}"
            echo "  - Password: ${SECURITY_PASS:-<set SECURITY_PASS env var>}"
            echo ""

            # Quick health check
            local health
            health=$(curl -s http://localhost:8081/actuator/health 2>/dev/null | head -c 100)
            if [[ -n "${health}" ]]; then
                echo "Health Check: ${health}"
            else
                echo "Health Check: Unable to connect"
            fi
        else
            echo "✗ Container is STOPPED"
            echo ""
            echo "To start the container, select option 2."
        fi
    fi

    echo ""
    echo "================================"
}

stop_all_containers() {
    echo "Stopping ALL Email AI Analyzer containers..."

    # Find all running containers with the image name
    local running_containers
    running_containers=$(docker ps --filter "ancestor=${IMAGE_NAME}" --format '{{.Names}}' 2>/dev/null)

    if [[ -z "${running_containers}" ]]; then
        echo "No running Email AI Analyzer containers found."
        return
    fi

    echo "Found running containers:"
    echo "${running_containers}"
    echo ""

    for container in ${running_containers}; do
        echo "Stopping container: ${container}"
        docker stop "${container}" 2>/dev/null || echo "Failed to stop ${container}"
    done

    echo ""
    echo "All Email AI Analyzer containers stopped."
}

remove_all_images() {
    echo "================================"
    echo "  Remove ALL Email AI Analyzer Images"
    echo "================================"

    echo "WARNING: This will remove ALL Docker images for email-ai-analyzer!"
    echo "This action cannot be undone."
    echo ""

    # Find all images related to email-ai-analyzer
    local all_images
    all_images=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep "email-ai-analyzer" 2>/dev/null)

    if [[ -z "${all_images}" ]]; then
        echo "No Email AI Analyzer images found."
        return
    fi

    echo "Found the following images:"
    echo "${all_images}"
    echo ""

    # Stop and remove any containers using these images
    echo "Stopping and removing containers using these images..."
    local containers_to_remove
    containers_to_remove=$(docker ps -a --filter "ancestor=${IMAGE_NAME}" --format '{{.Names}}' 2>/dev/null)

    for container in ${containers_to_remove}; do
        echo "Stopping and removing container: ${container}"
        docker stop "${container}" 2>/dev/null || true
        docker rm "${container}" 2>/dev/null || true
    done

    echo ""
    echo "Removing images..."

    # Remove each image
    for image in ${all_images}; do
        echo "Removing image: ${image}"
        docker rmi "${image}" 2>/dev/null || echo "Failed to remove ${image} (may be in use)"
    done

    # Also try to remove by repository pattern
    echo ""
    echo "Cleaning up any remaining email-ai-analyzer images..."
    docker images | grep "email-ai-analyzer" | awk '{print $3}' | xargs -r docker rmi -f 2>/dev/null || true

    echo ""
    echo "All Email AI Analyzer images removed."
}

view_llm_settings() {
    echo "================================"
    echo "  LLM Settings (from database)"
    echo "================================"

    # Get settings from database
    local llm_url llm_model llm_temp
    llm_url=$(docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -N -e \
        "USE ${SELECTED_DB}; SELECT llm_url FROM app_settings WHERE id = 1;" 2>&1 | grep -v Warning | tr -d '\r\n')
    llm_model=$(docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -N -e \
        "USE ${SELECTED_DB}; SELECT llm_model FROM app_settings WHERE id = 1;" 2>&1 | grep -v Warning | tr -d '\r\n')
    llm_temp=$(docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -N -e \
        "USE ${SELECTED_DB}; SELECT llm_temperature FROM app_settings WHERE id = 1;" 2>&1 | grep -v Warning | tr -d '\r\n')

    if [[ -n "${llm_url}" && -n "${llm_model}" ]]; then
        echo "Current LLM Configuration:"
        echo "  URL:    ${llm_url}"
        echo "  Model:  ${llm_model}"
        echo "  Temp:   ${llm_temp}"
        echo ""
        echo "Note: These settings are loaded by the application at startup."
        echo "      No container restart needed to update."
    else
        echo "Failed to retrieve LLM settings from database."
        echo "Make sure the database container (${MYSQL_CONTAINER}) is running."
    fi
}

update_llm_settings() {
    echo "================================"
    echo "  Update LLM Settings"
    echo "================================"

    # Show current settings
    view_llm_settings
    echo ""

    # Get new values
    echo -n "Enter Ollama URL [http://172.23.0.1:11434]: "
    read -r llm_url
    llm_url=${llm_url:-http://172.23.0.1:11434}

    # Validate URL format
    if [[ ! "${llm_url}" =~ ^https?:// ]]; then
        echo "ERROR: Invalid URL format. URL must start with http:// or https://"
        return 1
    fi

    echo -n "Enter Model name [llama3.2]: "
    read -r llm_model
    llm_model=${llm_model:-llama3.2}

    # Validate model name is not empty
    if [[ -z "${llm_model}" ]]; then
        echo "ERROR: Model name cannot be empty."
        return 1
    fi

    echo -n "Enter Temperature (0.0-1.0) [0.5]: "
    read -r llm_temp
    llm_temp=${llm_temp:-0.5}

    # Validate temperature is a number between 0 and 1
    if [[ ! "${llm_temp}" =~ ^[0-9]+\.?[0-9]*$ ]]; then
        echo "ERROR: Temperature must be a number (e.g., 0.5)."
        return 1
    fi

    # Check if temperature is in valid range using bc
    if ! echo "${llm_temp} >= 0 && ${llm_temp} <= 1" | bc -l | grep -q "1"; then
        echo "ERROR: Temperature must be between 0.0 and 1.0."
        return 1
    fi

    echo ""
    echo "Updating settings in database..."

    # Update database
    local result
    result=$(docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e \
        "USE ${SELECTED_DB}; UPDATE app_settings SET llm_url='${llm_url}', llm_model='${llm_model}', llm_temperature=${llm_temp} WHERE id = 1;" 2>&1 | grep -v Warning)

    if [[ $? -eq 0 ]]; then
        echo "✓ LLM settings updated successfully!"
        echo ""
        echo "New Configuration:"
        echo "  URL:    ${llm_url}"
        echo "  Model:  ${llm_model}"
        echo "  Temp:   ${llm_temp}"
        echo ""
        echo "Note: Changes take effect immediately for new AI requests."
    else
        echo "✗ Failed to update LLM settings."
        echo "Error: ${result}"
    fi
}

test_ai_connection() {
    echo "================================"
    echo "  Testing AI Connection"
    echo "================================"

    echo "Testing Ollama service directly..."
    local ollama_test
    ollama_test=$(timeout 10 curl -s -X POST http://localhost:11434/api/generate \
        -H "Content-Type: application/json" \
        -d '{"model": "llama3.2", "prompt": "Say OK in one word", "stream": false}' 2>&1)

    if echo "${ollama_test}" | jq -e '.response' > /dev/null 2>&1; then
        echo "✓ Ollama is responding"
        echo "  Response: $(echo "${ollama_test}" | jq -r '.response' | head -c 50)..."
    else
        echo "✗ Ollama is not responding"
        echo "  Response: ${ollama_test}"
    fi

    echo ""
    echo "Testing application /api/ai/test endpoint..."

    # Login to get session
    local cookie_jar
    cookie_jar=$(mktemp)

    local login_response
    login_response=$(curl -s -c "${cookie_jar}" -X POST http://localhost:8081/login \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=${SECURITY_USER:-admin}&password=${SECURITY_PASS:-<change_me>}" \
        -w "%{http_code}" -o /dev/null)

    if [[ "${login_response}" == "302" ]]; then
        echo "✓ Login successful"

        # Test AI endpoint
        local test_response
        test_response=$(curl -s -b "${cookie_jar}" -X POST http://localhost:8081/api/ai/test \
            -H "Content-Type: application/json")

        if echo "${test_response}" | jq -e '.success' > /dev/null 2>&1; then
            local success
            success=$(echo "${test_response}" | jq -r '.success')
            local message
            message=$(echo "${test_response}" | jq -r '.message')
            local response_time
            response_time=$(echo "${test_response}" | jq -r '.responseTimeMs')

            if [[ "${success}" == "true" ]]; then
                echo "✓ AI connection test PASSED"
                echo "  Message: ${message}"
                echo "  Response Time: ${response_time}ms"
            else
                echo "✗ AI connection test FAILED"
                echo "  Message: ${message}"
            fi
        else
            echo "✗ Failed to parse API response"
            echo "  Response: ${test_response}"
        fi

        rm -f "${cookie_jar}"
    else
        echo "✗ Login failed (HTTP ${login_response})"
        echo "  Check if application is running and credentials are correct"
    fi

    echo ""
    echo "Loaded models in Ollama:"
    docker exec logilink_ollama ollama ps 2>/dev/null || echo "  Unable to query Ollama"
}

# =============================================================================
# Ollama Model Management Functions
# =============================================================================

list_ollama_models() {
    echo "================================"
    echo "  Ollama Models (Sorted by Modified Date)"
    echo "================================"
    
    docker exec logilink_ollama ollama list 2>/dev/null || {
        echo "ERROR: Unable to list Ollama models."
        echo "Make sure the Ollama container (logilink_ollama) is running."
        return 1
    }
}

pull_ollama_model() {
    echo "================================"
    echo "  Download Ollama Model"
    echo "================================"
    
    # Show current models
    list_ollama_models
    echo ""
    
    echo -n "Enter Ollama model name to download (e.g., llama3.2, qwen2.5:7b, mistral): "
    read -r model_name
    
    if [[ -z "${model_name}" ]]; then
        echo "ERROR: Model name cannot be empty."
        return 1
    fi
    
    echo ""
    echo "Downloading Ollama model: ${model_name}"
    echo "This may take a while depending on the model size..."
    echo ""
    
    docker exec logilink_ollama ollama pull "${model_name}"
    
    if [[ $? -eq 0 ]]; then
        echo ""
        echo "✓ Ollama model '${model_name}' downloaded successfully!"
        echo ""
        list_ollama_models
    else
        echo ""
        echo "✗ Failed to download Ollama model '${model_name}'."
        echo "Check the model name and your internet connection."
    fi
}

start_ollama_model() {
    echo "================================"
    echo "  Start Ollama Model (Load into GPU)"
    echo "================================"
    
    # Show current models
    list_ollama_models
    echo ""
    
    echo -n "Enter Ollama model name to start: "
    read -r model_name
    
    if [[ -z "${model_name}" ]]; then
        echo "ERROR: Model name cannot be empty."
        return 1
    fi
    
    echo ""
    echo "Loading Ollama model '${model_name}' into GPU..."
    echo "This will keep the model loaded and ready for fast inference."
    echo "Press Ctrl+C to stop the model later."
    echo ""
    
    # Run the model in background to load it into GPU memory
    # Using timeout to prevent hanging, model stays loaded
    docker exec logilink_ollama timeout 5 ollama run "${model_name}" "Say 'Model loaded successfully' in one short sentence" 2>&1 || true
    
    echo ""
    echo "Ollama model '${model_name}' is now loaded in GPU memory."
    echo ""
    echo "Running Ollama models:"
    docker exec logilink_ollama ollama ps 2>/dev/null || echo "Unable to query running models"
}

stop_ollama_model() {
    echo "================================"
    echo "  Stop Ollama Model (Unload from GPU)"
    echo "================================"
    
    # Show running models
    echo "Currently running Ollama models:"
    docker exec logilink_ollama ollama ps 2>/dev/null || {
        echo "Unable to query running models."
        echo "Make sure the Ollama container (logilink_ollama) is running."
        return 1
    }
    echo ""
    
    echo -n "Enter Ollama model name to stop (or 'all' to stop all): "
    read -r model_name
    
    if [[ -z "${model_name}" ]]; then
        echo "ERROR: Model name cannot be empty."
        return 1
    fi
    
    echo ""
    
    if [[ "${model_name}" == "all" ]]; then
        echo "Stopping all Ollama models..."
        # Stop all by killing any running ollama processes
        docker exec logilink_ollama ollama stop --all 2>/dev/null || {
            # Fallback: restart the ollama serve process
            echo "Note: Using fallback method to stop all models."
            docker exec logilink_ollama pkill -f "ollama_llama_server" 2>/dev/null || true
        }
        echo "All Ollama models stopped."
    else
        echo "Stopping Ollama model: ${model_name}"
        docker exec logilink_ollama ollama stop "${model_name}" 2>/dev/null || {
            echo "Note: Could not stop gracefully, trying forceful stop..."
            docker exec logilink_ollama pkill -f "ollama_llama_server.*${model_name}" 2>/dev/null || true
        }
        echo "Ollama model '${model_name}' stopped."
    fi
    
    echo ""
    echo "Running Ollama models after stop:"
    docker exec logilink_ollama ollama ps 2>/dev/null || echo "Unable to query running models"
}

view_running_models() {
    echo "================================"
    echo "  Running Ollama Models"
    echo "================================"
    
    echo "Currently loaded Ollama models in GPU:"
    echo ""
    docker exec logilink_ollama ollama ps 2>/dev/null || {
        echo "ERROR: Unable to query running models."
        echo "Make sure the Ollama container (logilink_ollama) is running."
        return 1
    }
    
    echo ""
    echo "Note: Ollama models loaded in GPU provide faster response times."
    echo "      Use option 4 to unload models when not needed."
}

restart_ollama_with_gpu() {
    echo "================================"
    echo "  Restart Ollama Container with GPU"
    echo "================================"
    
    echo "WARNING: This will stop and restart the Ollama container!"
    echo "All loaded Ollama models will be unloaded."
    echo ""
    
    echo -n "Are you sure? (y/n): "
    read -r confirm
    
    if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
        echo "Operation cancelled."
        return 0
    fi
    
    echo ""
    echo "Stopping Ollama container..."
    docker stop logilink_ollama 2>/dev/null || echo "Container was not running"
    
    echo "Removing Ollama container..."
    docker rm logilink_ollama 2>/dev/null || echo "Container did not exist"
    
    echo ""
    echo "Starting new Ollama container with GPU support..."
    docker run -d \
        --gpus all \
        -v ollama:/root/.ollama \
        -p 11434:11434 \
        --name logilink_ollama \
        ollama/ollama
    
    if [[ $? -eq 0 ]]; then
        echo ""
        echo "✓ Ollama container restarted with GPU support!"
        echo ""
        echo "Waiting for Ollama to initialize..."
        sleep 5
        
        echo ""
        echo "Available Ollama models:"
        list_ollama_models
    else
        echo ""
        echo "✗ Failed to start Ollama container with GPU."
        echo "Make sure NVIDIA Docker is installed and configured."
    fi
}

# =============================================================================
# MySQL Database Management Functions
# =============================================================================

connect_to_mysql() {
    echo "================================"
    echo "  Connect to MySQL"
    echo "================================"
    
    echo "Connecting to MySQL database..."
    echo "Container: ${MYSQL_CONTAINER}"
    echo "User: ${MYSQL_USER}"
    echo "Current database: ${SELECTED_DB}"
    echo ""
    
    # Test connection
    docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "SELECT 'Connection successful!' AS status;" 2>&1 | grep -v Warning
    
    if [[ $? -eq 0 ]]; then
        echo ""
        echo "✓ MySQL connection is working!"
    else
        echo ""
        echo "✗ Failed to connect to MySQL."
        echo "Make sure the database container (${MYSQL_CONTAINER}) is running."
        return 1
    fi
}

show_databases() {
    echo "================================"
    echo "  MySQL Databases"
    echo "================================"
    
    docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "SHOW DATABASES;" 2>&1 | grep -v Warning
}

select_database() {
    echo "================================"
    echo "  Select Database"
    echo "================================"
    
    echo "Current database: ${SELECTED_DB}"
    echo ""
    echo "Available databases:"
    show_databases
    echo ""
    
    echo -n "Enter database name to select: "
    read -r db_name
    
    if [[ -z "${db_name}" ]]; then
        echo "ERROR: Database name cannot be empty."
        return 1
    fi
    
    # Verify database exists
    local result
    result=$(docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -N -e "SHOW DATABASES LIKE '${db_name}';" 2>&1 | grep -v Warning)
    
    if [[ -n "${result}" ]]; then
        SELECTED_DB="${db_name}"
        echo ""
        echo "✓ Database '${db_name}' selected!"
        echo "Current database is now: ${SELECTED_DB}"
    else
        echo ""
        echo "✗ Database '${db_name}' does not exist."
        return 1
    fi
}

show_tables() {
    echo "================================"
    echo "  MySQL Tables"
    echo "================================"
    
    echo "Current database: ${SELECTED_DB}"
    echo ""
    
    docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "USE ${SELECTED_DB}; SHOW TABLES;" 2>&1 | grep -v Warning
}

run_sql_query() {
    echo "================================"
    echo "  Run SQL Query"
    echo "================================"
    
    echo "Current database: ${SELECTED_DB}"
    echo ""
    echo "Enter a SQL query (e.g., SELECT * FROM table_name LIMIT 10;)"
    echo "Type 'CANCEL' to abort."
    echo ""
    echo -n "SQL> "
    read -r sql_query
    
    if [[ "${sql_query}" == "CANCEL" ]]; then
        echo "Query cancelled."
        return 0
    fi
    
    if [[ -z "${sql_query}" ]]; then
        echo "ERROR: SQL query cannot be empty."
        return 1
    fi
    
    echo ""
    echo "Executing: ${sql_query}"
    echo ""
    
    # Execute the query
    docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "USE ${SELECTED_DB}; ${sql_query}" 2>&1 | grep -v Warning
}

show_table_structure() {
    echo "================================"
    echo "  Show Table Structure"
    echo "================================"
    
    echo "Current database: ${SELECTED_DB}"
    echo ""
    echo "Available tables:"
    show_tables
    echo ""
    
    echo -n "Enter table name to describe: "
    read -r table_name
    
    if [[ -z "${table_name}" ]]; then
        echo "ERROR: Table name cannot be empty."
        return 1
    fi
    
    echo ""
    echo "Structure of table '${table_name}':"
    echo ""
    
    docker exec ${MYSQL_CONTAINER} mysql -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "USE ${SELECTED_DB}; DESCRIBE ${table_name};" 2>&1 | grep -v Warning
}

# Initialize current environment
CURRENT_ENV="${DEFAULT_ENV}"

echo "Email AI Analyzer - Docker Management Script"
echo "Version: ${SCRIPT_VERSION}"
echo "Default environment: ${CURRENT_ENV}"
echo ""

# Main loop
while true; do
    if [[ "${MAIN_MENU}" -eq 1 ]]; then
        show_main_menu
        read -r choice
        
        if ! validate_menu_choice "${choice}" 5; then
            continue
        fi
        
        case $choice in
            1)
                MAIN_MENU=2  # Email Analyzer submenu
                ;;
            2)
                MAIN_MENU=3  # Ollama submenu
                ;;
            3)
                MAIN_MENU=4  # MySQL submenu
                ;;
            4)
                echo "================================"
                echo "  Email AI Analyzer - Version Info"
                echo "================================"
                echo "Version:  ${SCRIPT_VERSION}"
                echo "Created:  ${SCRIPT_CREATED}"
                echo "Author:   Eyuce"
                echo "License:  MIT"
                echo "Repository: https://github.com/eyuce/email-ai-analyzer"
                echo "================================"
                ;;
            5)
                echo "Exiting..."
                exit 0
                ;;
        esac
    elif [[ "${MAIN_MENU}" -eq 2 ]]; then
        show_email_analyzer_menu
        read -r choice
        
        if ! validate_menu_choice "${choice}" 13; then
            continue
        fi
        
        case $choice in
            1)
                set_environment
                ;;
            2)
                start_container
                ;;
            3)
                pull_and_restart
                ;;
            4)
                restart_container
                ;;
            5)
                stop_container
                ;;
            6)
                view_logs
                ;;
            7)
                stop_all_containers
                ;;
            8)
                view_llm_settings
                ;;
            9)
                update_llm_settings
                ;;
            10)
                test_ai_connection
                ;;
            11)
                remove_all_images
                ;;
            12)
                check_status
                ;;
            13)
                MAIN_MENU=1
                ;;
        esac
    elif [[ "${MAIN_MENU}" -eq 3 ]]; then
        show_ollama_menu
        read -r choice
        
        if ! validate_menu_choice "${choice}" 7; then
            continue
        fi
        
        case $choice in
            1)
                list_ollama_models
                ;;
            2)
                pull_ollama_model
                ;;
            3)
                start_ollama_model
                ;;
            4)
                stop_ollama_model
                ;;
            5)
                view_running_models
                ;;
            6)
                restart_ollama_with_gpu
                ;;
            7)
                MAIN_MENU=1
                ;;
        esac
    elif [[ "${MAIN_MENU}" -eq 4 ]]; then
        show_mysql_menu
        read -r choice
        
        if ! validate_menu_choice "${choice}" 7; then
            continue
        fi
        
        case $choice in
            1)
                connect_to_mysql
                ;;
            2)
                show_databases
                ;;
            3)
                select_database
                ;;
            4)
                show_tables
                ;;
            5)
                run_sql_query
                ;;
            6)
                show_table_structure
                ;;
            7)
                MAIN_MENU=1
                ;;
        esac
    fi

    echo ""
done
