package util.geode.monitor.jmx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
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

import org.apache.http.client.methods.HttpPost;
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
	private static final String ALERT_CLUSTER_FQDN = "alert-cluster-fqdn";

	private static Properties alertProps;
	private static HashMap<String, String> httpParams = new HashMap<String, String>();
	private static String alertUrl;
	private static String alertClusterFqdn;
	private static StartMonitor monitor;

	static public void main(String[] args) throws Exception {
		monitor = new StartMonitor();
		boolean opened = true;
		if (!getSendAlertPropertyFile()) {
			monitor.getApplicationLog().error("Geode/GemFire Monitor failed to load alert property file");
			return;
		}

		monitor.initialize();

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
				} else {
					outToClient.writeBytes(Constants.INVALID_CMD + "\n");
				}
			} else if (message.startsWith(Constants.UNBLOCK)) {
				String[] msgParts = message.split("|");
				if (msgParts != null && msgParts.length == 2) {
					monitor.removeBlocker(msgParts[1]);
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

	private static boolean getSendAlertPropertyFile() {
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
									httpParams.put(keyValue[0], keyValue[1]);
								}
							}
						}
					}
					alertClusterFqdn = alertProps.getProperty(ALERT_CLUSTER_FQDN);
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
			httpclient = HttpClients.custom().setSSLSocketFactory(setupSSL()).build();
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
			Set<String> keys = httpParams.keySet();
			for (String key : keys) {
				httppost.addHeader(key, httpParams.get(key));
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
							byte[] responseData = null;
							int bytesRead = instream.read(responseData);
							if (bytesRead > 0) {
								monitor.getApplicationLog()
										.info("Alert URL Post Response: " + new String(responseData));
							} else {
								monitor.getApplicationLog().warn("No Alert URL Post Response received");
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
	}
}
