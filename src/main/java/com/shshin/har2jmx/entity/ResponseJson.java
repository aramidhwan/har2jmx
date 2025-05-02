package com.shshin.har2jmx.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name="t_response_json")
@Builder
@Getter
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ResponseJson {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;
    @NotNull
    private String prjNm;
    @NotNull
    private String tcNm;
    @NotNull
    private String path;
    @NotNull
    private int httpStatus;
    @NotNull
    private String mimeType;
    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String text;

    public void setPath(String path) {
        this.path = path ;
    }
}
