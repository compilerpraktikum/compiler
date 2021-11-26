FROM ubuntu:latest

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Europe/Berlin
RUN apt-get update && \
    apt-get -y install \
        openjdk-17-jdk git python3

RUN git clone "https://git.scc.kit.edu/IPDSnelting/mjtest-tests.git" /home/tests
RUN git clone "https://git.scc.kit.edu/IPDSnelting/mjtest.git" /home/mjtest
RUN rm -r /home/mjtest/tests && ln -s /home/tests /home/mjtest/tests

COPY . /home/compiler
WORKDIR /home/compiler
RUN ./build

CMD ["/bin/bash"]
