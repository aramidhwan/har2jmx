package com.shshin.har2jmx.service;

import com.shshin.har2jmx.dto.*;
import com.shshin.har2jmx.repository.ResponseJsonRepository;
import com.shshin.har2jmx.util.CommonUtil;
import com.shshin.har2jmx.util.XmlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HarService {
    @Value("${harParsor.queryString.type:PARAMETER-TYPE}")
    private String QUERY_STRING_TYPE ;

    // 업로드된 Har 파일에 "로그인 액션"이 없더라도 이전 Har의 "로그인 액션" 재활용 차 전역 변수화해야 한다.
    private HTTPSamplerDto loginActionSampler ;
    boolean needAuthorization ;

    private final TestFragmentService testFragmentService ;
    private final ResponseJsonRepository responseJsonRepository ;

    @Transactional
    public List<ResponseJsonDto> makeJmxFile(HarUploadDto harUploadDto) throws Exception {
        // 화면(HTTP Sampler List Modal)에 보내 줄 DTO (리턴 객체)
        List<ResponseJsonDto> responseJsonDtoList = new ArrayList<>() ;

        // 동일 PRJ_NM의 TC_NM의 기존 DB 데이터 삭제 ( PRJ_NM 과 TC_NM 이 기존에 있으면 삭제 )
        responseJsonRepository.deleteByPrjNmAndTcNm(harUploadDto.getPrjNm(), harUploadDto.getDTCName());

        // 업로드된 HAR 파일 내용을 한 줄씩 읽기
        StringBuilder harFileJsonContent = makeHarFileJsonContent(harUploadDto) ;

        // HAR entries [배열] (개별 url)
        JSONArray txList = getTransactionList(harFileJsonContent);

        // Sampler마다 반복 호출 > HTTPSamplerDto 리스트 만들기
        List<HTTPSamplerDto> httpSamplerDtoList = makeHttpSamplerDtoList(harUploadDto, responseJsonDtoList);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("jmeterTestPlan");
        rootElement.setAttribute("version", "1.2");
        rootElement.setAttribute("properties", "5.0");
        rootElement.setAttribute("jmeter", "5.6.3");
        doc.appendChild(rootElement);

        Element rootHashTree = doc.createElement("hashTree");
        rootElement.appendChild(rootHashTree);

        // ---------------------- [Test Plan] 생성 ----------------------
        Element testPlan = createTestPlan(doc);
        rootHashTree.appendChild(testPlan);

        Element testPlanHashTree = doc.createElement("hashTree");
        rootHashTree.appendChild(testPlanHashTree);

        // [환경변수] https://npims-stg.skcc.com
        Element httpDefaults = createHttpDefaults(doc, harUploadDto);
        testPlanHashTree.appendChild(httpDefaults);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // ---------------------- [환경변수] VUser 수 ----------------------
        // 변수 추가 : TEST_DURATION
        Map<String, Map<String, String>> variables = new LinkedHashMap<>();
        Map<String, String> var1 = new HashMap<>();
        var1.put("value", "300");
        var1.put("comment", "테스트 지속시간");
        variables.put("TEST_DURATION", var1);
        // 변수 추가 : RAMP_UP_PERIOD
        Map<String, String> var2 = new HashMap<>();
        var2.put("value", "20");
        var2.put("comment", "(초) Ramp-Up 시간");
        variables.put("RAMP_UP_PERIOD", var2);
        // 변수 추가 : NUM_OF_THREADS_메인페이지
        Map<String, String> var3 = new HashMap<>();
        var3.put("value", "1");
        var3.put("comment", "(명) 가상 유저 수");
        variables.put("NUM_OF_THREADS_"+harUploadDto.getDTCName(), var3);

        // User Defined Variables 생성
        String testname = "[환경변수] VUser 수" ;
        Element variables1 = createVariables(doc, testname, "가상 유저 수, 테스트 지속시간, Ramp-Up 시간 지정", variables);
        testPlanHashTree.appendChild(variables1);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [환경변수] JSR223 Assertion 제외 목록 (code==200) ------
        // 변수 추가 : excludePaths
        variables = new LinkedHashMap<>();
        var1 = new HashMap<>();
        var1.put("value", "/ko, /ko-1, /ko-2");
        var1.put("comment", "Assertion 대상 제외 목록 (exact match)");
        variables.put("excludePaths", var1);
        // 변수 추가 : excludeContainPaths
        var2 = new HashMap<>();
        var2.put("value", "/js/, /css/, /images/, /image/, /img/, /ko");
        var2.put("comment", "Assertion 대상 제외 목록 (contains match)");
        variables.put("excludeContainPaths", var2);

        // User Defined Variables 생성
        testname = "[환경변수] JSR223 Assertion 제외 목록 ("+harUploadDto.getAssertionCode()+")" ;
        Element variables2 = createVariables(doc, testname, null, variables);
        testPlanHashTree.appendChild(variables2);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [JSR223 Assertion] [응답 검증] JSR223 Assertion (code==200) ------
        Element jsr223Assertion = createJSR223Assertion(doc, harUploadDto);
        testPlanHashTree.appendChild(jsr223Assertion);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // ------------------ [결과 그래프] TPS ----------------
        Element tpsGraph = createTpsGraph(doc, harUploadDto) ;
        testPlanHashTree.appendChild(tpsGraph);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // ------------------ [결과 그래프] 응답시간 ----------------
        Element responseTimeGraph = createResponseTimeGraph(doc, harUploadDto) ;
        testPlanHashTree.appendChild(responseTimeGraph);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // ------------------ [결과 테이블] 대시보드 (표) ----------------
        Element statisticsTable = createStatisticsTable(doc, harUploadDto) ;
        testPlanHashTree.appendChild(statisticsTable);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [결과 확인] View Result Tree ------
        Element resultCollector = createResultCollector(doc);
        testPlanHashTree.appendChild(resultCollector);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [Backend Listener] InfluxDB 1.8 Listener ------
        Element influxDBListener = createBackendListener(doc, harUploadDto);
        testPlanHashTree.appendChild(influxDBListener);
        testPlanHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [ThreadGroup] ThreadGroup ------
        Element threadGroup = createThreadGroup(doc, harUploadDto) ;
        testPlanHashTree.appendChild(threadGroup);
        Element threadGroupHashTree = doc.createElement("hashTree") ;
        testPlanHashTree.appendChild(threadGroupHashTree);

        // -------- [ThreadGroup] > [Transaction Controller] ■ (업무) Transaction Controller ------
        Element transactionController = createTransactionController(doc, harUploadDto) ;
        threadGroupHashTree.appendChild(transactionController);
        Element transactionControllerHashTree = doc.createElement("hashTree") ;
        threadGroupHashTree.appendChild(transactionControllerHashTree);

        // -------- [ThreadGroup] > [Transaction Controller] PacingPause ------
        Element pacingPause1 = createPacingPause(doc) ;
        transactionControllerHashTree.appendChild(pacingPause1);
        transactionControllerHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [ThreadGroup] > [Transaction Controller] PacingPause ------
        Element pacingStart = createPacingStart(doc) ;
        transactionControllerHashTree.appendChild(pacingStart);
        transactionControllerHashTree.appendChild(doc.createElement("hashTree"));

        // -------- [ThreadGroup] > [Transaction Controller] > [HTTPSamplerProxy][HeaderManager][JWT JSON Extractor] 만들기 ------
        for (int inx = 0 ; inx < httpSamplerDtoList.size() ; inx++ ) {
            // "로그인 액션" Sampler 인 경우.xxx
            if ( CommonUtil.isLoginAction(httpSamplerDtoList.get(inx)) ) {
                this.loginActionSampler = httpSamplerDtoList.get(inx) ;
                // testname : 정규표현식: [DTC숫자-00] 구조에서 "숫자" 부분만 ${tcNo}로 대체
                this.loginActionSampler.setTestname(this.loginActionSampler.getTestname().replace("$dtcNoIndex$", "00").replaceAll("\\[DTC\\d+(?=-)", "[DTC\\${tcNo}").replace(harUploadDto.getDTCName(),"로그인"));
                this.loginActionSampler.setJsonExtractorDto(JsonExtractorDto.builder()
                                .testname("JSON Extractor ("+this.loginActionSampler.getJwtTokenKeyNm()+")")
                                .referenceName(this.loginActionSampler.getJwtTokenKeyNm())
                                .jsonPathExprs(this.loginActionSampler.getJwtJsonPathExprs())
                                .build()
                        );
                this.needAuthorization = true ;
                continue;
            }
            // HTTPSamplerProxy 추가
            Element httpSampler = createHttpSampler(doc, httpSamplerDtoList.get(inx), inx) ;
            transactionControllerHashTree.appendChild(httpSampler);
            // HTTPSamplerProxy > hashTree 추가
            Element httpSamplerHashTree = doc.createElement("hashTree") ;
            transactionControllerHashTree.appendChild(httpSamplerHashTree) ;

            // HTTPSamplerProxy > hashTree > HeaderManager 추가
            // jwtJsonPathExprs값이 채워지면 Authorization 헤더가 추가되므로 HeaderManager는 JWT JSON Extractor 앞에 달아야 함
            Element headerManager = createHeaderManager(doc, httpSamplerDtoList.get(inx)) ;
            httpSamplerHashTree.appendChild(headerManager) ;
            // HTTPSamplerProxy > hashTree > HeaderManager > hashTree 추가
            httpSamplerHashTree.appendChild(doc.createElement("hashTree")) ;

            // "로그인 액션" url 일 경우 JWT JSON Extractor (accessToken)추가
            // testFragmentService.addTestFragment4LoginAction() 로 이동
        }

        // -------- [ThreadGroup] > [Transaction Controller] PacingPause ------
        Element pacingPause2 = createPacingPause(doc) ;
        transactionControllerHashTree.appendChild(pacingPause2);
        transactionControllerHashTree.appendChild(doc.createElement("hashTree"));

        // JMX 파일 작성
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(harUploadDto.getJmxFileNm()));
        transformer.transform(source, result);

        // "로그인 액션" 처리
        if ( this.loginActionSampler != null ) {
            // Thread Group에 Only Once Controller 생성
            testFragmentService.addOnlyOnceController4LoginAction(harUploadDto.getJmxFileNm(), String.format("%02d", harUploadDto.getDTCNo()));

            // 마지막으로 "로그인" TestFragmentController 생성
            // tcNm에 "로그인 액션" url이 포함되어 있건 없건 모두 다 생성 (모든 화면이 로그인 필요하므로)
            testFragmentService.addTestFragment4LoginAction(harUploadDto.getJmxFileNm(), this.loginActionSampler);
        }

        System.out.println("✅ JMX 파일이 생성되었습니다.");

        return responseJsonDtoList ;
    }

    private List<HTTPSamplerDto> makeHttpSamplerDtoList(HarUploadDto harUploadDto, List<ResponseJsonDto> responseJsonDtoList) {
        List<HTTPSamplerDto> httpSamplerDtoList = new ArrayList<>() ;

        // HttpSampler에서 제외할 url
        List<String> EXCLUDE_POSTFIX = Arrays.asList(harUploadDto.getExcludePostfix().split(","));

        // 업로드된 HAR 파일 내용을 한 줄씩 읽기
        StringBuilder harFileJsonContent = makeHarFileJsonContent(harUploadDto) ;

        // HAR entries [배열] (개별 url)
        JSONArray txList = getTransactionList(harFileJsonContent);

        // Sampler마다 반복 호출 > HTTPSamplerDto 리스트 만들기
        for (int inx = 0; inx < txList.length(); inx++) {
            JSONObject response = txList.getJSONObject(inx).getJSONObject("response");
            // HTTPSamplerDto 만들기
            HTTPSamplerDto httpSamplerDto = makeHttpSamplerDto(harUploadDto, txList.getJSONObject(inx)) ;

            // "Sampler 만들기"에서 제외할 확장자는 건너띄기(continue) (.js .css .gif 등)
            if (EXCLUDE_POSTFIX.stream().anyMatch(CommonUtil.cutAfterQuestion(httpSamplerDto.getPath())::endsWith)) {
                continue;
            }

            // 콘솔 출력
            System.out.println("\n### path : [" + httpSamplerDto.getMethod() + "] " + httpSamplerDto.getPath());
            for (int iny = 0; iny < httpSamplerDto.getQueryArray().length(); iny++) {
                System.out.println("###        Param[" + iny + "] : " + httpSamplerDto.getQueryArray().getJSONObject(iny).getString("name") + " = " + httpSamplerDto.getQueryArray().getJSONObject(iny).getString("value"));
            }

            // HTTPSamplerDto 리스트에 추가
            httpSamplerDtoList.add(httpSamplerDto) ;

            // 화면(HTTP Sampler List)과 DB에 저장할 DTO
            ResponseJsonDto responseJsonDto = ResponseJsonDto.builder()
                    .prjNm(harUploadDto.getPrjNm())
                    .tcNm(harUploadDto.getDTCName())
                    .path(CommonUtil.cutAfterQuestion(httpSamplerDto.getPath()))
                    .httpStatus(response.getInt("status"))
                    .mimeType(response.getJSONObject("content").getString("mimeType"))
                    // jsonData는 밑에서 response가 json 응답일때 만 DB 저장
//                        .text(jsonData)
                    .build();

            // response가 json 응답일때만 H2 DB에 json 응답을 저장
            if ("application/json".equals(response.getJSONObject("content").getString("mimeType"))) {
                String jsonData = null ;
                try {
                    jsonData = response.getJSONObject("content").getString("text");
                } catch (JSONException ex) {
                    // content > text 가 없을 수도 있네? ex: my-pizza의 /auth/sign-in
//                    throw new RuntimeException("응답(Response) JSON 데이터가 잘못 되었습니다.\nPATH : " + CommonUtil.cutAfterQuestion(httpSamplerDto.getPath()) + "\nJSON DATA : " + jsonData) ;
                }
                responseJsonDto.setText(jsonData) ;
            }

            responseJsonRepository.save(responseJsonDto.toEntity());

            responseJsonDtoList.add(responseJsonDto) ;
        }
        return httpSamplerDtoList;
    }

    private Element createHttpSampler(Document doc, HTTPSamplerDto httpSamplerDto, int inx) {
        Element httpSampler = doc.createElement("HTTPSamplerProxy");
        httpSampler.setAttribute("guiclass", "HttpTestSampleGui");
        httpSampler.setAttribute("testclass", "HTTPSamplerProxy");
        httpSampler.setAttribute("testname", httpSamplerDto.getTestname().replace("$dtcNoIndex$", String.format("%02d", inx)) );
        httpSampler.setAttribute("enabled", "true");

        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.path", httpSamplerDto.getPath(), "stringProp"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.follow_redirects", "true"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.method", httpSamplerDto.getMethod()));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.use_keepalive", "true"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.postBodyRaw", String.valueOf(httpSamplerDto.isPostBodyRaw())));

        // "GET"/"POST" method용 User Defined Variables
        Element elementProp = doc.createElement("elementProp");
        elementProp.setAttribute("elementType", "Arguments");
        elementProp.setAttribute("name", "HTTPsampler.Arguments");
        // "GET" 전용 User Defined Variables 추가 설정
        if ( !httpSamplerDto.isPostBodyRaw() ) {
            elementProp.setAttribute("guiclass", "HTTPArgumentsPanel");
            elementProp.setAttribute("testclass", "Arguments");
            elementProp.setAttribute("testname", "User Defined Variables");
        }
        httpSampler.appendChild(elementProp) ;
        // User Defined Variables 내부 collectionProp
        Element collectionProp = doc.createElement("collectionProp");
        collectionProp.setAttribute("name", "Arguments.arguments");
        // "GET"에 정의된 User Defined Variables 기술
        Map<String, List<String>> queryParams = httpSamplerDto.getParameters() ;
        queryParams.forEach((key, valueList) -> {
            for (String value : valueList) {
                // Parameter 추가
                Element httpArg = doc.createElement("elementProp");
                httpArg.setAttribute("name", key);
                httpArg.setAttribute("elementType", "HTTPArgument");
                httpArg.appendChild(XmlUtil.createTextProp(doc, "HTTPArgument.always_encode", "false"));
                httpArg.appendChild(XmlUtil.createTextProp(doc, "Argument.name", key, "stringProp"));
                httpArg.appendChild(XmlUtil.createTextProp(doc, "Argument.value", value, "stringProp"));
                httpArg.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "="));
                httpArg.appendChild(XmlUtil.createTextProp(doc, "HTTPArgument.use_equals", "true"));
                collectionProp.appendChild(httpArg);
            }
        }) ;
        // "POST" postBodyRaw 작성
        if ( httpSamplerDto.isPostBodyRaw() ) {
            // Parameter 추가
            Element postDataElement = doc.createElement("elementProp");
            postDataElement.setAttribute("name", "");
            postDataElement.setAttribute("elementType", "HTTPArgument");
            postDataElement.appendChild(XmlUtil.createTextProp(doc, "HTTPArgument.always_encode", "false")) ;
            postDataElement.appendChild(XmlUtil.createTextProp(doc, "Argument.value", httpSamplerDto.getPostData(), "stringProp")) ;
            postDataElement.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;
            collectionProp.appendChild(postDataElement) ;
        }

        elementProp.appendChild(collectionProp) ;

        return httpSampler;
    }

    private HTTPSamplerDto makeHttpSamplerDto(HarUploadDto harUploadDto, JSONObject tx) {
        JSONObject request = tx.getJSONObject("request");
        JSONObject response = tx.getJSONObject("response");
        String path = CommonUtil.getPath(harUploadDto.getServerIp(), request);
        // "GET", "POST", "PATCH" 등
        String method = request.getString("method");
        // "POST" 방식일 때 post data
        JSONObject postData = null ;
        if ( "POST".equals(method) && request.has("postData") ) {
            postData = request.getJSONObject("postData") ;
        }
        boolean postBodyRaw = (postData != null);
        // "GET" 방식일 때 파라미터 배열
        JSONArray queryArray = "PATH-PARAMETER-TYPE".equals(QUERY_STRING_TYPE)? new JSONArray():request.getJSONArray("queryString");

        // Headers 추가
        JSONArray headers = request.getJSONArray("headers") ;
        Map<String, String> headerMap = new HashMap<>() ;
        for (int inx = 0; inx < headers.length(); inx++) {
            JSONObject header = headers.getJSONObject(inx);
            String name = header.getString("name");
            String value = header.getString("value");
            headerMap.put(name, value) ;
        }

        HTTPSamplerDto httpSamplerDto = HTTPSamplerDto.builder()
                .testname("[DTC"+String.format("%02d", harUploadDto.getDTCNo())+"-$dtcNoIndex$][" + harUploadDto.getDTCName() + "] " + CommonUtil.cutAfterQuestion(path))
                .path(path)
                .method(method)
                .postBodyRaw(postBodyRaw)
                .postData(postBodyRaw? postData.getString("text"):null)
                // "GET" Parameters
                .queryArray(queryArray)
                .jwtTokenKeyNm(harUploadDto.getJwtTokenKeyNm())
                .response(response)
                .headerManagerDto(HeaderManagerDto.builder()
                        .testname("Header Manager Each")
                        .headers(headerMap)
                        .build()
                )
                .build() ;

        // "로그인 액션"인 경우 jwtJsonPathExprs 값 셋팅
        if ( StringUtils.hasText(harUploadDto.getJwtTokenKeyNm()) ) {
            CommonUtil.isLoginAction(httpSamplerDto) ;
        }

        return httpSamplerDto ;
    }

    private JSONArray getTransactionList(StringBuilder harFileJsonContent) {
        // HAR JSON 데이터 Root
        JSONObject root = new JSONObject(harFileJsonContent.toString());
        // HAR log [단건]
        JSONObject log = root.getJSONObject("log");
        // HAR entries [배열]
        JSONArray entries = log.getJSONArray("entries");

        return entries ;
    }

    // 업로드 된 HAR 파일의 JSON 내용을 읽어 StringBuilder 로 만들어 리턴.
    private StringBuilder makeHarFileJsonContent(HarUploadDto harUploadDto) {
        StringBuilder harFileJsonContent = new StringBuilder();
        BufferedReader harFileReader = null ;
        String singleLine = null ;
        try {
            harFileReader = new BufferedReader(
                    new InputStreamReader(harUploadDto.getHarFile().getInputStream(), StandardCharsets.UTF_8)
            );

            while ((singleLine = harFileReader.readLine()) != null) {
                harFileJsonContent.append(singleLine).append("\n");
            }
        } catch (NoSuchFileException ex) {
            throw new RuntimeException("❌ [" + harUploadDto.getHarFile().getName() + "] 파일이 존재하지 않습니다.");
            // Do Nothing!. reader 기존 내용 재활용
//            log.info("### harFileReader 이전 내용 재활용!") ;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return harFileJsonContent ;
    }

    private Element createTransactionController(Document doc, HarUploadDto harUploadDto) {
        Element transactionController = doc.createElement("TransactionController");
        transactionController.setAttribute("guiclass", "TransactionControllerGui");
        transactionController.setAttribute("testclass", "TransactionController");
        transactionController.setAttribute("testname", "■ [TC"+String.format("%02d", harUploadDto.getDTCNo())+"]["+harUploadDto.getDTCName()+"] Transaction Controller");

        transactionController.appendChild(XmlUtil.createTextProp(doc, "TransactionController.parent", "true" ) ) ;
        transactionController.appendChild(XmlUtil.createTextProp(doc, "TransactionController.includeTimers", "false" ) ) ;

        return transactionController ;
    }

    private Element createThreadGroup(Document doc, HarUploadDto harUploadDto) {
        Element threadGroup = doc.createElement("ThreadGroup");
        threadGroup.setAttribute("guiclass", "ThreadGroupGui");
        threadGroup.setAttribute("testclass", "ThreadGroup");
        threadGroup.setAttribute("testname", "[TC"+String.format("%02d", harUploadDto.getDTCNo())+"] "+harUploadDto.getDTCName()+" Thread Group");
        threadGroup.setAttribute("enabled", "true");

        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.num_threads", "${NUM_OF_THREADS_" + harUploadDto.getDTCName() + "}" ) ) ;
        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.ramp_time", "${RAMP_UP_PERIOD}")) ;
        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.duration", "${TEST_DURATION}")) ;
        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.same_user_on_next_iteration", "true")) ;
        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.scheduler", "true")) ;
        threadGroup.appendChild(XmlUtil.createTextProp(doc, "ThreadGroup.on_sample_error", "startnextloop")) ;

        Element elementProp = doc.createElement("elementProp");
        elementProp.setAttribute("elementType", "LoopController");
        elementProp.setAttribute("guiclass", "LoopControlPanel");
        elementProp.setAttribute("name", "ThreadGroup.main_controller");
        elementProp.setAttribute("testclass", "LoopController");
        elementProp.setAttribute("testname", "Loop Controller");

        elementProp.appendChild(XmlUtil.createTextProp(doc, "LoopController.loops", "-1", "intProp")) ;
        elementProp.appendChild(XmlUtil.createTextProp(doc, "LoopController.continue_forever", "false")) ;

        threadGroup.appendChild(elementProp) ;

        return threadGroup;
    }

    private Element createPacingStart(Document doc) {
        Element pacingStart = doc.createElement("io.github.vdaburon.jmeterplugins.pacing.PacingStart");
        pacingStart.setAttribute("guiclass", "io.github.vdaburon.jmeterplugins.pacing.gui.PacingStartGui");
        pacingStart.setAttribute("testclass", "io.github.vdaburon.jmeterplugins.pacing.PacingStart");
        pacingStart.setAttribute("testname", "Pacing Start");

        pacingStart.appendChild(XmlUtil.createTextProp(doc, "PacingStart.variableName", "V_BEGIN")) ;

        return pacingStart ;
    }

    private Element createPacingPause(Document doc) {
        Element pacingPause = doc.createElement("io.github.vdaburon.jmeterplugins.pacing.PacingPause");
        pacingPause.setAttribute("guiclass", "io.github.vdaburon.jmeterplugins.pacing.gui.PacingPauseGui");
        pacingPause.setAttribute("testclass", "io.github.vdaburon.jmeterplugins.pacing.PacingPause");
        pacingPause.setAttribute("testname", "Pacing Pause (삭제 금지, 꼭 필요)");
        pacingPause.setAttribute("enabled", "true");

        pacingPause.appendChild(XmlUtil.createTextProp(doc, "PacingPause.variableName", "V_BEGIN")) ;
        pacingPause.appendChild(XmlUtil.createTextProp(doc, "PacingPause.duration", "3000")) ;

        return pacingPause ;
    }

    private Element createHeaderManager(Document doc, HTTPSamplerDto httpSamplerDto) {
        Element headerManager = doc.createElement("HeaderManager");
        headerManager.setAttribute("guiclass", "HeaderPanel");
        headerManager.setAttribute("testclass", "HeaderManager");
        headerManager.setAttribute("testname", httpSamplerDto.getHeaderManagerDto().getTestname());
        headerManager.setAttribute("enabled", "true");

        Element collectionProp = doc.createElement("collectionProp");
        collectionProp.setAttribute("name", "HeaderManager.headers");
        headerManager.appendChild(collectionProp) ;

        for (Map.Entry<String, String> entry : httpSamplerDto.getHeaderManagerDto().getHeaders().entrySet() ) {
            Element elementProp = doc.createElement("elementProp");
            elementProp.setAttribute("name", "");
            elementProp.setAttribute("elementType", "Header");
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Header.name", entry.getKey())) ;
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Header.value", entry.getValue())) ;
            collectionProp.appendChild(elementProp) ;
        }
        // jwtJsonPathExprs 값이 채워져 있으면 헤더에 Authorization 추가
        if ( this.needAuthorization ) {
            Element elementProp = doc.createElement("elementProp");
            elementProp.setAttribute("name", "");
            elementProp.setAttribute("elementType", "Header");
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Header.name", "Authorization")) ;
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Header.value", "Bearer ${"+httpSamplerDto.getJwtTokenKeyNm()+"}")) ;
            collectionProp.appendChild(elementProp) ;
        }

        return headerManager ;
    }

    private Element createResponseTimeGraph(Document doc, HarUploadDto harUploadDto) {
        // 그래프 콜렉터 엘리먼트 생성
        Element collector = doc.createElement("kg.apc.jmeter.vizualizers.CorrectedResultCollector");
        collector.setAttribute("guiclass", "kg.apc.jmeter.vizualizers.ResponseTimesOverTimeGui");
        collector.setAttribute("testclass", "kg.apc.jmeter.vizualizers.CorrectedResultCollector");
        collector.setAttribute("testname", "[결과 그래프] 응답시간");

        collector.appendChild(XmlUtil.createTextProp(doc, "ResultCollector.error_logging", "false"));

        Element objProp = doc.createElement("objProp");
        Element name = doc.createElement("name");
        name.setTextContent("saveConfig");
        objProp.appendChild(name);

        Element value = doc.createElement("value");
        value.setAttribute("class", "SampleSaveConfiguration");

        String[] trueFields = {
                "time", "latency", "timestamp", "success", "label", "code", "message", "threadName",
                "dataType", "assertions", "subresults", "fieldNames", "saveAssertionResultsFailureMessage",
                "bytes", "sentBytes", "url", "threadCounts", "idleTime", "connectTime"
        };

        String[] falseFields = {
                "encoding", "responseData", "samplerData", "xml", "responseHeaders",
                "requestHeaders", "responseDataOnError"
        };

        for (String field : trueFields) {
            Element el = doc.createElement(field);
            el.setTextContent("true");
            value.appendChild(el);
        }

        for (String field : falseFields) {
            Element el = doc.createElement(field);
            el.setTextContent("false");
            value.appendChild(el);
        }

        Element assertion = doc.createElement("assertionsResultsToSave");
        assertion.setTextContent("0");
        value.appendChild(assertion);

        objProp.appendChild(value);

        collector.appendChild(objProp);
        collector.appendChild(XmlUtil.createTextProp(doc, "filename", harUploadDto.getJmxFileNm().replace(".jmx", ".jtl")));
        collector.appendChild(XmlUtil.createTextProp(doc, "interval_grouping", "500"));
        collector.appendChild(XmlUtil.createTextProp(doc, "graph_aggregated", "false"));
        collector.appendChild(XmlUtil.createTextProp(doc, "include_sample_labels", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "exclude_sample_labels", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "start_offset", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "end_offset", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "include_checkbox_state", "false"));
        collector.appendChild(XmlUtil.createTextProp(doc, "exclude_checkbox_state", "false"));

        return collector ;
    }

    private Element createStatisticsTable(Document doc, HarUploadDto harUploadDto) {
        // 그래프 콜렉터 엘리먼트 생성
        Element collector = doc.createElement("ResultCollector");
        collector.setAttribute("guiclass", "my.jmeter.plugin.visualizer.StatVisualizer");
        collector.setAttribute("testclass", "ResultCollector");
        collector.setAttribute("testname", "[결과 테이블] 대시보드 (표)");

        collector.appendChild(XmlUtil.createTextProp(doc, "ResultCollector.error_logging", "false"));

        Element objProp = doc.createElement("objProp");
        Element name = doc.createElement("name");
        name.setTextContent("saveConfig");
        objProp.appendChild(name);

        Element value = doc.createElement("value");
        value.setAttribute("class", "SampleSaveConfiguration");

        String[] trueFields = {
                "time", "latency", "timestamp", "success", "label", "code", "message", "threadName",
                "dataType", "assertions", "subresults", "fieldNames", "saveAssertionResultsFailureMessage",
                "bytes", "sentBytes", "url", "threadCounts", "idleTime", "connectTime"
        };

        String[] falseFields = {
                "encoding", "responseData", "samplerData", "xml", "responseHeaders",
                "requestHeaders", "responseDataOnError"
        };

        for (String field : trueFields) {
            Element el = doc.createElement(field);
            el.setTextContent("true");
            value.appendChild(el);
        }

        for (String field : falseFields) {
            Element el = doc.createElement(field);
            el.setTextContent("false");
            value.appendChild(el);
        }

        Element assertion = doc.createElement("assertionsResultsToSave");
        assertion.setTextContent("0");
        value.appendChild(assertion);

        objProp.appendChild(value);

        collector.appendChild(objProp);
        collector.appendChild(XmlUtil.createTextProp(doc, "filename", harUploadDto.getJmxFileNm().replace(".jmx", ".jtl")));
        collector.appendChild(XmlUtil.createTextProp(doc, "interval_grouping", "500"));
        collector.appendChild(XmlUtil.createTextProp(doc, "graph_aggregated", "false"));
        collector.appendChild(XmlUtil.createTextProp(doc, "include_sample_labels", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "exclude_sample_labels", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "start_offset", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "end_offset", ""));
        collector.appendChild(XmlUtil.createTextProp(doc, "include_checkbox_state", "false"));
        collector.appendChild(XmlUtil.createTextProp(doc, "exclude_checkbox_state", "false"));

        return collector ;
    }

    private Element createTpsGraph(Document doc, HarUploadDto harUploadDto) {
        Element tpsGraph = doc.createElement("kg.apc.jmeter.vizualizers.CorrectedResultCollector");
        tpsGraph.setAttribute("guiclass", "kg.apc.jmeter.vizualizers.TransactionsPerSecondGui");
        tpsGraph.setAttribute("testclass", "kg.apc.jmeter.vizualizers.CorrectedResultCollector");
        tpsGraph.setAttribute("testname", "[결과 그래프] TPS");

        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "ResultCollector.error_logging", "false"));

        Element objProp = doc.createElement("objProp");
        Element name = doc.createElement("name");
        name.setTextContent("saveConfig");
        objProp.appendChild(name);

        Element value = doc.createElement("value");
        value.setAttribute("class", "SampleSaveConfiguration");

        String[] trueTags = {
                "time", "latency", "timestamp", "success", "label", "code", "message", "threadName", "dataType",
                "assertions", "subresults", "fieldNames", "saveAssertionResultsFailureMessage", "bytes", "sentBytes",
                "url", "threadCounts", "idleTime", "connectTime"
        };
        String[] falseTags = {
                "encoding", "responseData", "samplerData", "xml", "responseHeaders",
                "requestHeaders", "responseDataOnError"
        };

        for (String tag : trueTags) {
            Element element = doc.createElement(tag);
            element.setTextContent("true");
            value.appendChild(element);
        }
        for (String tag : falseTags) {
            Element element = doc.createElement(tag);
            element.setTextContent("false");
            value.appendChild(element);
        }

        Element assertionsResultsToSave = doc.createElement("assertionsResultsToSave");
        assertionsResultsToSave.setTextContent("0");
        value.appendChild(assertionsResultsToSave);

        objProp.appendChild(value);
        tpsGraph.appendChild(objProp);

        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "filename", harUploadDto.getJmxFileNm().replace(".jmx", ".jtl")));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "interval_grouping", "1000"));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "graph_aggregated", "false"));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "include_sample_labels", "^■ \\[TC\\d+\\]\\[.+?\\] Transaction Controller$"));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "exclude_sample_labels", ""));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "start_offset", ""));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "end_offset", ""));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "include_checkbox_state", "true"));
        tpsGraph.appendChild(XmlUtil.createTextProp(doc, "exclude_checkbox_state", "false"));

        return tpsGraph ;
    }

    
    private Element createTestPlan(Document doc) {
        Element testPlan = doc.createElement("TestPlan");
        testPlan.setAttribute("guiclass", "TestPlanGui");
        testPlan.setAttribute("testclass", "TestPlan");
        testPlan.setAttribute("testname", "Test Plan");
        testPlan.setAttribute("enabled", "true");

        testPlan.appendChild(XmlUtil.createTextProp(doc, "TestPlan.comments", ""));
        testPlan.appendChild(XmlUtil.createTextProp(doc, "TestPlan.functional_mode", "false"));
        testPlan.appendChild(XmlUtil.createTextProp(doc, "TestPlan.serialize_threadgroups", "false"));
        testPlan.appendChild(XmlUtil.createTextProp(doc, "TestPlan.user_define_classpath", ""));

        Element userDefinedVars = doc.createElement("elementProp");
        userDefinedVars.setAttribute("name", "TestPlan.user_defined_variables");
        userDefinedVars.setAttribute("elementType", "Arguments");
        userDefinedVars.setAttribute("guiclass", "ArgumentsPanel");
        userDefinedVars.setAttribute("testclass", "Arguments");
        userDefinedVars.setAttribute("testname", "User Defined Variables");
        Element collectionProp = doc.createElement("collectionProp");
        collectionProp.setAttribute("name", "Arguments.arguments");
        userDefinedVars.appendChild(collectionProp);
        testPlan.appendChild(userDefinedVars);

        return testPlan;
    }

    private Element createHttpDefaults(Document doc, HarUploadDto harUploadDto) {
        Element config = doc.createElement("ConfigTestElement");
        config.setAttribute("guiclass", "HttpDefaultsGui");
        config.setAttribute("testclass", "ConfigTestElement");
        config.setAttribute("testname", "[환경변수] " + harUploadDto.getServerIp());
        config.setAttribute("enabled", "true");

        String protocol = harUploadDto.getServerIp().startsWith("https")? "https":harUploadDto.getServerIp().startsWith("http")? "http":harUploadDto.getServerIp().substring(0,harUploadDto.getServerIp().indexOf("://")) ;
        String domain = harUploadDto.getServerIp().substring(harUploadDto.getServerIp().indexOf("://")+3) ;
        config.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.domain", domain));
        config.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.protocol", protocol));
        config.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.implementation", "HttpClient4"));

        Element httpArgs = doc.createElement("elementProp");
        httpArgs.setAttribute("name", "HTTPsampler.Arguments");
        httpArgs.setAttribute("elementType", "Arguments");
        httpArgs.setAttribute("guiclass", "HTTPArgumentsPanel");
        httpArgs.setAttribute("testclass", "Arguments");
        httpArgs.setAttribute("testname", "User Defined Variables");

        Element httpArgsCollection = doc.createElement("collectionProp");
        httpArgsCollection.setAttribute("name", "Arguments.arguments");
        httpArgs.appendChild(httpArgsCollection);
        config.appendChild(httpArgs);

        return config;
    }

    private Element createVariables(Document doc, String testname, String comment, Map<String, Map<String, String>> variables) {
        // Arguments 엘리먼트 (변수 정의)
        Element arguments = doc.createElement("Arguments");
        arguments.setAttribute("guiclass", "ArgumentsPanel");
        arguments.setAttribute("testclass", "Arguments");
        arguments.setAttribute("testname", testname);
        arguments.setAttribute("enabled", "true");

        // Arguments에 stringProp 추가 (Comment)
        arguments.appendChild(XmlUtil.createTextProp(doc, "TestPlan.comments", comment, "stringProp"));

        // Arguments에 collectionProp 내부 추가
        Element collectionProp = doc.createElement("collectionProp");
        collectionProp.setAttribute("name", "Arguments.arguments");
        arguments.appendChild(collectionProp);

        // User Defined Variables 추가
        createArgument(doc, collectionProp, variables) ;

//        // 변수 추가 : TEST_DURATION
//        Element argument1 = createArgument(doc, "TEST_DURATION", "300", "(초) 테스트 지속시간");
//        collectionProp.appendChild(argument1);
//
//        // 변수 추가 : RAMP_UP_PERIOD
//        Element argument2 = createArgument(doc, "RAMP_UP_PERIOD", "20", "(초) Ramp-Up 시간");
//        collectionProp.appendChild(argument2);
//
//        // 변수 추가 : NUM_OF_THREADS_메인페이지
//        Element argument3 = createArgument(doc, "NUM_OF_THREADS_메인페이지", "1", "(명) 가상 유저 수");
//        collectionProp.appendChild(argument3);

        return arguments;
    }

    private Element createJSR223Assertion(Document doc, HarUploadDto harUploadDto) {
        Element jsr223 = doc.createElement("JSR223Assertion");
        jsr223.setAttribute("guiclass", "TestBeanGUI");
        jsr223.setAttribute("testclass", "JSR223Assertion");
        jsr223.setAttribute("testname", "[응답 검증] JSR223 Assertion ("+harUploadDto.getAssertionCode()+")");
        jsr223.setAttribute("enabled", "true");
        String script = "import groovy.json.JsonSlurper\n" +
                "\n" +
                "// 현재 샘플러가 HTTP Sampler인지 확인\n" +
                "if (sampler instanceof org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase) {\t\n" +
                "    // 1-1. 사용자 정의 변수에서 Assertion 검사 제외할 url (excludePaths) 값을 가져와 배열로 변환 캐싱\n" +
                "    def excludePaths = vars.getObject(\"parsedExcludePaths\")\n" +
                "    // 1-2. 사용자 정의 변수에서 Assertion 검사 제외할 문자열(/html/등)이 포함된 url (excludePaths) 값을 가져와 배열로 변환\n" +
                "    def excludeContainPaths = vars.getObject(\"parsedExcludeContainPaths\")\n" +
                "\n" +
                "    // ------------------------- 캐싱 처리 시작 ---------------------------------------------------\n" +
                "    // 각 Thread(VUser) 마다 최초 1회만 수행\n" +
                "    if (excludePaths == null) {\n" +
                "        def excludePathsRaw = vars.get(\"excludePaths\") // \"ex: /actuator/healthcheck, /api/dummy.do\"\n" +
                "        excludePaths = excludePathsRaw != null ? excludePathsRaw.split(\",\").collect { it.trim() } : []\n" +
                "        // Java 객체를 Thread Local에 저장 항목 : vars.putObject(...)\n" +
                "        vars.putObject(\"parsedExcludePaths\", excludePaths)\n" +
                "        log.info(\"### excludePaths parsed &amp; cached: \" + excludePaths)\n" +
                "    }\n" +
                "\n" +
                "    // 각 Thread(VUser) 마다 최초 1회만 수행\n" +
                "    if (excludeContainPaths == null) {\n" +
                "        def excludeContainPathsRaw = vars.get(\"excludeContainPaths\") // \"ex: /html/, /js/\"\n" +
                "        excludeContainPaths = excludeContainPathsRaw != null ? excludeContainPathsRaw.split(\",\").collect { it.trim() } : []\n" +
                "        // Java 객체를 Thread Local에 저장 항목 : vars.putObject(...)\n" +
                "        vars.putObject(\"parsedExcludeContainPaths\", excludeContainPaths)\n" +
                "        log.info(\"### excludeContainPaths parsed &amp; cached: \" + excludeContainPaths)\n" +
                "    }\n" +
                "    // ------------------------- 캐싱 처리 종료 --------------------------------------------------\n" +
                "\n" +
                "    def responseText = prev.getResponseDataAsString()?.trim()\n" +
                "    // JSON 유효성 검사\n" +
                "    if ( !responseText?.startsWith(\"{\") && !responseText?.startsWith(\"[\")) {\n" +
                "        // 응답이 JSON 형식이 아님, skip!\n" +
                "    }\n" +
                "\t\n" +
                "    // 2. 샘플러의 경로 (Path) 가져오기\n" +
                "    def samplerPath = sampler.getProperty(\"HTTPSampler.path\").getStringValue()\n" +
                "\t\n" +
                "    // 3. 제외 조건 확인 (contains라서 헷갈리지 말 것. ExactMatch 일때만 배제됨, contains는 배열에 해당 값이 있는지를 체크하는 것임)\n" +
                "    def isExcludedByExactMatch = excludePaths.contains(samplerPath)\n" +
                "    def isExcludedByPrefix = excludeContainPaths.any { path -> samplerPath.contains(path) }\n" +
                "\t\n" +
                "    // 4. 제외되지 않은 경우에만 Assertion 수행\n" +
                "    if (!isExcludedByExactMatch && !isExcludedByPrefix) {\n" +
                "//        def responseText = prev.getResponseDataAsString()?.trim()\n" +
                "    \n" +
                "        // JSON 유효성 검사\n" +
                "        if (responseText?.startsWith(\"{\") || responseText?.startsWith(\"[\")) {\n" +
                "            def json = new JsonSlurper().parseText(responseText)\n" +
                "            assert json."+harUploadDto.getAssertionCode()+"\n" +
                "        } else {\n" +
                "            AssertionResult.setFailure(true)\n" +
                "            AssertionResult.setFailureMessage(\"응답이 JSON 형식이 아님(${samplerPath}): ${responseText}\")\n" +
                "        }\n" +
                "    }\n" +
                "}" ;
        jsr223.appendChild(XmlUtil.createTextProp(doc, "cacheKey", "true"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "filename", ""));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "parameters", ""));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "script", script, "stringProp"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "scriptLanguage", "groovy"));
        return jsr223;
    }

    private Element createResultCollector(Document doc) {
        Element collector = doc.createElement("ResultCollector");
        collector.setAttribute("guiclass", "ViewResultsFullVisualizer");
        collector.setAttribute("testclass", "ResultCollector");
        collector.setAttribute("testname", "[결과 확인] View Result Tree");
        collector.setAttribute("enabled", "true");
        return collector;
    }

    public void jtlFiltering(JtlUploadDto jtlUploadDto) throws IOException {

        // 업로드된 HAR 파일 내용을 한 줄씩 읽기
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(jtlUploadDto.getJtlFile().getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder filteredJtlContents = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            String filterStr = jtlUploadDto.getFilterStr();

            if ( line.contains(filterStr) ) {
                filteredJtlContents.append(line).append("\n");
            }
        }

        // 2. 결과 JMX 파일을 저장할 경로 설정 (업로드 파일과 동일한 DIR)
        Path resultJtlFile = Paths.get(jtlUploadDto.getJtlDir()+"\\filtered.jtl");

        // 3. 파일 저장 (UTF-8 인코딩)
        Files.writeString(resultJtlFile, filteredJtlContents);
        System.out.println("\n파일 저장 완료: " + resultJtlFile.toAbsolutePath());
    }

    private void createArgument(Document doc, Element collectionProp, Map<String, Map<String, String>> variables) {
        // argument 생성
        for (Map.Entry<String, Map<String, String>> entry : variables.entrySet()) {
            Map<String, String> data = entry.getValue();

            Element elementProp = doc.createElement("elementProp");
            elementProp.setAttribute("name", entry.getKey());
            elementProp.setAttribute("elementType", "Argument");

            elementProp.appendChild(XmlUtil.createTextProp(doc, "Argument.name", entry.getKey(), "stringProp")) ;
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Argument.value", data.get("value"), "stringProp")) ;
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=", "stringProp")) ;
            elementProp.appendChild(XmlUtil.createTextProp(doc, "Argument.desc", data.get("comment"), "stringProp")) ;

            collectionProp.appendChild(elementProp) ;
        }
    }

