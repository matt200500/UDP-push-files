import java.io.*;
import java.net.*;
import java.util.*;

/**
 * A Simple Echo Server
 * CPSC 441
 */

class WorkerThread implements Runnable{
	private Socket socket;
	public WorkerThread(Socket socket){
		this.socket = socket;
	}

	@Override
	public void run() {
		// Connected to client
		System.out.println("Worker Thread started: " + Thread.currentThread().getName());
		Scanner inputStream;
		PrintWriter outputStream;
        try {
            outputStream = new PrintWriter(new DataOutputStream(
                    socket.getOutputStream()));
			inputStream = new Scanner(new InputStreamReader(
					socket.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

		// Respond to messages from the client
		while (true) {
			String s = inputStream.nextLine();
			System.out.println(s);

			// exit if message from client is "bye"
			if (s.equalsIgnoreCase("bye")) {
				outputStream.println("bye");
				outputStream.flush();
				break;
			}

			outputStream.println(s);
			outputStream.flush();
		}

		inputStream.close();
		outputStream.close();
	}
}

public class TCPServer {

	public static void main(String[] args) {

		String s;
		ServerSocket serverSocket;

		try {
			// Listen on port 8888
			serverSocket = new ServerSocket(8888);

			while(true) {
				System.out.println("Waiting for Client");
				Socket socket = serverSocket.accept();
				System.out.println("Client Connected");
				WorkerThread workerThread = new WorkerThread(socket);
				Thread thr = new Thread(workerThread);
				thr.start();
			}
		}
		catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}
}
