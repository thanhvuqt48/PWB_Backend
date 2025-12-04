# Stage 1: Build
FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime (Temurin JDK 21 - Debian)
FROM eclipse-temurin:21-jre

# Set timezone
ENV TZ=Asia/Ho_Chi_Minh
ENV JAVA_OPTS="-Duser.timezone=Asia/Ho_Chi_Minh"

# Install ffmpeg static binary
RUN apt-get update && \
    apt-get install -y curl xz-utils && \
    curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz -o ffmpeg.tar.xz && \
    tar -xf ffmpeg.tar.xz && \
    mv ffmpeg-*-static/ffmpeg /usr/local/bin/ffmpeg && \
    mv ffmpeg-*-static/ffprobe /usr/local/bin/ffprobe && \
    chmod +x /usr/local/bin/ffmpeg /usr/local/bin/ffprobe && \
    rm -rf ffmpeg* && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Update ENTRYPOINT to use JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]