# Stage 1: Compile Java OOP Backend
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY src/main/java ./src/main/java
RUN mkdir -p /app/build/classes && \
    find src/main/java -name "*.java" > sources.txt && \
    javac --release 17 -encoding UTF-8 -d /app/build/classes @sources.txt

# Stage 2: Runtime with Java 17 JRE + Nginx Single-Port HTTP/WebSocket Router
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache nginx gettext
WORKDIR /app

COPY --from=builder /app/build/classes ./build/classes
COPY frontend ./frontend
COPY database ./database
COPY .env* ./

RUN mkdir -p /app/data /run/nginx && \
    cat << 'EOF' > /app/nginx.conf.template
worker_processes auto;
pid /run/nginx/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;
    sendfile      on;
    keepalive_timeout 65;

    map $http_upgrade $connection_upgrade {
        default upgrade;
        ''      close;
    }

    server {
        listen 0.0.0.0:${PORT};
        server_name _;

        location /ws {
            proxy_pass http://127.0.0.1:18081/ws;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection $connection_upgrade;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_read_timeout 3600s;
            proxy_send_timeout 3600s;
        }

        location / {
            proxy_pass http://127.0.0.1:18080;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
EOF

RUN cat << 'EOF' > /app/entrypoint.sh && chmod +x /app/entrypoint.sh
#!/bin/sh
set -e
export PORT="${PORT:-8080}"
export HTTP_PORT=18080
export WS_PORT=18081

envsubst '${PORT}' < /app/nginx.conf.template > /etc/nginx/nginx.conf

java -cp /app/build/classes com.nexus.gaming.NexusApplication &
JAVA_PID=$!

sleep 1
exec nginx -g "daemon off;"
EOF

EXPOSE 8080
CMD ["/app/entrypoint.sh"]
