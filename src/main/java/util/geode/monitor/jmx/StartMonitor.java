package util.geode.monitor.jmx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import javax.xml.bind.JAXBContext;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import util.geode.monitor.Constants;
import util.geode.monitor.impl.MonitorImpl;
import util.geode.monitor.log.LogMessage;
import util.geode.monitor.xml.ExcludedMessageObjectFactory;
import util.geode.monitor.xml.ExcludedMessages;

public class StartMonitor extends MonitorImpl {

	private Logger log = LogManager.getLogger(StartMonitor.class);

	static public void main(String[] args) throws Exception {
		StartMonitor monitor = new StartMonitor();
		boolean opened = true;

		monitor.initialize();
		
		ServerSocket commandSocket = new ServerSocket(monitor.getCommandPort());

		while (opened) {
			Socket connectionSocket = commandSocket.accept();
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			String message = inFromClient.readLine();
			if (Constants.RELOAD.equals(message)) {
				monitor.setExcludedMessages((ExcludedMessages) monitor.getUtil().processJAXB(
						JAXBContext.newInstance(ExcludedMessageObjectFactory.class), Constants.EXCLUDED_MESSAGE_FILE));
				outToClient.writeBytes(Constants.OK + "\n");
			} else if (Constants.SHUTDOWN.equals(message)) {
				monitor.setShutdown(true);
				monitor.disconnect();
				outToClient.writeBytes(Constants.OK + "\n");
				opened = false;
			} else if (Constants.STATUS.equals(message)) {
				if (monitor.isAttachedToManager()) {
					outToClient.writeBytes(Constants.RUNNING_CONNECT + "\n");
				} else {
					outToClient.writeBytes(Constants.RUNNING + "\n");
				}
			} else {
				outToClient.writeBytes(Constants.INVALID_CMD + "\n");
			}
			connectionSocket.close();
			commandSocket.close();
		}
	}

	@Override
	public void sendAlert(LogMessage logMessage) {
		log.info("Sending Message: " + logMessage.toString());
	}
}
