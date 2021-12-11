FROM ubuntu:focal AS base

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Europe/Berlin
RUN apt-get update && \
    apt-get -y install \
        nano openjdk-17-jdk git python3 build-essential


FROM base AS gradle

WORKDIR /build
COPY build.gradle settings.gradle gradlew /build/
COPY gradle /build/gradle

# populate gradle cache
RUN ./gradlew build || return 0


FROM base

# copy gradle cache
COPY --from=gradle /root/.gradle /root/.gradle

RUN git clone "https://git.scc.kit.edu/IPDSnelting/mjtest-tests.git" /home/tests
RUN git clone "https://git.scc.kit.edu/IPDSnelting/mjtest.git" /home/mjtest
RUN rm -r /home/mjtest/tests && ln -s /home/tests /home/mjtest/tests

WORKDIR /home/compiler
COPY . .

RUN ./gradlew shadowJar

CMD ["/bin/bash"]
