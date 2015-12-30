package at.coro.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import at.coro.network.connection.Server;
import at.coro.network.udp.discover.UDPDiscovery;
import at.coro.utils.ConfigManager;

public class Main {

	private final String version_number = "0.85";
	private final String version_addition = "Beta";
	public final String version = version_number + " " + version_addition;
	private static final String configPath = "config.ini";

	Properties configuration = null;
	private static ArrayList<Object[]> clientThreads = new ArrayList<Object[]>();

	private Properties configure(String configPath, boolean skipIO, boolean rewriteConfig) throws IOException {

		ConfigManager confMan = new ConfigManager(configPath);

		if (!rewriteConfig && (!skipIO && confMan.configExists())) {
			return confMan.loadConfig();
		} else {
			Properties configuration = new Properties();
			configuration.setProperty("UDP_Broadcast_Port", "2920");
			configuration.setProperty("Server_Socket_Port", "6988");
			configuration.setProperty("Broadcast_Interval", "5000");
			configuration.setProperty("MOTD", "Chitchat on BitChat!");
			configuration.setProperty("Version", this.version_number);
			configuration.setProperty("DDoSProtection", "true");
			configuration.setProperty("DDoSProtection_Cutoff", "5");
			configuration.setProperty("Password", "");

			if (!skipIO) {
				confMan.saveConfig(configuration);
			}

			return configuration;
		}
	}

	private static Thread udpBroadcast(String message, int interval, int port) {

		class BroadcastClass implements Runnable {
			String broadcastMessage = null;
			int broadcastInterval = 0;
			int broadcastPort = 0;

			BroadcastClass(String message, int interval, int port) {
				this.broadcastMessage = message;
				this.broadcastInterval = interval;
				this.broadcastPort = port;
			}

			@Override
			public void run() {
				UDPDiscovery udpDiscover = new UDPDiscovery();
				while (true) {
					udpDiscover.broadcast(this.broadcastMessage, this.broadcastPort);
					try {
						Thread.sleep(this.broadcastInterval);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

		return new Thread(new BroadcastClass(message, interval, port));
	}

	private void lifeGuard() {
		// System.out.println(clientThreads.size());
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			if (!((Thread) Main.clientThreads.get(i)[1]).isAlive()) {
				System.out.println("Thread " + i + " dead!\nDisposing...");
				// ((Thread) clientThreads.get(i)[1]).stop();
				Main.clientThreads.remove(i);
				System.out.println("Active Threads: " + Main.clientThreads.size());
			}
		}
	}

	private boolean ddosProtection(SocketAddress address, int cutOff) {
		int counter = 0;

		for (int i = 0; i < clientThreads.size(); i++) {
			if (((SocketAddress) clientThreads.get(i)[2]).toString().split(":")[0]
					.equals(address.toString().split(":")[0])) {
				counter++;
			}
			if (counter >= cutOff) {
				return true;
			}
		}
		return false;
	}

	private int getClientThread(String user) {
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			if (((Server) Main.clientThreads.get(i)[0]).getUsername().equalsIgnoreCase(user)) {
				return i;
			}
		}
		return -1;
	}

	private void disconnect(String user) throws IOException {
		if (user.equals("all")) {
			for (int i = Main.clientThreads.size() - 1; i >= 0; i--) {
				System.out.println("Disconnecting " + ((Server) Main.clientThreads.get(i)[0]).getUsername() + "...");
				((Server) Main.clientThreads.get(i)[0]).disconnect();
				Main.clientThreads.remove(i);
			}
		} else {
			if (userExists(user)) {
				System.out.println("Disconnecting " + user + "...");
				((Server) Main.clientThreads.get(getClientThread(user))[0]).disconnect();
			} else {
				System.err.println("NO SUCH USER!");
			}
		}
	}

	private void inputHandler() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			// System.out.print(">");
			String command = null;
			try {
				command = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			command = command.trim();
			if (command.toUpperCase().startsWith("/QUIT")) {
				try {
					disconnect("all");
				} catch (IOException e) {
				}
				System.out.println("Stopping Main Thread...");
				System.exit(0);
			} else if (command.toUpperCase().startsWith("/HELP")) {
				showCommands();
			} else if (command.toUpperCase().startsWith("/BRD")) {
				try {
					broadcastMessage("<SERVER>: " + command.substring(command.indexOf(" ") + 1, command.length()));
				} catch (IOException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException
						| NoSuchAlgorithmException | NoSuchPaddingException e) {
					e.printStackTrace();
				}
			} else if (command.toUpperCase().startsWith("/INFO")) {
				System.out.println("There are currently " + userCount() + " users online;");
				String[] onlineUsers = getUsers();
				System.out.print("| ");
				for (int i = 0; i < onlineUsers.length; i++) {
					System.out.print(onlineUsers[i] + " | ");
				}
				System.out.println();
			} else if (command.toUpperCase().startsWith("/DISCONNECT")) {
				try {
					disconnect(command.split(" ")[1]);
				} catch (IOException e) {
					System.out.println("Error while disconnecting users!");
				}
			} else if (command.toUpperCase().startsWith("/MOTD")) {
				System.out.println("To be implemented");
			} else {
				System.err.println("No such command! Type /help for a list of available commands!");
			}
		}

	}

	private void showCommands() {
		System.out.println("------------------------------------------------");
		System.out.println("/help : Shows this list.");
		System.out.println("/quit : Quits the server.");
		System.out.println("/info : Lists user count and usernames");
		System.out.println("/brd [Message] : Broadcasts message to all users");
		System.out.println(
				"/disconnect [user] : Disconnects specified user. Type \"all\" instead to disconnect everyody");
		System.out.println("------------------------------------------------");
	}

	// PUBLIC METHODS BELOW!

	public int userCount() {
		return Main.clientThreads.size();
	}

	public boolean userExists(String username) {
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			if (((Server) Main.clientThreads.get(i)[0]).getUsername().equalsIgnoreCase(username)) {
				return true;
			}
		}
		return false;
	}

