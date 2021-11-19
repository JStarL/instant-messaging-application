import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;

public class Client implements Runnable {

    private static boolean clientDebugMode;
    private static int serverPort;
    private static String serverName;

    /**
     * Maximum number of times a thread will try to send a msg before it is deemed to have been lost
     */
    private static int maxSendNumber;

    private static String username;

    private String threadType;
    private Socket conn;

    private PrintWriter clientWriter;
    private BufferedReader clientReader;
    private MessageMapParser mmParser;


    public Client() {
        threadType = null;
        conn = null;

        clientWriter = null;
        clientReader = null;
        mmParser = new MessageMapParser();
    }

    public Client(String threadType) {
        this();
        this.threadType = threadType;
    }

    public static void main(String[] args) throws NumberFormatException {

        if (args.length == 0) {
            System.out.println("Usage: java Client <server_port>");
            return;
        }

        if (args[0].equals("-d")) {
            if (args.length == 1) {
                System.out.println("Usage: java Client -d <server_port>");
                return;
            }

            clientDebugMode = true;
            serverPort = Integer.parseInt(args[1]);
        } else {
            clientDebugMode = false;
            serverPort = Integer.parseInt(args[0]);
        }

        serverName = "localhost";
        maxSendNumber = 10;

        Client clientSender = new Client("main-write");
        Client clientRecipient = new Client("main-read");

        Thread t1 = new Thread(clientSender);
        Thread t2 = new Thread(clientRecipient);

        t1.start();

    }

    
    @Override
    public void run() {
        switch (threadType) {
            case "main-write":
                
                try {
                    clientSenderThread();
                } catch (Exception e) {
                    printDebug(e.getMessage());
                    return;
                }
                break;
            case "main-read":
                
                try {
                    clientReaderThread();
                } catch (Exception e) {
                    printDebug(e.getMessage());
                    return;
                }
                break;
            case "p2p":
                
                break;

            default:
                return;

        }
        
    }
    
    private void clientSenderThread() throws IOException, Exception {
        
        if (!initialiseThread()) {throw new Exception("Thread Initialisation Error");}

        printDebug("Client Writer Thread Initialised");

        
        
        
    }
    
    private void clientReaderThread() throws IOException{
        
        
    }
    
    private boolean initialiseThread() throws IOException, SocketException {
        
        
        
        if (threadType.equals("main-write") || threadType.equals("main-read")) {
            
            conn = new Socket(serverName, serverPort);
            conn.setSoTimeout(100);
            
            clientWriter = new PrintWriter(conn.getOutputStream());
            clientReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            // prepare initial message map

            HashMap<String, String> initMsg = new HashMap<String, String>();

            initMsg.put("tag", "thread");
            
            if (threadType.equals("main-write")) {
                initMsg.put("client", "write");
            } else {
                initMsg.put("client", "read");
            }

            initMsg.put("body", null);

            // convert to string

            String initString = null;
            try {
                initString = mmParser.convertToMsg(initMsg);
            } catch (Exception e) {
                printDebug(e.getMessage());
                return false;
            }

            HashMap<String, String> previousMap = new HashMap<String, String>();
            for (int i = 0; i < maxSendNumber; i++) {
                
                printDebug("[Round " + i + "]: Sending " + initString);
                
                clientWriter.println(initString);
                clientWriter.flush();


                String recvdMsg = null;
                try {
                    recvdMsg = clientReader.readLine();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                
                printDebug("[Round " + i + "]: Received " + recvdMsg);
                
                HashMap<String, String> recvdMap;

                try {
                    recvdMap = mmParser.parseMessage(recvdMsg);
                } catch (Exception e) {
                    continue;
                }

                if (recvdMap.equals(previousMap)) {
                    continue;
                }

                if (recvdMap.containsKey("status") && recvdMap.get("status").equals("recvd")) {
                    // setup complete
                    return true;
                }

                previousMap = recvdMap;

            }

            return false;


        } // or if p2p conn...

        return false;
        
    }

    private static void printDebug(String msg) {
        if (clientDebugMode) {
            System.out.println(msg);
        }
    }

}