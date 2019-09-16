import java.net.*;
import java.util.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Server
{
	private int mPortNum;
	private int mSecondaryPortNum;
	private Map<String, User> mAllUsers;
	private Map<String, Group> mAllGroups;

	public static void main(String args[]) throws IOException {
		Server server = new Server(5000, 4000);
		server.start();
	}

	private Server(int portNum, int secondaryPortNum) {
		mPortNum = portNum;
		mSecondaryPortNum = secondaryPortNum;
		mAllUsers = new HashMap<String, User>();
		mAllGroups = new HashMap<String, Group>();
	}

	private void start() throws IOException {
		ServerSocket serverSocket = new ServerSocket(mPortNum);
		ServerSocket secondaryServerSocket = new ServerSocket(mSecondaryPortNum);
		System.out.println("Server started.");

		while (true) {
			Socket socket = serverSocket.accept();
			Socket secondarySocket = secondaryServerSocket.accept();
			System.out.println(String.format("Client at %s just connected.", socket.getRemoteSocketAddress()));
			User user = new User(socket, secondarySocket);
			user.start();
		}
	}

	private class User extends Thread {

		private DataInputStream mInputStream;
		private DataOutputStream mOutputStream;
		private DataOutputStream mSecondaryOutputStream;
		private String mUsername;

		private User(Socket socket, Socket secondarySocket) throws IOException {
			mInputStream = new DataInputStream(socket.getInputStream());
			mOutputStream = new DataOutputStream(socket.getOutputStream());
			mSecondaryOutputStream = new DataOutputStream(secondarySocket.getOutputStream());
		}

		public void run() {
			try {
				mOutputStream.writeUTF("Server: Enter your username: ");
				boolean usernameCreated = false;
				while (!usernameCreated) {
					mUsername = mInputStream.readUTF();
					if (mUsername.isEmpty()) {
						mOutputStream.writeUTF("Server: Username cannot be empty. Please try another username: ");					
					} else if (mAllUsers.containsKey(mUsername)) {
						mOutputStream.writeUTF(String.format("Server: Username '%s' is already taken. Please try another username: ", mUsername));
					} else {
						usernameCreated = true;
						mAllUsers.put(mUsername, this);
						new File(mUsername).mkdirs();
						mOutputStream.writeUTF(String.format("Server: Username '%s' successfully created.\n", mUsername));
					}
				}

				while (true) {
					String command = mInputStream.readUTF();
					execute(command);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void execute(String command) throws IOException {
			String[] words = command.split(" ");

			if (words[0].equals("create_group")) {
				create_group(words);
			} else if (words[0].equals("list_groups")) {
				list_groups(words);
			} else if (words[0].equals("join_group")) {
				join_group(words);
			} else if (words[0].equals("leave_group")) {
				leave_group(words);
			} else if (words[0].equals("list_detail")) {
				list_detail(words);
			} else if (words[0].equals("upload")) {
				upload(words);
			} else if (words[0].equals("create_folder")) {
				create_folder(words);
			} else if (words[0].equals("move_file")) {
				move_file(words);
			} else if (words[0].equals("share_msg")) {
				share_msg(words);
			} else if (words[0].equals("get_file")) {
				get_file(words);
			} else if (words[0].equals("upload_udp")) {
				upload_udp(words);
			} else {
				mOutputStream.writeUTF(String.format("Server: Invalid command '%s'.\n", command));
			}
		}

		private void create_group(String[] words) throws IOException {
			if (words.length != 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'create_group <groupname>'.\n");
			} else if (mAllGroups.containsKey(words[1])) {
				mOutputStream.writeUTF(String.format("Server: Error. Group '%s' already exists.\n", words[1]));
			} else {
				Group group = new Group(words[1]);
				mAllGroups.put(words[1], group);
				mOutputStream.writeUTF(String.format("Server: Group '%s' successfully created.\n", words[1]));
			}
		}

		private void list_groups(String[] words) throws IOException {
			if (words.length != 1) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'list_groups'.\n");
			} else {
				String outputString = "Server: List of groups:\n";
				for (String groupname : mAllGroups.keySet()) {
					outputString += groupname + "\n";
				}
				mOutputStream.writeUTF(outputString);
			}
		}

		private void join_group(String[] words) throws IOException {
			if (words.length != 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'join_group <groupname>'.\n");
			} else if (!mAllGroups.containsKey(words[1])) {
				mOutputStream.writeUTF(String.format("Server: Error. Group '%s' does not exist.\n", words[1]));
			} else {
				Group group = mAllGroups.get(words[1]);
				if (group.addUser(mUsername)) {
					mOutputStream.writeUTF(String.format("Server: You have successfully joined group '%s'.\n", words[1]));
				} else {
					mOutputStream.writeUTF(String.format("Server: You are already in group '%s'.\n", words[1]));
				}
			}
		}

		private void leave_group(String[] words) throws IOException {
			if (words.length != 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'leave_group <groupname>'.\n");
			} else if (!mAllGroups.containsKey(words[1])) {
				mOutputStream.writeUTF(String.format("Server: Error. Group '%s' does not exist.\n", words[1]));
			} else {
				Group group = mAllGroups.get(words[1]);
				if (group.addUser(mUsername)) {
					mOutputStream.writeUTF(String.format("Server: You have successfully left group '%s'.\n", words[1]));
				} else {
					mOutputStream.writeUTF(String.format("Server: You are not in group '%s'.\n", words[1]));
				}
			}
		}

		private void list_detail(String[] words) throws IOException {
			if (words.length != 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'list_detail <groupname>'.\n");
			} else if (!mAllGroups.containsKey(words[1])) {
				mOutputStream.writeUTF(String.format("Server: Error. Group '%s' does not exist.\n", words[1]));
			} else {
				Group group = mAllGroups.get(words[1]);
				String outputString = String.format("Server: List of users in group '%s':\n", words[1]);
				for (String username : group.getUsers()) {
					outputString += username + "\n";
				}
				outputString += "List of files:\n";
				mOutputStream.writeUTF(outputString);
				for (String username : group.getUsers()) {
					Files.find(Paths.get(username),
							Integer.MAX_VALUE,
							(filePath, fileAttr) -> fileAttr.isRegularFile())
					.forEach(s -> {
						try {
							mOutputStream.writeUTF(s + "\n");
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			}
		}

		private void upload(String[] words) throws IOException {
			String filename = mUsername + "/" + words[1];

			File file = new File(filename);
			if(file.exists()) {
				mOutputStream.writeUTF(String.format("Server: Error. File '%s' already exists on the server.\n", file.getName()));
				mSecondaryOutputStream.writeInt(-1);
				return;
			}

			mSecondaryOutputStream.writeInt(0);

			OutputStream fileOutputStream = new FileOutputStream(filename);
			long fileLength = Long.parseLong(words[2]);
			byte[] buffer = new byte[1024];
			long totalRead = 0;
			while (true) {
				int bytesRead = mInputStream.read(buffer);
				fileOutputStream.write(buffer, 0, bytesRead);
				totalRead += bytesRead;
				if (totalRead == fileLength) break;
			}
			fileOutputStream.close();
			mOutputStream.writeUTF(String.format("Server: File '%s' successfully uploaded.\n", file.getName()));			
		}

		private void upload_udp(String[] words) throws IOException {
			String filename = mUsername + "/" + words[1];

			File file = new File(filename);
			if(file.exists()) {
				mOutputStream.writeUTF(String.format("Server: Error. File '%s' already exists on the server.\n", file.getName()));
				mSecondaryOutputStream.writeInt(-1);
				return;
			}
			mSecondaryOutputStream.writeInt(0);

			DatagramSocket udpSocket = new DatagramSocket();
			int port = udpSocket.getLocalPort();
			mSecondaryOutputStream.writeInt(port);

			OutputStream fileOutputStream = new FileOutputStream(filename);
			long fileLength = Long.parseLong(words[2]);
			byte[] buffer = new byte[1024];
			long totalRead = 0;
			while (true) {
				DatagramPacket packet = new DatagramPacket(buffer, 1024);
				udpSocket.receive(packet);
				fileOutputStream.write(buffer, 0, packet.getLength());
				totalRead += packet.getLength();
				if (totalRead == fileLength) break;
			}
			fileOutputStream.close();
			mOutputStream.writeUTF(String.format("Server: File '%s' successfully uploaded.\n", file.getName()));
		}

		private void create_folder(String[] words) throws IOException {
			if (words.length != 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'create_folder <foldername>'.\n");
			} else if (words[1].indexOf('/') >= 0) {
				mOutputStream.writeUTF("Server: Error. Folder name cannot have '/' in it.\n");
			} else {
				File file = new File(mUsername + "/" + words[1]);
				if (file.mkdir()) mOutputStream.writeUTF(String.format("Server: Folder '%s' successfully created.\n", words[1]));
				else mOutputStream.writeUTF(String.format("Server: Error. Folder '%s' could not be created.\n", words[1]));
			}
		}

		private void move_file(String[] words) throws IOException {
			if (words.length != 3) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'move_file <source_path> <destination_path>'.\n");
			} else {
				String filename = mUsername + "/" + words[1];
				File file = new File(filename);
				if (!file.isFile()) {
					mOutputStream.writeUTF(String.format("Server: File '%s' does not exist on the server.\n", filename));
				} else {
					if (file.renameTo(new File(mUsername + "/" + words[2]))) {
						mOutputStream.writeUTF("Server: Successfully moved file.\n");						
					} else {						
						mOutputStream.writeUTF("Server: Error. Could not move file.\n");
					}
				}
			}
		}

		private void get_file(String[] words) throws IOException {
			if (words.length != 2 || countMatches(words[1], '/') < 2) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'get_file groupname/username/file_path'.\n");
				mSecondaryOutputStream.writeInt(-1);
			} else {
				String[] path = words[1].split("/");
				if (!mAllGroups.containsKey(path[0])) {
					mOutputStream.writeUTF(String.format("Server: Error. Group '%s' does not exist.\n", path[0]));
					mSecondaryOutputStream.writeInt(-1);
				} else if (!mAllGroups.get(path[0]).getUsers().contains(mUsername)) {
					mOutputStream.writeUTF(String.format("Server: Error. You are not in group '%s'.\n", path[0]));
					mSecondaryOutputStream.writeInt(-1);
				} else if (!mAllGroups.get(path[0]).getUsers().contains(path[1])) {
					mOutputStream.writeUTF(String.format("Server: Error. User '%s' is not in group '%s'.\n", path[1], path[0]));
					mSecondaryOutputStream.writeInt(-1);
				} else {
					String file_path = String.join("/", Arrays.copyOfRange(path, 1, path.length));
					File file = new File(file_path);
					if(!file.exists()) {
						mOutputStream.writeUTF(String.format("Server: Error. File '%s' does not exist on the server.\n", file_path));
						mSecondaryOutputStream.writeInt(-1);
						return;
					}
					mSecondaryOutputStream.writeInt(0);
					mSecondaryOutputStream.writeUTF(file.getName());
					mSecondaryOutputStream.writeLong(file.length());

					FileInputStream fileInputStream = new FileInputStream(file);
					byte[] buffer = new byte[1024];
					while (true)
					{
						int bytesRead = fileInputStream.read(buffer);
						if (bytesRead <= 0) break;
						mSecondaryOutputStream.write(buffer, 0, bytesRead);
					}
				}
			}
		}

		private void share_msg(String[] words) throws IOException {
			if (words.length < 3) {
				mOutputStream.writeUTF("Server: Incorrect format. Use as 'share_msg <groupname> <message>'.\n");
			} else if (!mAllGroups.containsKey(words[1])) {
				mOutputStream.writeUTF(String.format("Server: Error. Group '%s' does not exist.\n", words[1]));
			} else if (!mAllGroups.get(words[1]).getUsers().contains(mUsername)) {
				mOutputStream.writeUTF(String.format("Server: Error. You are not in group '%s'.\n", words[1]));
			} else {
				String prefix = mUsername + " to " + words[1] + ": ";
				String msg = String.join(" ", Arrays.copyOfRange(words, 2, words.length));
				Set<String> usernames = mAllGroups.get(words[1]).getUsers();
				for (String username : usernames) {
					mAllUsers.get(username).mOutputStream.writeUTF(prefix + msg + "\n");
				}
			}
		}

		private int countMatches(String str, char c) {
			int count = 0;
			for (int i = 0; i < str.length(); i++) {
				if (str.charAt(i) == c) {
					count++;
				}
			}
			return count;
		}
	}

	private class Group {

		private String mName;
		private Set<String> mUsers;

		private Group(String name) {
			mName = name;
			mUsers = new HashSet<String>();
		}

		private boolean addUser(String username) {
			return mUsers.add(username);
		}

		private boolean removeUser(String username) {
			return mUsers.remove(username);
		}

		private Set<String> getUsers() {
			return mUsers;
		}
	}
}
