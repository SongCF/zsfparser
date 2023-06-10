public class HttpParser2 {

    static String COOKIE = "";
    static Map<String, List<JSONObject>> map = new HashMap<>();

    //服务区相同长度
    final static int sameLen = 6;
    //>0 则只打印前printCount个
    final static int printCount = 0;
    //总数大于这个值的打印
    final static int printGreaterNum = 3;

    public static void main(String[] args) {

        long nowTs = System.currentTimeMillis() / 1000;
        String url = "https://www.zsf.com/user/web/init?t=" + System.currentTimeMillis();
        String ret = HttpUtil.get(url, null, false);
        JSONObject resp = JSONObject.parseObject(ret);
        JSONObject giftList = resp.getJSONObject("data").getJSONObject("gift");
        for (String giftId : giftList.getInnerMap().keySet()) {
            JSONObject gift = giftList.getJSONObject(giftId);
            if (gift.getInteger("part_last") <= 0 || gift.getInteger("part_total") <= 0 || gift.getLongValue("ads_time") > nowTs) {
                continue;
            }
            boolean valid = getGiftAndPartition(giftId);
            if (!valid) {
                System.err.println("invalid gift: " + gift.toJSONString());
            }
        }

        //排序输出
        Comparator<List<JSONObject>> valueComparator = new Comparator<List<JSONObject>>() {
            @Override
            public int compare(List<JSONObject> o1, List<JSONObject> o2) {
                return Integer.compare(o2.size(), o1.size()); // 按照cnt属性降序排列
            }
        };
        List<Map.Entry<String, List<JSONObject>>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, List<JSONObject>>>() {
            @Override
            public int compare(Map.Entry<String, List<JSONObject>> o1, Map.Entry<String, List<JSONObject>> o2) {
                return valueComparator.compare(o1.getValue(), o2.getValue());
            }
        });
        // 输出Map中的所有元素
        if (printCount > 0) {
            for (int i = 0; i < printCount; i++) {
                Map.Entry<String, List<JSONObject>> entry = list.get(i);
                System.out.println(entry.getValue().size() + "======" + entry.getKey());
                for (int idx = 0; idx < entry.getValue().size(); idx++) {
                    System.out.println(entry.getValue().get(idx).toJSONString());
                }
                System.out.println();
            }
        } else if (printGreaterNum > 0) {
            for (int i = 0; ; i++) {
                Map.Entry<String, List<JSONObject>> entry = list.get(i);
                if (entry.getValue().size() < printGreaterNum) {
                    break;
                }
                System.out.println(entry.getValue().size() + "======" + entry.getKey());
                for (int idx = 0; idx < entry.getValue().size(); idx++) {
                    System.out.println(entry.getValue().get(idx).toJSONString());
                }
                System.out.println();
            }
        }
    }

    public static boolean getGiftAndPartition(String adsId) {
        JSONObject giftInfo = getGiftInfo(adsId);
        if (giftInfo == null) {
            return false;
        }
        String name = giftInfo.getString("ads_name");

        String partitionList = getPartitionList();
        if (partitionList == null) {
            return false;
        }
        Document doc = Jsoup.parseBodyFragment(partitionList);
        Element selectElement = doc.selectFirst("select[name=partition]");
        if (selectElement != null) {
            Elements optionElements = selectElement.select("option");
            if (optionElements.first() != null) {
                String text = optionElements.first().text();
                System.out.println(name + " --- " + text);
                text = text.length() > sameLen ? text.substring(0, sameLen) : text;
                if (map.get(text) == null) {
                    List<JSONObject> l = new ArrayList<>();
                    l.add(giftInfo);
                    map.put(text, l);
                } else {
                    map.get(text).add(giftInfo);
                }
            }
        }
        return true;
    }


    public static JSONObject getGiftInfo(String adsId) {
        String url = "https://www.zsf.com/user/web/getGiftInfo";
        String requestBody = "ads_id=" + adsId;
        try {
            URL obj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("content-type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("x-requested-with", "XMLHttpRequest");
            //登陆信息
            conn.setRequestProperty("cookie", COOKIE);

            // 设置请求体
            conn.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(requestBody);
            writer.flush();
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = JSONObject.parseObject(response.toString()).getJSONObject("data");
                if (jsonResponse == null) {
                    System.err.println("invalid gift: " + adsId + ", ret: " + response.toString());
                }
                return jsonResponse;
            } else {
                System.err.println("POST request failed: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //必须调用getGiftInfo后接着调用它才会生效
    public static String getPartitionList() {
        String url = "https://www.zsf.com/user/web/getPartitionList";
        try {
            URL obj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("content-type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("x-requested-with", "XMLHttpRequest");
            conn.setRequestProperty("cookie", COOKIE);

            // 发送请求并获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // 解析JSON响应，获取data字段的值
                JSONObject jsonResponse = JSONObject.parseObject(response.toString());
                JSONObject data = jsonResponse.getJSONObject("data");
                if (data != null) {
                    return data.getString("list");
                } else {
                    System.err.println(response);
                }
            } else {
                System.out.println("GET request failed: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
