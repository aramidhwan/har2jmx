package com.shshin.har2jmx.util;

import com.shshin.har2jmx.dto.HTTPSamplerDto;
import com.shshin.har2jmx.dto.JsonExtractorDto;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XmlUtil {

    public static Element createTextProp(Document doc, String name, String value) {
        if ( value == null ) {
            value = "" ;
        }
        return createTextProp(doc, name, value, value.equals("true") || value.equals("false") ? "boolProp" :
                value.matches("\\d+") ? "longProp" : "stringProp");
    }

    public static Element createTextProp(Document doc, String name, String value, String tagName) {
        Element element = doc.createElement(tagName);
        element.setAttribute("name", name);
        element.setTextContent(value);
        return element ;
    }

    public synchronized static void createJsonExtractorList(Element httpSamplerHashTree, HTTPSamplerDto httpSamplerDto) {
        Document doc = httpSamplerHashTree.getOwnerDocument() ;
        for ( int inx = 0 ; inx < httpSamplerDto.getJsonExtractorDtoList().size() ; inx++ ) {
            JsonExtractorDto jsonExtractorDto = httpSamplerDto.getJsonExtractorDtoList().get(inx) ;
            Element jsonExtractor4AccessToken = doc.createElement("JSONPostProcessor") ;
            jsonExtractor4AccessToken.setAttribute("guiclass", "JSONPostProcessorGui");
            jsonExtractor4AccessToken.setAttribute("testclass", "JSONPostProcessor");
            jsonExtractor4AccessToken.setAttribute("testname", jsonExtractorDto.getTestname());
            jsonExtractor4AccessToken.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.referenceNames", jsonExtractorDto.getReferenceName(), "stringProp"));
            jsonExtractor4AccessToken.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.jsonPathExprs", jsonExtractorDto.getJsonPathExprs(), "stringProp"));
            jsonExtractor4AccessToken.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.match_numbers", "1", "stringProp"));
            jsonExtractor4AccessToken.appendChild(XmlUtil.createTextProp(doc, "JSONPostProcessor.defaultValues", "NOT_FOUND", "stringProp"));
            httpSamplerHashTree.appendChild(jsonExtractor4AccessToken) ;
        }
    }

    // Node node 뒤에 나오는 첫번째 <hashTree>를 찾는다.
    public static Node findNextHashTree(Node node) {
        Node next = node.getNextSibling();
        while (next != null && !(next.getNodeType() == Node.ELEMENT_NODE && next.getNodeName().equals("hashTree"))) {
            next = next.getNextSibling();
        }
        return next;
    }

}
