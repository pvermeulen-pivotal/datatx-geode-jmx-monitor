package util.geode.monitor.jmx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.bind.JAXBContext;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import util.geode.monitor.Constants;
import util.geode.monitor.Monitor;
import util.geode.monitor.impl.MonitorImpl;
import util.geode.monitor.log.LogMessage;
import util.geode.monitor.xml.ExcludedMessageObjectFactory;
import util.geode.monitor.xml.ExcludedMessages;

/**
 * @author PaulVermeulen
 *
 */
public class StartMonitor extends MonitorImpl implements Monitor {
	private static final String ALERT_URL = "alert-url";
	private static final String ALERT_URL_PARMS = "alert-url-parms";
	private static final String ALERT_CLUSTER_ID = "alert-cluster-id";
	private static final String CMDB_HEALTH_JSON = "cmdb-health.json";
	private static final String ALERT_PROPS = "alert.properties";
	private static final String HEALTH_PROPS = "health.properties";
	private static final String SEVERITY_MAPPING_PROPS = "severity-mapping.properties";
	private static final String ALERT_MAPPING_PROPS = "alert-mapping.properties";
	private static final String HEALTH_CHK_CMDB_URL = "health-check-cmdb-url";
	private static final String HEALTH_CHK_CMDB_ID = "health-check-cmdb-id";
	private static final String HEALTH_CHK_CMDB_URL_PARMS = "health-check-cmdb-url-parms";
	private static final String CLUSTER = "cluster";
	private static final String SITE = "site";
	private static final String ENVIRONMENT = "environment";
	private static final String USE_FILE = "usefile:";
	private static final String SEMI_COLON = ";";
	private static final String COMMA = ",";

	private Properties alertProps;
	private String alertUrl;
	private String alertClusterId;
	private String healthCheckCmdbUrl;
	private String healthCheckCmdbId;
	private HashMap<String, String> httpAlertParams = new HashMap<String, String>();
	private HashMap<String, String> httpHealthParams = new HashMap<String, String>();
	private HashMap<String, String> severityMapping = new HashMap<String, String>();
	private List<String> alertMapping = new ArrayList<String>();

