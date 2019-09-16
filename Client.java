import java.net.*;
import java.io.*;

class Client
{
	private String mServerHost;
	private int mServerPort;
	private int mServerSecondaryPort;
	private DataOutputStream mOutputStream;
	private DataInputStream mSecondaryInputStream;

	public static void main(String args[]) throws IOException {
		Client client = new Client("localhost", 5000, 4000);
		client.start();
	}

	private Client(String serverHost, int serverPort, int serverSecondaryPort) {
		mServerHost = serverHost;
		mServerPort = serverPort;
		mServerSecondaryPort = serverSecondaryPort;
	}

	private void start() throws IOException {
		Socket socket = new Socket(mServerHost, mServerPort);
		Socket secondarySocket = new Socket(mServerHost, mServerSecondaryPort);
		ServerListener serverListener = new ServerListener(socket);
		serverListener.start();

		mOutputStream = new DataOutputStream(socket.getOutputStream());
		mSecondaryInputStream = new DataInputStream(secondarySocket.getInputStream());

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String message = reader.readLine();
			String[] words = message.split(" ");
			if (words[0].equals("upload")) {
				upload(message);
			} else if (words[0].equals("upload_udp")) {
				upload_udp(message);
			} else if (words[0].equals("get_file")) {
				get_file(message);
			} else {
				mOutputStream.writeUTF(message);
			}
		}
	}

	private void upload(String message) throws FileNotFoundException, IOException {
		String[] words = message.split(" ");
		if (words.length != 2) {
			System.out.println("Incorrect format. Use as 'upload <filename>'.");
			return;
		}

		File file = new File(words[1]);
		if(!file.isFile()) {
			System.out.println(String.format("Error. File '%s' does not exist.", words[1]));
			return;
		}

		mOutputStream.writeUTF(words[0] + " " + file.getName() + " " + file.length());
		int retCode = mSecondaryInputStream.readInt();
		if (retCode == -1) {
			return;
		}

		FileInputStream fileInputStream = new FileInputStream(file);
		byte[] buffer = new byte[1024];
		while (true)
		{
			int bytesRead = fileInputStream.read(buffer);
			if (bytesRead <= 0) break;
			mOutputStream.write(buffer, 0, bytesRead);
		}
		fileInputStream.close();
	}

	private void upload_udp(String message) throws FileNotFoundException, IOException {
		String[] words = message.split(" ");
		if (words.length != 2) {
			System.out.println("Incorrect format. Use as 'upload_udp <filename>'.");
			return;
		}

		File file = new File(words[1]);
		if(!file.isFile()) {
			System.out.println(String.format("Error. File '%s' does not exist.", words[1]));
			return;
		}

		mOutputStream.writeUTF(words[0] + " " + file.getName() + " " + file.length());
		int retCode = mSecondaryInputStream.readInt();
		if (retCode == -1) {
			return;
		}

		int udpPort = mSecondaryInputStream.readInt();
		DatagramSocket udpSocket = new DatagramSocket();

		FileInputStream fileInputStream = new FileInputStream(file);
		byte[] buffer = new byte[1024];
		while (true)
		{
			int bytesRead = fileInputStream.read(buffer);
			if (bytesRead <= 0) break;
            DatagramPacket packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(mServerHost), udpPort);
            udpSocket.send(packet);
		}
		fileInputStream.close();
	}

	private void get_file(String message) throws FileNotFoundException, IOException {
		mOutputStream.writeUTF(message);
		int retCode = mSecondaryInputStream.readInt();
		if (retCode == -1) {
			return;
		}

		String filename = mSecondaryInputStream.readUTF();
		long fileLength = mSecondaryInputStream.readLong();

		OutputStream fileOutputStream = new FileOutputStream(filename);
		byte[] buffer = new byte[1024];
		long totalRead = 0;
		while (true) {
			int bytesRead = mSecondaryInputStream.read(buffer);
			fileOutputStream.write(buffer, 0, bytesRead);
			totalRead += bytesRead;
			if (totalRead == fileLength) break;
		}
		fileOutputStream.close();
		System.out.println(String.format("File '%s' successfully downloaded.", filename));
	}

	private class ServerListener extends Thread {

		private DataInputStream mInputStream;

		private ServerListener(Socket socket) throws IOException {
			mInputStream = new DataInputStream(socket.getInputStream());
		}

		public void run() {
			try {
				while (true) {
					String message = mInputStream.readUTF();
					System.out.print(message);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
