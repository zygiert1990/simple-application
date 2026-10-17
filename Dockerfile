# Plain JVM startup: java -jar
# No training step, so the image does not depend on CPU count - one image for any --cpus
# Build: docker build -t simple-application:main .
# Run:   docker run --cpus=<N> simple-application:main

FROM debian:trixie-20261005-slim AS jdk
ARG JDK_URL=https://download.java.net/java/early_access/jdk28/20/GPL/openjdk-28-ea+20_linux-x64_bin.tar.gz
ARG JDK_SHA256=fd0b94162fede0e2f7a31106a38d0105b23e96521d8c61c0f1a0967e1383b59d
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/jdk.tar.gz "$JDK_URL" \
    && echo "$JDK_SHA256  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/jdk && tar -xzf /tmp/jdk.tar.gz -C /opt/jdk --strip-components=1 \
    && rm /tmp/jdk.tar.gz
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH

# ---------- stage 1: build fat jar ----------
FROM jdk AS build
ARG SBT_URL=https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz
ARG SBT_SHA256=c57cae60c2122ca1bba77184dfb4d0d25fc6c18805394ab36ab6208b0c0f262f
RUN curl -fsSL -o /tmp/sbt.tgz "$SBT_URL" \
    && echo "$SBT_SHA256  /tmp/sbt.tgz" | sha256sum -c - \
    && tar -xzf /tmp/sbt.tgz -C /opt \
    && rm /tmp/sbt.tgz
ENV PATH=/opt/sbt/bin:$PATH
WORKDIR /src
COPY . .
RUN sbt -batch assembly \
    && cp target/scala-*/simple-application.jar /simple-application.jar

# ---------- stage 2: runtime image ----------
FROM jdk
WORKDIR /app
COPY --from=build /simple-application.jar /app/simple-application.jar
ENTRYPOINT ["java", "-jar", "simple-application.jar"]
