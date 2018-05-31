import com.alibaba.fastjson.JSON;

import java.util.Map;

public class Test {


    public static void main(String[] args) {
        String value = "{\"@message\":\"172.16.128.70 - - [27/Oct/2017:14:51:49 +0800] \\\"GET /api/itoa/app/searchcollect?appId=8&size=1000000 HTTP/2.0\\\" 200 4343 \\\"https://newlook.eoitek.net/0/dashboard/105904514699264?edit=1\\\" \\\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36\\\" \\\"-\\\"\"}";

        Map<String,String> map = JSON.parseObject(value,Map.class);
        System.out.println(JSON.toJSONString(map));
    }
}
