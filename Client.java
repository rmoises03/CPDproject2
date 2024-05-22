import java.util.Objects;
import java.nio.channels.SocketChannel;

public class Client 
{
    public final String username; 
    private final String password; 
    public int level; 
    private String token; 
    private SocketChannel socket; 

    Client(String username,String password, SocketChannel socket) 
    {
        this.username = username; 
        this.password = password; 
        this.socket = socket;
    }

    public SocketChannel getSocket() 
    {
        return this.socket; 
    }
    public void setSocket(SocketChannel socket) 
    {
        this.socket = socket; 
    }

    public int getLevel() 
    {
        return level; 
    }
    public void setLevel(int level) 
    {
        this.level = level; 
    } 
    public void setToken(String token) 
    {
        this.token = token; 
    }
    public String getToken() 
    {
        return this.token; 
    }
    public String getPassword() 
    {
        return this.password; 
    }

    public boolean equals(Client client) 
    {
        return this.username.equals(client.username);
    }

    public boolean isConnected() {
        return socket != null && socket.isOpen() && socket.isConnected();
    }
}