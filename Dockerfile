# ==========================================
# STEP 1: Build the Java Agent Application Jar
# ==========================================
FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies early to optimize layer caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# STEP 2: Assemble Polyglot Runtime Environment
# ==========================================
FROM ubuntu:24.04

# Avoid interactive prompts during installations
ENV DEBIAN_FRONTEND=noninteractive

# Install core system packages, Git, Java, Python, and Go
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    ca-certificates \
    openjdk-17-jdk-headless \
    maven \
    python3 \
    python3-pip \
    python3-venv \
    golang-go \
    && && rm -rf /var/lib/apt/lists/*

# Install Node.js (v20.x LTS) & npm cleanly via NodeSource
# Install Node.js (v20.x LTS) & npm cleanly via NodeSource manual setup
RUN mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://nodesource.com | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://nodesource.com nodistro main" | tee /etc/apt/keyrings/nodesource.list \
    && apt-get update \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install global Python packages (like pytest) inside system scope safely
RUN pip3 install --break-system-packages pytest

# Configure crucial Environment Variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:/usr/local/go/bin
WORKDIR /app

# Copy the compiled executable Fat-JAR from the builder stage
COPY --from=builder /app/target/ai-bug-fixer-agent-1.0.0-SNAPSHOT.jar ./agent.jar

# OpenShift run requirement: Ensure the container can run as a non-root user (random UID)
RUN chown -R 1001:0 /app && chmod -R g+rwX /app
USER 1001

# Document that the app listens on Port 9090
EXPOSE 9090

# Command to execute the microservice application instance at launch
ENTRYPOINT ["java", "-jar", "agent.jar"]
