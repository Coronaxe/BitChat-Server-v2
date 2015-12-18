package at.coro.network.connection;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import at.coro.crypto.ADEC;
import at.coro.run.Main;

public class Server implements Runnable {

	private Socket clientSocket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	private ADEC cryptoEngine;
	private PublicKey clientPublicKey;

	private String username = "user_" + Math.round(Math.random() * 1000);

	public Server(Socket socket) throws IOException {
		System.out
				.println("--------------------\nNew incoming client connection!\nAddress is: "
						+ socket.getRemoteSocketAddress());
		this.clientSocket = socket;
		this.oos = new ObjectOutputStream(this.clientSocket.getOutputStream());
		this.ois = new ObjectInputStream(this.clientSocket.getInputStream());
	}

	public void sendMessage(Object message) throws IOException {
		this.oos.writeObject(message);
	}

	public void sendEncryptedMessage(Object message)
			throws InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, NoSuchAlgorithmException,
			NoSuchPaddingException, IOException {
		this.oos.writeObject(cryptoEngine.encryptString(clientPublicKey,
				message.toString()));
	}

	public Object listen() throws ClassNotFoundException, IOException {
		return ois.readObject();
	}

	@Override
	public void run() {
		Main mainThread = new Main();
		try {
			System.out.println("Generating keys...");
			cryptoEngine = new ADEC(2048);
			System.out.println("Done!");
			// Exchanging keys
			System.out.println("Sending Public Key...");
			sendMessage(cryptoEngine.publicKey());
			System.out.println("Waiting for Public Key...");
			clientPublicKey = (PublicKey) listen();
			System.out.println("Success!\n--------------------");
			//
		} catch (KeyException | NoSuchAlgorithmException
				| NoSuchProviderException | IOException
				| ClassNotFoundException e) {
			System.err.println("An Error Occurred!\nCause: " + e.getMessage());
			return;
		}

		do {

			String clientMessage = null;
			try {

				clientMessage = cryptoEngine.decryptString((byte[]) listen());
				System.out.println(clientMessage);

				if (clientMessage.toUpperCase().startsWith("/BRD")) {
					mainThread.broadcastMessage("<"
							+ this.username
							+ ">: "
							+ clientMessage.substring(
									clientMessage.indexOf(" ") + 1,
									clientMessage.length()));
				} else if (clientMessage.toUpperCase().startsWith("/CHUSR")) {
					this.username = clientMessage.split(" ")[1];
					sendEncryptedMessage("NEW USERNAME: " + username);
				}

			} catch (ClassNotFoundException | IOException | InvalidKeyException
					| NoSuchAlgorithmException | NoSuchPaddingException
					| IllegalBlockSizeException | BadPaddingException e) {
				// System.err.println("An Error Occurred!\nCause: " +
				// e.getMessage());
				break;
			}

		} while (!this.clientSocket.isClosed());
		// Cleanup
		System.out.println("CLEANING");
		try {
			this.ois.close();
			this.oos.close();
			this.clientSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err
					.println("Something went wrong while cleaning! We're fine though!");
		}
		System.out.println("User Disconnected.");
		return;

	}
}
