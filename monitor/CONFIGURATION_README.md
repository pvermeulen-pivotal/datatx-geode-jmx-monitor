# Geode/GemFire JMX Monitor Configuration

The following files will need to be changed for each installation. These files are located in the conf directory.

### monitor.properties ###

The following monitor properties ***do need*** to be changed for each installation:   

managers={A comma separated list of all locators defined to the cluster running JMX manager}

The following static monitor properties that ***do not need*** to be changed for each installation:           

port=1099   
command-port=6780   
message-duration=15   
maximum-duplicates=1   
reconnect-wait-time=60   
reconnect-retry-attempts=5   
log-file-size=2000000   
log-file-backups=5   
health-check-enabled=true
health-check-interval=10

### alert.properties ###

The following alert properties ***do need*** to be changed for each installation:      

alert-url={The URL used to send cluster/application events}      
alert-cluster-id={The fqdn name assigned to cluster and configured in the alert-url server}         

The following static alert properties that ***do not need*** to be changed for each installation:        

alert-url-parms=content-type,application/json;cache-control,no-cache;verify,false;   

### health.properties ###

The following alert properties ***do need*** to be changed for each installation:      

health-check-cmdb-url={The URL or file used to get cluster configuration}      
health-check-cmdb-id={The fqdn name assigned to cluster and configured in the cmdb-url server}         

The following static alert properties that ***do not need*** to be changed for each installation:        

health-check-cmdb-url-parms=content-type,application/json; 

### log4j.properties ###

The following log4j properties ***do need*** to be changed for each installation:   
      
log4j.appender.applicationLog.File=/opt/monitor/logs/Alert_Health_Monitor.log   
log4j.appender.exceptionLog.File=/opt/monitor/logs/Alert_Health_Monitor_Exceptions   

The following static log4j properties that ***do not need*** to be changed for each installation:   
        
log4j.rootLogger=TRACE, stdout   
log4j.rootLogger=OFF   
log4j.appender.stdout=org.apache.log4j.ConsoleAppender   
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout   
log4j.appender.applicationLog=org.apache.log4j.RollingFileAppender   
log4j.appender.applicationLog.layout=org.apache.log4j.PatternLayout   
log4j.appender.applicationLog.MaxFileSize=2000KB   
log4j.appender.applicationLog.MaxBackupIndex=5   
log4j.appender.exceptionLog=org.apache.log4j.RollingFileAppender     
log4j.appender.exceptionLog.layout=org.apache.log4j.PatternLayout    
log4j.appender.exceptionLog.MaxFileSize=2000KB   
log4j.appender.exceptionLog.MaxBackupIndex=5   
log4j.category.applicationLog=TRACE, applicationLog   
log4j.additivity.applicationLog=false   
log4j.category.exceptionLog=DEBUG, exceptionLog   
log4j.additivity.exceptionLog=false   