//    private Element createArgument(Document doc, String name, String value, String desc) {
//        Element elementProp = doc.createElement("elementProp");
//        elementProp.setAttribute("name", name);
//        elementProp.setAttribute("elementType", "Argument");
//
//        Element stringPropName = doc.createElement("stringProp");
//        stringPropName.setAttribute("name", "Argument.name");
//        stringPropName.setTextContent(name);
//
//        Element stringPropValue = doc.createElement("stringProp");
//        stringPropValue.setAttribute("name", "Argument.value");
//        stringPropValue.setTextContent(value);
//
//        Element stringPropMeta = doc.createElement("stringProp");
//        stringPropMeta.setAttribute("name", "Argument.metadata");
//        stringPropMeta.setTextContent("=");
//
//        Element stringPropDesc = doc.createElement("stringProp");
//        stringPropDesc.setAttribute("name", "Argument.desc");
//        stringPropDesc.setTextContent(desc);
//
//        elementProp.appendChild(stringPropName);
//        elementProp.appendChild(stringPropValue);
//        elementProp.appendChild(stringPropMeta);
//        elementProp.appendChild(stringPropDesc);
//
//        return elementProp;
//    }

    private Element createBackendListener(Document doc, HarUploadDto harUploadDto) {
        Element backendListener = doc.createElement("BackendListener");
        backendListener.setAttribute("guiclass", "BackendListenerGui");
        backendListener.setAttribute("testclass", "BackendListener");
        backendListener.setAttribute("testname", "InfluxDB 1.8 Listener" );
        backendListener.setAttribute("enabled", "false");

        // "GET"/"POST" method용 User Defined Variables
        Element elementProp = doc.createElement("elementProp");
        elementProp.setAttribute("name", "arguments");
        elementProp.setAttribute("elementType", "Arguments");
        elementProp.setAttribute("guiclass", "ArgumentsPanel");
        elementProp.setAttribute("testclass", "Arguments");
        backendListener.appendChild(elementProp) ;

        Element collectionProp = doc.createElement("collectionProp");
        collectionProp.setAttribute("name", "Arguments.arguments");
        elementProp.appendChild(collectionProp) ;

        Element influxdbMetricsSender = doc.createElement("elementProp");
        influxdbMetricsSender.setAttribute("name", "influxdbMetricsSender");
        influxdbMetricsSender.setAttribute("elementType", "Argument");
        collectionProp.appendChild(influxdbMetricsSender) ;
        influxdbMetricsSender.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "influxdbMetricsSender")) ;
        influxdbMetricsSender.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "org.apache.jmeter.visualizers.backend.influxdb.HttpMetricsSender")) ;
        influxdbMetricsSender.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element influxdbUrl = doc.createElement("elementProp");
        influxdbUrl.setAttribute("name", "influxdbUrl");
        influxdbUrl.setAttribute("elementType", "Argument");
        collectionProp.appendChild(influxdbUrl) ;
        influxdbUrl.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "influxdbUrl")) ;
        influxdbUrl.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "http://localhost:8086/write?db=jmeter")) ;
        influxdbUrl.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element application = doc.createElement("elementProp");
        application.setAttribute("name", "application");
        application.setAttribute("elementType", "Argument");
        collectionProp.appendChild(application) ;
        application.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "application")) ;
        application.appendChild(XmlUtil.createTextProp(doc, "Argument.value", harUploadDto.getPrjNm())) ;
        application.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element measurement = doc.createElement("elementProp");
        measurement.setAttribute("name", "measurement");
        measurement.setAttribute("elementType", "Argument");
        collectionProp.appendChild(measurement) ;
        measurement.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "measurement")) ;
        measurement.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "jmeter")) ;
        measurement.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element summaryOnly = doc.createElement("elementProp");
        summaryOnly.setAttribute("name", "summaryOnly");
        summaryOnly.setAttribute("elementType", "Argument");
        collectionProp.appendChild(summaryOnly) ;
        summaryOnly.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "summaryOnly")) ;
        summaryOnly.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "false")) ;
        summaryOnly.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element samplersRegex = doc.createElement("elementProp");
        samplersRegex.setAttribute("name", "samplersRegex");
        samplersRegex.setAttribute("elementType", "Argument");
        collectionProp.appendChild(samplersRegex) ;
        samplersRegex.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "samplersRegex")) ;
        samplersRegex.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "^■.*")) ;
        samplersRegex.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element percentiles = doc.createElement("elementProp");
        percentiles.setAttribute("name", "percentiles");
        percentiles.setAttribute("elementType", "Argument");
        collectionProp.appendChild(percentiles) ;
        percentiles.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "percentiles")) ;
        percentiles.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "90;95")) ;
        percentiles.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element testTitle = doc.createElement("elementProp");
        testTitle.setAttribute("name", "testTitle");
        testTitle.setAttribute("elementType", "Argument");
        collectionProp.appendChild(testTitle) ;
        testTitle.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "testTitle")) ;
        testTitle.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "90;95")) ;
        testTitle.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        Element eventTags = doc.createElement("elementProp");
        eventTags.setAttribute("name", "eventTags");
        eventTags.setAttribute("elementType", "Argument");
        collectionProp.appendChild(eventTags) ;
        eventTags.appendChild(XmlUtil.createTextProp(doc, "Argument.name", "eventTags")) ;
        eventTags.appendChild(XmlUtil.createTextProp(doc, "Argument.value", "")) ;
        eventTags.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=")) ;

        backendListener.appendChild(XmlUtil.createTextProp(doc, "classname", "org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient")) ;

        return backendListener ;
    }
}
