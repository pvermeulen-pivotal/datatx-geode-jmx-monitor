# Geode/GemFire JMX Monitor

The datatx-geode-jmx-monitor is an application which is used to monitor a Geode/GemFire cluster and generate alert messages based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. JMX notifications are error messages written to Geode/GemFire logs with a severity of "warning" or higher. Additional notifications that are also sent from the Geode/GemFire locators include the departure, crash or joining of a member to the cluster.

Another feature of the monitor application supports the definition Geode/GemFire metrics which can be monitored and have alerts generated in the event a metric threshold is exceeded.

###Monitor Configuration Files


managers=localhost
port=1099
command-port=6780
message-duration=15
maximum-duplicates=1
reconnect-wait-time=60
reconnect-retry-attempts=5
log-file-size=2000000
log-file-backups=5
