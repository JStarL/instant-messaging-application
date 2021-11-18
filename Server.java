import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Server {

    private static boolean serverDebugMode;

    private static int serverPort;
    private static int blockDuration;
    private static int timeout;

    private static String credentialsFile;
    private static ArrayList<User> users;

    public Server() {
        //
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        
        if (args.length < 3) {
            System.out.println("Usage: java Server <server_port> <block_duration> <timeout>");
            return;
        }

        
        if (args[0].equals("-d")) {
            if (args.length < 4) {
                System.out.println("Usage: java Server -d <server_port> <block_duration> <timeout>");
                return;
            }
            
            serverPort = Integer.parseInt(args[1]);
            blockDuration = Integer.parseInt(args[2]);
            timeout = Integer.parseInt(args[3]);
        
            serverDebugMode = true;

        } else {
            serverPort = Integer.parseInt(args[0]);
            blockDuration = Integer.parseInt(args[1]);
            timeout = Integer.parseInt(args[2]);
            
            serverDebugMode = false;
        }
        
        credentialsFile = "credentials.txt";
        users = new ArrayList<User>();
        
        loadCredentialsFile();
        
        Server mainServer = null;
        mainServer = new Server();
    }
    
    private static void loadCredentialsFile() throws IOException {
        Path credFile = Paths.get(credentialsFile);
        List<String> credentials = Files.readAllLines(credFile);

        Iterator<String> credIterator = credentials.iterator();

        printDebug("Loading Credentials File...");
        
        while (credIterator.hasNext()) {
            String userCredentials = credIterator.next();
            String[] userAndPword = userCredentials.split(" ");

            printDebug("User: " + userAndPword[0]);
            printDebug("Password: " + userAndPword[1]);

            User user = new User(userAndPword[0], userAndPword[1]);

            users.add(user);
        }

    }

    private static void printDebug(String msg) {
        if (serverDebugMode) {
            System.out.println(msg);
        }
    }

}