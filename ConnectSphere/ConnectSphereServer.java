import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class ConnectSphereServer {
    private static final int PORT = 5555;
    private static HashSet<PrintWriter> writers = new HashSet<>();
    private static HashMap<String, PrintWriter> userWriters = new HashMap<>();
    private static HashSet<String> usernamesLower = new HashSet<>(); // Store lowercase usernames
    private static HashMap<String, String> usernameCaseMap = new HashMap<>(); // Map lowercase to original case
    private static HashSet<String> typingUsers = new HashSet<>(); // Track users who are typing in public chat
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private static final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 50;

    public static void main(String[] args) {
        System.out.println("Chat Server is running on port " + PORT);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
            System.exit(1);
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String name;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Request and store client name
                while (true) {
                    out.println("SUBMITNAME");
                    out.flush(); // Ensure the message is sent immediately
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    name = name.trim(); // Trim whitespace but keep internal spaces
                    synchronized (usernamesLower) {
                        String nameLower = name.toLowerCase();
                        if (!name.isEmpty() && !usernamesLower.contains(nameLower)) {
                            usernamesLower.add(nameLower);
                            usernameCaseMap.put(nameLower, name); // Store original case
                            break;
                        }
                    }
                }

                // Welcome the new client
                System.out.println("Server: Client " + name + " connected");
                out.println("NAMEACCEPTED " + name);
                out.flush();
                synchronized (userWriters) {
                    userWriters.put(name.toLowerCase(), out);
                    writers.add(out);
                }

                // Send message history to the new client
                synchronized (messageHistory) {
                    for (String msg : messageHistory) {
                        out.println("MESSAGE " + msg);
                        out.flush();
                    }
                }

                // Broadcast user joined and update user list
                broadcast(name + " joined the chat");
                broadcastUserList();

                // Send current typing status to the new client
                synchronized (typingUsers) {
                    for (String typingUser : typingUsers) {
                        out.println("TYPING " + typingUser + " start");
                        out.flush();
                    }
                }

                // Handle client messages
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Server: Received from " + name + ": " + message);
                    if (!message.isEmpty()) {
                        String timestamp = sdf.format(new Date());
                        if (message.startsWith("/pm ")) {
                            handlePrivateMessage(message, timestamp);
                        } else if (message.startsWith("/typing ")) {
                            System.out.println("Server: Processing typing message: " + message);
                            handlePublicTypingStatus(message);
                        } else if (message.startsWith("/pmtyping ")) {
                            System.out.println("Server: Processing pmtyping message: " + message);
                            handlePrivateTypingStatus(message);
                        } else {
                            String formattedMessage = "[" + timestamp + "] " + name + ": " + message;
                            broadcast(formattedMessage);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Server: Client disconnected: " + name + " - " + e.getMessage());
            } finally {
                if (name != null) {
                    String nameLower = name.toLowerCase();
                    synchronized (usernamesLower) {
                        usernamesLower.remove(nameLower);
                        usernameCaseMap.remove(nameLower);
                    }
                    synchronized (userWriters) {
                        userWriters.remove(nameLower);
                    }
                    // Remove from typing users if they were typing
                    synchronized (typingUsers) {
                        if (typingUsers.remove(name)) {
                            broadcastTypingStatus(name, "stop");
                        }
                    }
                    broadcast(name + " left the chat");
                    broadcastUserList();
                }
                if (out != null) {
                    synchronized (writers) {
                        writers.remove(out);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Server: Error closing socket: " + e.getMessage());
                }
            }
        }

        private void handlePrivateMessage(String message, String timestamp) {
            if (!message.startsWith("/pm ") || message.length() <= 4) {
                out.println("MESSAGE [" + timestamp + "] Server: Invalid private message format. Use: /pm username message");
                out.flush();
                return;
            }

            String remaining = message.substring(4).trim();
            if (remaining.isEmpty()) {
                out.println("MESSAGE [" + timestamp + "] Server: Invalid private message format. Use: /pm username message");
                out.flush();
                return;
            }

            String recipient = null;
            String pmContent = null;
            synchronized (userWriters) {
                for (String username : usernameCaseMap.values()) {
                    String usernameLower = username.toLowerCase();
                    if (remaining.toLowerCase().startsWith(usernameLower + " ")) {
                        recipient = username;
                        pmContent = remaining.substring(username.length()).trim();
                        break;
                    }
                }
            }

            if (recipient == null || pmContent == null || pmContent.isEmpty()) {
                out.println("MESSAGE [" + timestamp + "] Server: Invalid private message format or user not found. Use: /pm username message");
                out.flush();
                return;
            }

            String formattedMessage = "[" + timestamp + "] (Private from " + name + "): " + pmContent;

            synchronized (userWriters) {
                PrintWriter recipientWriter = userWriters.get(recipient.toLowerCase());
                if (recipientWriter != null) {
                    recipientWriter.println("MESSAGE " + formattedMessage);
                    recipientWriter.flush();
                    out.println("MESSAGE [" + timestamp + "] (Private to " + recipient + "): " + pmContent);
                    out.flush();
                } else {
                    out.println("MESSAGE [" + timestamp + "] Server: User " + recipient + " not found.");
                    out.flush();
                }
            }
        }

        private void handlePublicTypingStatus(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 2) {
                System.out.println("Server: Invalid typing message format: " + message);
                return;
            }
            String status = parts[1];
            System.out.println("Server: Received /typing " + status + " from " + name);
            synchronized (typingUsers) {
                if ("start".equals(status)) {
                    typingUsers.add(name);
                } else if ("stop".equals(status)) {
                    typingUsers.remove(name);
                }
                broadcastTypingStatus(name, status);
            }
        }

        private void handlePrivateTypingStatus(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 2) {
                System.out.println("Server: Invalid pmtyping message format: " + message);
                return;
            }
            String status = parts[1];
            System.out.println("Server: Received /pmtyping " + status + " from " + name);

            // Find the recipient of the private chat (we need the client to send the recipient)
            String recipient = null;
            String remaining = message.substring(9).trim();
            if (remaining.startsWith("start ") || remaining.startsWith("stop ")) {
                String[] subParts = remaining.split(" ", 2);
                if (subParts.length > 1) {
                    String recipientPart = subParts[1].trim();
                    synchronized (userWriters) {
                        for (String username : usernameCaseMap.values()) {
                            String usernameLower = username.toLowerCase();
                            if (recipientPart.toLowerCase().startsWith(usernameLower)) {
                                recipient = username;
                                break;
                            }
                        }
                    }
                }
            }

            if (recipient == null) {
                System.out.println("Server: Could not determine recipient for /pmtyping from " + name);
                return;
            }

            // Send the typing status only to the recipient
            synchronized (userWriters) {
                PrintWriter recipientWriter = userWriters.get(recipient.toLowerCase());
                if (recipientWriter != null) {
                    String broadcastMessage = "PMTYPING " + name + " " + status;
                    System.out.println("Server: Sending to " + recipient + ": " + broadcastMessage);
                    recipientWriter.println(broadcastMessage);
                    recipientWriter.flush();
                }
            }
        }

        private void broadcastTypingStatus(String user, String status) {
            String broadcastMessage = "TYPING " + user + " " + status;
            System.out.println("Server: Broadcasting: " + broadcastMessage);
            synchronized (writers) {
                for (PrintWriter writer : writers) {
                    writer.println(broadcastMessage);
                    writer.flush();
                }
            }
        }

        private void broadcast(String message) {
            synchronized (messageHistory) {
                messageHistory.add(message);
                if (messageHistory.size() > MAX_HISTORY) {
                    messageHistory.remove(0);
                }
            }
            System.out.println("Server: Broadcasting message: " + message);
            synchronized (writers) {
                for (PrintWriter writer : writers) {
                    writer.println("MESSAGE " + message);
                    writer.flush();
                }
            }
        }

        private void broadcastUserList() {
            synchronized (usernameCaseMap) {
                List<String> originalNames = new ArrayList<>(usernameCaseMap.values());
                String userList = String.join(",", originalNames);
                System.out.println("Server: Broadcasting user list: USERLIST " + userList);
                for (PrintWriter writer : writers) {
                    writer.println("USERLIST " + userList);
                    writer.flush();
                }
            }
        }
    }
}