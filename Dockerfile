FROM registry.opensource.zalan.do/stups/openjdk:latest

MAINTAINER Zalando SE

CMD java $JAVA_OPTS $(java-dynamic-memory-opts 50) $(newrelic-agent) $(appdynamics-agent) -jar /essentials.jar

COPY target/essentials.jar /

ADD target/scm-source.json /scm-source.json
