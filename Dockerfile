#
# Custom base image for jib with systemd and smartctl installed
#
# $ docker buildx build --push --platform linux/arm64/v8,linux/amd64 --tag krillsson/openjdk25-ubuntu-systemd:ffm-probe .
FROM azul/zulu-openjdk:25-jre

RUN apt-get update && apt-get -qq -y install systemd -y --no-install-recommends \
    systemd \
    curl \
    nut \
    smartmontools \
    pciutils