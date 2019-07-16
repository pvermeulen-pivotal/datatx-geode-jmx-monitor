# Geode/GemFire JMX Monitor

The datatx-geode-jmx-monitor is an application which is used to monitor a Geode/GemFire cluster and generate alert messages based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. JMX notifications are error messages written to Geode/GemFire logs with a severity of "warning" or higher. Additional notifications that are also sent from the Geode/GemFire locators include the departure, crash or joining of a member to the cluster.

Another feature of the monitor application supports the definition Geode/GemFire metrics which can be monitored and have alerts generated in the event a metric threshold is exceeded. - This property 

## Monitor Configuration Files

### monitor.propertiers
managers - This property is a comma seperated list of locators that have a JMX manager defined=localhost
port - This property defines the port number assiigned to the JMX manager
command-port - This property defines the incoming TCP/IP port the monitor listens on for operator commands
message-duration  - The time in minutes the monitor will wait before sending a duplicate message
maximum-duplicates - The maximum number of duplicate messages
reconnect-wait-time - The time in minutes the monitor will wait before trying to reconnect to a locator  
reconnect-retry-attempts - The number of retry attempts before the monitor will try to use the next locator JMX maqnaqger defined in the managers property. 
log-file-size - The maximum size of a monitor log file
log-file-backups - The number of log backupsto maintain before rolling off the oldest log file
