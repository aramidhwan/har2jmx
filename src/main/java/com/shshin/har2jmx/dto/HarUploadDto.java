package com.shshin.har2jmx.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HarUploadDto {
    private String prjNm;
    private String serverIp;
    private int DTCNo;
    private String DTCName;
    private String excludePostfix;
    private MultipartFile harFile;
    private String jwtTokenKeyNm;
    private String assertionCode;
    private String excludePaths;
    private String excludeContainPaths;
    private String jmxFileNm;

//    public String getServerIp() {
//        return (serverIp!=null)? serverIp:"" ;
//    }
    public String getExcludePostfix() {
        return (excludePostfix!=null)? excludePostfix:"" ;
    }
}
