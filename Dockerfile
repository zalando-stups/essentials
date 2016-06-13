FROM registry.opensource.zalan.do/stups/openjdk:8-26

MAINTAINER Zalando SE

EXPOSE 8080

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(newrelic-agent) $(appdynamics-agent) -jar /essentials.jar

COPY target/essentials.jar /

ADD target/scm-source.json /scm-source.json
