# Stage 1
FROM openjdk:8u232-jdk-stretch AS builder

ADD ./backend /usr/build/app

ARG SBT_VERSION=1.7.1

WORKDIR /tmp

RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

WORKDIR /usr/build/app

ARG SBT_OPTS="-Xmx4096m -Xms1024m -Xss32m -XX:ReservedCodeCacheSize=128m -XX:+UseCodeCacheFlushing -XX:+UseCompressedOops -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"
RUN sbt "project rambutanInitializer" clean stage

# Stage 2
FROM openjdk:8u232-jre-stretch
COPY --from=builder /usr/build/app/apps/rambutan-initializer/target/universal /opt/initializer

CMD tail -f /dev/null