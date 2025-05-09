package com.shshin.har2jmx.service;

import com.shshin.har2jmx.dto.HTTPSamplerDto;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestFragmentService {

    // "로그인 액션" url 용 TestFragment
    public List<String> addTestFragment4LoginAction(String jmxFileNm, HTTPSamplerDto loginActionSampler) throws Exception {
        // 작업내용 메시지
        List<String> returnMsg = new ArrayList<>() ;

        if ( loginActionSampler == null ) {
            return returnMsg ;
        }
        
        // TestFragment를 추가할 JMX 파일
        File inputFile = new File(jmxFileNm);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputFile);
        doc.getDocumentElement().normalize();

        // TestPlan 노드 찾기
        NodeList testPlanList = doc.getElementsByTagName("TestPlan");
        if (testPlanList.getLength() == 0) {
            System.out.println("TestPlan 노드를 찾을 수 없습니다.");
            throw new RuntimeException("TestPlan 노드를 찾을 수 없습니다.");
        }

        // TestPlan은 보통 1개 이다.
        Node testPlanNode = testPlanList.item(0);

        // TestPlan의 hashTree 찾기
        Node testPlanHashTree = XmlUtil.findNextHashTree(testPlanNode);
        if (testPlanHashTree == null) {
            System.out.println("TestPlan에 연결된 hashTree를 찾을 수 없습니다.");
            throw new RuntimeException("TestPlan에 연결된 hashTree를 찾을 수 없습니다.");
        }

        // --------- (1) TestFragmentController 추가 ---------
        Element tfController = doc.createElement("TestFragmentController");
        tfController.setAttribute("guiclass", "TestFragmentControllerGui");
        tfController.setAttribute("testclass", "TestFragmentController");
        tfController.setAttribute("testname", "[로그인] - Test Fragment");
        tfController.setAttribute("enabled", "true");

        // --------- (2) TestFragmentController용 hashTree ---------
        Element tfHashTree = doc.createElement("hashTree");

        // --------- (3) 로그인 액션용 TransactionController 추가 ---------
        Element txController = doc.createElement("TransactionController");
        txController.setAttribute("guiclass", "TransactionControllerGui");
        txController.setAttribute("testclass", "TransactionController");
        txController.setAttribute("testname", "로그인 Transaction Controller");

        txController.appendChild(XmlUtil.createTextProp(doc, "TransactionController.parent", "true"));
        txController.appendChild(XmlUtil.createTextProp(doc, "TransactionController.includeTimers", "false"));

        Element txControllerHashTree = doc.createElement("hashTree");

        //-------------------------------------------------
        // ➕ HTTPSampler 1 (로그인 페이지)
        //-------------------------------------------------
        // makeLoginSampler(Document doc, String name, String path, String method, boolean postBodyRaw)
//        Element http1 = makeLoginSampler(doc,
//                "[D${tcNm}-01][로그인] /common-service/html/auth/loginPage",
//                "/common-service/html/auth/loginPage",
//                "GET", false);

        //-------------------------------------------------
        // ➕ HTTPSampler 2 (로그인 액션)
        //-------------------------------------------------
        // makeLoginSampler(Document doc, String name, String path, String method, boolean postBodyRaw)
        Element http2 = makeLoginSampler(doc, loginActionSampler );

        //-------------------------------------------------
        // HTTPSampler 2 - (로그인 액션) HeaderManager
        //-------------------------------------------------
        Element headerManager = doc.createElement("HeaderManager");
        headerManager.setAttribute("guiclass", "HeaderPanel");
        headerManager.setAttribute("testclass", "HeaderManager");
        headerManager.setAttribute("testname", loginActionSampler.getHeaderManagerDto().getTestname());
        headerManager.setAttribute("enabled", "true");

        Element headers = doc.createElement("collectionProp");
        headers.setAttribute("name", "HeaderManager.headers");

        Element header = null ;
        Map<String, String> headerMap = loginActionSampler.getHeaderManagerDto().getHeaders();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            header = doc.createElement("elementProp");
            header.setAttribute("name", "");
            header.setAttribute("elementType", "Header");
            Element hName = doc.createElement("stringProp");
            hName.setAttribute("name", "Header.name");
            hName.setTextContent(entry.getKey());
            Element hValue = doc.createElement("stringProp");
            hValue.setAttribute("name", "Header.value");
            hValue.setTextContent(entry.getValue());
            header.appendChild(hName);
            header.appendChild(hValue);
            headers.appendChild(header);
        }
        headerManager.appendChild(headers);

        //-----------------------------------------------------------------
        // HTTPSampler 2 - (로그인 액션) JSON Extractor for JWT accessToken
        //-----------------------------------------------------------------
        Element jsonExtractor = doc.createElement("JSONPostProcessor");
        jsonExtractor.setAttribute("guiclass", "JSONPostProcessorGui");
        jsonExtractor.setAttribute("testclass", "JSONPostProcessor");
        jsonExtractor.setAttribute("testname", loginActionSampler.getJsonExtractorDtoList().get(0).getTestname());
        jsonExtractor.setAttribute("enabled", "true");

        jsonExtractor.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.referenceNames", loginActionSampler.getJsonExtractorDtoList().get(0).getReferenceName(), "stringProp"));
        jsonExtractor.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.jsonPathExprs", loginActionSampler.getJsonExtractorDtoList().get(0).getJsonPathExprs(), "stringProp"));
        jsonExtractor.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.match_numbers", "1", "stringProp"));
        jsonExtractor.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.defaultValues", "NOT_FOUND"));

        // 조립
        Element http2Tree = doc.createElement("hashTree");
        http2Tree.appendChild(headerManager);
        http2Tree.appendChild(doc.createElement("hashTree"));
        http2Tree.appendChild(jsonExtractor);
        http2Tree.appendChild(doc.createElement("hashTree"));

        // 로그인 페이지용
