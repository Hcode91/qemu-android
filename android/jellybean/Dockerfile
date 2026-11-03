# AOSP 4.1 native guest builder, not the Android APK toolchain.
FROM ubuntu@sha256:18305429afa14ea462f810146ba44d4363ae76e4c8dfc38288cf73aa07485005
ENV DEBIAN_FRONTEND=noninteractive LC_ALL=C
# The signed Ubuntu archive supplies legacy build dependencies. Do not allow
# unauthenticated packages or disable signature validation if this fails.
RUN printf '%s\n' \
    'deb http://old-releases.ubuntu.com/ubuntu precise main universe multiverse restricted' \
    'deb http://old-releases.ubuntu.com/ubuntu precise-updates main universe multiverse restricted' \
    'deb http://old-releases.ubuntu.com/ubuntu precise-security main universe multiverse restricted' \
    > /etc/apt/sources.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends build-essential git python openjdk-6-jdk \
      flex bison gperf zip unzip zlib1g-dev lib32z1 g++-multilib \
      libc6-dev-i386 lib32stdc++6 libxml2-utils xsltproc file \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