	/**
	 * Main monitor method
	 * 
	 * @param args
	 *
	 * @return
	 */
	static public void main(String[] args) throws Exception {
		boolean opened = true;
		StartMonitor monitor = new StartMonitor();

		monitor.initialize();

		if (!monitor.loadAlertProperties(monitor.getApplicationLog())) {
			monitor.getApplicationLog().error("(main) Geode/GemFire Monitor failed to load alert.properties file");
			return;
		}

		if (!monitor.loadSeverityMappingProperties(monitor.getApplicationLog())) {
			monitor.getApplicationLog()
					.error("(main) Geode/GemFire Monitor failed to load severity-mapping.properties file");
			return;
		}

		if (!monitor.loadAlertMappingProperties(monitor.getApplicationLog())) {
			monitor.getApplicationLog()
					.error("(main) Geode/GemFire Monitor failed to load alert-mapping.properties file");
			return;
		}

		if (!monitor.loadHealthProperties(monitor.isHealthCheck(), monitor.getApplicationLog())) {
			monitor.getApplicationLog().error("(main) Geode/GemFire Monitor failed to load health.properties file");
			return;
		}

		JSONObject jObj = new JSONObject(monitor.getCmdbHealth(monitor.getApplicationLog()));
		if (jObj != null) {
			monitor.setCluster((String) monitor.getJsonField(jObj, CLUSTER, monitor.getApplicationLog()));
			monitor.setSite((String) monitor.getJsonField(jObj, SITE, monitor.getApplicationLog()));
			monitor.setEnvironment((String) monitor.getJsonField(jObj, ENVIRONMENT, monitor.getApplicationLog()));
		}

		monitor.start();

		ServerSocket commandSocket = new ServerSocket(monitor.getCommandPort());

		while (opened) {
			Socket connectionSocket = commandSocket.accept();
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			String message = inFromClient.readLine();
			monitor.getApplicationLog().info("(socket-command) Received socket command=" + message);
			if (Constants.RELOAD.equals(message)) {
				monitor.setExcludedMessages((ExcludedMessages) monitor.getUtil().processJAXB(
						JAXBContext.newInstance(ExcludedMessageObjectFactory.class), Constants.EXCLUDED_MESSAGE_FILE));
				outToClient.writeBytes(Constants.OK + "\n");
			} else if (Constants.SHUTDOWN.equals(message)) {
				monitor.setShutdown(true);
				monitor.disconnect();
				outToClient.writeBytes(Constants.OK + "\n");
				opened = false;
			} else if (message.startsWith(Constants.BLOCK)) {
				String[] msgParts = message.split("|");
				if (msgParts != null && msgParts.length == 2) {
					monitor.addBlocker(msgParts[1]);
					outToClient.writeBytes(Constants.OK + "\n");
				} else {
					outToClient.writeBytes(Constants.INVALID_CMD + "\n");
				}
			} else if (message.startsWith(Constants.UNBLOCK)) {
				String[] msgParts = message.split("|");
				if (msgParts != null && msgParts.length == 2) {
					monitor.removeBlocker(msgParts[1]);
					outToClient.writeBytes(Constants.OK + "\n");
				} else {
					outToClient.writeBytes(Constants.INVALID_CMD + "\n");
					monitor.getApplicationLog().warn("(socket-command) " + Constants.INVALID_CMD);
				}
			} else if (Constants.STATUS.equals(message)) {
				if (monitor.isAttachedToManager()) {
					outToClient.writeBytes(Constants.RUNNING_CONNECT + "\n");
				} else {
					outToClient.writeBytes(Constants.RUNNING + "\n");
				}
			} else {
				outToClient.writeBytes(Constants.INVALID_CMD + "\n");
				monitor.getApplicationLog().warn("(socket-command) " + Constants.INVALID_CMD);
			}
			connectionSocket.close();
			commandSocket.close();
		}
	}

	private Object getJsonField(JSONObject jObj, String key, Logger log) {
		try {
			return jObj.get(key);
		} catch (JSONException e) {
			log.error("(getJsonField) Failed to return value for json field " + key);
		}
		return null;
	}

