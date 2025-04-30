package com.shshin.har2jmx.dto;

import lombok.*;

@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FindJsonDataDto {
    private String findValue ;
}
