FROM registry.opensource.zalan.do/stups/openjdk:8-42

MAINTAINER Zalando SE

CMD java $JAVA_OPTS $(java-dynamic-memory-opts 70) $(newrelic-agent) $(appdynamics-agent) -jar /essentials.jar

COPY target/essentials.jar /

ADD target/scm-source.json /scm-source.json
