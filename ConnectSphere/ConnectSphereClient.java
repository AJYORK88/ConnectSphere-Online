import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.text.SimpleDateFormat; // Added to resolve SimpleDateFormat

public class ConnectSphereClient extends JFrame {
    private JTextField messageField;
    private JList<ChatMessage> chatList; // Use JList for public chat messages
    private DefaultListModel<ChatMessage> chatListModel; // Model for public chat JList
    private JComboBox<String> emojiPicker; // Emoji picker for public chat
    private JTextField serverField;
    private JTextField portField;
    private JButton connectButton;
    private JButton sendButton;
    private JButton privateChatButton;
    private JLabel notificationDot; // Red dot for unread message notification
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private String name;
    private boolean isConnected = false;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private PrivateChatBox privateChatBox;
    private Map<String, String> typingUsers = new HashMap<>(); // Track typing messages per user in public chat
    private List<ChatMessage> chatMessages = new ArrayList<>(); // Store public chat messages with IDs
    private Map<String, List<ChatMessage>> privateMessages = new HashMap<>(); // Store private messages with IDs
    private Map<String, Integer> unreadMessages = new HashMap<>(); // Track unread messages per user
    private Timer typingTimer; // Timer to detect when typing stops
    private boolean isTyping = false; // Track if this client is typing
    private int messageIdCounter = 0; // Counter for assigning message IDs
    protected final String[] emojiOptions = {"❤️", "👍", "😂", "😊"}; // Made protected for access by inner class
    private BufferedImage chatBackgroundImage; // Use BufferedImage for ImageIO
    private JLabel backgroundLabel; // Primary label for image
    private JLayeredPane chatLayeredPane; // Moved to class field
    private JScrollPane chatScrollPane; // Added as class field

    // Class to represent a chat message with ID and reactions
    private static class ChatMessage {
        String message;
        int messageId;
        List<String> reactions;
        Date timestamp;

        ChatMessage(String message, int messageId) {
            this.message = message;
            this.messageId = messageId;
            this.reactions = new ArrayList<>();
            this.timestamp = new Date(); // Set current timestamp
        }

        void addReaction(String reaction) {
            reactions.add(reaction);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            // Format timestamp as [MM/dd/yyyy HH:mm:ss]
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            sb.append("[").append(dateFormat.format(timestamp)).append("] ").append(message);
            if (!reactions.isEmpty()) {
                sb.append("\nReactions: ").append(String.join(", ", reactions));
            }
            return sb.toString();
        }
    }

    public ConnectSphereClient() {
        setTitle("ConnectSphere Chat Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(true); // Enable window resizing
        setMinimumSize(new Dimension(800, 400)); // Set minimum size to ensure usability

        // Define color theme
        Color logoColor = new Color(13, 25, 64); // #0D1940

        // Layered pane for chat area
        chatLayeredPane = new JLayeredPane();
        chatLayeredPane.setPreferredSize(new Dimension(650, 300)); // Default size for initial state
        add(chatLayeredPane, BorderLayout.CENTER);

        // Background label as primary
        backgroundLabel = new JLabel();
        backgroundLabel.setOpaque(false); // Let the image handle opacity
        backgroundLabel.setBounds(0, 0, 650, 300); // Initial bounds
        chatLayeredPane.add(backgroundLabel, Integer.valueOf(0)); // Bottom layer

        // Chat list
        chatListModel = new DefaultListModel<>();
        chatList = new JList<ChatMessage>(chatListModel);
        chatList.setBackground(new Color(0, 0, 0, 0)); // Transparent background
        chatList.setForeground(Color.BLACK);
        chatList.setOpaque(false); // Ensure transparency

        // Add chat list to scroll pane
        chatScrollPane = new JScrollPane(chatList); // Use class field
        chatScrollPane.setBounds(0, 0, 650, 300); // Initial bounds
        chatScrollPane.setBackground(new Color(0, 0, 0, 0)); // Fully transparent background
        chatScrollPane.setOpaque(false); // Ensure transparency
        chatScrollPane.getViewport().setOpaque(false); // Ensure viewport transparency
        chatScrollPane.setBorder(BorderFactory.createLineBorder(logoColor, 5));
        chatLayeredPane.add(chatScrollPane, Integer.valueOf(1)); // Above background

        // Connection panel with logo in top left
        JPanel connectPanel = new JPanel(new GridBagLayout());
        connectPanel.setBackground(logoColor);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding around components
        gbc.fill = GridBagConstraints.NONE; // Prevent stretching

        // Add logo with controlled size and left alignment
        JLabel logoLabel = new JLabel();
        try {
            File file = new File("FullLogoLettering_Transparent.png");
            if (!file.exists()) {
                System.err.println("Client: File 'FullLogoLettering_Transparent.png' not found in directory: " + file.getAbsolutePath());
            } else {
                ImageIcon logoIcon = new ImageIcon("FullLogoLettering_Transparent.png");
                Image originalImage = logoIcon.getImage();
                int newWidth = 350;
                int newHeight = (int) ((double) newWidth / originalImage.getWidth(null) * originalImage.getHeight(null));
                if (newHeight > 87) {
                    newHeight = 87;
                    newWidth = (int) ((double) newHeight / originalImage.getHeight(null) * originalImage.getWidth(null));
                }
                Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaledImage));
                logoLabel.setHorizontalAlignment(SwingConstants.LEFT);
            }
        } catch (Exception e) {
            System.err.println("Client: Error loading connection panel logo 'FullLogoLettering_Transparent.png': " + e.getMessage());
            logoLabel.setText("ConnectSphere");
            logoLabel.setHorizontalAlignment(SwingConstants.LEFT);
        }
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.2;
        connectPanel.add(logoLabel, gbc);

