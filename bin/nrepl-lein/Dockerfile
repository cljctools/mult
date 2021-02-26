FROM ubuntu:20.04
# FROM adoptopenjdk/openjdk14:jdk-14.0.2_12-ubuntu@sha256:59ba43fee9fe91a039e3f15028f1cfc1a3097e985bf9efce541ff4f0c50bc4ed
# FROM adoptopenjdk/openjdk14-openj9:jdk-14.0.2_12_openj9-0.21.0-ubuntu@sha256:6c312769c07b854113778ff5ffa2b07c601506b3c9bdc8dcfdd7b16fea1b0318

ENV DEBIAN_FRONTEND="noninteractive"

## core
RUN apt-get update && \
    apt-get install -y \
            sudo  \
            git-core  \
            rlwrap  \
            software-properties-common  \
            unzip wget curl net-tools lsof \
            zlib1g-dev gcc libc6-dev \
            build-essential

WORKDIR /tmp

## openjdk
# RUN apt-get update && \
#     apt-get install -y openjdk-8-jdk
# RUN apt-get update && \
#     apt-get install -y openjdk-11-jdk
# RUN apt-get update && \
#     apt-get install -y openjdk-14-jdk

## graalvm
# https://github.com/arjones/docker-graalvm/blob/master/Dockerfile
# https://github.com/OlegIlyenko/graalvm-native-image/blob/master/Dockerfile
ENV GRAALVM_VERSION=21.0.0.2
ENV SUFFIX_DIR=java11-${GRAALVM_VERSION}
ENV PATH $PATH:/usr/local/graalvm/bin
#  dir will be graalvm-ce-java8-${GRAALVM_VERSION}
RUN curl -Ls "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-${GRAALVM_VERSION}/graalvm-ce-java11-linux-amd64-${GRAALVM_VERSION}.tar.gz" | \
    tar zx -C /usr/local/ && \
    ls -l /usr/local/ && \
    rm -f /usr/local/graalvm-ce-${SUFFIX_DIR}/src.zip && \
    ln -s /usr/local/graalvm-ce-${SUFFIX_DIR} /usr/local/graalvm && \
    rm -fr /var/lib/apt
RUN gu install native-image

## clojure
ENV CLOJURE_TOOLS=linux-install-1.10.2.774.sh
RUN curl -O https://download.clojure.org/install/$CLOJURE_TOOLS && \
    chmod +x $CLOJURE_TOOLS && \
    sudo ./$CLOJURE_TOOLS && \
    clojure -Stree

## leiningen
ENV LEIN_VERSION=2.9.5
ENV LEIN_DIR=/usr/local/bin/
RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/${LEIN_VERSION}/bin/lein && \
    mv lein ${LEIN_DIR} && \
    chmod a+x ${LEIN_DIR}/lein && \
    lein version

## node
RUN curl -sL https://deb.nodesource.com/setup_12.x | bash - && \
    apt-get install -y nodejs 
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg |  apt-key add - && \
    echo "deb https://dl.yarnpkg.com/debian/ stable main" |  tee /etc/apt/sources.list.d/yarn.list && \
    apt-get update && apt-get -y install yarn

RUN sudo apt update && sudo apt install -y xvfb libgtk2.0-0 libxss1 libgconf-2-4

# ## upx
# ENV UPX_VERSION=3.96
# ENV PATH $PATH:/usr/local/upx
# RUN curl -Ls "https://github.com/upx/upx/releases/download/v${UPX_VERSION}/upx-${UPX_VERSION}-amd64_linux.tar.xz" | \
#     tar -xJ -C /usr/local/ && \
#     ln -s /usr/local/upx-${UPX_VERSION}-amd64_linux /usr/local/upx

# ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

ARG workdir

WORKDIR ${workdir}