# Geode/GemFire JMX Monitor #

The datatx-geode-jmx-monitor project is an application to monitor a Geode/GemFire cluster for errors, exceeded thresholds, health check and 
generates an alert message based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. 

JMX notifications are either error messages written to Geode/GemFire logs with a severity of "warning" or higher or notifications that are sent from the JMX agent for the departure, crash or joining of a member to the cluster.

The monitor application provides two (2) additional features.    
   1. Supports monitoring any of the Geode/GemFire metrics and generates alert in the event a metric threshold is exceeded.   
   2. Supports a health check performing validation of member counts, locators, cache servers, gateways along with a couple of key metrics to ensure the cluster is healthy.     

The additional features are managed using separate threads and the defined execution intervals are in the monitor.properties 
for the health check and in the mxbeans.xml for metrics.

The monitor application supports the definition of multiple locators acting as JMX managers. The monitor will connect to only 
one locator JMX manager at a time but if the locator is stopped or crashed, the monitor will connect to the next locator JMX manager 
in the configured list. Whenever a connection is lost to the locator, the monitor switches to another locator, sends an alert 
for the lost connection and continues monitoring the GemFire cluster.

This project implements the abstract project datatx-geode-monitor which provides the majority of the monitoring, metrics and 
device health  functionality with the exception of two abstract methods sendAlert and getCmdbHealth. Users will need to modify 
the StartMonitor class and override sendAlert and getCmdbHealth methods to manage the endpoint where alerts are sent and a 
CMDB endpoint or file that defines the details about a cluster. 

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
When the package is built, maven uses an assembly.xml file to create a zip file containing a deployable monitor. Unzip 
the package file to a location and the unzipped file creates the monitor directory, all sub-directories, scripts, configuration 
files and required jar files.

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

## Start Monitor Script (start_monitor.sh/start_monitor.cmd) ##
	
The start monitor script is used to start the monitor and requires a runtime property "log-file-location" to be set to the 
location where the monitor's log and exception files are wriiten. 

    #!/bin/bash    
    java -cp java -cp conf/:lib/* -Dlog-file-location=/geode/monitor/logs util.geode.monitor.jmx.StartMonitor    

## LogMessage Format: ##

The following outlines the LogMessage class:   

    public class LogMessage {
       private LogHeader header;
       private Notification event;
       private String body;
       private int count = 1;
    }
    
The following outlines the LogHeader class:   

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
    
## Configuration Files ##

### monitor.properties ###
The monitor properties are used to define the monitor configuration and behavior.

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
|log-file-backups|The number of log backups to maintain before rolling off the oldest log file|
|threshold-alert-count|The number of a threshold metric value exceeded before generating an alert|
|threshold-alert-ttl|The time in minutes before purging threshold metric alert if the threshold-alert-count is not exceeded|
|health-check-enabled|A boolean flag used to enable or disable the health check feature of the monitor|
|health-check-interval|The interval on how often the health check is performed|

### alert.properties ###
The alert properties are used to define the properties for sending alerts.

|Property Name|Description|
|-------------|-----------|
|alert-url|The HTTP/S URL or file used to send or capture alerts|
||To use a file as output define the value as ***usefile:location/name***|
|alert-url-parms|The HTTP/S request header parameters|
|alert-cluster-id|The id of the cluster as defined in the HTTP/S alert database|

### alert-mapping.properties ###
The alert-mapping properties are used to map GemFire alert to user JSON message.

The following monitor types are available for mapping a user alert.

|Monitor Type|Description|
|------------|-----------|
|alertClusterId|Alert cluster id defined in the alert properties|
|member|GemFire member that caused alert|
|date|Date of the alert|
|time|Time of the alert|
|severity|GemFire severity|
|message|Complete message|

***The order of the json object are based on the order of the properties*** 

Properties

|JSON User Field|Monitor Type|
|---------------|------------|
|fqdn|alertClusterId|
|severity|severity|
|message|message|

### severity-mapping.properties ###
The severity-mapping properties are used to map GemFire log severity to user severity.

|GemFire Log Severity Codes|
|--------------------------|
|SEVERE|
|ERROR|
|WARNING|
|INFO|
|CONFIG|

Properties

|GemFire Severity|Description|
|----------------|-----------|
|SEVERE|The user code for a GemFire severe error|
|ERROR|The user code for a GemFire error|
|WARNING|The user code for a GemFire warning|

### health.properties ###
The health properties are used to configure health check.

|Property Name|Description|
|-------------|-----------|
|health-check-cmdb-url|The HTTP/S URL or file used to retrieve CMDB details for a cluster|
||To use a file as input use the format of usefile:location/name|
|health-check-cmdb-url-parms|The HTTP/S header parameters|
|health-check-cmdb-id|The id of the cluster used by CMDB service to retrieve CMDB details for the cluster|

### log4j.properties ###
The log4j properties configure the monitor logging behavior.

log4j.rootLogger=TRACE, stdout
log4j.rootLogger=OFF
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog=org.apache.log4j.RollingFileAppender
log4j.appender.applicationLog.File=***${log-file-location}***/Alert_Health_Monitor.log

