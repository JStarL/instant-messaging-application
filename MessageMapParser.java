import java.util.HashMap;
import java.util.Iterator;

public class MessageMapParser {

    public MessageMapParser() {
    }

    // TODO MsgFormatException

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
    public HashMap<String, String> parseMessage(String msg) throws Exception {
        
        if (msg.length() == 0) {
            throw new Exception("Empty Message");
        }
        
        String input = msg.replaceAll("[\n\r]", "");
        
        if (input.charAt(0) != '<' || input.charAt(input.length() - 1) != '>') {
            // minimal check
            throw new Exception("Format Error: should be a tag message");
        }

        
        int i = input.indexOf(' ', 1);
        
        // Get the tag
        String tagName = input.substring(1, i);
        
        // check that the input ends with "... </tag>"
        if (!input.endsWith("</" + tagName + ">")) {
            throw new Exception("Format Error: should end with </"+tagName+">");
        }

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("tag", tagName);

        while (input.charAt(i + 1) != '>') {
            
            // parsing: field="..."

            int equalsIndex = input.indexOf('=', i + 1);
            int lastQuoteIndex = input.indexOf("\" ", i + 1);

            String field = input.substring(i + 1, equalsIndex);
            String value = input.substring(equalsIndex + 2, lastQuoteIndex);

            if (value.length() == 0) {
                map.put(field, null);
            } else {
                map.put(field, value);
            }

            // set i
            i = lastQuoteIndex + 1;
        }

        int endingTagIndex = input.lastIndexOf("<");

        String body = input.substring(i + 2, endingTagIndex);

        if (body.length() == 0) {
            map.put("body", null);
        } else {
            map.put("body", body);
        }

        return map;
    }

    public String convertToMsg(HashMap<String, String> map) throws Exception {
        if (map == null) {
            throw new Exception("map is null");
        }

        if (!map.containsKey("tag")) {
            throw new Exception("map does not contain tag");
        }

        if (!map.containsKey("body")) {
            throw new Exception("map does not contain body");
        }

        String msg = "<" + map.get("tag") + " ";

        Iterator<String> keysIterator = map.keySet().iterator();

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();

            if (key.equals("tag") || key.equals("body")) {
                continue;
            }

            String extra = key + "=\"" + map.get(key) + "\" ";

            msg += extra;
        }

        msg += ">";

        msg += map.get("body");

        msg += "</" + map.get("tag") + ">";

        return msg;

    }

    public void printHashMap(HashMap<String, String> map) {
        
    }
}