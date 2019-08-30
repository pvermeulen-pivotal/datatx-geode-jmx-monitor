package util.geode.monitor.jmx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.bind.JAXBContext;

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
import org.json.JSONObject;

import util.geode.monitor.Constants;
import util.geode.monitor.Monitor;
import util.geode.monitor.impl.MonitorImpl;
import util.geode.monitor.log.LogMessage;
import util.geode.monitor.xml.ExcludedMessageObjectFactory;
import util.geode.monitor.xml.ExcludedMessages;

public class StartMonitor extends MonitorImpl implements Monitor {
	private static final String ALERT_URL = "alert-url";
	private static final String ALERT_URL_PARMS = "alert-url-parms";
	private static final String ALERT_CLUSTER_ID = "alert-cluster-id";
	private static final String CMDB_HEALTH_JSON = "cmdb-health.json";
	private static final String HEALTH_PROPS = "health.properties";
	private static final String HEALTH_CHK_CMDB_URL = "health-check-cmdb-url";
	private static final String HEALTH_CHK_CMDB_ID = "health-check-cmdb-id";
	private static final String HEALTH_CHK_CMDB_URL_PARMS = "health-check-cmdb-url-parms";

	private static Properties alertProps;
	private static String alertUrl;
	private static String alertClusterFqdn;
	private static String healthCheckCmdbUrl;
	private static String healthCheckCmdbId;
	private static StartMonitor monitor;

	private static HashMap<String, String> httpAlertParams = new HashMap<String, String>();
	private static HashMap<String, String> httpHealthParams = new HashMap<String, String>();

