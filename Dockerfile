FROM centos:centos7
ENV container=docker

RUN yum -y update
RUN yum makecache fast
RUN yum install -y nc
RUN yum install -y net-tools
RUN yum install -y tcpdump
RUN yum install -y java-1.8.0-openjdk 
RUN yum install -y java-1.8.0-openjdk-devel
RUN yum install -y ant
RUN yum install -y zip
RUN yum install -y unzip
RUN yum install -y mtr

ADD target/datatx-geode-jmx-monitor-1.0.0-archive.zip /usr/bin

RUN unzip /usr/bin/datatx-geode-jmx-monitor-1.0.0-archive.zip -d /usr/bin/
RUN chmod +x /usr/bin/monitor/start_monitor.sh
RUN chmod +x /usr/bin/monitor/monitor_command.sh
RUN rm -f /usr/bin/datatx-geode-jmx-monitor-1.0.0-archive.zip

EXPOSE 6780

CMD  bash "/usr/bin/monitor/start_monitor.sh"