        // Add connection controls in a horizontal FlowLayout
        JPanel controlsPanel = new JPanel(new FlowLayout());
        controlsPanel.setBackground(logoColor);

        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setForeground(Color.WHITE);
        controlsPanel.add(serverLabel);
        serverField = new JTextField("localhost", 15);
        serverField.setBackground(Color.WHITE);
        serverField.setForeground(Color.BLACK);
        controlsPanel.add(serverField);
        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(Color.WHITE);
        controlsPanel.add(portLabel);
        portField = new JTextField("5555", 5);
        portField.setBackground(Color.WHITE);
        portField.setForeground(Color.BLACK);
        controlsPanel.add(portField);
        connectButton = new JButton("Connect");
        connectButton.setBackground(Color.WHITE);
        connectButton.setForeground(logoColor);
        controlsPanel.add(connectButton);

        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 0.8;
        connectPanel.add(controlsPanel, gbc);

        add(connectPanel, BorderLayout.NORTH);

        // User list panel (on the right)
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBackground(logoColor);
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(Color.WHITE);
        userList.setForeground(Color.BLACK);
        JScrollPane userListScrollPane = new JScrollPane(userList);
        userListScrollPane.setBackground(logoColor);
        userListScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(logoColor), "Online Users", 0, 0, null, Color.WHITE));
        userPanel.add(userListScrollPane, BorderLayout.CENTER);

        // Private Chat button with notification dot
        privateChatButton = new JButton("Private Chat");
        privateChatButton.setEnabled(false);
        privateChatButton.setBackground(Color.WHITE);
        privateChatButton.setForeground(logoColor);
        privateChatButton.addActionListener(this::openPrivateChatBox);

        // Use JLayeredPane to overlay the notification dot on the button
        JLayeredPane buttonPanel = new JLayeredPane();
        buttonPanel.setBackground(logoColor);
        privateChatButton.setBounds(0, 0, 150, 30);
        buttonPanel.add(privateChatButton, JLayeredPane.DEFAULT_LAYER);

        // Add a red dot for notifications, on the left
        notificationDot = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.RED);
                g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g.setColor(Color.BLACK);
                g.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        notificationDot.setPreferredSize(new Dimension(15, 15));
        notificationDot.setBounds(0, 0, 15, 15);
        notificationDot.setVisible(false);
        buttonPanel.add(notificationDot, JLayeredPane.PALETTE_LAYER);
        buttonPanel.setPreferredSize(new Dimension(150, 30));

        JPanel southPanel = new JPanel(new GridLayout(1, 1));
        southPanel.setBackground(logoColor);
        southPanel.add(buttonPanel);
        userPanel.add(southPanel, BorderLayout.SOUTH);
        userPanel.setPreferredSize(new Dimension(150, 0));
        add(userPanel, BorderLayout.EAST);

        // Message input panel (for public chat)
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBackground(logoColor);
        messageField = new JTextField();
        messageField.setBackground(Color.WHITE);
        messageField.setForeground(Color.BLACK);

        emojiPicker = new JComboBox<>(emojiOptions);
        emojiPicker.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedEmoji = (String) emojiPicker.getSelectedItem();
                if (selectedEmoji != null) {
                    messageField.setText(messageField.getText() + selectedEmoji);
                    messageField.requestFocus();
                }
            }
        });

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.setBackground(Color.WHITE);
        sendButton.setForeground(logoColor);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(logoColor);
        inputPanel.add(emojiPicker, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        messagePanel.add(inputPanel, BorderLayout.CENTER);
        add(messagePanel, BorderLayout.SOUTH);

        // Event listeners
        connectButton.addActionListener(this::connectToServer);
        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);

        // Typing detection for public chat
        typingTimer = new Timer(2000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isTyping) {
                    isTyping = false;
                    if (out != null) {
                        out.println("/typing stop");
                        out.flush();
                        System.out.println("Client " + name + ": Sent /typing stop");
                    }
                }
            }
        });
        typingTimer.setRepeats(false);

        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                if (!isTyping && isConnected) {
                    isTyping = true;
                    if (out != null) {
                        out.println("/typing start");
                        out.flush();
                        System.out.println("Client " + name + ": Sent /typing start");
                    } else {
                        System.out.println("Client " + name + ": Failed to send /typing start - output stream is null");
                    }
                }
                typingTimer.restart();
            }
        });

        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });

        // Add component listener for resize
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeChatComponents();
            }
        });

        // Set taskbar icon before showing the frame
        setTaskbarIcon();
        pack(); // Use pack() to let layout manager size the frame
        setVisible(true);
        revalidate(); // Ensure layout is updated
        repaint();    // Force frame repaint

        // Prompt for username
        name = JOptionPane.showInputDialog(this, "Enter your name:", "Name Entry", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            System.exit(0);
        }
        name = name.trim();

        // Load the faded logo image for chat background
        SwingUtilities.invokeLater(this::loadBackgroundImage);
    }

    private void setTaskbarIcon() {
        try {
            File file = new File("C:\\Users\\ajyor\\OneDrive\\Documents\\Java Projects\\Lab 4-5\\FullLogo_NoBuffer.jpg");
            System.out.println("Client " + (name != null ? name : "unknown") + ": Checking file for taskbar icon: " + file.getAbsolutePath() + ", exists: " + file.exists());
            if (!file.exists()) {
                System.err.println("Client: File 'FullLogo_NoBuffer.jpg' not found for taskbar icon in directory: " + file.getAbsolutePath());
            } else {
                BufferedImage originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    System.err.println("Client: ImageIO failed to read 'FullLogo_NoBuffer.jpg' for taskbar icon");
                } else {
                    int originalWidth = originalImage.getWidth();
                    int originalHeight = originalImage.getHeight();
                    System.out.println("Client " + (name != null ? name : "unknown") + ": Original taskbar icon dimensions: " + originalWidth + "x" + originalHeight);

                    // Force taskbar icon to 64x64
                    BufferedImage scaledIconImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2dIcon = scaledIconImage.createGraphics();
                    g2dIcon.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2dIcon.drawImage(originalImage, 0, 0, 64, 64, null); // Stretch to fit 64x64
                    g2dIcon.dispose();
                    setIconImage(scaledIconImage);
                    System.out.println("Client " + (name != null ? name : "unknown") + ": Taskbar icon set to 64x64");
                }
            }
        } catch (IOException e) {
            System.err.println("Client: IOException loading taskbar icon 'FullLogo_NoBuffer.jpg': " + e.getMessage());
        }
    }

    private void loadBackgroundImage() {
        try {
            File file = new File("C:\\Users\\ajyor\\OneDrive\\Documents\\Java Projects\\Lab 4-5\\FullLogo_Transparent.png");
            System.out.println("Client " + name + ": Checking file: " + file.getAbsolutePath() + ", exists: " + file.exists());
            if (!file.exists()) {
                System.err.println("Client: File 'FullLogo_Transparent.png' not found in directory: " + file.getAbsolutePath());
            } else {
                BufferedImage originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    System.err.println("Client: ImageIO failed to read 'FullLogo_Transparent.png'");
                } else {
                    int originalWidth = originalImage.getWidth();
                    int originalHeight = originalImage.getHeight();
                    System.out.println("Client " + name + ": Original image dimensions: " + originalWidth + "x" + originalHeight);

                    // Get current dimensions of chatLayeredPane
                    int targetWidth = chatLayeredPane.getWidth();
                    int targetHeight = chatLayeredPane.getHeight();
                    if (targetWidth <= 0 || targetHeight <= 0) {
                        targetWidth = 650; // Default width if not yet set
                        targetHeight = 300; // Default height if not yet set
                    }

                    // Scale background image to fit current dimensions with aspect ratio preserved
                    double widthRatio = (double) targetWidth / originalWidth;
                    double heightRatio = (double) targetHeight / originalHeight;
                    double scaleFactor = Math.min(widthRatio, heightRatio) * 1.5; // Increase size by 1.5x
                    int scaledWidth = (int) (originalWidth * scaleFactor);
                    int scaledHeight = (int) (originalHeight * scaleFactor);
                    // Cap dimensions to fit within current bounds
                    if (scaledWidth > targetWidth) {
                        scaledWidth = targetWidth;
                        scaledHeight = (int) (scaledWidth * (originalHeight / (double) originalWidth));
                    }
                    if (scaledHeight > targetHeight) {
                        scaledHeight = targetHeight;
                        scaledWidth = (int) (scaledHeight * (originalWidth / (double) originalHeight));
                    }
                    int x = (targetWidth - scaledWidth) / 2; // Center horizontally
                    int y = (targetHeight - scaledHeight) / 2; // Center vertically
                    BufferedImage scaledBackgroundImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2dBackground = scaledBackgroundImage.createGraphics();
                    g2dBackground.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2dBackground.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // 50% opacity
                    g2dBackground.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
                    g2dBackground.dispose();
                    chatBackgroundImage = scaledBackgroundImage;
                    System.out.println("Client " + name + ": Chat background image scaled to " + scaledWidth + "x" + scaledHeight + " with transparency");
                    backgroundLabel.setIcon(new ImageIcon(chatBackgroundImage));
                    backgroundLabel.setBounds(0, 0, targetWidth, targetHeight);

                    // Adjust chatScrollPane bounds
                    chatScrollPane.setBounds(0, 0, targetWidth, targetHeight);

                    // Force update
                    backgroundLabel.revalidate();
                    backgroundLabel.repaint();
                    chatLayeredPane.revalidate();
                    chatLayeredPane.repaint();
                    revalidate();
                    repaint();
                    System.out.println("Client " + name + ": Background label size: " + backgroundLabel.getWidth() + "x" + backgroundLabel.getHeight() + ", visible: " + backgroundLabel.isVisible());
                }
            }
        } catch (IOException e) {
            System.err.println("Client: IOException loading chat background image 'FullLogo_Transparent.png': " + e.getMessage());
        }
    }

    private void resizeChatComponents() {
        SwingUtilities.invokeLater(() -> {
            loadBackgroundImage(); // Reload and rescale background on resize
            revalidate();
            repaint();
        });
    }

    private void connectToServer(ActionEvent e) {
        try {
            String serverAddress = serverField.getText();
            int port = Integer.parseInt(portField.getText());
            socket = new Socket(serverAddress, port);

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;
            System.out.println("Client " + name + ": Connected to server at " + serverAddress + ":" + port);

            // Start a thread to handle server messages
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("Client " + name + ": Received from server: " + line);
                        if (line.startsWith("SUBMITNAME")) {
                            out.println(name);
                            out.flush();
                            System.out.println("Client " + name + ": Sent name: " + name);
                        } else if (line.startsWith("NAMEACCEPTED")) {
                            SwingUtilities.invokeLater(() -> {
                                sendButton.setEnabled(true);
                                privateChatButton.setEnabled(true);
                                connectButton.setEnabled(false);
                                serverField.setEnabled(false);
                                portField.setEnabled(false);
                                System.out.println("Client " + name + ": Name accepted, UI updated");
                            });
                        } else if (line.startsWith("MESSAGE")) {
                            String message = line.substring(8);
                            System.out.println("Client " + name + ": Processing MESSAGE: " + message);
                            if (message.contains("(Private from") || message.contains("(Private to")) {
                                handlePrivateMessage(message);
                            } else {
                                SwingUtilities.invokeLater(() -> {
                                    String sender = extractSenderFromMessage(message);
                                    if (sender != null) {
                                        removeTypingMessage(sender);
                                    }
                                    ChatMessage chatMessage = new ChatMessage(message + "\n", messageIdCounter++);
                                    chatMessages.add(chatMessage);
                                    rebuildChatArea();
                                });
                            }
                        } else if (line.startsWith("USERLIST")) {
                            String userListStr = line.substring(9);
                            SwingUtilities.invokeLater(() -> updateUserList(userListStr));
                        } else if (line.startsWith("TYPING")) {
                            System.out.println("Client " + name + ": Raw TYPING message: '" + line + "'");
                            String status = null;
                            String user = null;
                            if (line.endsWith(" start")) {
                                status = "start";
                                user = line.substring(7, line.length() - 6).trim();
                            } else if (line.endsWith(" stop")) {
                                status = "stop";
                                user = line.substring(7, line.length() - 5).trim();
                            }
                            if (user != null && status != null) {
                                System.out.println("Client " + name + ": Processing TYPING " + user + " " + status);
                                final String finalUser = user;
                                final String finalStatus = status;
                                SwingUtilities.invokeLater(() -> {
                                    updateTypingIndicator(finalUser, finalStatus);
                                    if (privateChatBox != null) {
                                        privateChatBox.updatePublicTypingIndicator(finalUser, finalStatus);
                                    }
                                });
                            } else {
                                System.out.println("Client " + name + ": Invalid TYPING message format: " + line);
                            }
                        } else if (line.startsWith("PMTYPING")) {
                            System.out.println("Client " + name + ": Raw PMTYPING message: '" + line + "'");
                            String status = null;
                            String user = null;
                            if (line.endsWith(" start")) {
                                status = "start";
                                user = line.substring(9, line.length() - 6).trim();
                            } else if (line.endsWith(" stop")) {
                                status = "stop";
                                user = line.substring(9, line.length() - 5).trim();
                            }
                            if (user != null && status != null) {
                                System.out.println("Client " + name + ": Processing PMTYPING " + user + " " + status);
                                final String finalUser = user;
                                final String finalStatus = status;
                                SwingUtilities.invokeLater(() -> {
                                    if (privateChatBox != null) {
                                        privateChatBox.updatePrivateTypingIndicator(finalUser, finalStatus);
                                    }
                                });
                            } else {
                                System.out.println("Client " + name + ": Invalid PMTYPING message format: " + line);
                            }
                        } else if (line.startsWith("REACTION")) {
                            // Handle private reaction messages: REACTION user messageId emoji
                            String[] parts = line.split(" ", 4);
                            if (parts.length == 4) {
                                String user = parts[1];
                                int messageId = Integer.parseInt(parts[2]);
                                String emoji = parts[3];
                                handleReaction(user, messageId, emoji);
                            }
                        } else if (line.startsWith("REACTION_PUBLIC")) {
                            // Handle public reaction messages: REACTION_PUBLIC messageId emoji
                            String[] parts = line.split(" ", 3);
                            if (parts.length == 3) {
                                int messageId = Integer.parseInt(parts[1]);
                                String emoji = parts[2];
                                handlePublicReaction(messageId, emoji);
                            }
                        } else {
                            System.out.println("Client " + name + ": Unhandled message type: " + line);
                        }
                    }
                } catch (IOException ex) {
                    if (isConnected) {
                        SwingUtilities.invokeLater(() -> {
                            chatMessages.add(new ChatMessage("Connection lost\n", -1));
                            rebuildChatArea();
                        });
                    }
                    System.out.println("Client " + name + ": Connection error: " + ex.getMessage());
                } finally {
                    disconnect();
                }
            }).start();

        } catch (IOException ex) {
            chatMessages.add(new ChatMessage("Error connecting to server: " + ex.getMessage() + "\n", -1));
            rebuildChatArea();
            System.out.println("Client " + name + ": Error connecting to server: " + ex.getMessage());
        }
    }

    private void rebuildChatArea() {
        System.out.println("Client " + name + ": rebuildChatArea called, on EDT: " + SwingUtilities.isEventDispatchThread());
        chatListModel.clear();
        for (ChatMessage message : chatMessages) {
            chatListModel.addElement(message);
        }
        chatList.ensureIndexIsVisible(chatListModel.getSize() - 1);
        chatList.revalidate();
        chatList.repaint();
        chatList.getParent().revalidate();
        chatList.getParent().repaint();
        backgroundLabel.repaint(); // Ensure background remains visible
        System.out.println("Client " + name + ": Rebuilt chat area. Messages: " + chatListModel.getSize());
    }

    private String extractSenderFromMessage(String message) {
        int start = message.indexOf(']') + 2;
        int end = message.indexOf(':', start);
        if (end == -1) return null;
        return message.substring(start, end);
    }

    private void updateTypingIndicator(String user, String status) {
        System.out.println("Client " + name + ": updateTypingIndicator called, on EDT: " + SwingUtilities.isEventDispatchThread());
        if (user.equals(name)) {
            System.out.println("Client " + name + ": Ignoring own typing status for " + user);
            return;
        }
        System.out.println("Client " + name + ": Before update - typingUsers: " + typingUsers);
        if ("start".equals(status)) {
            // Remove any existing typing message for this user
            if (typingUsers.containsKey(user)) {
                String existingMessage = typingUsers.get(user);
                chatMessages.removeIf(msg -> msg.message.equals(existingMessage));
            }
            // Add the new typing message
            String typingMessage = user + " is typing...\n";
            typingUsers.put(user, typingMessage);
            chatMessages.add(new ChatMessage(typingMessage, -1));
            System.out.println("Client " + name + ": Added typing user: " + user);
        } else if ("stop".equals(status)) {
            if (typingUsers.containsKey(user)) {
                String typingMessage = typingUsers.remove(user);
                chatMessages.removeIf(msg -> msg.message.equals(typingMessage));
                System.out.println("Client " + name + ": Removed typing user: " + user);
            }
        }
        rebuildChatArea();
        System.out.println("Client " + name + ": After update - typingUsers: " + typingUsers);
    }

    private void removeTypingMessage(String user) {
        if (typingUsers.containsKey(user)) {
            String typingMessage = typingUsers.remove(user);
            chatMessages.removeIf(msg -> msg.message.equals(typingMessage));
            System.out.println("Client " + name + ": Removed typing user: " + user + " (message received)");
            rebuildChatArea();
        }
    }

    private void handlePrivateMessage(String message) {
        System.out.println("Client " + name + ": Handling private message: " + message);
        String otherUser;
        boolean isIncoming = message.contains("(Private from");
        if (message.contains("(Private from")) {
            int start = message.indexOf("(Private from ") + 14;
            int end = message.indexOf("):");
            otherUser = message.substring(start, end);
        } else if (message.contains("(Private to")) {
            int start = message.indexOf("(Private to ") + 12;
            int end = message.indexOf("):");
            otherUser = message.substring(start, end);
        } else {
            System.out.println("Client " + name + ": Message does not match private message format: " + message);
            return;
        }

        String otherUserLower = otherUser.toLowerCase();
        privateMessages.putIfAbsent(otherUserLower, new ArrayList<>());
        List<ChatMessage> userMessages = privateMessages.get(otherUserLower);
        ChatMessage chatMessage = new ChatMessage(message, messageIdCounter++);
        userMessages.add(chatMessage);

        // Update unread messages if this is an incoming message
        if (isIncoming) {
            unreadMessages.merge(otherUserLower, 1, Integer::sum);
            updateNotification();
        }

        if (privateChatBox != null) {
            privateChatBox.appendMessage(chatMessage, otherUserLower, isIncoming);
        }
    }

    private void handleReaction(String user, int messageId, String emoji) {
        String userLower = user.toLowerCase();
        List<ChatMessage> userMessages = privateMessages.get(userLower);
        if (userMessages != null) {
            for (ChatMessage msg : userMessages) {
                if (msg.messageId == messageId) {
                    msg.addReaction(emoji);
                    break;
                }
            }
            if (privateChatBox != null) {
                privateChatBox.refreshMessages(userLower);
            }
        }
    }

    private void handlePublicReaction(int messageId, String emoji) {
        for (ChatMessage msg : chatMessages) {
            if (msg.messageId == messageId) {
                msg.addReaction(emoji);
                break;
            }
        }
        rebuildChatArea();
    }

    // Method to add a public reaction (used via context menu)
    @SuppressWarnings("unused")
    private void addPublicReaction(int messageIndex, String emoji) {
        if (messageIndex >= 0 && messageIndex < chatListModel.size()) {
            ChatMessage chatMessage = chatListModel.getElementAt(messageIndex);
            if (out != null && isConnected) {
                out.println("/reaction_public " + chatMessage.messageId + " " + emoji);
                out.flush();
                System.out.println("Client " + name + ": Sent public reaction: " + emoji + " for message ID " + chatMessage.messageId);
            } else {
                System.out.println("Client " + name + ": Cannot send reaction - not connected or output stream null");
            }
        }
    }

    private void updateUserList(String userListStr) {
        userListModel.clear();
        if (!userListStr.isEmpty()) {
            String[] users = userListStr.split(",");
            for (String user : users) {
                userListModel.addElement(user.trim());
            }
        }
        List<String> onlineUsers = new ArrayList<>();
        for (int i = 0; i < userListModel.getSize(); i++) {
            onlineUsers.add(userListModel.getElementAt(i));
        }
        // Remove typing messages for users who are no longer online
        Set<String> keysToRemove = new HashSet<>();
        for (String user : typingUsers.keySet()) {
            if (!onlineUsers.contains(user)) {
                String typingMessage = typingUsers.get(user);
                chatMessages.removeIf(msg -> msg.message.equals(typingMessage));
                System.out.println("Client " + name + ": Removed typing user (disconnected): " + user);
                keysToRemove.add(user);
            }
        }
        for (String user : keysToRemove) {
            typingUsers.remove(user);
        }
        rebuildChatArea();
        userList.revalidate();
        userList.repaint();
        System.out.println("Client " + name + ": Updated user list: " + userListStr);
        // Update the private chat box user list
        if (privateChatBox != null) {
            privateChatBox.updateUserList(onlineUsers);
        }
    }

    private void openPrivateChatBox(ActionEvent e) {
        if (privateChatBox == null) {
            privateChatBox = new PrivateChatBox(name, out, privateMessages);
            // Populate initial user list
            List<String> onlineUsers = new ArrayList<>();
            for (int i = 0; i < userListModel.getSize(); i++) {
                onlineUsers.add(userListModel.getElementAt(i));
            }
            privateChatBox.updateUserList(onlineUsers);
        }
        privateChatBox.showWindow();
        // Clear notification for the currently selected tab (if any)
        privateChatBox.clearNotificationForSelectedTab();
    }

    private void sendMessage(ActionEvent e) {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            out.flush();
            System.out.println("Client " + name + ": Sent message: " + message);
            messageField.setText("");
            if (isTyping) {
                isTyping = false;
                out.println("/typing stop");
                out.flush();
                typingTimer.stop();
                System.out.println("Client " + name + ": Sent /typing stop (message sent)");
            }
        }
    }

    private void disconnect() {
        if (!isConnected) return;

        try {
            isConnected = false;
            if (out != null) {
                if (isTyping) {
                    out.println("/typing stop");
                    out.flush();
                    System.out.println("Client " + name + ": Sent /typing stop (disconnect)");
                }
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            SwingUtilities.invokeLater(() -> {
                sendButton.setEnabled(false);
                privateChatButton.setEnabled(false);
                connectButton.setEnabled(true);
                serverField.setEnabled(true);
                portField.setEnabled(true);
                userListModel.clear();
                // Clear all typing messages
                for (String typingMessage : typingUsers.values()) {
                    chatMessages.removeIf(msg -> msg.message.equals(typingMessage));
                }
                typingUsers.clear();
                chatMessages.add(new ChatMessage("Disconnected from server\n", -1));
                rebuildChatArea();
                if (privateChatBox != null) {
                    privateChatBox.dispose();
                    privateChatBox = null;
                }
                // Reset notification dot and unread messages
                unreadMessages.clear();
                notificationDot.setVisible(false);
                privateChatButton.setText("Private Chat");
                privateChatButton.revalidate();
                privateChatButton.repaint();
                System.out.println("Client " + name + ": Disconnected from server");
            });
        } catch (IOException ex) {
            chatMessages.add(new ChatMessage("Error during disconnect: " + ex.getMessage() + "\n", -1));
            rebuildChatArea();
            System.out.println("Client " + name + ": Error during disconnect: " + ex.getMessage());
        }
    }

    private void updateNotification() {
        int totalUnread = unreadMessages.values().stream().mapToInt(Integer::intValue).sum();
        SwingUtilities.invokeLater(() -> {
            System.out.println("Client " + name + ": Updating notification: totalUnread = " + totalUnread);
            if (totalUnread > 0) {
                privateChatButton.setText("Private Chat (" + totalUnread + ")");
                notificationDot.setVisible(true);
            } else {
                privateChatButton.setText("Private Chat");
                notificationDot.setVisible(false);
            }
            privateChatButton.revalidate();
            privateChatButton.repaint();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ConnectSphereClient::new);
    }

    private class PrivateChatBox extends JFrame {
        private JTabbedPane chatTabs;
        private Map<String, JList<ChatMessage>> chatLists; // Use JList for messages
        private Map<String, DefaultListModel<ChatMessage>> chatListModels; // Models for JList
        private Map<String, JTextField> messageFields;
        private Map<String, JButton> sendButtons;
        private Map<String, JLabel> tabLabels; // Store tab labels for styling
        private Map<String, JComboBox<String>> emojiPickers; // Emoji pickers for each tab
        private PrintWriter out;
        private String senderName;
        private Map<String, List<ChatMessage>> privateMessages; // Reference to ConnectSphereClient's privateMessages
        private Map<String, String> typingUsers; // Typing indicators per user
        private Timer typingTimer;
        private boolean isTyping = false;
        private Color logoColor = new Color(13, 25, 64); // #0D1940
        private Image backgroundImage; // Moved to class field

        public PrivateChatBox(String senderName, PrintWriter out, Map<String, List<ChatMessage>> privateMessages) {
            this.senderName = senderName;
            this.out = out;
            this.privateMessages = privateMessages;
            setTitle("ConnectSphere Private Chat");
            setSize(400, 300);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

            // Initialize data structures
            typingUsers = new HashMap<>();
            chatLists = new HashMap<>();
            chatListModels = new HashMap<>();
            messageFields = new HashMap<>();
            sendButtons = new HashMap<>();
            tabLabels = new HashMap<>();
            emojiPickers = new HashMap<>();

            // Load the faded logo image for chat background
            try {
                File file = new File("FullLogo_NoBuffer.jpg");
                if (!file.exists()) {
                    System.err.println("Client: File 'FullLogo_NoBuffer.jpg' not found in directory: " + file.getAbsolutePath());
                } else {
                    backgroundImage = new ImageIcon("FullLogo_NoBuffer.jpg").getImage();
                    // Scale the background image to a larger size for visibility (e.g., 200x100)
                    int targetWidth = 200;
                    int targetHeight = 100;
                    int originalWidth = backgroundImage.getWidth(null);
                    int originalHeight = backgroundImage.getHeight(null);
                    if (originalWidth > 0 && originalHeight > 0) { // Check for valid dimensions
                        int newWidth = targetWidth;
                        int newHeight = (int) ((double) newWidth / originalWidth * originalHeight);
                        if (newHeight > targetHeight) {
                            newHeight = targetHeight;
                            newWidth = (int) ((double) newHeight / originalHeight * originalWidth);
                        }
                        backgroundImage = backgroundImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                        System.out.println("Client " + senderName + ": Background image loaded and scaled to " + newWidth + "x" + newHeight);
                    } else {
                        System.err.println("Client: Invalid image dimensions for 'FullLogo_NoBuffer.jpg'");
                    }
                }
            } catch (Exception e) {
                System.err.println("Client: Error loading chat background image 'FullLogo_NoBuffer.jpg': " + e.getMessage());
            }

            // Chat tabs
            chatTabs = new JTabbedPane();
            chatTabs.setBackground(logoColor); // Set navy background for the tabbed pane
            chatTabs.setOpaque(true); // Ensure background is fully applied
            System.out.println("Setting tabbed pane background to: " + logoColor); // Debug output
            add(chatTabs, BorderLayout.CENTER);

            // Typing detection for private chat
            typingTimer = new Timer(2000, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isTyping) {
                        isTyping = false;
                        int selectedIndex = chatTabs.getSelectedIndex();
                        if (selectedIndex != -1) {
                            String recipient = chatTabs.getTitleAt(selectedIndex);
                            if (out != null && recipient != null) {
                                out.println("/pmtyping " + recipient + " stop");
                                out.flush();
                                System.out.println("Client " + senderName + ": Sent /pmtyping stop (private)");
                            }
                        }
                    }
                }
            });
            typingTimer.setRepeats(false);

            // Add listener to clear unread status when a tab is selected
            chatTabs.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    int selectedIndex = chatTabs.getSelectedIndex();
                    if (selectedIndex != -1) {
                        String selectedUser = chatTabs.getTitleAt(selectedIndex);
                        clearUnreadStatus(selectedUser.toLowerCase());
                    }
                }
            });

            setLocationRelativeTo(null);
        }

        public void updateUserList(List<String> onlineUsers) {
            // Keep track of existing tabs
            List<String> existingTabs = new ArrayList<>();
            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                existingTabs.add(chatTabs.getTitleAt(i).toLowerCase());
            }

            // Add new users as tabs
            for (String user : onlineUsers) {
                if (!user.equalsIgnoreCase(senderName)) {
                    String userLower = user.toLowerCase();
                    if (!existingTabs.contains(userLower)) {
                        DefaultListModel<ChatMessage> chatListModel = new DefaultListModel<>();
                        chatListModels.put(userLower, chatListModel);

                        // Background panel for private chat
                        JPanel tabPanel = new JPanel(new BorderLayout()) {
                            @Override
                            protected void paintComponent(Graphics g) {
                                super.paintComponent(g);
                                g.setColor(logoColor); // Set navy background first
                                g.fillRect(0, 0, getWidth(), getHeight());
                                if (backgroundImage != null) {
                                    System.out.println("Client " + senderName + ": Painting tab panel background for " + userLower + ", width: " + getWidth() + ", height: " + getHeight());
                                    Graphics2D g2d = (Graphics2D) g;
                                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)); // 20% opacity
                                    g2d.drawImage(backgroundImage, (getWidth() - backgroundImage.getWidth(null)) / 2,
                                            (getHeight() - backgroundImage.getHeight(null)) / 2, null);
                                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                                } else {
                                    System.out.println("Client " + senderName + ": Background image is null for " + userLower + ", not rendering.");
                                }
                            }
                        };
                        tabPanel.setBackground(logoColor); // Set navy background color

                        JList<ChatMessage> chatList = new JList<>(chatListModel);
                        chatList.setBackground(Color.WHITE); // Solid white background for text
                        chatList.setForeground(Color.BLACK);
                        chatList.setOpaque(true); // Ensure JList renders its background
                        chatLists.put(userLower, chatList);

                        JScrollPane chatScrollPane = new JScrollPane(chatList);
                        chatScrollPane.setPreferredSize(new Dimension(350, 200)); // Larger size for testing
                        chatScrollPane.setBackground(logoColor);
                        chatScrollPane.getViewport().setOpaque(false); // Make viewport transparent to show background
                        tabPanel.add(chatScrollPane, BorderLayout.CENTER);

                        // Add context menu for reactions in private chat
                        JPopupMenu contextMenu = new JPopupMenu();
                        JMenuItem heartItem = new JMenuItem("❤️ Heart");
                        heartItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                addReaction(userLower, chatList.getSelectedIndex(), "❤️");
                            }
                        });
                        contextMenu.add(heartItem);
                        JMenuItem thumbsUpItem = new JMenuItem("👍 Thumbs Up");
                        thumbsUpItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                addReaction(userLower, chatList.getSelectedIndex(), "👍");
                            }
                        });
                        contextMenu.add(thumbsUpItem);
                        JMenuItem thumbsDownItem = new JMenuItem("👎 Thumbs Down");
                        thumbsDownItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                addReaction(userLower, chatList.getSelectedIndex(), "👎");
                            }
                        });
                        contextMenu.add(thumbsDownItem);
                        JMenuItem clapItem = new JMenuItem("👏 Clapping Hands");
                        clapItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                addReaction(userLower, chatList.getSelectedIndex(), "👏");
                            }
                        });
                        contextMenu.add(clapItem);
                        JMenuItem partyItem = new JMenuItem("🎉 Party Popper");
                        partyItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                addReaction(userLower, chatList.getSelectedIndex(), "🎉");
                            }
                        });
                        contextMenu.add(partyItem);

                        chatList.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                if (SwingUtilities.isRightMouseButton(e)) {
                                    System.out.println("Client " + senderName + ": Right-click detected on private chat JList for user: " + userLower);
                                    if (chatList.getModel().getSize() > 0) {
                                        int index = chatList.locationToIndex(e.getPoint());
                                        if (index >= 0) {
                                            chatList.setSelectedIndex(index);
                                            System.out.println("Client " + senderName + ": Showing private chat context menu with " + contextMenu.getComponentCount() + " reaction options for user: " + userLower);
                                            contextMenu.show(chatList, e.getX(), e.getY());
                                        } else {
                                            System.out.println("Client " + senderName + ": No message selected at click location for user: " + userLower);
                                        }
                                    } else {
                                        System.out.println("Client " + senderName + ": No messages in private chat to show context menu for user: " + userLower);
                                    }
                                }
                            }
                        });

                        JTextField messageField = new JTextField();
                        messageField.setBackground(Color.WHITE);
                        messageField.setForeground(Color.BLACK);
                        messageFields.put(userLower, messageField);

                        JButton sendButton = new JButton("Send");
                        sendButton.setBackground(Color.WHITE);
                        sendButton.setForeground(logoColor);
                        sendButtons.put(userLower, sendButton);

                        JComboBox<String> emojiPicker = new JComboBox<>(ConnectSphereClient.this.emojiOptions); // Access outer class field
                        emojiPicker.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                String selectedEmoji = (String) emojiPicker.getSelectedItem();
                                if (selectedEmoji != null) {
                                    messageField.setText(messageField.getText() + selectedEmoji);
                                    messageField.requestFocus();
                                }
                            }
                        });
                        emojiPickers.put(userLower, emojiPicker);

                        JPanel inputPanel = new JPanel(new BorderLayout());
                        inputPanel.setBackground(logoColor);
                        inputPanel.add(emojiPicker, BorderLayout.WEST);
                        inputPanel.add(messageField, BorderLayout.CENTER);
                        inputPanel.add(sendButton, BorderLayout.EAST);
                        tabPanel.add(inputPanel, BorderLayout.SOUTH);

                        JLabel tabLabel = new JLabel(user);
                        tabLabels.put(userLower, tabLabel);
                        int tabIndex = chatTabs.getTabCount();
                        chatTabs.addTab(user, tabPanel);
                        chatTabs.setTabComponentAt(tabIndex, tabLabel);

                        // Add event listeners for this tab
                        sendButton.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                sendMessage(user);
                            }
                        });
                        messageField.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                sendMessage(user);
                            }
                        });
                        messageField.addKeyListener(new KeyAdapter() {
                            @Override
                            public void keyTyped(KeyEvent event) {
                                if (!isTyping && isConnected) {
                                    isTyping = true;
                                    if (out != null) {
                                        out.println("/pmtyping " + user + " start");
                                        out.flush();
                                        System.out.println("Client " + senderName + ": Sent /pmtyping start (private)");
                                    }
                                }
                                typingTimer.restart();
                            }
                        });

                        // Load existing messages
                        List<ChatMessage> messages = privateMessages.getOrDefault(userLower, new ArrayList<>());
                        for (ChatMessage message : messages) {
                            chatListModel.addElement(message);
                        }
                        chatList.ensureIndexIsVisible(chatListModel.getSize() - 1);

                        // Update tab label with unread count if any
                        Integer unreadCount = unreadMessages.getOrDefault(userLower, 0);
                        if (unreadCount > 0) {
                            tabLabel.setText(userLower + " (" + unreadCount + ")");
                        }
                    }
                }
            }

            // Remove tabs for users who are no longer online
            for (int i = chatTabs.getTabCount() - 1; i >= 0; i--) {
                String tabUser = chatTabs.getTitleAt(i);
                if (!onlineUsers.contains(tabUser) && !tabUser.equalsIgnoreCase(senderName)) {
                    chatTabs.remove(i);
                    String tabUserLower = tabUser.toLowerCase();
                    chatLists.remove(tabUserLower);
                    chatListModels.remove(tabUserLower);
                    messageFields.remove(tabUserLower);
                    sendButtons.remove(tabUserLower);
                    tabLabels.remove(tabUserLower);
                    emojiPickers.remove(tabUserLower);
                }
            }

            // Remove typing indicators for users who are no longer online
            Set<String> typingKeysToRemove = new HashSet<>();
            for (String user : typingUsers.keySet()) {
                if (!onlineUsers.contains(user)) {
                    typingKeysToRemove.add(user);
                }
            }
            for (String user : typingKeysToRemove) {
                typingUsers.remove(user);
            }
        }

        private void sendMessage(String recipient) {
            JTextField messageField = messageFields.get(recipient.toLowerCase());
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                out.println("/pm " + recipient + " " + message);
                out.flush();
                System.out.println("Client " + senderName + ": Sent private message to " + recipient + ": " + message);
                messageField.setText("");
                if (isTyping) {
                    isTyping = false;
                    out.println("/pmtyping " + recipient + " stop");
                    out.flush();
                    typingTimer.stop();
                    System.out.println("Client " + senderName + ": Sent /pmtyping stop (private message sent)");
                }
            }
        }

        private void addReaction(String userLower, int messageIndex, String emoji) {
            DefaultListModel<ChatMessage> chatListModel = chatListModels.get(userLower);
            if (chatListModel != null && messageIndex >= 0 && messageIndex < chatListModel.size()) {
                ChatMessage chatMessage = chatListModel.getElementAt(messageIndex);
                out.println("/reaction " + userLower + " " + chatMessage.messageId + " " + emoji);
                out.flush();
                System.out.println("Client " + senderName + ": Sent reaction to " + userLower + ": " + emoji);
            }
        }

        public void appendMessage(ChatMessage chatMessage, String otherUserLower, boolean isIncoming) {
            System.out.println("Client " + senderName + ": Appending message: " + chatMessage.message);
            DefaultListModel<ChatMessage> chatListModel = chatListModels.get(otherUserLower);
            JList<ChatMessage> chatList = chatLists.get(otherUserLower);
            JLabel tabLabel = tabLabels.get(otherUserLower);
            if (chatListModel != null && chatList != null) {
                chatListModel.addElement(chatMessage);
                chatList.ensureIndexIsVisible(chatListModel.getSize() - 1);
            }
            if (tabLabel != null && isIncoming) {
                int selectedIndex = chatTabs.getSelectedIndex();
                String selectedUser = selectedIndex != -1 ? chatTabs.getTitleAt(selectedIndex).toLowerCase() : null;
                if (selectedUser == null || !selectedUser.equals(otherUserLower)) {
                    Integer unreadCount = unreadMessages.getOrDefault(otherUserLower, 0);
                    tabLabel.setText(unreadCount > 0 ? otherUserLower + " (" + unreadCount + ")" : otherUserLower);
                    tabLabel.revalidate();
                    tabLabel.repaint();
                }
            }
        }

        public void refreshMessages(String userLower) {
            DefaultListModel<ChatMessage> chatListModel = chatListModels.get(userLower);
            JList<ChatMessage> chatList = chatLists.get(userLower);
            if (chatListModel != null && chatList != null) {
                chatListModel.clear();
                List<ChatMessage> messages = privateMessages.getOrDefault(userLower, new ArrayList<>());
                for (ChatMessage message : messages) {
                    chatListModel.addElement(message);
                }
                chatList.ensureIndexIsVisible(chatListModel.getSize() - 1);
            }
        }

        private void clearUnreadStatus(String userLower) {
            if (unreadMessages.containsKey(userLower)) {
                System.out.println("Client " + senderName + ": Clearing unread for user: " + userLower);
                unreadMessages.remove(userLower);
                JLabel tabLabel = tabLabels.get(userLower);
                if (tabLabel != null) {
                    tabLabel.setText(userLower);
                    tabLabel.revalidate();
                    tabLabel.repaint();
                }
                ConnectSphereClient.this.updateNotification();
            }
        }

        private void clearNotificationForSelectedTab() {
            int selectedIndex = chatTabs.getSelectedIndex();
            if (selectedIndex != -1) {
                String selectedUser = chatTabs.getTitleAt(selectedIndex);
                clearUnreadStatus(selectedUser.toLowerCase());
            } else if (chatTabs.getTabCount() == 1) {
                // If there's only one tab, clear its unread status
                String onlyUser = chatTabs.getTitleAt(0);
                clearUnreadStatus(onlyUser.toLowerCase());
            }
        }

        public void updatePublicTypingIndicator(String user, String status) {
            // Do nothing; this method is called for public typing indicators
            // We don't want public typing indicators to affect the private chat
        }

        public void updatePrivateTypingIndicator(String user, String status) {
            if (user.equals(senderName)) {
                return;
            }
            String userLower = user.toLowerCase(); // Correctly define userLower
            if ("start".equals(status)) {
                typingUsers.put(userLower, user + " is typing...\n");
            } else if ("stop".equals(status)) {
                typingUsers.remove(userLower);
            }
            int selectedIndex = chatTabs.getSelectedIndex();
            if (selectedIndex != -1) {
                String selectedUser = chatTabs.getTitleAt(selectedIndex);
                if (userLower.equalsIgnoreCase(selectedUser.toLowerCase())) {
                    DefaultListModel<ChatMessage> chatListModel = chatListModels.get(userLower);
                    JList<ChatMessage> chatList = chatLists.get(userLower);
                    if (chatListModel != null && chatList != null) {
                        List<ChatMessage> messages = privateMessages.getOrDefault(userLower, new ArrayList<>());
                        chatListModel.clear();
                        for (ChatMessage message : messages) {
                            chatListModel.addElement(message);
                        }
                        if (typingUsers.containsKey(userLower)) {
                            chatListModel.addElement(new ChatMessage(typingUsers.get(userLower), -1));
                        }
                        chatList.ensureIndexIsVisible(chatListModel.getSize() - 1);
                    }
                }
            }
        }

        public void showWindow() {
            setVisible(true);
            toFront();
        }
    }
}