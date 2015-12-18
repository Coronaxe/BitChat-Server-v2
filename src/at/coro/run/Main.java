package at.coro.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
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

	public static final String version = "0.1a";
	private static final String configPath = "config.ini";

	Properties configuration = null;
	private static ArrayList<Object[]> clientThreads = new ArrayList<Object[]>();

	private Properties configure(String configPath) throws IOException {

		ConfigManager confMan = new ConfigManager(configPath);

		if (confMan.configExists()) {
			return confMan.loadConfig();
		} else {
			Properties configuration = new Properties();
			configuration.setProperty("UDP_Broadcast_Port", "2920");
			configuration.setProperty("Server_Socket_Port", "6988");
			configuration.setProperty("Broadcast_Interval", "5000");
			configuration.setProperty("MOTD", "Chitchat on BitChat!");

			confMan.saveConfig(configuration);

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
					udpDiscover.broadcast(this.broadcastMessage,
							this.broadcastPort);
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
				System.out.println("Active Threads: "
						+ Main.clientThreads.size());
			}
		}
	}

	private void inputHandler() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String command = null;
			try {
				command = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			command = command.trim();
			if (command.toUpperCase().startsWith("/QUIT")) {
				System.exit(0);
			} else if (command.toUpperCase().startsWith("/HELP")) {
				showCommands();
			} else if (command.toUpperCase().startsWith("/BRD")) {
				try {
					broadcastMessage("<SERVER>: " + command.substring(
							command.indexOf(" ") + 1, command.length()));
				} catch (IOException | InvalidKeyException
						| IllegalBlockSizeException | BadPaddingException
						| NoSuchAlgorithmException | NoSuchPaddingException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void showCommands() {

	}

	// PUBLIC METHODS BELOW!

	public int userCount() {
		return Main.clientThreads.size();
	}

	public void broadcastMessage(String message) throws IOException,
			InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, NoSuchAlgorithmException,
			NoSuchPaddingException {
		for (int i = 0; i < Main.clientThreads.size(); i++) {
			((Server) Main.clientThreads.get(i)[0])
					.sendEncryptedMessage(message);
		}
	}

	@SuppressWarnings("resource")
	public static void main(String[] args) {

		Main mainThread = new Main();

		System.out.println("Starting Server...");
		System.out.println("Loading configuration...");

		try {
			mainThread.configuration = mainThread.configure(configPath);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Starting UDP Broadcast...");
		// Sets up a Thread to broadcast the server announce message over UDP
		// (Port 2920).
		Thread udpBroadcastThread = udpBroadcast("BITCHAT_UDP_DISCOVER:"
				+ mainThread.configuration.getProperty("MOTD"),
				Integer.parseInt(mainThread.configuration
						.getProperty("Broadcast_Interval")),
				Integer.parseInt(mainThread.configuration
						.getProperty("UDP_Broadcast_Port")));
		// Sets Thread as a daemon, exits when Main Thread exits.
		udpBroadcastThread.setDaemon(true);
		// Starts the Thread.
		udpBroadcastThread.start();

		// Handles the input in a separate Thread, as Socket.accept() blocks
		// the Main Thread.
		System.out.println("Starting inputHandler...");
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
		System.out.println("Starting LifeGuard Thread");
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

		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(
					Integer.parseInt(mainThread.configuration
							.getProperty("Server_Socket_Port")));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(2);
		}
		while (true) {
			try {
				Socket clientSocket = serverSocket.accept();
				Object[] client = new Object[2];
				client[0] = new Server(clientSocket);
				client[1] = new Thread((Runnable) client[0]);
				Main.clientThreads.add(client);
				((Thread) Main.clientThreads.get(Main.clientThreads.size() - 1)[1])
						.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}
