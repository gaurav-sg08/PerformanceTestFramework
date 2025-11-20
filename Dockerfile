# docker/jmeter/Dockerfile
FROM eclipse-temurin:17-jdk

ARG JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter
ENV PATH=$JMETER_HOME/bin:$PATH

RUN apt-get update && apt-get install -y curl tar && rm -rf /var/lib/apt/lists/*

RUN curl -sL https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz \
    | tar -xz -C /opt \
    && mv /opt/apache-jmeter-${JMETER_VERSION} ${JMETER_HOME}

WORKDIR /tests

ENTRYPOINT ["jmeter"]
