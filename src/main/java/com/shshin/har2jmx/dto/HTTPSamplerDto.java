package com.shshin.har2jmx.dto;

import lombok.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Getter
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HTTPSamplerDto {
    private String testname ;
    private String path ;
    private String method ;
    private boolean postBodyRaw ;
    private JSONObject postData ;
    private String postData_mimeType ;
    private String postData_text ;
    @Builder.Default
    private Map<String, List<String>> postData_params = new HashMap<>();          // "POST" 방식의 Parameter (har 파일의 postData > params[])
    private JSONArray queryString;              // "GET" 방식의 Parameter (har 파일의 queryString[])
    private String jwtTokenKeyNm ;
    private String jwtJsonPathExprs ;
    private JSONObject response ;
    private HeaderManagerDto headerManagerDto ;
    private List<JsonExtractorDto> jsonExtractorDtoList ;

    public void setTestname(String testname) {
        this.testname = testname ;
    }

    public void setJwtJsonPathExprs(String jwtJsonPathExprs) {
        this.jwtJsonPathExprs = jwtJsonPathExprs ;
    }

    private void getPostData() {
        // Do Nothing HERE. public 으로 호출하지 못하도록 private 조치!
    }

    // Builder 패턴에서는 set... 메소드 안불려짐
    public void setPostData(JSONObject postData) {
System.out.println("안불려져??? ###############");
        this.postData = postData ;

        if ( this.postData != null ) {
            if ( !this.postData.getString("mimeType").startsWith("multipart/form-data")) {
                this.postBodyRaw = true;
            }
            if ( this.postData.getJSONArray("params") != null ) {
                JSONObject queryParam = null ;

                // HTTPArgumentsPanel 의 variable
                // 예: 반복문으로 여러 개 추가
                JSONArray params = this.postData.getJSONArray("params") ;
                if ( params != null && !params.isEmpty() ) {
                    for (int inx = 0; inx < params.length(); inx++ ) {
                        queryParam = params.getJSONObject(inx) ;
                        this.postData_params.computeIfAbsent(queryParam.getString("name"), k -> new ArrayList<>()).add(queryParam.getString("value"));
                    }
                } // end of if else
            }
        }
    }

    public boolean isPostBodyRaw() {
        if ( postData != null && !this.postData.getString("mimeType").startsWith("multipart/form-data")) {
            this.postBodyRaw = true;
        }
        return this.postBodyRaw ;
    }

    public String getPostData_mimeType() {
        if ( this.postData != null && this.postData.getString("mimeType") != null ) {
            return this.postData.getString("mimeType") ;
        } else {
            return "" ;
        }
    }

    public String getPostData_text() {
        if ( this.postData != null && this.postData.getString("text") != null ) {
//            return this.postData.getString("text").replaceAll("\"", "&quot;") ;
            return this.postData.getString("text") ;
        } else {
            return "" ;
        }
    }

    public Map<String, List<String>> getPostData_params() {
        Map<String, List<String>> parameters = new HashMap<>();

        if ( this.postData == null || this.postData.getJSONArray("params") == null ) {
            return parameters ;
        }

        JSONObject queryParam = null ;

        // HTTPArgumentsPanel 의 variable
        // 예: 반복문으로 여러 개 추가
        JSONArray params = this.postData.getJSONArray("params") ;
        if ( params != null && !params.isEmpty() ) {
            for (int inx = 0; inx < params.length(); inx++ ) {
                queryParam = params.getJSONObject(inx) ;
                parameters.computeIfAbsent(queryParam.getString("name"), k -> new ArrayList<>()).add(queryParam.getString("value"));
            }
        } // end of if else

        return parameters ;
    }

    private void getQueryString() {
        // Do Nothing HERE. public 으로 호출하지 못하도록 private 조치!
    }

    // 순수 표준 Java 버전 : Map<String, List<String>>
    public Map<String, List<String>> getQueryStrings() {
        Map<String, List<String>> parameters = new HashMap<>();
        JSONObject queryParam = null ;

        // HTTPArgumentsPanel 의 queryString
        // 예: 반복문으로 여러 개 추가
        if ( this.queryString != null && !this.queryString.isEmpty() ) {
            for (int inx = 0; inx < this.queryString.length(); inx++ ) {
                queryParam = this.queryString.getJSONObject(inx) ;
                parameters.computeIfAbsent(queryParam.getString("name"), k -> new ArrayList<>()).add(queryParam.getString("value"));
            }
        } // end of if else

        return parameters ;
    }

    // 순수 표준 Java 버전 : Map<String, List<String>>
//    public Map<String, List<String>> getParams() {
//        Map<String, List<String>> parameters = new HashMap<>();
//        JSONObject queryParam = null ;
//
//        // HTTPArgumentsPanel 의 params
//        if ( this.params != null && !this.params.isEmpty() ) {
//            for (int inx = 0; inx < this.params.length(); inx++ ) {
//                queryParam = this.params.getJSONObject(inx) ;
//                parameters.computeIfAbsent(queryParam.getString("name"), k -> new ArrayList<>()).add(queryParam.getString("value"));
//            }
//        } // end of if else
//
//        return parameters ;
//    }

    // Spring 전용버전 : MultiValueMap (Spring 환경일 경우)
//    public MultiValueMap<String, String> getQueryStrings() {
//        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
//        JSONObject queryParam = null ;
//
//        // HTTPArgumentsPanel 의 queryString
//        if ( queryArray != null && !queryArray.isEmpty() ) {
//            for ( int inx = 0; inx < queryArray.length(); inx++ ) {
//                queryParam = queryArray.getJSONObject(inx) ;
//                parameters.add(queryParam.getString("name"), queryParam.getString("value"));
//            }
//        } // end of if else
//
////        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://example.com/search")
////                .queryParams(parameters);
////
////        String url = builder.toUriString(); // https://example.com/search?tag=java&tag=spring&tag=docker
//
//        return parameters ;
//    }

    public void setHeaderManagerDto(HeaderManagerDto headerManagerDto) {
        this.headerManagerDto = headerManagerDto ;
    }
    public void setJsonExtractorDto(JsonExtractorDto jsonExtractorDto) {
        this.jsonExtractorDtoList = new ArrayList<>() ;
        this.jsonExtractorDtoList.add(jsonExtractorDto) ;
    }
    public void setJsonExtractorDtoList(List<JsonExtractorDto> jsonExtractorDtoList) {
        this.jsonExtractorDtoList = jsonExtractorDtoList ;
    }

    public void addJsonExtractorDto(JsonExtractorDto jsonExtractorDto) {
        if ( this.jsonExtractorDtoList == null ) {
            this.jsonExtractorDtoList = new ArrayList<>() ;
        }
        this.jsonExtractorDtoList.add(jsonExtractorDto) ;
    }
}