log4j.appender.applicationLog.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog.MaxFileSize=2000KB
log4j.appender.applicationLog.MaxBackupIndex=5
log4j.appender.exceptionLog=org.apache.log4j.RollingFileAppender
log4j.appender.exceptionLog.File=***${log-file-location}***/Alert_Health_Monitor_Exception

log4j.appender.exceptionLog.layout=org.apache.log4j.PatternLayout
log4j.appender.exceptionLog.MaxFileSize=2000KB
log4j.appender.exceptionLog.MaxBackupIndex=5
log4j.category.applicationLog=TRACE, applicationLog
log4j.additivity.applicationLog=false
log4j.category.exceptionLog=DEBUG, exceptionLog
log4j.additivity.exceptionLog=false

### gemfireThreads.xml ###
The gemfireThreads XML file defines the messages generated by Geode/GemFire threads that will be suppressed by the monitor.

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
The mxbeans XML file contain the JMX mBeans Geode/GemFire objects and associated object properties for threshold monitoring. The 
sampleTime property is used to define the frequency of the threshold check in milliseconds.

    <mxBeans sampleTime="5000">
         <mxBean mxBeanName="DistributedSystemMXBean">
             <fields>
                 <field beanProperty="" fieldName="UsedHeapSize" fieldSize="ACTUAL" count="0" percentage=".75"
                    percentageField="TotalHeapSize" percentageFieldSize="ACTUAL" />
                 <field beanProperty="" fieldName="JVMPauses" fieldSize="ACTUAL" count="2" percentage="0" 
                    percentageField="" percentageFieldSize="ACTUAL" />
             </fields>
         </mxBean>
         <mxBean mxBeanName="MemberMXBean">
             <fields>
 		 <field beanProperty="" fieldName="NumThreads"
		    fieldSize="ACTUAL" count="0" percentage=".90" percentageField="250" percentageFieldSize="ACTUAL" />
             </fields>
         </mxBean>
    </mxBeans>

### mxBean XML Properties ###

The following define the node and attributes fields in the mxbeans.xml file. 

|Node|Name|Attribute|Description|
|----|----|---------|-----------|
|mxBeanName|||The Geode/GemFire mBean object name|
|Fields|||Collection of fields|
||Field||Field information|
|||beanProperty|Property defines a particular mBean object name for a region, disk store name, etc.|
|||fieldName|The mBean field name to monitor|
|||fieldSize|Enum ACTUAL/KILOBYTES/MEGABYTES|
|||count|Threshold is a count| 
|||percentage|Threshold is a percentage|
|||percentageField|The mBean field or constant that will be used to validate if a threshold for a metric is exceeded.|
|||percentageFieldSize|Enum ACTUAL/KILOBYTES/MEGABYTES|
   
## Dockerfile ##

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

To build the docker image, use the following docker command.

docker build -t geode-monitor .

### Docker Run ###

To run the docker image, use the following docker command.

docker run -d -it geode-monitor

## Monitor Command Script (monitor_command.sh/monitor_command.cmd) ##

The Geode/GemFire JMX Monitor Command is used to send commands to the Geode/GemFire JMX Monitor. 

#!/bin/bash   
java -cp conf/:lib/* util.geode.monitor.client.MonitorCommand $*   

The Monitor Command Client script requires three (3) arguments to be passed. 

|Argument|Value|Description|
|--------|-----|-----------|
|-h|hostname/IP address|The hostname or IP address of the JMX monitor|
|-p|port number|The port number the JMX Monitor listens for incoming connections from monitor.properties command-port|
|-c|command|The command to execute|
||Reload|Reload excluded message file|
||Status|Get status of monitor [RUNNING/RUNNING_CONNECTED]|
||Shutdown|Gracefully shuts down the monitor|
||Block|Blocks alerts from being sent for a Geode/GemFire cluster member|
||Unblock|Unblocks alerts from being sent for a Geode/GemFire cluster member|

### Client Command Example ###
java -cp lib/* util.geode.monitor.client.MonitorCommand -h localhost -p 1099 -c status

## JSON Payload for CMDB ##

The monitor requires a connection or a file to define the Geode/GemFire cluster CMDB. The CMDB service or a file is 
used to provide the configuration details of a GemFire cluster being monitored.   

***CMDB JSON payload is outlined below:***

      {
    	"cluster": "cluster-name",   
       	"site": "cluster-site",   
       	"environment": "cluster-environment",   
       	"locatorCount": 1,   
       	"serverCount": 2,   
       	"maximumHeapUsagePercent": 0.95,   
       	"maximumGCTimeMillis": 1000,   
       	"gateway": "true",   
       	"gatewayMaximumQueueSize": 500,   
       	"locators": [   
             	{
               	   "name": "locator1",   
               	   "host": "hostname",   
               	   "port": 10334   
             	}   
       	],    
    	"servers": [   
             	{   
               	   "name": "server1",   
               	   "host": "hostname",   
               	   "port": 40404   
             	},   
             	{   
               	   "name": "server11",   
               	   "host": "hostname",   
               	   "port": 40405   
             	}   
       	]   
      }   