	static public void main(String[] args) throws Exception {
		monitor = new StartMonitor();
		boolean opened = true;

		monitor.initialize();

		if (!loadAlertProperties()) {
			monitor.getApplicationLog().error("Geode/GemFire Monitor failed to load alert property file");
			return;
		}

		if (!loadHealthProperties()) {
			monitor.getApplicationLog().error("Geode/GemFire Monitor failed to load health property file");
			return;
		}

		JSONObject jObj = new JSONObject(monitor.getCmdbHealth());
		if (jObj != null) {
			monitor.setCluster((String)jObj.get("cluster"));
			monitor.setSite((String) jObj.get("site"));
			monitor.setEnvironment((String) jObj.getString("environment"));
		}
		
		monitor.start();

		ServerSocket commandSocket = new ServerSocket(monitor.getCommandPort());

		while (opened) {
			Socket connectionSocket = commandSocket.accept();
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			String message = inFromClient.readLine();
			monitor.getApplicationLog().info("Received socket command=" + message);
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
					monitor.getApplicationLog().warn(Constants.INVALID_CMD);
				}
			} else if (Constants.STATUS.equals(message)) {
				if (monitor.isAttachedToManager()) {
					outToClient.writeBytes(Constants.RUNNING_CONNECT + "\n");
				} else {
					outToClient.writeBytes(Constants.RUNNING + "\n");
				}
			} else {
				outToClient.writeBytes(Constants.INVALID_CMD + "\n");
				monitor.getApplicationLog().warn(Constants.INVALID_CMD);
			}
			connectionSocket.close();
			commandSocket.close();
		}
	}

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

	private SSLConnectionSocketFactory setupSSL() throws Exception {
		SSLContext ssl_ctx = SSLContext.getInstance("TLS");
		TrustManager[] trust_mgr = get_trust_mgr();
		ssl_ctx.init(null, trust_mgr, new SecureRandom());
		HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
		return new SSLConnectionSocketFactory(ssl_ctx, allowAllHosts);
	}

	private static boolean loadHealthProperties() {
		boolean healthLoaded = true;
		Properties healthProps = new Properties();
		try {
			healthProps.load(StartMonitor.class.getClassLoader().getResourceAsStream(HEALTH_PROPS));
			healthCheckCmdbUrl = (String) healthProps.get(HEALTH_CHK_CMDB_URL);
			healthCheckCmdbId = (String) healthProps.get(HEALTH_CHK_CMDB_ID);
			if (healthCheckCmdbUrl == null || healthCheckCmdbUrl.length() == 0) {
				if (monitor.isHealthCheck()) {
					monitor.getApplicationLog().error(
							"The health-check-cmdb-url in the health.properties file is not defined or is invalid + cmdb-url="
									+ healthCheckCmdbUrl);
					healthLoaded = false;
				}
			} else {
				if (!healthCheckCmdbUrl.endsWith("/")) {
					healthCheckCmdbUrl = healthCheckCmdbUrl + "/";
				}
			}

			String urlParams = (String) healthProps.get(HEALTH_CHK_CMDB_URL_PARMS);
			if (urlParams != null && urlParams.length() > 0) {
				if (!urlParams.endsWith(";")) {
					urlParams = urlParams + ";";
				}
				String[] params = urlParams.split(";");
				if (params != null && params.length > 0) {
					for (String str : params) {
						String[] keyValue = str.split(",");
						if (keyValue != null && keyValue.length > 0) {
							httpHealthParams.put(keyValue[0], keyValue[1]);
						}
					}
				}
			}
		} catch (IOException e) {
			monitor.getApplicationLog().error("Error loading health.properties. Exception: " + e.getMessage());
			healthLoaded = false;
		}
		return healthLoaded;
	}

	private static boolean loadAlertProperties() {
		boolean alertLoaded = false;
		try {
			InputStream input = StartMonitor.class.getClassLoader().getResourceAsStream("alert.properties");
			alertProps = new Properties();
			try {
				alertProps.load(input);
				alertUrl = (String) alertProps.get(ALERT_URL);
				if (alertUrl != null && alertUrl.length() > 0) {
					alertLoaded = true;
					String urlParams = (String) alertProps.get(ALERT_URL_PARMS);
					if (urlParams != null && urlParams.length() > 0) {
						if (!urlParams.endsWith(";")) {
							urlParams = urlParams + ";";
						}
						String[] params = urlParams.split(";");
						if (params != null && params.length > 0) {
							for (String str : params) {
								String[] keyValue = str.split(",");
								if (keyValue != null && keyValue.length > 0) {
									httpAlertParams.put(keyValue[0], keyValue[1]);
								}
							}
						}
					}
					alertClusterFqdn = alertProps.getProperty(ALERT_CLUSTER_ID);
				}
			} catch (Exception e) {
				monitor.getApplicationLog().error("Error loading alert.properties Exception: " + e.getMessage());
			}
		} catch (Exception e) {
			monitor.getApplicationLog().error("Error loading alert.properties Exception: " + e.getMessage());
		}
		return alertLoaded;
	}

	@Override
	public void sendAlert(LogMessage logMessage) {
		monitor.getApplicationLog()
				.info("Sending Alert Message: url=" + alertUrl + " message=" + logMessage.toString());

		CloseableHttpClient httpclient = null;
		try {
			if (alertUrl.startsWith("https")) {
				httpclient = HttpClients.custom().setSSLSocketFactory(setupSSL()).build();
			} else {
				httpclient = HttpClients.createDefault();
			}
		} catch (Exception e) {
			monitor.getApplicationLog().error("Error creating custom HttpClients exception=" + e.getMessage());
		}
		if (httpclient == null)
			return;

		HttpPost httppost = new HttpPost(alertUrl);

		String severity = logMessage.getHeader().getSeverity();
		if (logMessage.getHeader().getSeverity().equals(Constants.WARNING)) {
			severity = "MINOR";
		}

		String json = new JSONObject().put("fqdn", alertClusterFqdn).put("severity", severity)
				.put("message", logMessage.getHeader().toString() + " " + logMessage.getBody()).toString();
		monitor.getApplicationLog().info("Sending Alert Message json payload=" + json);

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
					monitor.getApplicationLog().info("Alert URL Post Response Code: " + code);
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						try {
							InputStream instream = entity.getContent();
							byte[] responseData = new byte[1000];
							int bytesRead = instream.read(responseData);
							if (bytesRead > 0) {
								monitor.getApplicationLog()
										.info("Alert URL Post Response: " + new String(responseData));
							} else {
								monitor.getApplicationLog().info("No Alert URL Post Response received");
							}
							instream.close();
						} catch (Exception e) {
							monitor.getApplicationLog()
									.error("Error reading http response exception: " + e.getMessage());
						}
					}
				} catch (Exception e) {
					monitor.getApplicationLog().error("Error executing HTTP post exception: " + e.getMessage());
				}
			} catch (Exception e) {
				monitor.getApplicationLog().error("Error adding header/entity exception: " + e.getMessage());
			}
		} catch (UnsupportedEncodingException e) {
			monitor.getApplicationLog().error("Error creating string entity exception=" + e.getMessage());
		}

		if (httpclient != null) {
			try {
				httpclient.close();
			} catch (IOException e) {
				monitor.getApplicationLog().error("Error closing HTTPClient exception=" + e.getMessage());
			}
		}
	}

	/**
	 * HTTP service to get the health properties from the CMDB
	 * 
	 * @return
	 */
	@Override
	public String getCmdbHealth() {
		CloseableHttpClient httpclient = null;
		String cmdbResponse = null;

		monitor.getApplicationLog().info("Getting CMDB health");

		if (healthCheckCmdbUrl.toUpperCase().startsWith("USEFILE")) {
			try {
				cmdbResponse = new String(Files.readAllBytes(Paths.get(CMDB_HEALTH_JSON)));
			} catch (IOException e) {
				monitor.getApplicationLog().error("(getCmdbHealth) file method exception: " + e.getMessage());
			}
		} else {
			try {
				if (healthCheckCmdbUrl.startsWith("https")) {
					httpclient = HttpClients.custom().setSSLSocketFactory(setupSSL()).build();
				} else {
					httpclient = HttpClients.createDefault();
				}

				URIBuilder builder = new URIBuilder(healthCheckCmdbUrl + healthCheckCmdbId);
				HttpGet httpGet = new HttpGet(builder.build());
				Set<String> keys = httpHealthParams.keySet();
				for (String key : keys) {
					httpGet.addHeader(key, httpHealthParams.get(key));
				}
				HttpResponse response = null;
				try {
					response = httpclient.execute(httpGet);
					int code = response.getStatusLine().getStatusCode();
					monitor.getApplicationLog().info("HTTP CMDB response code: " + code);
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
									monitor.getApplicationLog().info("CMDB HTTP Get Response: " + cmdbResponse);
								} else {
									monitor.getApplicationLog().info("CMDB HTTP no response to Get received");
								}
								instream.close();
							} catch (Exception e) {
								monitor.getApplicationLog()
										.error("Error reading HTTP CMDB Get response exception: " + e.getMessage());
							}
						} else {
							monitor.getApplicationLog().warn("HTTP CMDB Get response entity was null");
						}
					} else {
						monitor.getApplicationLog()
								.error("Invalid response code received from HTTP CMDB Get code = " + code);
					}
				} catch (Exception e) {
					monitor.getApplicationLog().error("Error executing HTTP CMDB Get exception: " + e.getMessage());
				}
			} catch (Exception e) {
				monitor.getApplicationLog().error("Error adding CMDB header/entity exception: " + e.getMessage());
			}

			if (httpclient != null) {
				try {
					httpclient.close();
				} catch (IOException e) {
					monitor.getApplicationLog().error("Error closing CMDB HTTP Client exception: " + e.getMessage());
				}
			}
		}

		return cmdbResponse;
	}
}