	public String[] getUsers() {
		String[] userList = new String[Main.clientThreads.size()];
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			userList[i] = (((Server) Main.clientThreads.get(i)[0]).getUsername());
		}
		return userList;

	}

	public void broadcastMessage(String message) throws IOException, InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			((Server) Main.clientThreads.get(i)[0]).sendEncryptedMessage(message);
		}
	}

	public void directMessage(String username, String message) throws InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, IOException {
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			if (((Server) Main.clientThreads.get(i)[0]).getUsername().equalsIgnoreCase(username)) {
				((Server) Main.clientThreads.get(i)[0]).sendEncryptedMessage(message);
			}
		}
	}

	@SuppressWarnings("resource")
	public static void main(String[] args) {

		Main mainThread = new Main();

		System.out.println("Starting Server version " + mainThread.version);
		System.out.println("Loading configuration...");

		try {
			mainThread.configuration = mainThread.configure(configPath, false, false);
			if (Double.parseDouble(mainThread.configuration.getProperty("Version")) < Double
					.parseDouble(mainThread.version_number)) {
				System.out.println("Version change detected, rewriting config...");
				mainThread.configuration = mainThread.configure(configPath, false, true);
			}
		} catch (IOException ioEx) {
			System.err
					.println("Could not Write config, Probably due to a Read-Only File System. Skipping IO-Section...");
			try {
				mainThread.configuration = mainThread.configure(configPath, true, false);
			} catch (IOException fallbackError) {
				System.err.println("Fatal Error occurred in fallback mode, giving up...");
				System.exit(0);
			}
		}
		mainThread.configuration.list(System.out);

		System.out.println("Starting UDP Broadcast Thread...");
		// Sets up a Thread to broadcast the server announce message over UDP
		// (Port 2920).
		boolean password = true;
		if (mainThread.configuration.getProperty("Password").isEmpty()) {
			password = false;
		}
		Thread udpBroadcastThread = udpBroadcast(
				"BITCHAT_UDP_DISCOVER:PASSWORD=" + password + ":" + mainThread.configuration.getProperty("MOTD"),
				Integer.parseInt(mainThread.configuration.getProperty("Broadcast_Interval")),
				Integer.parseInt(mainThread.configuration.getProperty("UDP_Broadcast_Port")));
		// Sets Thread as a daemon, exits when Main Thread exits.
		udpBroadcastThread.setDaemon(true);
		// Starts the Thread.
		udpBroadcastThread.start();

		// Handles the input in a separate Thread, as Socket.accept() blocks
		// the Main Thread.
		// System.out.println("Starting InputHandler Thread...");
		Thread inputHandlerThread = new Thread(new Runnable() {

			@Override
			public void run() {
				mainThread.inputHandler();
			}

		});

		inputHandlerThread.setDaemon(true);
		inputHandlerThread.start();

		// The LifeGuard Thread checks the clientThread List every second for
		// dead Threads and disposes them.
		System.out.println("Starting LifeGuard Thread...");
		Thread lifeGuardThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					mainThread.lifeGuard();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		});

		lifeGuardThread.setDaemon(true);
		lifeGuardThread.start();

		System.out.println("Startup complete, waiting for input;");

		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(
					Integer.parseInt(mainThread.configuration.getProperty("Server_Socket_Port")));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(2);
		}
		// System.out.println("Ready.");
		Socket clientSocket = null;
		while (true) {
			try {
				clientSocket = serverSocket.accept();
				System.out.println(clientSocket.getRemoteSocketAddress());
				if (mainThread.ddosProtection(clientSocket.getRemoteSocketAddress(),
						Integer.parseInt(mainThread.configuration.getProperty("DDoSProtection_Cutoff")))) {
					clientSocket.close();
				} else {
					Object[] client = new Object[3];
					client[0] = new Server(clientSocket, mainThread.configuration.getProperty("Password"));
					client[1] = new Thread((Runnable) client[0]);
					client[2] = (SocketAddress) clientSocket.getRemoteSocketAddress();
					Main.clientThreads.add(client);
					((Thread) Main.clientThreads.get(Main.clientThreads.size() - 1)[1]).start();
				}
			} catch (IOException e) {
				e.printStackTrace();
				try {
					clientSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}

	}
}
