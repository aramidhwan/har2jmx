package com.shshin.har2jmx.util;

import com.shshin.har2jmx.dto.HTTPSamplerDto;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommonUtil {
    private static String QUERY_STRING_TYPE ;

    @Value("${harParsor.queryString.type:PARAMETER-TYPE}")
    private void setQueryStringType(String value) {
        QUERY_STRING_TYPE = value;
    }

    public static String getQueryStringType() {
        return QUERY_STRING_TYPE;
    }

    private static boolean isJsonFormat(JSONObject response) {
        return "application/json".equals(response.getJSONObject("content").getString("mimeType")) ;
    }

    public static boolean isLoginAction(HTTPSamplerDto httpSamplerDto) {
        boolean isLoginPage = false ;
        JSONObject response = httpSamplerDto.getResponse() ;
        String jwtTokenKeyNm = httpSamplerDto.getJwtTokenKeyNm() ;
        String responseJsonData = null ;

        // jwtJsonPathExprs 이 채워져 있으면 "로그인 액션" url임
        if (StringUtils.hasText(httpSamplerDto.getJwtJsonPathExprs())) {
            return true ;
        // index.html 화면에서 jwtTokenKeyNm 값을 안 받았거나, response가 "application/json" 가 아니면 "로그인 액션" 이 아님.
        } else if ( !StringUtils.hasText(jwtTokenKeyNm) || !isJsonFormat(response) ) {
            return false ;
        }

        // 응답 json 데이터의 KEY 중에 jwtTokenKeyNm (ex: accessToken) 값이 들어있으면 "로그인 액션 url"로 판단!
        // "로그인 액션" url이 아닐 경우 RuntimeException 발생함
        // jsonPathExpression 추출 (ex: $.data.accessToken)
        // Header에 Authorization Bearer 안달기
        try {
            responseJsonData = response.getJSONObject("content").getString("text");
            httpSamplerDto.setJwtJsonPathExprs(JsonUtil.findJsonPathsByKeyOrValue(responseJsonData, jwtTokenKeyNm, "KEY"));
            isLoginPage = true;
        } catch (RuntimeException ex) {
            // 응답 json 데이터의 KEY 중에 jwtTokenKeyNm 값이 없으면 "로그인 액션 url"이 아니다.
            // Do Nothing Here. ==> return false
        }

        return isLoginPage ;
    }

    public static String getPath(String serverIp, JSONObject request) {
        String path = request.getString("url") ;
        if (serverIp!=null) {
            path = path.replace(serverIp, "") ;
        }

        if ( !"PATH-PARAMETER-TYPE".equals(getQueryStringType()) ) {
            path = cutAfterQuestion(path);
        }
        return path ;
    }

    public static String cutAfterQuestion(String path) {
        if ( StringUtils.hasText(path) ) {
            return path.contains("?") ? path.substring(0, path.indexOf("?")) : path ;
        } else {
            return path ;
        }
    }
}
