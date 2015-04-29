FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

COPY target/essentials.jar /

CMD java $(java-dynamic-memory-opts) -jar /essentials.jar
