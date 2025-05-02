package com.shshin.har2jmx.controller;

import com.shshin.har2jmx.dto.HarUploadDto;
import com.shshin.har2jmx.dto.JtlUploadDto;
import com.shshin.har2jmx.dto.ResponseDto;
import com.shshin.har2jmx.dto.ResponseJsonDto;
import com.shshin.har2jmx.service.HarService;
import com.shshin.har2jmx.service.JmxService;
import com.shshin.har2jmx.service.TestFragmentService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class Har2JmxController {
    private final HarService harService;
    private final JmxService jmxService ;
    private final TestFragmentService testFragmentService ;

    @GetMapping("/")
    public String mainPage() {
        return "index" ;
    }

    @GetMapping("/jtl-filter")
    public String jtlFilter() {
        return "jtl-filter" ;
    }


    @PostMapping("/uploadJtlFile")
    public String uploadJtlFile(@ModelAttribute JtlUploadDto jtlUploadDto,
                                  Model model) throws IOException {

        // make Sampler
        harService.jtlFiltering(jtlUploadDto);

        return "" ;
    }
    @PostMapping("/uploadHarFile")
    public String uploadHarFile(@ModelAttribute HarUploadDto harUploadDto, Model model) {
        try {
            // make Sampler
//            List<ResponseJsonDto> responseJsonDtoList = harService.makeJmxFile(harUploadDto);
            List<ResponseJsonDto> responseJsonDtoList = harService.makeJmxFile(harUploadDto);

            // 성공 시 메시지 추가
            model.addAttribute("msg", "파일 업로드 성공");
            model.addAttribute("prjNm", harUploadDto.getPrjNm());
            model.addAttribute("serverIp", harUploadDto.getServerIp());
            model.addAttribute("excludeProstfix", harUploadDto.getExcludePostfix());
            model.addAttribute("DTCNo", harUploadDto.getDTCNo());
            model.addAttribute("DTCName", harUploadDto.getDTCName());
            model.addAttribute("jwtTokenNm", harUploadDto.getJwtTokenKeyNm());
            model.addAttribute("assertionCode", harUploadDto.getAssertionCode());
            model.addAttribute("excludePaths", harUploadDto.getExcludePaths());
            model.addAttribute("excludeContainPaths", harUploadDto.getExcludeContainPaths());
            model.addAttribute("jmxFileWithPath", harUploadDto.getJmxFileNm());
            model.addAttribute("jmxFileNm", Paths.get(harUploadDto.getJmxFileNm()).getFileName().toString());
            model.addAttribute("responseJsonDtoList", responseJsonDtoList);

            return "findJsonText"; // 업로드 후 이동할 페이지

        } catch (NoSuchFileException ex) {
            model.addAttribute("msg", "다음 파일이 존재하지 않습니다.\n\n" + ex.getMessage());
            return "errorPage";
        } catch (IOException ex) {
            model.addAttribute("msg", "파일 작업이 실패하였습니다. 확인해 주세요.<br><br>" + ex.getMessage());
            return "errorPage";
        } catch (Throwable ex) {
            ex.printStackTrace();
            model.addAttribute("msg", "알 수 없는 오류입니다. 확인해 주세요.<br><br>" + ex.getMessage());
            return "errorPage";
//        } catch (HarParsorException ex) {
//            model.addAttribute("msg", "제작자 [신승환 010-2353-5704]에게 문의하세요.<br><br>" + ex.getMessage());
//            return "errorPage" ;
        }
    }

    @PostMapping("/api/findJsonData")
    public ResponseEntity<ResponseDto> findJsonData(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");

        // make Sampler
        List<ResponseJsonDto> responseJsonDtoList = jmxService.findJsonData(keyword) ;

        // 성공 시 메시지 추가
        ResponseDto responseDto = ResponseDto.builder()
                .BIZ_SUCCESS(0)
                .data(responseJsonDtoList)
                .build();
        return ResponseEntity.ok(responseDto) ;
    }

    @PostMapping("/api/convertJMX")
    // JMX 파일 내용의 keyword를 변수로 대체하기
    public ResponseEntity<ResponseDto> convertJMX(@RequestBody Map<String, String> body) {
        String prjNm = body.get("prjNm");
        String tcNm = body.get("tcNm");
        String jmxFileNm = body.get("jmxFileWithPath");
        String keyword = body.get("keyword");
        String keywordKeyNm = body.get("keywordKeyNm");

        try {
            List<String> msgs = jmxService.convertJMX(jmxFileNm, prjNm, tcNm, keywordKeyNm, keyword) ;
            List<ResponseJsonDto> responseJsonDtoList = jmxService.getDBSamplerList(prjNm, tcNm) ;

            // 성공 시 메시지 추가
            JSONArray jsonArray = new JSONArray();
            for (String msg : msgs) {
                JSONObject obj = new JSONObject();
                obj.put("msg", msg);
                jsonArray.put(obj);
            }
            String finalMsg = !msgs.isEmpty()? "\"" + StringUtils.getFilename(jmxFileNm) + "\" 파일을 변경하였습니다.\n\n" + String.join("\n", msgs) : "변경 내역이 없습니다." ;
            ResponseDto responseDto = ResponseDto.builder()
                    .BIZ_SUCCESS(0)
                    .msg(finalMsg)
                    .data(responseJsonDtoList)
                    .build();
            return ResponseEntity.ok(responseDto) ;

        } catch (Throwable ex) {
            ResponseDto responseDto = ResponseDto.builder()
                    .BIZ_SUCCESS(9)
                    .msg("❌ JMX 파일 작업이 실패하였습니다. 확인해 주세요.\n\n" + ex.getMessage())
                    .build();
            return ResponseEntity.ok(responseDto) ;
//        } catch (HarParsorException ex) {
//            model.addAttribute("msg", "제작자 [신승환 010-2353-5704]에게 문의하세요.<br><br>" + ex.getMessage());
//            return "errorPage" ;
        }
    }

    @PostMapping("/api/addJsonExtractor")
    // JMX 파일 내용의 keyword를 변수로 대체하기
    public ResponseEntity<ResponseDto> addJsonExtractor(@RequestBody Map<String, String> body) {
        String prjNm = body.get("prjNm");
        String tcNm = body.get("tcNm");
        String jmxFileWithPath = body.get("jmxFileWithPath");
        String keyword = body.get("keyword");
        String targetPath4JsonExtractor = body.get("targetPath4JsonExtractor");

        try {
            List<String> msgs = jmxService.addJsonExtractor(jmxFileWithPath, prjNm, tcNm, targetPath4JsonExtractor, keyword) ;

            // 성공 시 메시지 추가
            String finalMsg = !msgs.isEmpty()? "\"" + StringUtils.getFilename(jmxFileWithPath) + "\" 파일을 변경하였습니다.\n\n" + String.join("\n", msgs) : "변경 내역이 없습니다." ;

            ResponseDto responseDto = ResponseDto.builder()
                    .BIZ_SUCCESS(0)
                    .msg(finalMsg)
                    .build();
            return ResponseEntity.ok(responseDto) ;

        } catch (Throwable ex) {
            ex.printStackTrace();
            ResponseDto responseDto = ResponseDto.builder()
                    .BIZ_SUCCESS(9)
                    .msg("❌ JMX 파일 작업이 실패하였습니다. 확인해 주세요.\n\n" + ex.getMessage())
                    .build();
            return ResponseEntity.ok(responseDto) ;
//        } catch (HarParsorException ex) {
//            model.addAttribute("msg", "제작자 [신승환 010-2353-5704]에게 문의하세요.<br><br>" + ex.getMessage());
//            return "errorPage" ;
        }
    }

    // 아래 메소드는 HarService.makeJmxFile()에 포함됨
//    @PostMapping("/api/addTestFragment")
//    // JMX 파일 내용의 keyword를 변수로 대체하기
//    public ResponseEntity<ResponseDto> addTestFragment4LoginAction(@RequestBody Map<String, String> body) {
//        String jmxFileNm = body.get("jmxFileNm");
//        System.out.println("### jmxFileNm : " + jmxFileNm);
//
//        try {
//            List<String> msgs = testFragmentService.addTestFragment4LoginAction(jmxFileNm) ;
//
//            // 성공 시 메시지 추가
//            ResponseDto responseDto = ResponseDto.builder()
//                    .BIZ_SUCCESS(0)
//                    .msg("\"" + StringUtils.getFilename(jmxFileNm) + "\" 파일을 변경하였습니다.\n\n" + String.join("\n", msgs))
//                    .build();
//            return ResponseEntity.ok(responseDto) ;
//
//        } catch (Throwable ex) {
//            ex.printStackTrace();
//            ResponseDto responseDto = ResponseDto.builder()
//                    .BIZ_SUCCESS(9)
//                    .msg("❌ JMX 파일 작업이 실패하였습니다. 확인해 주세요.\n\n" + ex.getMessage())
//                    .build();
//            return ResponseEntity.ok(responseDto) ;
////        } catch (HarParsorException ex) {
////            model.addAttribute("msg", "제작자 [신승환 010-2353-5704]에게 문의하세요.<br><br>" + ex.getMessage());
////            return "errorPage" ;
//        }
//    }

    @PostMapping("/api/runJmeter")
    public ResponseEntity<ResponseDto> runJMeter(@RequestBody JMeterRequest request) {
        String jmxPath = request.getPath();
        String resultMsg = null ;

        // JMeter 실행 파일 경로 (환경에 맞게 수정)
        String jmeterBat = "D:\\apache-jmeter-5.6.3\\bin\\jmeter.bat";

        // .jmx 파일이 존재하는지 확인
        if (!new File(jmxPath).exists()) {
            resultMsg = "❌ JMX 파일이 존재하지 않습니다: \n\n" + jmxPath;
        } else {
            ProcessBuilder builder = new ProcessBuilder(jmeterBat, "-t", jmxPath);
//            ProcessBuilder builder = new ProcessBuilder(
//                    "cmd", "/c", "start", "\"\"", jmeterBat, "-t", jmxPath
//            );

            builder.redirectErrorStream(true);

            try {
                builder.start();  // 비동기 실행

                resultMsg = "✅ JMeter가 실행됩니다.\n\n하단 작업바를 확인하세요.";
            } catch (IOException e) {
                resultMsg = "❌ 실행 실패: " + e.getMessage();
            }
        }

        ResponseDto responseDto = ResponseDto.builder()
                .BIZ_SUCCESS(0)
                .msg(resultMsg)
                .build();
        return ResponseEntity.ok(responseDto) ;
    }

    @Getter
    @Setter
    public static class JMeterRequest {
        private String path;
    }
}
