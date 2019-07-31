package util.geode.monitor.jmx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.JAXBContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import util.geode.monitor.Constants;
import util.geode.monitor.Monitor;
import util.geode.monitor.impl.MonitorImpl;
import util.geode.monitor.log.LogMessage;
import util.geode.monitor.xml.ExcludedMessageObjectFactory;
import util.geode.monitor.xml.ExcludedMessages;

public class StartMonitor extends MonitorImpl implements Monitor {
	private static Properties alertProps;
	private static HashMap<String, String> httpParams = new HashMap<String, String>();
	private static String alertUrl;
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

	private static boolean getSendAlertPropertyFile() {
		boolean alertLoaded = false;
		try {
			InputStream input = StartMonitor.class.getClassLoader().getResourceAsStream("alert.properties");
			alertProps = new Properties();
			try {
				alertProps.load(input);
				alertUrl = (String) alertProps.get("alert-url");
				if (alertUrl != null && alertUrl.length() > 0) {
					alertLoaded = true;
					String urlParams = (String) alertProps.get("url-parms");
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
		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost(alertUrl);

		String severity = logMessage.getHeader().getSeverity();
		if (logMessage.getHeader().getSeverity().equals(Constants.WARNING)) {
			severity = "MINOR";
		}

		String json = new JSONObject().put("fqdn", logMessage.getHeader().getMember()).put("severity", severity)
				.put("message", logMessage.getBody()).toString();
		monitor.getApplicationLog().info("Sending Alert Message json payload=" + json);

		try {
			StringEntity sEntity = new StringEntity("data=" + json);
			Set<String> keys = httpParams.keySet();
			for (String key : keys) {
				httppost.addHeader(key, httpParams.get(key));
			}
			httppost.addHeader("verify", "false");
			try {
				httppost.setEntity(sEntity);
				HttpResponse response = null;
				try {
					response = httpclient.execute(httppost);
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
					} else {
						monitor.getApplicationLog().warn("Http post response entity was null");
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
