function callFetchApi(url, queryString, params, callBackFunctionName) {
    fetch((queryString==null)? url:url+"?"+queryString, params)
    .then(response => {
        if ( !response.ok ) {
            throw new Error("❌ " + response.status + ' 에러가 발생했습니다.') ;
        }
        return response.json() ;
    }) // 서버 응답을 JSON 형식으로 변환합니다.
    .then(json => {
        if ( json.BIZ_SUCCESS == "0" ) {
            if ( json.msg != null ) {
                alert(json.msg) ;
            }
            if ( callBackFunctionName != null ) {
                if (typeof window[callBackFunctionName] === "function") {
                    window[callBackFunctionName](json);
                } else {
                    alert("❌ 해당 함수가 존재하지 않습니다. ["+callBackFunctionName+"()]");
                }
            }
        // 비즈니스 Exception 인 경우
        } else if ( json.BIZ_SUCCESS == "2" ) {
            if ( json.msg != null ) {
                alert(json.msg) ;
            }
            return false ;
        } else {
            alert("❌ 관리자에게 문의하십시요.\n예상치 못한 상태, json.BIZ_SUCCESS : " + json.BIZ_SUCCESS + ", " + json.msg);
        }
    })
    .catch(error => {
        alert(error.message) ;
    })
    ;
}

// keyword(String)를 찾아서 하이라이트, objName은 div 같은 특정 객체 내에서 keyword 찾기
function highlightKeyword(keyword, objName) {
//    const keyword = document.getElementById("keywordInput").value;
    if (!keyword) return;

    const container = document.getElementById(objName);

    // 기존 하이라이트 제거 후 다시 검색
    removeHighlights(container);

    const regex = new RegExp(`(${escapeRegExp(keyword)})`, 'gi');

    walkAndHighlight(container, regex);
}

function walkAndHighlight(node, regex) {
    if (node.nodeType === 3) { // 텍스트 노드
        const match = node.nodeValue.match(regex);
        if (match) {
            const span = document.createElement('span');
            span.innerHTML = node.nodeValue.replace(regex, `<mark>$1</mark>`);
            node.parentNode.replaceChild(span, node);
//        html 파일에 <mark> 태그 class 속성
//        mark {      // <mark> 태그 속성 지정(keyword 찾기)
//            background-color: orange;
//            color: black;
//            font-weight: bold;
//        }
        }
    } else if (node.nodeType === 1 && node.childNodes && !/(script|style|mark)/i.test(node.tagName)) {
        Array.from(node.childNodes).forEach(child => walkAndHighlight(child, regex));
    }
}

// 기존 하이라이트 제거
function removeHighlights(container = document) {
    const marks = container.querySelectorAll("mark");
    marks.forEach(mark => {
        const parent = mark.parentNode;
        parent.replaceChild(document.createTextNode(mark.textContent), mark);
        parent.normalize(); // 인접 텍스트 노드 정리
    });
}

// 정규식 특수문자 이스케이프 처리
function escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// JSON 데이터에서 KEY 또는 VALUE에서 keyword를 찾아서 클립보드에 jsonPathExpression를 복사. $.data[0].dashboardUid
function findJsonPathsByValueOrKey(json, keyword, needAlert) {
    // 문자열일 경우 파싱
    if (typeof json === "string") {
        try {
            json = JSON.parse(json);
        } catch (e) {
            alert("❌ 유효한 JSON 문자열이 아닙니다.");
            return;
        }
    }

    const results = [];

    function recursiveSearch(obj, currentPath) {
        if (Array.isArray(obj)) {
            obj.forEach((item, index) => {
                recursiveSearch(item, `${currentPath}[${index}]`);
            });
        } else if (typeof obj === "object" && obj !== null) {
            for (let key in obj) {
                if (!obj.hasOwnProperty(key)) continue;

                const value = obj[key];
                const path = `${currentPath}.${key}`;

                // ✅ key가 keyword일 경우 경로 저장
                if (String(key) === String(keyword)) {
                    results.push(path);
                }

                // ✅ value가 keyword일 경우 경로 저장
                if (typeof value !== "object" && String(value) === String(keyword)) {
                    results.push(path);
                }

                // ✅ object일 경우 재귀 탐색
                if (typeof value === "object") {
                    recursiveSearch(value, path);
                }
            }
        }
    }

    recursiveSearch(json, "$");

    const textToCopy = results.join("\n"); // 줄바꿈으로 구분
    if (textToCopy.length === 0) {
        alert("❌ 일치하는 key 또는 value가 없습니다.");
        return;
    }

    navigator.clipboard.writeText(textToCopy)
        .then(() => {if (needAlert) alert('✅ JMeter [Post Processor] > [JSON Extractor]의 "JSON Path expressions"가 클립보드에 복사되었습니다:\n\n' + textToCopy)} )
        .catch(err => alert("❌ 복사 실패: " + err));

    return textToCopy ;
}

// JMeter 기동시키는 스크립트
function launchJMeter() {
    const jmxPath = document.getElementById("jmxFileWithPath").dataset.value;

    const url = '/api/runJmeter' ;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // 데이터 형식 설정
        },
        redirect: "follow", // follow, error, or manual
        body: JSON.stringify({
            "path": jmxPath
        })
    } ;

    callFetchApi(url, null, params, null) ;
}
