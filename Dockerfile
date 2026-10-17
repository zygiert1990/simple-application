# CRaC (Warp engine): checkpoint created during build, image restores from it
# Build: docker build --build-arg TRAINING_CPUS=4 -t simple-application:crac-cpus4 .
# Run:   docker run --rm --cpus=4 simple-application:crac-cpus4
# TRAINING_CPUS must match --cpus used at runtime (default 1)

FROM debian:trixie-20261005-slim AS jdk
ARG JDK_URL=https://cdn.azul.com/zulu/bin/zulu27.28.101-ca-crac-jdk27.0.0-linux_x64.tar.gz
ARG JDK_SHA256=65f9dee7d4a700fa013c336e4a969f94ffc8e5970485ef3ee0ee43149a03569d
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/jdk.tar.gz "$JDK_URL" \
    && echo "$JDK_SHA256  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/jdk && tar -xzf /tmp/jdk.tar.gz -C /opt/jdk --strip-components=1 \
    && rm /tmp/jdk.tar.gz
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH

# ---------- stage 1a: build fat jar ----------
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
    && cp target/scala-*/simple-application-crac.jar /simple-application-crac.jar

# ---------- stage 1b: training run, produces checkpoint in /app/cr ----------
FROM jdk AS train
# JVM sizes itself (GC/JIT threads, Netty event loops, ...) as with --cpus=$TRAINING_CPUS at runtime,
# those sizes are frozen in the checkpoint
ARG TRAINING_CPUS=1
ARG WARMUP_REQUESTS=1000
WORKDIR /app
COPY --from=build /simple-application-crac.jar /app/simple-application-crac.jar
RUN <<'EOF'
set -eu
# app blocks on stdin after start, fifo keeps stdin open during warmup and checkpoint
mkfifo /tmp/stdin
# timeout: fail instead of hanging forever if training gets stuck
timeout 600 java -XX:ActiveProcessorCount=$TRAINING_CPUS -XX:CRaCEngine=warp -XX:CRaCCheckpointTo=/app/cr \
    -jar simple-application-crac.jar < /tmp/stdin > /tmp/app.log 2>&1 &
pid=$!
exec 3> /tmp/stdin

i=0
until grep -q "Server started" /tmp/app.log; do
    i=$((i + 1))
    if ! kill -0 $pid 2> /dev/null || [ $i -gt 600 ]; then cat /tmp/app.log; exit 1; fi
    sleep 0.1
done

for n in $(seq 1 $WARMUP_REQUESTS); do
    curl -fsS -m 10 -o /dev/null "http://localhost:8080/hello?name=michal"
done

# JVM terminates itself after checkpoint is written
timeout 120 jcmd simple-application-crac.jar JDK.checkpoint
rc=0
wait $pid || rc=$?
[ $rc -ne 124 ]
grep -v DEBUG /tmp/app.log | grep -i -E "checkpoint|crac|error|exception" || true
test -n "$(ls -A /app/cr)"
EOF

# ---------- stage 2: runtime image ----------
FROM jdk
WORKDIR /app
COPY --from=train /app /app
ENTRYPOINT ["java", "-XX:CRaCEngine=warp", "-XX:CRaCRestoreFrom=/app/cr"]
