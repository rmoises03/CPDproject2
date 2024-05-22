import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Game {
    private boolean gameEnded = false;
    private String winner = "";
    private List<Client> players;
    private int mode;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition gameEndedCondition = lock.newCondition();
    private volatile boolean winnerSent = false;


    public Game(List<Client> players, int mode) {
        this.players = players;
        this.mode = mode; 
    }

    public void start() {
        try {
            System.out.println("Game started with players: " + this.players.size());
            String winner = playGame();
            System.out.println("Game ended. The winner is: " + winner);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private String playGame() {
    Random random = new Random();
    String randomString = generateRandomString(random); // Pass the Random object to the method

    // Send the random string to each client
    for (Client player : players) {
        String username = player.username;
        System.out.println(username + " is playing.");
        try {
            // Send the random string to the client
            MainClient.sendMessage(player.getSocket(), "RANDOM_STRING," + randomString);
        } catch (IOException e) {
            System.out.println("Error sending random string to client: " + username);
            e.printStackTrace();
            continue; // Continue to the next client
        }
    }

    // Create a thread for each client to handle input
    Thread[] inputThreads = new Thread[players.size()];
    for (int i = 0; i < players.size(); i++) {
        Client player = players.get(i);
        inputThreads[i] = new Thread(() -> handleInput(player, randomString));
        inputThreads[i].start();
    }
    
    // Wait for one of the players to win
    lock.lock();
    try {
        while (!gameEnded) {
            //check if the threads are receiving the input
            for (Thread thread : inputThreads) {
                if (thread.isAlive()) {
                    try {
                        thread.join(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (!gameEnded) {
                try {
                    gameEndedCondition.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    if(gameEnded){
        boolean allMessagesReceived = true;
        for(Client c : players) 
        {
            try {
                String message = MainClient.readMessage(c.getSocket());
                if(message.equals("REJOIN") || message.equals("QUEUE"))
                {
                    MainClient.sendMessage(c.getSocket(),"QUEUE");   
                    if(mode == 1) 
                    {
                        if(c.equals(winner)) 
                        {
                            c.setLevel(c.level + 30); 
                        }
                        else 
                        {
                            c.setLevel(c.level - 10);
                        }
                    }
                }
                else if(message.equals("WINNER"))
                {
                    System.out.println("");
                }
                else
                {
                    MainClient.sendMessage(c.getSocket(), "EXIT"); 
                }
            } catch (IOException e) {
                System.out.println("Error sending REJOIN signal to player: " + c.username);
                e.printStackTrace();
                allMessagesReceived = false;
            }
        }
        if(allMessagesReceived){
            System.out.println("All players have received the winner message");
        }
       
    }
    }
    finally {
        lock.unlock();
    }
    return winner; 
    

}


private void handleInput(Client player, String randomString) {
    try {
        while(!gameEnded){

            // Check if the game has ended before prompting for input
            if (gameEnded) {
                break;
            }
            // Prompt the client to input a string
            MainClient.sendMessage(player.getSocket(), "STRING, " + randomString);

            // Receive input from the client
            String input = MainClient.readMessage(player.getSocket());

            // Check if the input matches the random string
            if (input.trim().equalsIgnoreCase(randomString.trim())) {
                lock.lock();
                try {
                    List<Client> playersCopy = new ArrayList<>(players);
                    for(Client c : playersCopy)
                    {
                        if(c.equals(player))
                        {
                            c.setLevel(c.level + 30); 
                        }
                        else 
                        {
                            c.setLevel(c.level - 10);
                        }
                    }
                    winner = player.username;
                    gameEnded = true;
                    gameEndedCondition.signalAll();
                    Thread.currentThread().setName(player.username);
                } finally {
                    lock.unlock();
                }
            }
            
        }
        
    } catch (ClosedByInterruptException e) {
        // Ignore the exception if the game has ended
        if (!gameEnded) {
            e.printStackTrace();
        }
    } catch (IOException e) {
        System.out.println("Error receiving input from client: " + player.username);
        e.printStackTrace();
    }

    
        if (gameEnded) {
            lock.lock();
            try {
                if (!winnerSent) {
                    for (Client p : players) {
                        try {
                            MainClient.sendMessage(p.getSocket(), "WINNER," + winner);
                        } catch (IOException ioException) {
                            System.out.println("Error sending WINNER signal to player: " + p.username);
                            ioException.printStackTrace();
                        }
                    }
                    winnerSent = true;
                    System.out.println(winner + " wins!");
                }
            } finally {
                lock.unlock();
            }
        }
    return;
}
    // Method to generate a random string
    private String generateRandomString(Random random) {
        Path filePath = Paths.get("phrases.txt");
        List<String> lines;
        try {
            lines = Files.readAllLines(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return lines.get(random.nextInt(lines.size()));
    }

    public void removePlayer(Client player) {
        players.remove(player);
        return; 
    }
}
