package com.shshin.har2jmx.dto;

import lombok.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HTTPSamplerDto {
    private String testname ;
    private String path ;
    private String method ;
    private boolean postBodyRaw ;
    private String postData ;
    private JSONArray queryArray ;
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

    public void setPostData(String postData) {
        this.postData = postData.replaceAll("\"", "&quot;") ;
    }

    // 순수 표준 Java 버전 : Map<String, List<String>>
    public Map<String, List<String>> getParameters() {
        Map<String, List<String>> parameters = new HashMap<>();
        JSONObject queryParam = null ;

        // HTTPArgumentsPanel 의 queryString
        // 예: 반복문으로 여러 개 추가
        if ( queryArray != null && !queryArray.isEmpty() ) {
            for ( int inx = 0; inx < queryArray.length(); inx++ ) {
                queryParam = queryArray.getJSONObject(inx) ;
                parameters.computeIfAbsent(queryParam.getString("name"), k -> new ArrayList<>()).add(queryParam.getString("value"));
            }
        } // end of if else

        return parameters ;
    }

    // Spring 전용버전 : MultiValueMap (Spring 환경일 경우)
//    public MultiValueMap<String, String> getParameters() {
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
