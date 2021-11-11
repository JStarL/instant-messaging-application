import java.util.HashMap;

public class MessageParser {

    public MessageParser() {
    }

    /**
     * Assume input type:
     * <tag field1="..." field2="..." >body</tag>
     * or <tag >body</tag>
     * Incrementally build HashMap, as:
     * tag -> name
     * field1 -> ...
     * field2 -> ...
     * body -> content
     * @param msg
     * @return
     */
    public HashMap parseMessage(String msg) {
        
    }

    public void printHashMap(HashMap map) {
        
    }
}