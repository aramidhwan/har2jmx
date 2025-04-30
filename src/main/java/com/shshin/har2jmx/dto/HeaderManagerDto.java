package com.shshin.har2jmx.dto;

import lombok.*;

import java.util.Map;


@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HeaderManagerDto {
    String testname ;
    // Map<hName, hValue> headers
    Map<String, String> headers ;

}
