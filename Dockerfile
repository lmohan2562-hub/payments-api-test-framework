# Runs the full suite in a clean, reproducible container.
#   docker build -t payments-api-tests .
#   docker run --rm -v "$PWD/target:/workspace/target" payments-api-tests
# Override the suite:  docker run --rm payments-api-tests -Dsuite=smoke
FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /workspace

# Cache dependencies in their own layer so source edits don't re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Run as an unprivileged user.
RUN useradd --create-home --uid 10001 tester \
    && mkdir -p /workspace/target \
    && chown -R tester:tester /workspace \
    && cp -r /root/.m2 /home/tester/.m2 \
    && chown -R tester:tester /home/tester/.m2
USER tester

ENTRYPOINT ["mvn", "-B", "verify"]
CMD []
