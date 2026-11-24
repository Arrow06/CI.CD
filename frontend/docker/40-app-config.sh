#!/bin/sh
# Генерує config.js з налаштуваннями середовища під час старту контейнера.
set -e
cat > /usr/share/nginx/html/config.js <<EOF
window.APP_CONFIG = { apiBaseUrl: '${API_BASE_URL}' };
EOF
echo "config.js: apiBaseUrl='${API_BASE_URL}'"