	/**
	 * TrustManager
	 * 
	 * Used to accept any server's certificate without requiring trust store
	 * 
	 * @return
	 */
	private TrustManager[] get_trust_mgr() {
		TrustManager[] certs = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String t) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String t) {
			}
		} };
		return certs;
	}

	/**
	 * setupSSL
	 * 
	 * Configures TLS socket factory
	 * 
	 * @return
	 * @throws Exception
	 */
	private SSLConnectionSocketFactory setupSSL() throws Exception {
		SSLContext ssl_ctx = SSLContext.getInstance(Constants.TLS);
		TrustManager[] trust_mgr = get_trust_mgr();
		ssl_ctx.init(null, trust_mgr, new SecureRandom());
		HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
		return new SSLConnectionSocketFactory(ssl_ctx, allowAllHosts);
	}

	/**
	 * loadMappingProperties
	 * 
	 * Load the severity-mapping.properties file
	 * 
	 * @param log
	 * @return
	 * @throws Exception
	 */
	private boolean loadSeverityMappingProperties(Logger log) throws Exception {
		Properties mappingProps = new Properties();
		mappingProps.load(StartMonitor.class.getClassLoader().getResourceAsStream(SEVERITY_MAPPING_PROPS));
		if (mappingProps.isEmpty()) {
			log.error("(loadSeverityMappingProperties) No severity-mapping properties found or contains no values");
			return false;
		}

		Set<Object> mappingKeys = mappingProps.keySet();
		for (Object key : mappingKeys) {
			severityMapping.put(((String) key).toUpperCase(), ((String) mappingProps.get(key)).toUpperCase());
		}
		return true;
	}

	/**
	 * loadAlertMappingProperties
	 * 
	 * Load the alert-mapping.properties file
	 * 
	 * @param log
	 * @return
	 * @throws Exception
	 */
	private boolean loadAlertMappingProperties(Logger log) throws Exception {
		Properties alertMappingProps = new Properties();
		alertMappingProps.load(StartMonitor.class.getClassLoader().getResourceAsStream(ALERT_MAPPING_PROPS));
		if (alertMappingProps.isEmpty()) {
			log.error("(loadAlertMappingProperties) No alert-mapping properties found or contains no values");
			return false;
		}

		Set<Object> mappingKeys = alertMappingProps.keySet();
		for (Object key : mappingKeys) {
			alertMapping.add((String) key + "=" + (String) alertMappingProps.get(key));
		}
		return true;
	}

	/**
	 * loadHealthProperties
	 * 
	 * loads the health.properties file
	 * 
	 * @return
	 * @throws Exception
	 */
	private boolean loadHealthProperties(boolean healthCheck, Logger log) throws Exception {
		boolean healthLoaded = true;
		Properties healthProps = new Properties();
		healthProps.load(StartMonitor.class.getClassLoader().getResourceAsStream(HEALTH_PROPS));
		if (healthProps.isEmpty()) {
			log.error("(loadHealthProperties) No health properties found or contains no values");
			return false;
		}
		healthCheckCmdbUrl = (String) healthProps.get(HEALTH_CHK_CMDB_URL);
		healthCheckCmdbId = (String) healthProps.get(HEALTH_CHK_CMDB_ID);
		if (healthCheckCmdbUrl == null || healthCheckCmdbUrl.length() == 0) {
			if (healthCheck) {
				log.error(
						"(loadHealthProperties) The health-check-cmdb-url in the health.properties file is not defined or is invalid + cmdb-url="
								+ healthCheckCmdbUrl);
				healthLoaded = false;
			}
		}

		String urlParams = (String) healthProps.get(HEALTH_CHK_CMDB_URL_PARMS);
		if (urlParams != null && urlParams.length() > 0) {
			if (!urlParams.endsWith(SEMI_COLON)) {
				urlParams = urlParams + SEMI_COLON;
			}
			String[] params = urlParams.split(SEMI_COLON);
			if (params != null && params.length > 0) {
				for (String str : params) {
					String[] keyValue = str.split(COMMA);
					if (keyValue != null && keyValue.length > 0) {
						httpHealthParams.put(keyValue[0], keyValue[1]);
					}
				}
			}
		}
		return healthLoaded;
	}

	/**
	 * loadAlertProperties
	 * 
	 * loads the alert.properties file
	 * 
	 * @return
	 * @throws Exception
	 */
	private boolean loadAlertProperties(Logger log) throws Exception {
		boolean alertLoaded = false;
		alertProps = new Properties();
		alertProps.load(StartMonitor.class.getClassLoader().getResourceAsStream(ALERT_PROPS));
		if (alertProps.isEmpty()) {
			log.error("(loadAlertProperties) No alert properties found or contains no values");
			return false;
		}
		alertUrl = (String) alertProps.get(ALERT_URL);
		if (alertUrl != null && alertUrl.length() > 0) {
			alertLoaded = true;
			String urlParams = (String) alertProps.get(ALERT_URL_PARMS);
			if (urlParams != null && urlParams.length() > 0) {
				if (!urlParams.endsWith(SEMI_COLON)) {
					urlParams = urlParams + SEMI_COLON;
				}
				String[] params = urlParams.split(SEMI_COLON);
				if (params != null && params.length > 0) {
					for (String str : params) {
						String[] keyValue = str.split(COMMA);
						if (keyValue != null && keyValue.length > 0) {
							httpAlertParams.put(keyValue[0], keyValue[1]);
						}
					}
				}
			}
			alertClusterId = alertProps.getProperty(ALERT_CLUSTER_ID);
		}
		return alertLoaded;
	}

	/**
	 * getSeverityMapping
	 * 
	 * Get the mapping for the severity
	 * 
	 * @param severity
	 * @return
	 */
	private String getSeverityMapping(String severity) {
		return severityMapping.get(severity.toUpperCase());
	}

	private String convertEventToJson(Logger log, String severity, LogMessage logMessage) {
		JSONObject jObj = new JSONObject();
		for (String map : alertMapping) {
			String[] fields = map.split("=");
			if (fields != null && fields.length == 2) {
				if (fields[1].toUpperCase().equals("ALERTCLUSTERID")) {
					jObj.put(fields[0], alertClusterId);
				} else if (fields[1].toUpperCase().equals("MEMBER")) {
					jObj.put(fields[0], logMessage.getHeader().getMember());
				} else if (fields[1].toUpperCase().equals("DATE")) {
					jObj.put(fields[0], logMessage.getHeader().getDate());
				} else if (fields[1].toUpperCase().equals("TIME")) {
					jObj.put(fields[0], logMessage.getHeader().getTime());
				} else if (fields[1].toUpperCase().equals("SEVERITY")) {
					jObj.put(fields[0], severity);
				} else if (fields[1].toUpperCase().equals("MESSAGE")) {
					jObj.put(fields[0], logMessage.getHeader().toString() + " " + logMessage.getBody().toString());
				}
			} else {
				log.error("(convertEventToJson) Error proccessing alertMapping, invalid value " + map);
			}
		}
		return jObj.toString();
	}

	/**
	 * sendAlert
	 * 
	 * Send alert to endpoint
	 * 
	 * @param logMessage
	 * 
	 * @return
	 */
	@Override
	public void sendAlert(LogMessage logMessage, Logger log) {
		log.debug("(sendAlert) Sending Alert Message: url=" + alertUrl + " message=" + logMessage.toString());

		String severity = getSeverityMapping(logMessage.getHeader().getSeverity());

		String json = convertEventToJson(log, severity, logMessage);

		log.debug("(sendAlert) Sending Alert Message json payload=" + json);

		if (alertUrl.toLowerCase().startsWith(USE_FILE)) {
			try {
				String fileName = alertUrl.substring(USE_FILE.length());
				if (fileName != null && fileName.length() > 0) {
					FileUtils.writeStringToFile(new File(alertUrl.substring(USE_FILE.length())), json + "\n",
							Charset.defaultCharset(), true);
				} else {
					log.error("(sendAlert) file method exception: no file defined in " + alertUrl);
				}
			} catch (IOException e) {
				log.error("(sendAlert) file method exception: " + e.getMessage());
			}
			return;
		}

		CloseableHttpClient httpclient = null;
		try {
			if (alertUrl.startsWith("https")) {
				httpclient = HttpClients.custom().setSSLSocketFactory(setupSSL()).build();
			} else {
				httpclient = HttpClients.createDefault();
			}
		} catch (Exception e) {
			log.error("(sendAlert) Error creating custom HttpClients exception=" + e.getMessage());
		}
		if (httpclient == null)
			return;

		HttpPost httppost = new HttpPost(alertUrl);

		try {
			StringEntity sEntity = new StringEntity(json);
			Set<String> keys = httpAlertParams.keySet();
			for (String key : keys) {
				httppost.addHeader(key, httpAlertParams.get(key));
			}
			try {
				httppost.setEntity(sEntity);
				HttpResponse response = null;
				try {
					response = httpclient.execute(httppost);
					int code = response.getStatusLine().getStatusCode();
					if (code != 201) {
						log.info("(sendAlert) Alert URL Post Response Code: " + code);
					}
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						try {
							InputStream instream = entity.getContent();
							byte[] responseData = new byte[5000];
							int bytesRead = instream.read(responseData);
							if (bytesRead > 0) {
								log.debug("(sendAlert) Alert URL Post Response: " + new String(responseData));
							} else {
								log.info("(sendAlert) No Alert URL Post Response received");
							}
							responseData = null;
							instream.close();
						} catch (Exception e) {
							log.error("(sendAlert) Error reading http response exception: " + e.getMessage());
						}
					}
				} catch (Exception e) {
					log.error("(sendAlert) Error executing HTTP post exception: " + e.getMessage());
				}
			} catch (Exception e) {
				log.error("(sendAlert) Error adding header/entity exception: " + e.getMessage());
			}
		} catch (UnsupportedEncodingException e) {
			log.error("(sendAlert) Error creating string entity exception=" + e.getMessage());
		}

		if (httpclient != null) {
			try {
				httpclient.close();
			} catch (IOException e) {
				log.error("(sendAlert) Error closing HTTPClient exception=" + e.getMessage());
			}
		}
	}

	/**
	 * getCmdbHealth
	 * 
	 * Get the health properties from the CMDB
	 * 
	 * @return
	 */
	@Override
	public String getCmdbHealth(Logger log) {
		CloseableHttpClient httpclient = null;
		String cmdbResponse = null;

		log.debug("(getCmdbHealth) Getting CMDB health");

		if (healthCheckCmdbUrl.toLowerCase().startsWith(USE_FILE)) {
			try {
				String fileName = healthCheckCmdbUrl.substring(USE_FILE.length());
				if (fileName != null && fileName.length() > 0) {
					cmdbResponse = new String(Files.readAllBytes(Paths.get(CMDB_HEALTH_JSON)));
					return cmdbResponse;
				} else {
					log.error("(getCmdbHealth) file method exception: no file defined in " + healthCheckCmdbUrl);
				}
			} catch (IOException e) {
				log.error("(getCmdbHealth) file method exception: " + e.getMessage());
			}
			return null;
		}

		try {
			if (healthCheckCmdbUrl.startsWith("https")) {
				httpclient = HttpClients.custom().setSSLSocketFactory(setupSSL()).build();
			} else {
				httpclient = HttpClients.createDefault();
			}

			URIBuilder builder;
			if (!healthCheckCmdbUrl.endsWith("/")) {
				builder = new URIBuilder(healthCheckCmdbUrl + "/" + healthCheckCmdbId);
			} else {
				builder = new URIBuilder(healthCheckCmdbUrl + healthCheckCmdbId);
			}

			HttpGet httpGet = new HttpGet(builder.build());
			Set<String> keys = httpHealthParams.keySet();
			for (String key : keys) {
				httpGet.addHeader(key, httpHealthParams.get(key));
			}
			HttpResponse response = null;
			try {
				response = httpclient.execute(httpGet);
				int code = response.getStatusLine().getStatusCode();
				log.debug("(getCmdbHealth) HTTP CMDB response code: " + code);
				if (code == 200) {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						try {
							InputStream instream = entity.getContent();
							byte[] responseData = new byte[5000];
							int bytesRead = instream.read(responseData);
							if (bytesRead > 0) {
								cmdbResponse = new String(responseData).trim();
								if (!cmdbResponse.startsWith("{"))
									cmdbResponse = "{" + cmdbResponse;
								if (!cmdbResponse.endsWith("}"))
									cmdbResponse = cmdbResponse + "}";
								log.info("(getCmdbHealth) CMDB HTTP Get Response: " + cmdbResponse);
							} else {
								log.info("(getCmdbHealth) CMDB HTTP no response to Get received");
							}
							responseData = null;
							instream.close();
						} catch (Exception e) {
							log.error("(getCmdbHealth) Error reading HTTP CMDB Get response exception: "
									+ e.getMessage());
						}
					} else {
						log.warn("(getCmdbHealth) HTTP CMDB Get response entity was null");
					}
				} else {
					log.error("(getCmdbHealth) Invalid response code received from HTTP CMDB Get code = " + code);
				}
			} catch (Exception e) {
				log.error("(getCmdbHealth) Error executing HTTP CMDB Get exception: " + e.getMessage());
			}
		} catch (Exception e) {
			log.error("(getCmdbHealth) Error adding CMDB header/entity exception: " + e.getMessage());
		}

		if (httpclient != null) {
			try {
				httpclient.close();
			} catch (IOException e) {
				log.error("(getCmdbHealth) Error closing CMDB HTTP Client exception: " + e.getMessage());
			}
		}

		return cmdbResponse;
	}
}
