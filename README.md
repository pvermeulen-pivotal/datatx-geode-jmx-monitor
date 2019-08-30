# Geode/GemFire JMX Monitor

The datatx-geode-jmx-monitor project provides an application used to monitor a Geode/GemFire cluster and generates alert messages based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. JMX notifications are error messages written to Geode/GemFire logs with a severity of "warning" or higher. Additional notifications that are also sent from the Geode/GemFire locators include the departure, crash or joining of a member to the cluster.

The monitor application provides two (2) additional features.    
   1. Supports any of the Geode/GemFire metrics that can be monitored and generates an alert in the event a metric threshold is exceeded.   
   2. Supports a cluster health check that performs validation of member counts, locators, cache servers, gateways and a couple of key metrics to ensure the cluster is healthy.     

These features are controlled by separate threads and execution intervals are defined in the monitor.properties for the health check and in the mxbeans.xml for metrics.

The monitor application provides support for multiple locators acting as JMX managers. The monitor connects to only one locator JMX manager at a time and if the locator is stopped or crashed it will connect to the next locator JMX manager in the configured list. Whenever a connection is lost to the locator, the monitor switches to another locator, sends an alert for the lost connection and continues monitoring the GemFire cluster.

This project implements the abstract project datatx-geode-monitor which provides the majority of the monitoring, metrics and device health  functionality with the exception of two abstract methods sendAlert and getCmdbHealth. Users will need to modify the StartMonitor class and override sendAlert and getCmdbHealth methods to manage the endpoint where alerts are sent and a CMDB endpoint or file that defines the details about a cluster. 

***public abstract void sendAlert(LogMessage logMessage);***

Example:	

    @Override
    public void sendAlert(LogMessage logMessage) {
        log.info("Sending Message: " + logMessage.toString());
    }

***public abstract void getCmdbHealth();***

Example:	

    @Override
    public String getCmdbHealth() {
	if (healthCheckCmdbUrl.toUpperCase().startsWith("USEFILE")) {
		try {
		      cmdbResponse = new String(Files.readAllBytes(Paths.get(CMDB_HEALTH_JSON)));
		} catch (IOException e) {
		      monitor.getApplicationLog().error("(getCmdbHealth) file method exception: " + e.getMessage());
		}
        }
    }

## Installation ##
The package is built, maven will use the assembly.xml file to create a zip file package containing a deployable monitor. Unzip the package to a location and the zip file creates the monitor directory, all sub-directories, scripts, configuration and jar files.

    monitor
        conf/   
        lib/   
        logs/   
        README.md   
	CONFIGURATION_README.md
        start_monitor.sh   
        start_monitor.cmd   
        monitor_command.sh   
        monitor_command.cmd   

## LogMessage Format: ##

    public class LogMessage {
       private LogHeader header;
       private Notification event;
       private String body;
       private int count = 1;
    }
    
    public LogHeader(String severity, String date, String time, String zone,
       String member, String event, String tid) {
       this.severity = severity;
       this.date = date;
       this.zone = zone;
       this.member = member;
       this.event = event;
       this.time = time.replace(".", ":");
       this.tid = tid;
    }
    
## Monitor Configuration Files ##

### monitor.propertiers ###
The monitor properties are used to define the monitor configuration and behaviuor.

|Property Name|Description|
|-------------|-----------|
|managers|A comma seperated list of locators that have a JMX manager defined|
|port|The port number assiigned to the JMX manager|
|command-port|The incoming TCP/IP port the monitor listens on for operator commands|
|message-duration|The time (in minutes) the monitor will wait before sending a duplicate message|
|maximum-duplicates|The maximum number of duplicate messages|
|reconnect-wait-time|The time (in minutes) the monitor will wait before trying to reconnect to a locator|
|reconnect-retry-attempts|The number of retry attempts before the monitor will try to use the next locator JMX manager defined in the managers property|
|log-file-size|The maximum size of a monitor log file|
|log-file-backups|The number of log backupsto maintain before rolling off the oldest log file|
|health-check-enabled|A boolean flag used to enable or disable the health check feature of the monitor|
|health-check-interval|The interval on how often the health check is performed|

### alert.properties ###
The alert properties are used to define the properties for sending alerts.

|Property Name|Description|
|-------------|-----------|
|alert-url|The HTTP/S URL used to send alerts|
|alert-url-parms|The HTTP/S request header parameters|
|alert-cluster-id|The id of the cluster as defined in the HTTP/S alert database|

### health.properties ###
The health properties are used to define the properties performing cluster health check.

|Property Name|Description|
|-------------|-----------|
|health-check-cmdb-url|The HTTP/S URL or file used to retrieve CMDB details for a cluster|
|health-check-cmdb-url-parms|The HTTP/S header parameters|
|health-check-cmdb-id|The id of the cluster used by CMDB service to retrieve CMDB details for the cluster|

### log4j.properties ###
The log4j properties are used to define the monitor logging behavior.

log4j.rootLogger=TRACE, stdout
log4j.rootLogger=OFF
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog=org.apache.log4j.RollingFileAppender
***log4j.appender.applicationLog.File=${log-file-location}/Alert_Health_Monitor.log***

    Change this property to control where monitor log is written

log4j.appender.applicationLog.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog.MaxFileSize=2000KB
log4j.appender.applicationLog.MaxBackupIndex=5
log4j.appender.exceptionLog=org.apache.log4j.RollingFileAppender
***log4j.appender.exceptionLog.File=${log-file-location}/Alert_Health_Monitor_Exceptions***

    Change this property to control where monitor exception log is written

