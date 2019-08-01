package util.geode.monitor.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MonitorCommand {
	private static final String RELOAD = "RELOAD";
	private static final String SHUTDOWN = "SHUTDOWN";
	private static final String STATUS = "STATUS";
	private static final String BLOCK = "BLOCK";
	private static final String UNBLOCK = "UNBLOCK";
	private static Socket clientSocket;
	private static PrintWriter out;
	private static BufferedReader in;
	private static String host = null;
	private static String cmd = null;
	private static int port = 0;

	public static void main(String[] args) throws Exception {
		processArgs(args);
		if (validateArgs()) {
			startConnection();
			String resp = sendMessage(cmd);
			System.out.println("Monitor response: " + resp);
			stopConnection();
		}
	}

	private static boolean validateArgs() {
		if (host == null || host.length() == 0) {
			usage();
			return false;
		}
		if (port == 0) {
			usage();
			return false;
		}
		if (cmd == null || cmd.length() == 0) {
			usage();
			return false;
		}
		return true;
	}

	private static void usage() {
		System.out.println();
		System.out.println("MonitorCommand Usage:");
		System.out.println("  -h hostname/ip address");
		System.out.println("  -p port number");
		System.out.println("  -c monitor command");
		System.out.println("     " + RELOAD + " Reloads excluded message file");
		System.out.println("     " + SHUTDOWN + " Shuts down the monitor");
		System.out.println("     " + STATUS + " Provides monitor status");
		System.out.println("     " + BLOCK + "|[Member Name] Blocks a member from sending alerts");
		System.out.println("     " + UNBLOCK + "|[Member Name] Unblocks a member from sending alerts");
		System.out.println();
	}

	private static void processArgs(String[] args) {
		String lastArg = null;
		if (args != null) {
			for (String arg : args) {
				if (arg.toUpperCase().equals("-H") || arg.toUpperCase().equals("-P")
						|| arg.toUpperCase().equals("-C")) {
					lastArg = arg.toUpperCase();
				} else {
					if (lastArg != null) {
						if (lastArg.equals("-H")) {
							host = arg;
						} else if (lastArg.equals("-P")) {
							port = Integer.parseInt(arg);
						} else if (lastArg.equals("-C")) {
							cmd = arg.toUpperCase();
						}
					}
				}
			}
			if (!cmd.equalsIgnoreCase(RELOAD) && !cmd.equalsIgnoreCase(SHUTDOWN) && !cmd.equalsIgnoreCase(STATUS)
					&& !cmd.toUpperCase().startsWith(BLOCK) && !cmd.toUpperCase().startsWith(UNBLOCK)) {
				cmd = null;
			}
		}
	}

	private static void startConnection() throws Exception {
		clientSocket = new Socket(host, port);
		out = new PrintWriter(clientSocket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	}

	private static String sendMessage(String msg) {
		out.println(msg);
		String resp = null;
		try {
			resp = in.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return resp;
	}

	private static void stopConnection() {
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		out.close();
		try {
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
