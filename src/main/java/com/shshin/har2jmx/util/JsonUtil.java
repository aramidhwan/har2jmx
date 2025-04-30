package com.shshin.har2jmx.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonUtil {

    // JSON 데이터에서 KEY가 targetKey 인것을 찾는다.
    public static String findValueByKey(JSONObject jsonObject, String targetKey) {
        Object result = recursiveFind(jsonObject, targetKey);
        return result != null ? result.toString() : null;
    }

    private static Object recursiveFind(Object node, String targetKey) {
        if (node instanceof JSONObject json) {
            for (String key : json.keySet()) {
                Object value = json.get(key);

                // 🔑 key 일치 시 바로 반환
                if (key.equals(targetKey)) {
                    return value;
                }

                // ⬇️ value가 JSONObject or JSONArray라면 재귀 탐색
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    Object found = recursiveFind(value, targetKey);
                    if (found != null) return found;
                }
            }
        } else if (node instanceof JSONArray array) {
            for (Object item : array) {
                Object found = recursiveFind(item, targetKey);
                if (found != null) return found;
            }
        }

        return null; // key 못 찾음
    }

    // JSON 데이터에서 KEY 혹은 VALUE에서 keyword를 찾는다.(findKeyOrValue : "KEY", "VALUE", "KEY-VALUE")
    public static String findJsonPathsByKeyOrValue(String jsonString, String keyword, String findKeyOrValue) throws RuntimeException {
        ObjectMapper mapper = new ObjectMapper();
        List<String> results = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(jsonString);
            recursiveSearch(root, "$", keyword, results, findKeyOrValue);
        } catch (Exception e) {
            throw new RuntimeException("❌ 유효한 JSON 문자열이 아닙니다.");
        }

        if (results.isEmpty()) {
            throw new RuntimeException( "❌ 일치하는 key 또는 value가 없습니다." );
        }

        return String.join("\n", results);
    }

    private static void recursiveSearch(JsonNode node, String currentPath, String keyword, List<String> results, String findKeyOrValue) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                recursiveSearch(node.get(i), currentPath + "[" + i + "]", keyword, results, findKeyOrValue);
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String path = currentPath + "." + key;

                // ✅ key 일치
                if ( findKeyOrValue.contains("KEY") ) {
                    if (key.equals(keyword)) {
                        results.add(path);
                    }
                }

                // ✅ value 일치 (기본 타입인 경우만)
                if ( findKeyOrValue.contains("VALUE") ) {
                    if (value.isValueNode() && value.asText().equals(keyword)) {
                        results.add(path);
                    }
                }

                // ✅ 객체나 배열이면 재귀
                if (value.isContainerNode()) {
                    recursiveSearch(value, path, keyword, results, findKeyOrValue);
                }
            }
        }
    }

    // XML Node에서 빈 줄 제거
    public static void removeWhitespaceNodes(Node node) {
        NodeList list = node.getChildNodes();
        for (int i = list.getLength() - 1; i >= 0; i--) {
            Node child = list.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else if (child.hasChildNodes()) {
                removeWhitespaceNodes(child);
            }
        }
    }
}
