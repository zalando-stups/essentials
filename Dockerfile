FROM zalando/openjdk:8u66-b17-1-2

MAINTAINER Zalando SE

COPY target/essentials.jar /

EXPOSE 8080
ENV HTTP_PORT=8080

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(appdynamics-agent) -jar /essentials.jar

ADD target/scm-source.json /scm-source.json
