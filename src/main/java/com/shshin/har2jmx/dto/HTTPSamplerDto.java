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
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HTTPSamplerDto {
    String testname ;
    String path ;
    String method ;
    boolean postBodyRaw ;
    String postData ;
    JSONArray queryArray ;
    String jwtTokenKeyNm ;
    String jwtJsonPathExprs ;
    JSONObject response ;
    HeaderManagerDto headerManagerDto ;
    List<JsonExtractorDto> jsonExtractorDtoList ;

    public void setTestname(String testname) {
        this.testname = testname ;
    }

    public void setJwtJsonPathExprs(String jwtJsonPathExprs) {
        this.jwtJsonPathExprs = jwtJsonPathExprs ;
    }

    public void setPostData(String postData) {
        this.postData = postData.replaceAll("\"", "&quot;") ;
    }

    public Map<String, String> getParameters() {
        Map<String, String> parameters = new HashMap<>() ;
        JSONObject queryParam = null ;

        // HTTPArgumentsPanel 의 queryString
        if ( queryArray != null && !queryArray.isEmpty() ) {
            for ( int iny = 0; iny < queryArray.length(); iny++ ) {
                queryParam = queryArray.getJSONObject(iny) ;
                parameters.put(queryParam.getString("name"), queryParam.getString("value")) ;
            }
        } // end of if else

        return parameters ;
    }

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
