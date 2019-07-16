# Geode/GemFire JMX Monitor

The datatx-geode-jmx-monitor is an application which is used to monitor a Geode/GemFire cluster and generate alert messages based on JMX notifications sent from Geode/GemFire locator(s) to the monitor. JMX notifications are error messages written to Geode/GemFire logs with a severity of "warning" or higher. Additional notifications that are also sent from the Geode/GemFire locators include the departure, crash or joining of a member to the cluster.

Another component of the monitor allows for defining Geode/GemFire metrics which can be monitored and will generate alerts in the event the metric threshold is exceeded.
