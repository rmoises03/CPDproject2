import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Arrays; 

public class MainClient {
    private static int port;
    private static SocketChannel socketChannel;
    
    MainClient(int port) 
    {
        this.port = port; 
    }


    public void open() throws IOException {
        this.socketChannel = SocketChannel.open();
        this.socketChannel.connect(new InetSocketAddress(this.port));
    }

    

    public void close() throws IOException {
        if (this.socketChannel != null) {
            this.socketChannel.close();
        }
    }

    public static void sendMessage(SocketChannel socket, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.trim().getBytes());
        while (buffer.hasRemaining()) {
            socket.write(buffer);
        }
    }

    public static String readMessage(SocketChannel socket) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socket.read(buffer);
        if (bytesRead == -1) {
            socket.close();
        } else {
            return new String(buffer.array(), 0, bytesRead, StandardCharsets.UTF_8);
        }
        return "";
    }

    public SocketChannel getSocket() {
        return Objects.requireNonNull(this.socketChannel, "SocketChannel is not initialized. Call open() first.");
    }


    private static void checkConnection(Client client) throws IOException {
        boolean waiting_check = false;
        while(!waiting_check)
        {  
            System.out.println("CHECKING THE CONNECTION");
            String response = MainClient.readMessage(client.getSocket());
            if(response.equals("CHECK")){
                System.out.println("Received a CHECK from the server.");
                MainClient.sendMessage(client.getSocket(),"CONNECTED");
                waiting_check = true;
            }
            else if(response.equals("QUEUE"))
            {
                System.out.println("Received a QUEUE from the server.");
                MainClient.sendMessage(client.getSocket(),"QUEUE"); 
            }
            else if(response.equals("REJOIN"))
            {
                System.out.println("REJOIN received from the server.");
                MainClient.sendMessage(client.getSocket(),"QUEUE");
            }
            else if(response.equals("EXIT"))
            {
                System.out.println("Received an EXIT from the server.");
                MainClient.sendMessage(client.getSocket(),"EXIT");
            }
            else if(response.startsWith("LEVEL"))
            {
                String[] parts = response.split(","); 
                int level = Integer.parseInt(parts[1].trim());
                System.out.println("Your current level is: " + level);
            }
            else 
            {
                System.out.println("Received an unexpected message from the server: " + response);
            }
        }
    }

    private static void playGame(Client client) throws IOException {
        boolean gameEnded = false;
        while(!gameEnded) 
        {
            String response = MainClient.readMessage(client.getSocket());
            if(response.startsWith("CHECK"))
            {
                MainClient.sendMessage(client.getSocket(), "CONNECTED");
            }
            if(response.startsWith("STRING"))
            {
                System.out.println("Type as fast as you can the following sentence: " + response.split(",")[1]);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Enter a string: ");
                String input = reader.readLine();
                //dont let the input be empty
                while(input.isEmpty())
                {
                    System.out.println("The input cannot be empty. Please enter a valid input.");
                    input = reader.readLine();
                }
                MainClient.sendMessage(client.getSocket(), input);
            }
            else if(response.startsWith("WINNER"))
            {
                String[] parts = response.split(",");
                if(parts.length > 1) {
                    System.out.println("The winner is: " + parts[1]);
                }
                //Let the users rejoin the queue if they want to play again
                System.out.println("Do you want to play again? (yes/no)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String input = reader.readLine();
                if(input.equals("yes"))
                {
                    MainClient.sendMessage(client.getSocket(),"REJOIN");
                    System.out.println("Sended REJOIN to the server");
                    gameEnded = false;
                }
                else
                {
                    MainClient.sendMessage(client.getSocket(),"EXIT");
                    System.out.println("Sended EXIT to the server");
                    gameEnded = false; 
                }
                gameEnded = true;
            }
        }
        if(gameEnded)
        {
            String response = MainClient.readMessage(client.getSocket());
            if(response.startsWith("QUEUE"))
            {
               //send a command to add the client to the queue
                MainClient.sendMessage(client.getSocket(),"QUEUE");
                System.out.println("Sended QUEUE to the server"); 
            }
            else 
            {
                MainClient.sendMessage(client.getSocket(),"EXIT");
                System.out.println("Sended EXIT to the server");
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Incorrect format! Please format as the following : java MainClient <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);        
        boolean loggedIn = false;
        Client client = null;

        try {
            // Connect to the server
            MainClient mainClient = new MainClient(port);
            mainClient.open();
            

            System.out.println("Connected to the server.");

            while (!loggedIn) {
                // Read user credentials from the console
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Enter username: ");
                String username = reader.readLine();
                System.out.print("Enter password: ");
                String password = reader.readLine();
                String hashedPassword = ""; 

                // Hash the password
                try
                {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(password.getBytes());
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                    hashedPassword = sb.toString();
                }
                catch (Exception e)
                {
                    System.out.println("Error hashing the password: " + e.getMessage());
                }

                // Send the credentials to the server for authentication
                client = new Client(username, hashedPassword, mainClient.getSocket());
                MainClient.sendMessage(client.getSocket(), "AUTH," + username + "," + hashedPassword);
                System.out.println("Sent authentication request to the server.");
                // Receive authentication response from the server
                String response = MainClient.readMessage(client.getSocket());
                if (response.equals("SUCCESS")) {
                    System.out.println("Authentication successful.");
                    loggedIn = true;
                    String token = MainClient.readMessage(client.getSocket());
                    //store only the part of the string that contains the token
                    if(token.startsWith("TOKEN"))
                    {
                        String[] parts = token.split(",");
                        token = parts[1];
                        System.out.println(token); 
                        client.setToken(token);
                    }
                } 
                else {
                    System.out.println("Authentication failed. Please check your credentials and try again.");
                }
            }

            // Keep waiting for messages from the server
            while (true) {
                checkConnection(client);
                playGame(client);
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}