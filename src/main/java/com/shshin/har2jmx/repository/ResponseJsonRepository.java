package com.shshin.har2jmx.repository;

import com.shshin.har2jmx.entity.ResponseJson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(path="responseJsons", collectionResourceRel = "responseJsons")
public interface ResponseJsonRepository extends JpaRepository<ResponseJson, Long> {
    @Query(value = "SELECT * FROM t_response_json WHERE text LIKE %:value%", nativeQuery = true)
    List<ResponseJson> findByTextContaining(@Param("value") String value);

    @Modifying
    @Query("DELETE FROM ResponseJson r WHERE r.prjNm = :prjNm AND r.tcNm = :dtcName")
    int deleteByPrjNmAndTcNm(@Param("prjNm") String prjNm, @Param("dtcName") String dtcName);

    List<ResponseJson> findByPrjNmAndTcNmAndPath(String prjNm, String dtcName, String targetPath2addJsonExtractor);

    List<ResponseJson> findByPrjNmAndTcNm(String prjNm, String dtcName);
}
