FROM registry.opensource.zalan.do/stups/openjdk:8u66-b17-1-12

MAINTAINER Zalando SE

COPY target/essentials.jar /

EXPOSE 8080

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(newrelic-agent) $(appdynamics-agent) -jar /essentials.jar

ADD target/scm-source.json /scm-source.json
