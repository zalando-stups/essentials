FROM zalando/openjdk:8u45-b14-5

MAINTAINER Zalando SE

COPY target/essentials.jar /

CMD java $(java-dynamic-memory-opts) $(appdynamics-agent) -jar /essentials.jar

ADD target/scm-source.json /scm-source.json
