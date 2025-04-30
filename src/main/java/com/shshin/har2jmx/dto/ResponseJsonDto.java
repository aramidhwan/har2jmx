package com.shshin.har2jmx.dto;

import com.shshin.har2jmx.entity.ResponseJson;
import lombok.*;


@Builder
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ResponseJsonDto {
    private Long id;
    private String prjNm;
    private String tcNm;
    private String path;
    private int httpStatus;
    private String mimeType;
    private String text;

    public void setText(String text) {
        this.text = text ;
    }

    public ResponseJson toEntity() {
        return ResponseJson.builder()
                .prjNm(prjNm)
                .tcNm(tcNm)
                .path(path)
                .httpStatus(httpStatus)
                .mimeType(mimeType)
                .text(text)
                .build();
    }

    public static ResponseJsonDto of(ResponseJson responseJson) {
        return ResponseJsonDto.builder()
                .id(responseJson.getId())
                .prjNm(responseJson.getPrjNm())
                .tcNm(responseJson.getTcNm())
                .path(responseJson.getPath())
                .httpStatus(responseJson.getHttpStatus())
                .mimeType(responseJson.getMimeType())
                .text(responseJson.getText())
                .build();
    }
}
