import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedReader;
import java.io.FileReader;



public class Server
{
    private final int port; 
    private int mode; 
    private ServerSocketChannel serverSocketChannel;
    private final ExecutorService executorToStartGame;
    private final ExecutorService executorNewClientsConnected;
    private final List<Client> rejoin_queue = new ArrayList<>();
    private static final ReentrantLock lock = new ReentrantLock();
    private final Map<String, String> userTokens = new HashMap<>();
    private final String DATABASE_PATH = "database.csv";
    private final int MIN_PLAYERS_PER_GAME = 3; 
    private int ELO_TOLERANCE = 100; 
    private int tokenCounter = 0; 

    public static List<Client> clients_queue; 

    public Server(int port,int mode) 
    {
        this.port = port; 
        this.mode = mode; 
        this.executorToStartGame = Executors.newCachedThreadPool();
        this.executorNewClientsConnected = Executors.newCachedThreadPool();
        this.clients_queue = new ArrayList<Client>(); 
    }

   


    public void create() {
    System.out.println("Creating server...");
    try {
        this.serverSocketChannel = ServerSocketChannel.open();
        System.out.println("ServerSocketChannel opened");
        serverSocketChannel.bind(new InetSocketAddress(this.port));
        System.out.println("Server started on port " + this.port);
        
        // Start the game scheduler thread
        start();
    } catch (IOException e) {
        System.out.println("Error starting server");
        e.printStackTrace();
    }
}

private int authenticate(String username, String password) {
    System.out.println("Authenticating user " + username + "...");
    //check if the username and the password are in the file database.csv
    try 
    {
        BufferedReader reader = new BufferedReader(new FileReader(DATABASE_PATH));
        String line = reader.readLine();
        while(line != null)
        {
            String[] parts = line.split(",");
            if(parts[0].equals(username) && parts[1].equals(password))
            {
                return Integer.parseInt(parts[2]); 
            }
            line = reader.readLine();
        }
    }
    catch(IOException e)
    {
        System.out.println("Error reading database file");
        e.printStackTrace();
    }
    return -1; 
}

private void handleClientMessage(Client client, String message) {
        boolean inQueue = false;
        if (message.startsWith("AUTH,")) {
            String[] parts = message.split(",");
            if (parts.length == 3) {
                String username = parts[1];
                String password = parts[2];
                System.out.println("Received authentication request for " + username);
                int level = authenticate(username,password);
                if (level != -1) {
                    try {
                        client.setLevel(level);
                        System.out.println("Authentication successful for " + client.username);
                        MainClient.sendMessage(client.getSocket(), "SUCCESS"); 
                        //add to the queue if the token is not already assigned
                        for(int i = 0; i < clients_queue.size(); i++) 
                        {
                            if(clients_queue.get(i).username.equals(client.username))
                            {
                                clients_queue.get(i).setSocket(client.getSocket());
                                System.out.println(clients_queue.get(i).getSocket());
                                inQueue = true;
                            }
                        }
                        if(!inQueue)
                        {
                            System.out.println("Adding " + client.username + " to the queue...");
                            addClientToQueue(client);

                        }
                        assignToken(client);
                    } catch (IOException e) {
                        System.out.println("Error sending authentication response");
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Authentication failed for " + client.username);
                    try {
                        MainClient.sendMessage(client.getSocket(), "FAILURE, TRY AGAIN !");
                    } catch (IOException e) {
                        System.out.println("Error sending authentication response");
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("Invalid authentication request format.");
            }
        } 
        else if(message.startsWith("RESUME,"))
        {
            String token = message.split(",")[1]; 
            //Find the client with the matching token
            for(Client queuedClient : clients_queue)
            {
                if(queuedClient.getToken().equals(token))
                {
                    queuedClient.setSocket(client.getSocket());
                    break; 
                }
            }
        }
        else if(message.startsWith("REJOIN"))
        {
            try
            {
            System.out.println("Client " + client.username + " rejoining the game...");
            addClientToQueue(client);
            MainClient.sendMessage(client.getSocket(),"REJOIN, SUCCESS");
            }
            catch(IOException e)
            {
                System.out.println("Error sending rejoin response");
                e.printStackTrace();
            }
        }
        else if(message.startsWith("EXIT"))
        {
            //close the connection to the client
            System.out.println("Client " + client.username + " has left the game.");
            try {
                client.getSocket().close();                
            } catch (IOException e) {
                System.out.println("Error closing client connection");
                e.printStackTrace();
            }
        }
        else if(message.startsWith("QUEUE"))
        {
            System.out.println("Client " + client.username + " wants to join the queue.");
            addClientToQueue(client);
        }
        else {
        }
}

private void assignToken(Client client) {
    String username = client.username;
    if (userTokens.containsKey(username)) {
        String token = userTokens.get(username);
        System.out.println("Reusing token " + token + " for " + username + "...");
        client.setToken(token);
        try {
            MainClient.sendMessage(client.getSocket(),"TOKEN," + client.getToken());
        } catch (IOException e) {
            System.out.println("Error sending token to client");
            e.printStackTrace();
        }
    } else {
        try (BufferedReader reader = new BufferedReader(new FileReader("tokens.txt"))) {
            String token = null;
            int lineCounter = 0;
            while((token = reader.readLine()) != null && lineCounter < tokenCounter) {
                lineCounter++;
            }
            if (token != null) {
                System.out.println("Assigning token " + token + " to " + username + "...");
                client.setToken(token);
                try {
                    MainClient.sendMessage(client.getSocket(),"TOKEN," + client.getToken());
                } catch (IOException e) {
                    System.out.println("Error sending token to client");
                    e.printStackTrace();
                }
                userTokens.put(username, token);
                tokenCounter++;
            } else {
                System.out.println("No more tokens available");
            }
        } catch(IOException e) {
            System.out.println("Error reading tokens file");
            e.printStackTrace();
        }
    }
}

private void acceptNewClients() 
{
    while(true) 
    {
        try 
        {
            SocketChannel socket = serverSocketChannel.accept();
            System.out.println("New client connected"); 
            Runnable clientHandler = () ->
        {
            try
            {
            String response = MainClient.readMessage(socket); 
            String username = response.split(",")[1];
            String password = response.split(",")[2];
            Client client = new Client(username,password,socket);
            handleClientMessage(new Client(username,password,socket),response);
            }
            catch(IOException e)
            {
                System.out.println("Error accepting new client");
                e.printStackTrace();
            }
        };
        this.executorNewClientsConnected.execute(clientHandler);
            }
        catch(IOException e)
            {
                    System.out.println("Error accepting new client");
                    e.printStackTrace();
            }
    }
}


    
public void start() throws IOException {
    Thread gameThread = new Thread(() -> {
        while (true) {
                synchronized (clients_queue) {
                    synchronized (rejoin_queue) {
                        clients_queue.removeAll(rejoin_queue);
                        clients_queue.addAll(rejoin_queue);
                        rejoin_queue.clear();
                    }
                if (clients_queue.size() >= MIN_PLAYERS_PER_GAME) {
                    System.out.println("Waiting for players to join...");

                    try {
                        System.out.println("Thread Sleeping");
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("The client queue is composed by " + clients_queue.size() + " clients");
                    boolean allClientsConnected = true;
                    synchronized (rejoin_queue) {
                        clients_queue.removeAll(rejoin_queue);
                        clients_queue.addAll(rejoin_queue);
                        rejoin_queue.clear();
                    }
                    Iterator<Client> iterator = clients_queue.iterator();
                    while (iterator.hasNext()) {
                        if(clients_queue.size() < MIN_PLAYERS_PER_GAME) 
                        {
                        break; 
                        }
                        Client client = iterator.next();
                        System.out.println("Checking if client " + client.username + " is connected...");
                        
                        try {
                            MainClient.sendMessage(client.getSocket(), "CHECK");
                            String response = MainClient.readMessage(client.getSocket());

                            System.out.println("The message from the client " + client.username + " is " + response);
                            if (!response.equals("CONNECTED")) {
                                System.out.println("Client " + client.username + " is not connected");
                                allClientsConnected = false;
                                try {
                                    System.out.println("Sleeping for 10 seconds");
                                    Thread.sleep(10000);
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                                System.out.println(client.getSocket());
                                if(!client.isConnected())
                                {
                                    System.out.println("Client " + client.username + " is not connected");
                                    iterator.remove();
                                }
                                break;
                            } else {
                                System.out.println("Client " + client.username + " is connected");
                            }
                        } catch (IOException e) {
                            System.out.println("Error checking connection for player: " + client.username);
                            e.printStackTrace();
                            allClientsConnected = false;
                            iterator.remove();
                            break;
                        }
                    }

                    if (allClientsConnected) {
                        if (this.mode == 0) {
                            if(clients_queue.size() >= MIN_PLAYERS_PER_GAME) {
                            Runnable startGame = () -> {
                                List<Client> players = new ArrayList<>(); 
                                for(int i = 0; i < clients_queue.size(); i++) 
                                {
                                    players.add(clients_queue.get(i));
                                }
                                clients_queue.removeAll(players);
                                new Game(players,this.mode).start();
                                for (Client c : players) {
                                    try {
                                        String receive = MainClient.readMessage(c.getSocket());
                                        if (receive.equals("QUEUE") || receive.equals("REJOIN")) {
                                            System.out.println("Client " + c.username + " wants to play again");
                                            rejoin_queue.add(c);
                                        }
                                        else if(receive.equals("EXIT"))
                                        {
                                            System.out.println("Client" + c.username + " wants to exit the game");
                                        }
                                    } catch (IOException e) {
                                        System.out.println("ERROR");
                                        e.printStackTrace();
                                    }
                                }
                                synchronized (clients_queue) {
                                    synchronized (rejoin_queue) {
                                        clients_queue.removeAll(rejoin_queue);
                                        clients_queue.addAll(rejoin_queue);
                                        rejoin_queue.clear();
                                    }
                                }
                            };
                            
                            this.executorToStartGame.execute(startGame);
                        }
                        } else if (this.mode == 1) {
                            if(clients_queue.size() >= MIN_PLAYERS_PER_GAME) {
                            this.clients_queue.sort(Comparator.comparing(Client::getLevel));
                            List<Client> clients_queue_copy = new ArrayList<>(this.clients_queue);
                            List<Client> players = new ArrayList<>();
                            clients_queue_copy.sort(Comparator.comparing(Client::getLevel));
                            for(int i = 0; i < clients_queue_copy.size() - 1; i++) 
                            {
                                Client player1 = clients_queue_copy.get(i); 
                                Client player2 = clients_queue_copy.get(i+1);
                                // If the players levels are close enough, add them to the game
                                if(Math.abs(player1.level - player2.level) <= ELO_TOLERANCE)
                                {
                                    if(players.contains(player1) && players.contains(player2))
                                    {
                                        continue;
                                    }
                                    else if(players.contains(player1) && !players.contains(player2))
                                    {
                                        players.add(player2);
                                    }
                                    else if(!players.contains(player1) && players.contains(player2))
                                    {
                                        players.add(player1);
                                    }
                                    else
                                {
                                    
                                    players.add(player1);
                                    players.add(player2);
                                }
                                }
                                else
                                {
                                    // If the level difference between two sequential clients is more than 100, break the loop
                                    break;
                                }
                            }
                            if(players.size() >= MIN_PLAYERS_PER_GAME){
                            System.out.println("Matchmaking players: " + players);
                            Runnable startGame = () -> 
                            {
                                clients_queue.removeAll(players);
                                new Game(players,this.mode).start();
                                ELO_TOLERANCE = 100; 
                                for (Client c : players) {
                                    try {
                                        String receive = MainClient.readMessage(c.getSocket());
                                        if (receive.equals("QUEUE") || receive.equals("REJOIN")) {
                                            System.out.println("Client " + c.username + " wants to play again");
                                            rejoin_queue.add(c);
                                        }
                                        else 
                                        {
                                            c.getSocket().close();
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                for(Client c : players) 
                                {
                                    try {
                                    MainClient.sendMessage(c.getSocket(),"LEVEL, " + c.level);
                                    }
                                    catch(IOException e)
                                    {
                                        System.out.println("Error sending the level to the client");
                                        e.printStackTrace();
                                    }
                                }
                                synchronized (clients_queue) {
                                    synchronized (rejoin_queue) {
                                        clients_queue.removeAll(rejoin_queue);
                                        clients_queue.addAll(rejoin_queue);
                                        rejoin_queue.clear();
                                    }
                                }
                            };
                        
                            this.executorToStartGame.execute(startGame);
                            }
                            else 
                            {
                                ELO_TOLERANCE += 100;
                                System.out.println("The ELO_TOLERANCE is " + ELO_TOLERANCE);
                            }
                        }
                    }
                    }
                }
            }
        }
    });

    gameThread.start();

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    executorService.submit(() -> {
        while (true) {
            acceptNewClients();
        }
    });
}


    public static void addClientToQueue(Client client)
    {
        lock.lock();
        clients_queue.add(client);
        lock.unlock();
    }

    public static List<Client> getClientsQueue() {
        lock.lock();
        try {
            return new ArrayList<>(clients_queue);
        } finally {
            lock.unlock();
        }
    }


    public static void main(String[] args)  throws IOException
{
    if(args.length != 2)  
    {
        System.out.println("Incorrect format! Please format as the following: java Server <port> <mode> where the mode is 0 (unranked ) or 1 (ranked)");
        return; 
    }

    int port = Integer.parseInt(args[0]); 
    int mode = Integer.parseInt(args[1]); 
    Server server = new Server(port,mode); 
    server.create(); 
    server.start(); 
    System.out.println("Server started on port " + port);
}

}

