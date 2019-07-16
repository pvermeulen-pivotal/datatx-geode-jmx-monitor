# Geode/GemFire JMX Monitor

The datatx-geode-jmx-monitor is an application which is used to monitor a Geode/GemFire cluster and generate alert messages based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. JMX notifications are error messages written to Geode/GemFire logs with a severity of "warning" or higher. Additional notifications that are also sent from the Geode/GemFire locators include the departure, crash or joining of a member to the cluster.

Another feature of the monitor application supports the definition Geode/GemFire metrics which can be monitored and have alerts generated in the event a metric threshold is exceeded. - This property 

## Monitor Configuration Files

### monitor.propertiers
The monitor properties are used to define the monitor configuration and behaviuor.

1. managers - This property is a comma seperated list of locators that have a JMX manager defined=localhost
2. port - This property defines the port number assiigned to the JMX manager
3. command-port - This property defines the incoming TCP/IP port the monitor listens on for operator commands
4. message-duration  - The time in minutes the monitor will wait before sending a duplicate message
5. maximum-duplicates - The maximum number of duplicate messages
6. reconnect-wait-time - The time in minutes the monitor will wait before trying to reconnect to a locator  
7. reconnect-retry-attempts - The number of retry attempts before the monitor will try to use the next locator JMX maqnaqger defined in the managers property. 
8. log-file-size - The maximum size of a monitor log file
9. log-file-backups - The number of log backupsto maintain before rolling off the oldest log file

### log4j.properties
The log4j properties are used to define the monitor logging behavior.

log4j.rootLogger=TRACE, stdout
log4j.rootLogger=OFF
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog=org.apache.log4j.RollingFileAppender
log4j.appender.applicationLog.File=/usr/bin/monitor/logs/Alert_Health_Monitor.log
log4j.appender.applicationLog.layout=org.apache.log4j.PatternLayout
log4j.appender.applicationLog.MaxFileSize=2000KB
log4j.appender.applicationLog.MaxBackupIndex=5
log4j.appender.exceptionLog=org.apache.log4j.RollingFileAppender
log4j.appender.exceptionLog.File=/usr/bin/monitor/logs/Alert_Health_Monitor_Exceptions
log4j.appender.exceptionLog.layout=org.apache.log4j.PatternLayout
log4j.appender.exceptionLog.MaxFileSize=2000KB
log4j.appender.exceptionLog.MaxBackupIndex=5
log4j.category.applicationLog=TRACE, applicationLog
log4j.additivity.applicationLog=false
log4j.category.exceptionLog=DEBUG, exceptionLog
log4j.additivity.exceptionLog=false

###
gemfireThreads.xml

<gemfireThreads>
	<gemfireThread event="Event Processor for GatewaySender_" />
	<gemfireThread event="WAN Locator Discovery Thread" />
</gemfireThreads>
