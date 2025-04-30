package com.shshin.har2jmx.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JtlUploadDto {
    private String prjNm;
    private MultipartFile jtlFile;
    private String filterStr;
    private String jtlDir;
}
