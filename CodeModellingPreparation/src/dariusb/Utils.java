package dariusb;

import javax.print.attribute.HashAttributeSet;
import java.text.NumberFormat;
import java.util.*;

public class Utils {

    public static HashSet<String> getKMostPopular(HashMap<String, Integer> map, int k)
    {
        List<Map.Entry<String, Integer>> list = new LinkedList(map.entrySet());

        Collections.sort(list, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        var result = new HashSet<String>();

        int i = 0;
        for (Map.Entry<String, Integer> entry : list) {
            if (++i > k)
                break;

            result.add(entry.getKey());
        }

        return result;
    }

    public static boolean isNumeric(String str) {
        try {
            NumberFormat.getInstance().parse(str);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
