FROM centos:centos7
RUN yum -y update
RUN yum install -y nc
RUN yum install -y net-tools
RUN yum install -y tcpdump
RUN yum install -y openjdk-8-jdk
RUN yum install -y ant

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

ADD target/datatx-geode-jmx-monitor-1.0.0-archive.zip /usr/bin

RUN unzip /usr/bin/datatx-geode-jmx-monitor-1.0.0-archive.zip
RUN chmod +x /usr/bin/monitor/start_monitor.sh
RUN rm -f /usr/bin/datatx-geode-jmx-monitor-1.0.0-archive.zip

EXPOSE 6780

CMD ["/bin/bash", "/usr/bin/monitor/start_monitor.sh"]