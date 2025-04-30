package com.shshin.har2jmx.dto;

import lombok.*;

@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JsonExtractorDto {
    String testname ;
    String referenceName ;
    String jsonPathExprs ;
}