//        txControllerHashTree.appendChild(http1);
//        txControllerHashTree.appendChild(doc.createElement("hashTree"));
        txControllerHashTree.appendChild(http2);
        txControllerHashTree.appendChild(http2Tree);

        // 트리 구조 조립
        tfHashTree.appendChild(txController);
        tfHashTree.appendChild(txControllerHashTree);

        // 최상위 hashTree에 추가
        testPlanHashTree.appendChild(tfController);
        testPlanHashTree.appendChild(tfHashTree);

        //-------------------------------------------------

        // 결과 저장 전에 불필요한 공백 노드 제거
        JsonUtil.removeWhitespaceNodes(doc);

        // 결과 저장
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(inputFile);
        transformer.transform(source, result);

        returnMsg.add("✅ Test Fragment가 성공적으로 추가되었습니다.");
        System.out.println("✅ Test Fragment가 성공적으로 추가되었습니다.");

        return returnMsg ;
    }

    // "로그인 액션" url 용 OnlyOnceController
    public List<String> addOnlyOnceController4LoginAction(String jmxFileNm, String tcNo) throws Exception {
        // 작업내용 메시지
        List<String> returnMsg = new ArrayList<>() ;

        File inputFile = new File(jmxFileNm);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputFile);
        doc.getDocumentElement().normalize();

        // TestPlan 노드 찾기
        NodeList testPlanList = doc.getElementsByTagName("TestPlan");
        if (testPlanList.getLength() == 0) {
            System.out.println("TestPlan 노드를 찾을 수 없습니다.");
            throw new RuntimeException("TestPlan 노드를 찾을 수 없습니다.");
        }

        // TestPlan은 보통 1개 이다.
        Element testPlanElement = (Element)testPlanList.item(0);
        String testPlanTestName = testPlanElement.getAttribute("testname") ;

        // Thread Group 찾기
        NodeList threadGroups = doc.getElementsByTagName("ThreadGroup");
        if (threadGroups.getLength() == 0) {
            throw new RuntimeException("ThreadGroup 노드를 찾을 수 없습니다.");
        }

        Node threadGroupNode = threadGroups.item(0);

        // ThreadGroup 다음에 오는 첫 번째 <hashTree> 찾기
        Node threadGroupHashTree = XmlUtil.findNextHashTree(threadGroupNode) ;

        if (threadGroupHashTree == null) {
            throw new RuntimeException("ThreadGroup 아래 hashTree 노드를 찾을 수 없습니다.");
        }

        // ThreadGroup > 첫 번째 Element로 OnceOnlyController 추가
        Element onceOnlyController = doc.createElement("OnceOnlyController");
        onceOnlyController.setAttribute("guiclass", "OnceOnlyControllerGui");
        onceOnlyController.setAttribute("testclass", "OnceOnlyController");
        onceOnlyController.setAttribute("testname", "[로그인] - Once Only Controller");

        Element onceOnlyHashTree = doc.createElement("hashTree");

        // ThreadGroup > OnceOnlyController > JSR223PreProcessor 추가 (tcNo 전달용)
        Element jsr223 = doc.createElement("JSR223PreProcessor");
        jsr223.setAttribute("guiclass", "TestBeanGUI");
        jsr223.setAttribute("testclass", "JSR223PreProcessor");
        jsr223.setAttribute("testname", "JSR223 PreProcessor - 로그인 변수 설정");

        jsr223.appendChild(XmlUtil.createTextProp(doc, "cacheKey", "true", "stringProp"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "filename", "", "stringProp"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "parameters", "", "stringProp"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "script",
                "vars.put(\"tcNo\", \""+tcNo+"\")\nvars.put(\"email\", \"aramidkim@naver.com\")", "stringProp"));
        jsr223.appendChild(XmlUtil.createTextProp(doc, "scriptLanguage", "groovy"));

        Element jsr223Tree = doc.createElement("hashTree");

        // ThreadGroup > OnceOnlyController > ModuleController 추가 (로그인 액션 TestFragment 호출용)
        Element moduleCtrl = doc.createElement("ModuleController");
        moduleCtrl.setAttribute("guiclass", "ModuleControllerGui");
        moduleCtrl.setAttribute("testclass", "ModuleController");
        moduleCtrl.setAttribute("testname", "[로그인] - Module Controller");
        moduleCtrl.setAttribute("enabled", "true");

        Element modulePath = doc.createElement("collectionProp");
        modulePath.setAttribute("name", "ModuleController.node_path");

        modulePath.appendChild(XmlUtil.createTextProp(doc, "xxx", "Test Plan"));     // name : 764597751
        modulePath.appendChild(XmlUtil.createTextProp(doc, "yyy", testPlanTestName, "stringProp"));        // name : -1941078309
        modulePath.appendChild(XmlUtil.createTextProp(doc, "zzz", "[로그인] - Test Fragment")); // name : -2008930265

        moduleCtrl.appendChild(modulePath);
        Element moduleTree = doc.createElement("hashTree");

        // 조립
        onceOnlyHashTree.appendChild(jsr223);
        onceOnlyHashTree.appendChild(jsr223Tree);
        onceOnlyHashTree.appendChild(moduleCtrl);
        onceOnlyHashTree.appendChild(moduleTree);

        threadGroupHashTree.appendChild(onceOnlyController);
        threadGroupHashTree.appendChild(onceOnlyHashTree);

        // Thread Group의 첫 번째 자식 앞에 삽입
        Node firstChild = threadGroupHashTree.getFirstChild();
        threadGroupHashTree.insertBefore(onceOnlyHashTree, firstChild);
        threadGroupHashTree.insertBefore(onceOnlyController, onceOnlyHashTree);

        // 결과 저장
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(inputFile);
        transformer.transform(source, result);

        returnMsg.add("✅ OnceOnlyController 블록이 성공적으로 추가되었습니다.");
        System.out.println("✅ OnceOnlyController 블록이 성공적으로 추가되었습니다.");

        return returnMsg ;
    }


    // <HTTPSamplerProxy> 작성
    private Element makeLoginSampler(Document doc, HTTPSamplerDto loginActionSampler) {
        Element httpSampler = doc.createElement("HTTPSamplerProxy");
        httpSampler.setAttribute("guiclass", "HttpTestSampleGui");
        httpSampler.setAttribute("testclass", "HTTPSamplerProxy");
        httpSampler.setAttribute("testname", loginActionSampler.getTestname());
        httpSampler.setAttribute("enabled", "true");

        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.path", loginActionSampler.getPath(), "stringProp"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.follow_redirects", "true"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.method", loginActionSampler.getMethod()));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.use_keepalive", "true"));
        httpSampler.appendChild(XmlUtil.createTextProp(doc, "HTTPSampler.postBodyRaw", String.valueOf(loginActionSampler.isPostBodyRaw())));

        Element args = doc.createElement("elementProp");
        args.setAttribute("name", "HTTPsampler.Arguments");
        args.setAttribute("elementType", "Arguments");

        // Arguments 내부 collectionProp
        Element coll = doc.createElement("collectionProp");
        coll.setAttribute("name", "Arguments.arguments");

        // HTTPArgument 추가
        Element httpArg = doc.createElement("elementProp");
        httpArg.setAttribute("name", "");
        httpArg.setAttribute("elementType", "HTTPArgument");

        httpArg.appendChild(XmlUtil.createTextProp(doc, "HTTPArgument.always_encode", "false"));
        httpArg.appendChild(XmlUtil.createTextProp(doc, "Argument.value", loginActionSampler.getPostData_text(), "stringProp"));
        httpArg.appendChild(XmlUtil.createTextProp(doc, "Argument.metadata", "=", "stringProp"));

        // HTTPArgument를 collectionProp에 추가
        coll.appendChild(httpArg);
        args.appendChild(coll);

        httpSampler.appendChild(args);

        return httpSampler;
    }
}
