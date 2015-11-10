FROM zalando/openjdk:8u66-b17-1-2

MAINTAINER Zalando SE

COPY target/essentials.jar /

CMD java $(java-dynamic-memory-opts) $(appdynamics-agent) -jar /essentials.jar

ADD target/scm-source.json /scm-source.json
