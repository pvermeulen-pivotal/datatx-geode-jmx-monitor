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

ADD target/datatx-geode-jmx-monitor-1.0.0.SNAPSHOT-package.zip /usr

RUN unzip /usr/datatx-geode-jmx-monitor-1.0.0.SNAPSHOT-package.zip -d /usr
RUN chmod +x /usr/monitor/start_monitor.sh
RUN chmod +x /usr/monitor/monitor_command.sh
RUN rm -f /usr/datatx-geode-jmx-monitor-1.0.0.SNAPSHOT-package.zip

EXPOSE 6780

CMD  bash "/usr/monitor/start_monitor.sh"
