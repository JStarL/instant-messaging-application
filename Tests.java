import java.util.HashMap;

public class Tests {

    public static void main(String[] args) {
        MessageMapParser mmp = new MessageMapParser();

        String t1 = "<start field=\"value\" >Content</start>";

        HashMap<String, String> map1;
        try {
            map1 = mmp.parseMessage(t1);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        System.out.println("Map1 Values");
        System.out.println("tag: " + map1.get("tag"));
        System.out.println("field: " + map1.get("field"));
        System.out.println("body: " + map1.get("body"));

        String reT1;
        try {
            reT1 = mmp.convertToMsg(map1);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        
        if (reT1.equals(t1)) {
            System.out.println("reT1 == t1");
        } else {
            System.out.println("reT1 != t1");
        }

        HashMap<String, String> map2 = new HashMap<String, String>();

        map2.put("tag", "start");

        String nope;
        try {
            nope = mmp.convertToMsg(map2);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        map2.remove("tag");
        map2.put("body", "Some content");
        try {
            nope = mmp.convertToMsg(map2);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }
}