FROM ubuntu:latest

RUN apt-get update && \
    apt-get -y install \
        openjdk-17-jdk

COPY . /home/compiler
WORKDIR /home/compiler
RUN ./build

CMD ["/bin/bash"]
