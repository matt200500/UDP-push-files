/**
 *  StopWaitFTP class
 * 
 * CPSC 441
 * Assignment 4
 * 
 * @author 	Matteo Cusanelli
 * @version 2024
 *
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.*;

public class StopWaitFtp {
	
	private static final Logger logger = Logger.getLogger("StopWaitFtp"); // global logger	

	int PacketTimeout;

	/**
	 * Constructor to initialize the program 
	 * 
	 * @param timeout		The time-out interval for the retransmission timer, in milli-seconds
	 */
	public StopWaitFtp(int timeout){
		PacketTimeout = timeout;
	}


	/**
	 * Send the specified file to the specified remote server.
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 * @return 				true if the file transfer completed successfully, false otherwise
	 */
	public boolean send(String serverName, int serverPort, String fileName){

		// Variables for the Streams and sockets
        Socket socket = null;
		DatagramSocket UdpSocket = null;
		DataOutputStream dataOutputStream = null;
		DataInputStream dataInputStream = null;
		FileInputStream fileInputStream = null;

		// Variable for the timer
		Timer timer = new Timer();

		try{
			socket = new Socket(serverName, serverPort); // create TCP socket
			UdpSocket = new DatagramSocket(); // Create UDP socket

			// Create Streams
			dataOutputStream = new DataOutputStream(socket.getOutputStream());
			dataInputStream = new DataInputStream(socket.getInputStream());

			InetAddress IPAddress = InetAddress.getByName(serverName); // get the ip address of the server
 
			File f = new File(fileName); // get the file associated with the given filename

			if (!(f.exists())){ // If the file does not exist
				System.err.println("Error, input file does not exist");
				// Close the streams, sockets, and timers
				dataInputStream.close();
				dataOutputStream.close();
				socket.close();
				UdpSocket.close();
				timer.cancel();
				timer.purge();
				return false;			
			}

			long FileLength = f.length(); // get file length

			System.out.println("\nSENDING FILE INFORMATION");

			int udpPort = UdpSocket.getLocalPort();

			// Send file information to server
			dataOutputStream.writeUTF(fileName);
			System.out.println("Filename = "+fileName); // prints the file name

			dataOutputStream.writeLong(FileLength);
			System.out.println("File Length = "+FileLength); // prints the file length

			dataOutputStream.writeInt(udpPort);
			dataOutputStream.flush(); // flush all data after writing them all to server
			System.out.println("UDP local port = "+udpPort + "\n"); // prints the local port

			// Receive the UDPServer port as well as the initial seq number back from the server
			int UDPServerPort = dataInputStream.readInt();
			int InitialSeqNum = dataInputStream.readInt();

			fileInputStream = new FileInputStream(f.getAbsolutePath()); // input stream to read from file

			boolean EndofFile = false; // boolean to display if file is empty or not

			//Create a buffer of bytes to read from file
			byte[] buffer2 = new byte[FtpSegment.MAX_PAYLOAD_SIZE];
			int count;
			
			UdpSocket.setSoTimeout(PacketTimeout*20); // set the socket timeout to allow us to detect the server unresponsive timeout

			int SendSeqNum = InitialSeqNum;
			System.out.println("TRANSMITTING FILE");
			while(!EndofFile){
				if ((count = fileInputStream.read(buffer2)) > 0){ // If there is remaining bytes of file
					byte[] buffer = new byte[count];
					for (int i = 0; i < count; i++){
						buffer[i] = buffer2[i];
					}
					boolean Received = false; // Boolean to track if ACK has been received
					FtpSegment ftpSegment = new FtpSegment(SendSeqNum, buffer, buffer.length); // initialize the FTP segment
					DatagramPacket packet = FtpSegment.makePacket(ftpSegment, IPAddress, UDPServerPort);
					UdpSocket.send(packet);
					System.out.println("send: "+ ftpSegment.getSeqNum());
					
					// start the retransmission timer
					TimerTask timerTask = new TimeoutHandler(UdpSocket, packet, ftpSegment.getSeqNum());
					timer.scheduleAtFixedRate(timerTask, PacketTimeout, PacketTimeout);

					long millis = System.currentTimeMillis(); // current time
					long futureTime = millis + (PacketTimeout*10); // time 10 packet timeouts into the future

					while (!Received){ // Checks if we received the ACK

						try{ // block so when the server socket times out, we check if the server unresponsive
							long currentTime = System.currentTimeMillis(); // current time
							if (currentTime > futureTime){ // Checks if the server is unresponsive
								System.out.println("Error: Server Unresponsive");

								// Close the streams, sockets, and timers
								fileInputStream.close();
								dataInputStream.close();
								dataOutputStream.close();
								socket.close();
								UdpSocket.close();
								timer.cancel();
								timer.purge();
								return false; // return false as file was not able to be sent
							}
	
							byte[] receivebuffer = new byte[FtpSegment.MAX_PAYLOAD_SIZE]; // create buffer for receiver
							DatagramPacket receivedpPacket = new DatagramPacket(receivebuffer, FtpSegment.MAX_PAYLOAD_SIZE); //create receiver packet
							UdpSocket.receive(receivedpPacket); // check socket for packet
	
							// Getting the ack from the socket
							FtpSegment ack = new FtpSegment(receivedpPacket);
							int ackNum = ack.getSeqNum();
							// Getting the previous number from the socket
							int prev_num = ftpSegment.getSeqNum();
							prev_num += 1;
							if (ackNum == prev_num){ // checking if the next number we are going to send = the ack sent
								timerTask.cancel();
								Received = true; // set the received flag to true, breaking us out of the loop
								System.out.println("ack: "+ackNum);
								SendSeqNum += 1; // increase the sequence number by 1
							}else{ //if they are not the same, ignore ack
								System.out.println("ack: "+ackNum + " ignore, out of order");
							}
						} catch(SocketTimeoutException e){ // cacth block so when out socket times out, we can go back to top of loop

						}	
					}
				}else{ // If the remaining part of the file is empty
					EndofFile = true;
				}
			}

			// Close the streams, sockets, and timers
			fileInputStream.close();
			dataInputStream.close();
			dataOutputStream.close();
			socket.close();
			UdpSocket.close();
			timer.cancel();
			timer.purge();

		} 
		catch(Exception e){ // if an exception occurs
			System.err.println("Error: " + e.getCause());
			return false; // return false as file was not able to be sent
		}
		return true; // return true if there were no errors
	}


	/*
	 *  Class to handle resending packets
	 */
	public class TimeoutHandler extends TimerTask { 
		private DatagramSocket UdpSocket;
		private DatagramPacket packet;
		private int SeqNum;

		public TimeoutHandler(DatagramSocket UdpSocket, DatagramPacket packet, int SeqNum){
			this.UdpSocket = UdpSocket;
			this.packet = packet;
			this.SeqNum = SeqNum;
		}

		@Override
		public void run(){
			try{
				System.out.println("timeout: "+SeqNum );
				UdpSocket.send(packet);
				System.out.println("retx: "+ SeqNum);

			} catch(Exception e){
				System.err.println("TimeoutHandler Error: " +e.getCause());
			}
		}
	}

} // end of class