log4j.appender.exceptionLog.layout=org.apache.log4j.PatternLayout
log4j.appender.exceptionLog.MaxFileSize=2000KB
log4j.appender.exceptionLog.MaxBackupIndex=5
log4j.category.applicationLog=TRACE, applicationLog
log4j.additivity.applicationLog=false
log4j.category.exceptionLog=DEBUG, exceptionLog
log4j.additivity.exceptionLog=false

### gemfireThreads.xml ###
The gemfireThreads XML file contain the messages generated by Geode/GemFire threads that will be supprersed by the monitor.

    <gemfireThreads>
        <gemfireThread thread="Event Processor for GatewaySender" />
        <gemfireThread thread="WAN Locator Discovery Thread" />
    </gemfireThreads>

### excludedMessages.xml ###
The excludedMessages XML file contain the messages or message snippets generated by Geode/GemFire that will be suppressed by the monitor.

    <excludedMessages>
        <excludedMessage>
            <criteria>DISTRIBUTED_NO_ACK but enable-network-partition-detection</criteria>
        </excludedMessage>
        <excludedMessage>
            <criteria>DLockRequestProcessor</criteria>
            <criteria>have elapsed while waiting for replies</criteria>
        </excludedMessage>
    </excludedMessages>

### mxbeans.xml ###
The mxbeans XML file contain the JMX mBeans objects and associated object properties used to monitor Geode/GemFire metrics

    <mxBeans sampleTime="5000">
         <mxBean mxBeanName="DistributedSystemMXBean">
             <fields>
                 <field beanProperty="" fieldName="UsedHeapSize" fieldSize="ACTUAL" count="0" percentage=".75"
                    percentageField="TotalHeapSize" percentageFieldSize="ACTUAL" />
                 <field beanProperty="" fieldName="JVMPauses" fieldSize="ACTUAL" count="2" percentage="0" 
                    percentageField="" percentageFieldSize="ACTUAL" />
             </fields>
         </mxBean>
    </mxBeans>

### mxBean XML Properties ###

1. mxBeanName - The Geode/GemFire mBean object name
2. Fields
3. Field
   * beanProperty - This property is used define a particular mBean object such as a region, disk store name, etc
   * fieldName - The mBean field name to monitor
   * fieldSize - Enum ACTUAL/KILOBYTES/MEGABYTES
   * count - Threashold as a count 
   * percentage - Threshold as a percentage 
   * percentageField - The mBean field that will be used to validate if a threshold for a metric is exceeded.
   * percentageFieldSize - Enum ACTUAL/KILOBYTES/MEGABYTES
   
## Dockerfile

The Geode/GemFire monitor can be run in a docker container. 

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

### Docker Build ###

docker build -t geode-monitor .

### Docker Run ###
docker run -d -it geode-monitor

## Start Monitor Script (start_monitor.sh) ##

#!/bin/bash   
java -cp java -cp conf/:lib/* -Dlog-file-location=/geode/monitor/logs util.geode.monitor.jmx.StartMonitor  

# Geode/GemFire JMX Monitor Command Client #

The Geode/GemFire JMX Monitor Command Client application is used to send commands to the Geode/GemFire JMX Monitor. The Monitor Command requires three (3) arguments. 

    -h = hostname or IP address where the Geode/GemFire monitor is running
    -p = port number the Geode?GemFire JMX Monitor listens for incoming connections.
    -c = command to run [Reload,Status,Shutdown,Block,Unblock]

## Client Commands ##

1. RELOAD - This command will reload the excluded message file to pick up new message exclusions. The execludedMessages.xml file in the Geode/GemFire JMX monitor will need to be modified in the contqainer (if Docker container is used) 
2. STATUS - Provides the status of the monitor and if the monitor is connected to Geode/GemFire locator(s).
3. SHUTDOWN - Shutdown the Geode/GemFire JMX monitor
4. BLOCK - This command will block alerts for a cluster member. The format of this command is BLOCK|[Member Name]
5. UNBLOCK - This command will unblock alerts for a cluster member. The format of this command is UNBLOCK|[Member Name]

### Client Command Example ###
java -cp lib/* util.geode.monitor.client.MonitorCommand -h localhost -p 1099 -c status

## Monitor Command Script (monitor_command.sh) ##

#!/bin/bash   
java -cp conf/:lib/* util.geode.monitor.client.MonitorCommand $*   

# JSON Payload for CMDB #

The CMDB service or a file can be used to provide the configuration management details of a GemFire cluster being monitor and are outlined below:

      {
    	"cluster": "Cluster-1",   
       	"site": "PasV2",   
       	"environment": "Development",   
       	"locatorCount": 1,   
       	"serverCount": 2,   
       	"maximumHeapUsagePercent": 0.95,   
       	"maximumGCTimeMillis": 1000,   
       	"gateway": "true",   
       	"gatewayMaximumQueueSize": 500,   
       	"locators": [   
             	{
               	   "name": "locator1",   
               	   "host": "RCPLT001",   
               	   "port": 10334   
             	}   
       	],    
    	"servers": [   
             	{   
               	   "name": "server1",   
               	   "host": "RCPLT001",   
               	   "port": 40404   
             	},   
             	{   
               	   "name": "server11",   
               	   "host": "RCPLT001",   
               	   "port": 40405   
             	}   
       	]   
      }   
