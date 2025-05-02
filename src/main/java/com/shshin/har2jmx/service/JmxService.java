package com.shshin.har2jmx.service;

import com.shshin.har2jmx.dto.HTTPSamplerDto;
import com.shshin.har2jmx.dto.ResponseJsonDto;
import com.shshin.har2jmx.dto.JsonExtractorDto;
import com.shshin.har2jmx.entity.ResponseJson;
import com.shshin.har2jmx.repository.ResponseJsonRepository;
import com.shshin.har2jmx.util.JsonUtil;
import com.shshin.har2jmx.util.XmlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JmxService {

    private final ResponseJsonRepository responseJsonRepository;

    // DB에 저장된 응답 JSON 데이터에서 [FIND] keyword 찾기
    public List<ResponseJsonDto> findJsonData(String keyword) {
        List<ResponseJsonDto> responseJsonDtoList = responseJsonRepository.findByTextContaining(keyword)
                .stream()
                .map(ResponseJsonDto::of)
                .toList();
//        for (ResponseDataDto responseDataDto : responseDataDtoList) {
//            System.out.println("### id : " + responseDataDto.getId());
//            System.out.println("### path : " + responseDataDto.getPath());
//            System.out.println("### text : " + responseDataDto.getText());
//        }

        return responseJsonDtoList;
    }

    // [FIND] keyword를 변수로 대체
    @Transactional
    public List<String> convertJMX(String jmxFileNm, String prjNm, String tcNm, String keywordKeyNm, String keyword) throws RuntimeException {
        // 작업내용 메시지
        List<String> returnMsg = new ArrayList<>() ;
        Path resultJmxFile = Paths.get(jmxFileNm);

        // JMX 파일
        try {
            if ( Files.notExists(resultJmxFile) || Files.size(resultJmxFile) == 0) {
                returnMsg.add("\"" + resultJmxFile.getFileName() + "\" 파일이 존재하지 않거나, 내용이 비어있습니다.") ;
                return returnMsg ;
            }

            // JMX XML 문서 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(resultJmxFile.toFile());
            doc.getDocumentElement().normalize();

            // <HTTPSamplerProxy> 엘리먼트를 찾기
            NodeList samplerList = doc.getElementsByTagName("HTTPSamplerProxy");

            // 정규식("온전한 단어"): 경계(문자시작 or /, 문자끝 or /) 기준으로 정확히 keyword만 매칭
            // (?<=^|/|=) → keyword 앞에 문자열 시작(^), 슬래시(/), 또는 등호(=)가 있는 경우만 허용
            // ex: /api/common/384/menus/project
            // (?=/|$|\\$) → keyword 뒤에 슬래시(/), 문자열 끝($), 또는 리터럴 &가 있는 경우만 허용
            // ex: /api/pms/dashboard/board?projectUid=384&boardPageType=BOARD_BASIC
            //※ \\$는 **리터럴 $**을 의미 (정규식에서 $는 특수 의미라 이스케이프 필요)
            String pattern = "(?<=^|/|=)" + Pattern.quote(keyword) + "(?=/|$|&)";
            Pattern regex4Keyword = Pattern.compile(pattern);

            // <HTTPSamplerProxy> List Loop
            for (int inx = 0; inx < samplerList.getLength(); inx++) {
                Element httpSampler = (Element) samplerList.item(inx);

                // <HTTPSamplerProxy testname 대체(keyword)
                String testname = httpSampler.getAttribute("testname");
                String prefix = testname.substring(0, testname.indexOf("/")) ;
                String targetPathOfTestName = testname.substring(testname.indexOf("/")) ;

                //-------------------------------------------------
                // HTTPSamplerProxy의 testname에 있는 keyword를 변수로 대체 시작
                //-------------------------------------------------
                Matcher matcher4TestName = regex4Keyword.matcher(targetPathOfTestName);
                //-------------------------------------------------
                // HTTPSampler.path 의 keyword를 변수로 대체 시작
                //-------------------------------------------------
                if (matcher4TestName.find()) {
                    // 정확히 일치하는 부분만 치환
                    targetPathOfTestName = matcher4TestName.replaceAll(Matcher.quoteReplacement("${" + keywordKeyNm + "}"));
                    httpSampler.setAttribute("testname", prefix+targetPathOfTestName);
                    returnMsg.add("✅ testname 변경됨: " + testname + " → " + (prefix+targetPathOfTestName));
                    System.out.println("✅ testname 변경됨: " + testname + " → " + (prefix+targetPathOfTestName));
                }

                // sampler 안의 모든 <stringProp> 를 찾는다. HeaderManager의 <stringProp>는 대상이 아님
                NodeList stringProps = httpSampler.getElementsByTagName("stringProp");
                for (int iny = 0; iny < stringProps.getLength(); iny++) {
                    Element stringProp = (Element) stringProps.item(iny);

                    //-------------------------------------------------
                    // HTTPSamplerProxy의 HTTPSampler.path 값 중 keyword를 변수로 대체 시작
                    //-------------------------------------------------
                    if ("HTTPSampler.path".equals(stringProp.getAttribute("name"))) {
                        String currentPath = stringProp.getTextContent();
                        Matcher matcher4HTTPSamplerPath = regex4Keyword.matcher(currentPath);

                        if (matcher4HTTPSamplerPath.find()) {
                            String newPath = matcher4HTTPSamplerPath.replaceAll(Matcher.quoteReplacement("${" + keywordKeyNm + "}"));
                            stringProp.setTextContent(newPath);

                            List<ResponseJson> responseJsonList = responseJsonRepository.findByPrjNmAndTcNmAndPath(prjNm, tcNm, currentPath) ;
                            for (ResponseJson responseJson : responseJsonList) {
                                responseJson.setPath(newPath);
                                responseJsonRepository.save(responseJson) ;
                            }
                            returnMsg.add("✅ HTTPSampler.path 변경됨: " + currentPath + " → " + newPath);
                        }

                    //-------------------------------------------------
                    // HTTPSamplerProxy의 Parameter(Argument.value) 값 중 keyword를 변수로 대체 시작
                    //-------------------------------------------------
                    // Argument.value 값이 keyword와 온전히 같으면 변수명(keywordKeyNm)으로 대체
                    } else if ("Argument.value".equals(stringProp.getAttribute("name"))) {
                        String currentValue = stringProp.getTextContent();

                        // 값이 keyword 와 정확히 일치하면 변경
                        if (keyword.equals(currentValue)) {
                            stringProp.setTextContent("${"+keywordKeyNm+"}");
                            returnMsg.add("✅ HTTPSamplerProxy의 Argument.value에 있는 keyword:["+keyword+"]를 변수:["+keywordKeyNm+"]로 대체 완료.") ;
                            System.out.println("✅ HTTPSamplerProxy의 Argument.value 변경됨: " + currentValue + " → " + "${"+keywordKeyNm+"}");
                        }
                    }
                }
            }

            // <HeaderManager> 에서 keyword 찾기
            NodeList headerManagers = doc.getElementsByTagName("HeaderManager");
            for (int inx = 0; inx < headerManagers.getLength(); inx++) {
                Element headerManager = (Element) headerManagers.item(inx);
                NodeList stringProps = headerManager.getElementsByTagName("stringProp");

                //-------------------------------------------------
                // HTTPSamplerProxy의 HeaderManager 중에서 Header.value 값 중 keyword를 변수로 대체 시작
                //-------------------------------------------------
                for (int iny = 0; iny < stringProps.getLength(); iny++) {
                    Element stringProp = (Element) stringProps.item(iny);

                    if ("Header.value".equals(stringProp.getAttribute("name"))) {
                        String currentValue = stringProp.getTextContent();

                        // 값이 keyword 와 온전히 일치하면 변경
                        Matcher matcher4HeaderValue = regex4Keyword.matcher(currentValue);
                        if (matcher4HeaderValue.find()) {
                            String replacedValue = matcher4HeaderValue.replaceAll(Matcher.quoteReplacement("${" + keywordKeyNm + "}"));
                            stringProp.setTextContent(replacedValue);

                            System.out.println("✅ HeaderManager의 Header.value 변경됨: " + currentValue + " → " + stringProp.getTextContent());
                            returnMsg.add("✅ Header.value 변경됨: " + currentValue + " → " + stringProp.getTextContent());
                        }
                    }
                }
            }

            // 결과 저장 전에 불필요한 공백 노드 제거
            JsonUtil.removeWhitespaceNodes(doc);

            // 결과 저장
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(resultJmxFile.toFile());
            transformer.transform(source, result);

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        System.out.println("📄 변경된 JMX 파일 저장 완료(덮어쓰기).");

        return returnMsg ;
    }

    // [FIND] keyword를 추출할 JSON Extractor 추가
    public List<String> addJsonExtractor(String jmxFileNm, String prjNm, String tcNm, String targetPath4JsonExtractor, String keyword) {
        // 작업내용 메시지
        List<String> returnMsg = new ArrayList<>() ;
        Path resultJmxFile = Paths.get(jmxFileNm);

        try {

            if (Files.notExists(resultJmxFile) || Files.size(resultJmxFile) == 0) {
                returnMsg.add("\"" + resultJmxFile.getFileName() + "\" 파일이 존재하지 않거나, 내용이 비어있습니다.");
                return returnMsg;
            }

            // DB에서 Path와 Response JSON Data를 얻어온다.
            List<ResponseJson> responseJsonList = responseJsonRepository.findByPrjNmAndTcNmAndPath(prjNm, tcNm, targetPath4JsonExtractor);
            String responseJsonString = responseJsonList.get(0).getText();  // Response JSON Data
            String keywordJsonPath = JsonUtil.findJsonPathsByKeyOrValue(responseJsonString, keyword, "VALUE").split("\n")[0];
            String keywordKeyNm = keywordJsonPath.substring(keywordJsonPath.lastIndexOf(".") + 1);
//            System.out.println("### prjNm : " + prjNm);
//            System.out.println("### TC Name : " + tcNm);
//            System.out.println("### keyword : " + keyword);
//            System.out.println("### keywordJsonPath : " + keywordJsonPath);
//            System.out.println("### targetPath4JsonExtractor : " + targetPath4JsonExtractor);

            // JMX XML 문서 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(resultJmxFile.toFile());
            doc.getDocumentElement().normalize();

            // <HTTPSamplerProxy> 엘리먼트를 찾기
            NodeList samplerList = doc.getElementsByTagName("HTTPSamplerProxy");

            // <HTTPSamplerProxy> List Loop
            HTTPSamplerDto httpSamplerDto4JsonExtractor = null ;
            for (int inx = 0; inx < samplerList.getLength(); inx++) {
                Element httpSampler = (Element) samplerList.item(inx);

                // sampler 안의 모든 <stringProp> 를 찾는다. HeaderManager/Json Extractor의 <stringProp>는 대상이 아님
                NodeList stringProps = httpSampler.getElementsByTagName("stringProp");
                for (int iny = 0; iny < stringProps.getLength(); iny++) {
                    Element stringProp = (Element) stringProps.item(iny);

                    //---------------------------------------------------------------------
                    // HTTP Sampler의 Path가 targetPath4JsonExtractor이면 JSON Extractor 추가
                    //---------------------------------------------------------------------
                    if ("HTTPSampler.path".equals(stringProp.getAttribute("name"))) {
                        String currentPath = stringProp.getTextContent();

                        if (currentPath.equals(targetPath4JsonExtractor)) {
                            JsonExtractorDto jsonExtractorDto = JsonExtractorDto.builder()
                                    .testname("JSON Extractor ("+keywordKeyNm+")")
                                    .referenceName(keywordKeyNm)
                                    .jsonPathExprs(keywordJsonPath)
                                    .build();
                            httpSamplerDto4JsonExtractor = HTTPSamplerDto.builder().build() ;
                            httpSamplerDto4JsonExtractor.setJsonExtractorDto(jsonExtractorDto);
                            // 다음 Sampler로 점프, 다음 Sampler에도 동일한 Path가 존재할 수 있음
                            break;
                        }
                        // HTTPSampler.path를 검토했으면 다른 stringProp는 검토 불필요, 다음 Sampler로 점프
                        break ;
                    }
                }
                
                // JSON Extractor 추가
                if ( httpSamplerDto4JsonExtractor != null ) {
                    Node httpSamplerHashTree = XmlUtil.findNextHashTree(httpSampler) ;
                    XmlUtil.createJsonExtractorList((Element) httpSamplerHashTree, httpSamplerDto4JsonExtractor);
                    returnMsg.add("✅ ["+targetPath4JsonExtractor+"]에 keyword:["+keyword+"]를 추출할 JSON Extractor를 추가하였습니다. 변수명: "+keywordKeyNm) ;
                    httpSamplerDto4JsonExtractor = null ;
                }
            }

            // 결과 저장 전에 불필요한 공백 노드 제거
            JsonUtil.removeWhitespaceNodes(doc);

            // 결과 저장
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(resultJmxFile.toFile());
            transformer.transform(source, result);

        } catch (IOException | ParserConfigurationException | SAXException | TransformerException ex) {
            throw new RuntimeException(ex) ;
        }

        return returnMsg ;
    }

    public List<ResponseJsonDto> getDBSamplerList(String prjNm, String tcNm) {
        return responseJsonRepository.findByPrjNmAndTcNm(prjNm, tcNm)
                .stream()
                .map(ResponseJsonDto::of)
                .toList();
    }
}
