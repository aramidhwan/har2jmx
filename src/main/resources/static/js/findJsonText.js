document.addEventListener("click", (event) => {
    const pathLink = event.target.closest("a.copyPath");
    const jsonLink = event.target.closest("a.copyJson");

    // Path를 클립보드에 붙여넣기
    if (pathLink) {
        event.preventDefault();
        const textToCopy = pathLink.innerText;

        navigator.clipboard.writeText(textToCopy)
            .then(() => alert("PATH가 클립보드에 복사되었습니다:\n\n" + textToCopy))
            .catch(err => alert("복사 실패: " + err));

    // Response JSON Data를 클립보드에 붙여넣기
    } else if (jsonLink) {
       event.preventDefault();
       fnCopyJson(event);
    // JSON Extractor 추가하기, [추가하기] 버튼
    } else if ( event.target.id == "btnAddExtractor" ) {
        const tr = event.target.closest("tr"); // 클릭한 버튼이 포함된 가장 가까운 tr
        const btnAddExtractorCell = tr.querySelector(".btnAddExtractor"); // 해당 tr 안에서 button를 가진 td 찾기
        const tcNmCell = tr.querySelector(".tcNm"); // 해당 tr 안에서 tcNm 클래스를 가진 td 찾기
        const pathCell = tr.querySelector(".path"); // 해당 tr 안에서 path 클래스를 가진 td 찾기
        if (btnAddExtractorCell && tcNmCell && pathCell) {
            fnAddJsonExtractor(tcNmCell.innerText /* tc name */, pathCell.innerText /* path */, event.target.value /* keyword */) ;
            document.querySelectorAll("td.btnAddExtractor").forEach(td => {
                const button = td.querySelector('button');
                if (button) {
                    button.remove();
                }
            });
            btnAddExtractorCell.innerText = '추가됨' ;

            // JSON Extractor를 추가했던 keyword를 저장해두기
            const jsonCell = tr.querySelector(".jsonData"); // 해당 tr 안에서 jsonData 클래스를 가진 td 찾기
            let keywordKeyNm = null ;
            if (jsonCell) {
                keywordKeyNm = findJsonPathsByValueOrKey(jsonCell.innerText, event.target.value /* keyword */, false /* needAlert */) ;
                keywordKeyNm = keywordKeyNm.split(/\r?\n/)[0];  /* '$.data[0].projectUid' */
                keywordKeyNm = keywordKeyNm.substring(keywordKeyNm.lastIndexOf('.') + 1);   /* 'projectUid' */
            }

            jsonExtractorAddedKeywords.set(event.target.value /* keyword */, pathCell.innerText /* path */ + '^' + keywordKeyNm);
        } else {
            alert("jsonData(class) 셀을 찾을 수 없습니다.");
        }
    } else if ( event.target.id == "btnCopyJmeter" ) {
        const tr = event.target.closest("tr"); // 클릭한 버튼이 포함된 가장 가까운 tr
        const jsonCell = tr.querySelector(".jsonData"); // 해당 tr 안에서 jsonData 클래스를 가진 td 찾기
        if (jsonCell) {
            findJsonPathsByValueOrKey(jsonCell.innerText, event.target.value /* keyword */, true /* needAlert */) ;
        }
    } else {
//        console.log("# tagName : " + event.target.tagName) ;
//        console.log("# id : " + event.target.id) ;
    }
});
document.addEventListener("DOMContentLoaded", () => {
    // -------------------------------
    // [Modal] 창의 [Path]의 숫자부분 하이라이트 처리
    // -------------------------------
    const pathCells = document.querySelectorAll(".path-cell");
    pathCells.forEach(cell => {
        const original = cell.textContent;
        const highlighted = original.replace(/(\d+)/g, '<span class="text-danger fw-bold">$1</span>');
        cell.innerHTML = highlighted;
    });

    // keyword input에서 엔터 시 submit
    document.getElementById("findJsonForm").addEventListener("submit", function(e) {
        e.preventDefault(); // 기본 submit 방지
        fnLoadFindData() ;
    });

    // 모달 닫힐 때 이벤트 감지 ('hidden.bs.modal' 이벤트는 모달이 완전히 닫힌 후 발생)
    const convertJMXResultModal = document.getElementById('convertJMXResultModal');
    convertJMXResultModal.addEventListener('hidden.bs.modal', function () {
        fnCloseConvertJMXResultModal() ;
    });

});

// JSON Extractor를 추가했던 keyword를 배열로 저장
let jsonExtractorAddedKeywords = new Map();

function fnCopyJson(event) {
    event.preventDefault();

    const aLink = event.target; // 클릭된 COPY 버튼
    const tr = aLink.closest("tr"); // 해당 버튼이 포함된 가장 가까운 tr
    const jsonCell = tr.querySelector(".jsonData"); // tr 내의 .jsonData 셀 찾기

    if (jsonCell) {
        // 클립보드에 복사
        navigator.clipboard.writeText(jsonCell.innerText)
            .then(() => {
                alert("JSON 텍스트가 클립보드에 복사되었습니다.");
            })
            .catch(err => {
                console.error("클립보드 복사 실패:", err);
                alert("복사에 실패했습니다.");
            });
    } else {
        alert("JSON TEXT 셀을 찾을 수 없습니다.");
    }
}

// 함수 노출: 전역에서 호출 가능하도록 window 객체에 할당
function fnAddJsonExtractor(tcNm, path, keyword) {
    const url = '/api/addJsonExtractor' ;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // 데이터 형식 설정
        },
        redirect: "follow", // follow, error, or manual
        body: JSON.stringify({
            "targetPath4JsonExtractor": path,
            "prjNm": document.getElementById("prjNm").innerText,
            "tcNm": tcNm,
            "jmxFileWithPath": document.getElementById("jmxFileWithPath").dataset.value,
            "keyword": keyword
        })
    } ;

    callFetchApi(url, null, params, "fnAddJsonExtractor_CALLBACK") ;
}

function fnAddJsonExtractor_CALLBACK(json) {
}

// 함수 노출: 전역에서 호출 가능하도록 window 객체에 할당
function fnLoadFindData() {
//    event.preventDefault();  // <-- 이거 추가!
    const keyword = document.getElementById("keyword").value;

    const url = '/api/findJsonData' ;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // 데이터 형식 설정
        },
        redirect: "follow", // follow, error, or manual
        body: JSON.stringify({
            "keyword": keyword
        })
    } ;

    callFetchApi(url, null, params, "fnDisplayFindData") ;
}

function fnDisplayFindData(json) {
    fnCreateTable_findKeyword() ;

    if ( json.data.length == 0 ) {
        fnCreateFindTR(null) ;
    } else {
        json.data.forEach(responseDataDto => {
            fnCreateFindTR(responseDataDto) ;
        }) ;
        highlightKeyword(keyword.value, "tableContainer") ;
    }
}

function fnCreateFindTR(responseDataDto) {
    const tblData = document.getElementById("tblData-"+keyword.value);
    tbody = tblData.querySelector("tbody"); // tbody 요소를 선택

    var newRow   = tbody.insertRow(tbody.rows.length);
    if ( responseDataDto == null ) {
        var newCell1 = newRow.insertCell(0);
        newCell1.classList.add("cssAlignCenter") ;
        newCell1.colSpan = tblData.querySelectorAll("th").length; ; ;
        newCell1.innerText = "응답 JSON에 해당 keyword가 없습니다." ;
        return ;
    }

    var newCell1 = newRow.insertCell();    // ID
    newCell1.classList.add("cssAlignCenter") ;
    newCell1.classList.add("tcNm") ;
    newCell1.innerText = responseDataDto.tcNm ;

    var newCell2 = newRow.insertCell();    // PATH
    newCell2.classList.add("path") ;
    newCell2.innerHTML = `<a href="#" class="copyPath">${responseDataDto.path}</a>`;

    var keywordValue = document.getElementById("keyword").value;
    var targetTcNm = document.getElementById("tcNm").innerText.split(" ")[1];
    var newCell3 = newRow.insertCell(); // Response JSON
    newCell3.classList.add("cssAlignCenter") ;
    newCell3.classList.add("btnAddExtractor") ;
    if ( targetTcNm == responseDataDto.tcNm ) {
        var btnText = '' ;
        if (jsonExtractorAddedKeywords.has(keywordValue) ) {
            if (jsonExtractorAddedKeywords.get(keywordValue).split("^")[0] == responseDataDto.path ) {
                newCell3.innerText = `추가됨`;
            } else {
                btnText = '또 추가하기' ;
            }
        } else {
            btnText = '추가하기' ;
        }

        if ( btnText != '' ) {
            newCell3.innerHTML = `<button id='btnAddExtractor' value='${keywordValue}'>`+btnText+`</button>`;
        }

    } else {
        newCell3.innerText = `다른화면`;
    }

    var newCell4 = newRow.insertCell(); // Response JSON
    newCell4.innerHTML = `<button id='btnCopyJmeter' value='${keywordValue}'>클립보드</button>`;

    var newCell5 = newRow.insertCell();    // Response JSON
    newCell5.classList.add("jsonData") ;
    newCell5.innerHTML = `<a href="#" class="copyJson">${responseDataDto.text}</a>`;
}

function fnClearAllFindTables() {
//    event.preventDefault();
    document.getElementById("tableContainer").innerHTML = "";
}

function fnClearAllDB(event) {
//    event.preventDefault();  // <-- 이거 추가!
//    const keyword = document.getElementById("keyword").value;
//
//    const url = '/api/findJsonData' ;
//    const params = {
//        method: 'POST',
//        headers: {
//            'Content-Type': 'application/json', // 데이터 형식 설정
//        },
//        redirect: "follow", // follow, error, or manual
//        body: JSON.stringify({
//            "keyword": keyword
//        })
//    } ;
//
//    callFetchApi(url, null, params, "fnDisplayFindData") ;
}

function fnCreateTable_findKeyword() {
    const keyword = document.getElementById("keyword")
    // 테이블 컨테이너 (기존 테이블이 있을 경우 제거)
    let container = document.getElementById("tableContainer");
//    container.innerHTML = ""; // 기존 테이블 제거 후 새로 생성

    // 기존 span이 있다면 삭제 (중복 방지)
    const existingSpan = document.getElementById("divTblTitle-"+keyword.value);
    if (existingSpan) {
        existingSpan.remove();
    }

    // <span> 요소 생성
    let span = document.createElement("span");
    span.id = "divTblTitle-"+keyword.value; // 동적 ID 설정
    span.innerText = '['+keyword.value+'] 결과데이터'; // 텍스트 설정

    // 기존 table이 있다면 삭제 (중복 방지)
    const existingTable = document.getElementById("tblData-"+keyword.value);
    if (existingTable) {
        existingTable.remove();
    }
    // <table> 요소 생성
    let table = document.createElement("table");
    table.id = "tblData-"+keyword.value ;
    table.classList.add("table", "table-striped", "table-bordered");
//    table.style.maxHeight = "150px";
    table.style.overflowY = "scroll";

    // <thead> 생성
    let thead = document.createElement("thead");
    let headerRow = document.createElement("tr");
    headerRow.classList.add("table-warning");

    // 테이블 헤더 컬럼 데이터
    const headers = [
        { text: "화면명", width: "40px" },
        { text: "PATH", width: "200px" },
        { text: "JSON Extractor", width: "50px" },
        { text: "변수확인", width: "50px" },
        { text: "JSON", width: "300px" }
    ];

    // <th> 요소 동적 생성
    headers.forEach(header => {
        let th = document.createElement("th");
        th.classList.add("cssAlignCenter");
        th.style.width = header.width;
        th.innerText = header.text;
        headerRow.appendChild(th);
    });

    // <thead>에 <tr> 추가
    thead.appendChild(headerRow);
    table.appendChild(thead);

    // <tbody> 추가 (데이터가 추가될 영역)
    let tbody = document.createElement("tbody");
    table.appendChild(tbody);

    // 컨테이너에 테이블 추가 (상단에 추가)
    container.insertBefore(table, container.firstChild);

    // <tableContainer>에 <span> 추가 (상단에 추가)
    container.insertBefore(span, container.firstChild);

}

// -------------------------------
// JMX 파일에서 keyword를 변수로 대체 (HTTPSamplerProxy, Header, Path 등등)
// -------------------------------
function fnConvertJMX() {

    const keywordValue = document.getElementById("keyword").value;
    const prjNm = document.getElementById("prjNm").innerText ;
    const targetTcNm = document.getElementById("tcNm").innerText.split(" ")[1];
    let keywordKeyNm = null ;
    if ( jsonExtractorAddedKeywords.has(keywordValue) ) {
        keywordKeyNm = jsonExtractorAddedKeywords.get(keywordValue).split("^")[1] ;
    } else {
        const userInput = prompt("⚠️ JSON Extractor를 먼저 추가하거나, keyword("+keywordValue+")를 추출한 변수명을 입력하세요. ex: projectUid\n\n'" + keywordValue + "'를 입력한 변수명으로 대체합니다.\nex: projectUid → ${projectUid}");

        if (userInput !== null && userInput.trim() !== "") {
            keywordKeyNm = userInput ;
        } else {
//            alert("입력이 취소되었거나 빈 값입니다.");
            return false ;
        }
//        alert("⚠️ 먼저 JSON Extractor를 추가하십시요.") ;
    }

    const url = '/api/convertJMX' ;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // 데이터 형식 설정
        },
        redirect: "follow", // follow, error, or manual
        body: JSON.stringify({
            "keywordKeyNm": keywordKeyNm,
            "prjNm": prjNm,
            "tcNm": targetTcNm,
            "jmxFileWithPath": document.getElementById("jmxFileWithPath").dataset.value,
            "keyword": keywordValue
        })
    } ;

    callFetchApi(url, null, params, "fnConvertJMX_CALLBACK") ;
}

function fnConvertJMX_CALLBACK(jsonData) {

    let cellIndex = 0 ;
    const tblSamplerList = document.getElementById("tblSamplerList");

    const tbody = tblSamplerList.querySelector("tbody"); // tbody 요소를 선택
    tbody.innerHTML = ""; // tbody 내 모든 내용을 삭제

    for (let inx = 0; inx < jsonData.data.length; inx++) {
        cellIndex = 0 ;
        const ResponseJsonDto = jsonData.data[inx];

        // 선택된 메뉴 내용 표시
        var newRow = tbody.insertRow(tbody.rows.length);

        var newCell1 = newRow.insertCell(cellIndex++);    // #
        newCell1.classList.add("path-cell") ;
        newCell1.innerText = ResponseJsonDto.path ;
    }

    // -------------------------------
    // [Modal] 창의 [Path]의 숫자부분 하이라이트 처리
    // -------------------------------
    const pathCells = document.querySelectorAll(".path-cell");
    pathCells.forEach(cell => {
        const original = cell.textContent;
        const highlighted = original.replace(/(\d+)/g, '<span class="text-danger fw-bold">$1</span>');
        cell.innerHTML = highlighted;
    });
    // -------------------------------
    // [Modal] 창의 [Path]의 ${변수명} 하이라이트 처리
    // -------------------------------
    const pattern = /\$\{.*?\}/g;
    pathCells.forEach(cell => {
        const original = cell.textContent;
        if (pattern.test(original)) {
            const highlighted = original.replace(pattern, match => `<span class="text-primary fw-bold">${match}</span>`);
            cell.innerHTML = highlighted;
        }
    });
}

function fnConvertJMX_CALLBACK_bakup(json) {
    const tblData = document.getElementById("tblConvertJMXResult");
    tbody = tblData.querySelector("tbody"); // tbody 요소를 선택

    let msgs = JSON.parse(json.data);  // 문자열을 배열 객체로 변환

    if ( json.data == null || msgs.length == 0 ) {
        let newRow   = tbody.insertRow(tbody.rows.length);
        let newCell1 = newRow.insertCell();
        newCell1.innerText = "변경 내역이 없습니다." ;
    } else {
        msgs.forEach(msg => {
            // 새 행(Row) 추가
            let newRow = tbody.insertRow(tbody.rows.length);
            // 새 행(Row)에 Cell 추가
            let newCell1 = newRow.insertCell();    // ID
    //        newCell1.classList.add("cssAlignCenter") ;
            newCell1.innerText = msg.msg ;
        });

    }

    var myModal = new bootstrap.Modal(document.getElementById('convertJMXResultModal'));
    myModal.show();
    // .modal-dialog의 width를 동적으로 설정
    const modalDialog = document.querySelector('#convertJMXResultModal .modal-dialog');
    const modalContent = document.querySelector('#convertJMXResultModal .modal-content');
    // 내용물의 너비 측정
    const contentWidth = modalContent.scrollWidth;
    // dialog 너비 설정
    modalDialog.style.width = contentWidth + 'px';
}

// 모달창 닫을 때 내용 삭제
function fnCloseConvertJMXResultModal() {
    const tblData = document.getElementById("tblConvertJMXResult");
    tbody = tblData.querySelector("tbody"); // tbody 요소를 선택
    tbody.innerHTML = "" ;
}


// -------------------------------
// JMX 파일에 TestFragment 달기
// -------------------------------
function fnAddTestFragment() {

    const keywordValue = document.getElementById("keyword").value;
    const prjNm = document.getElementById("prjNm").innerText ;
    const targetTcNm = document.getElementById("tcNm").innerText.split(" ")[1];

    const url = '/api/addTestFragment' ;
    const params = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // 데이터 형식 설정
        },
        redirect: "follow", // follow, error, or manual
        body: JSON.stringify({
            ".dataset.value": document.getElementById(".dataset.value").dataset.value
        })
    } ;

    callFetchApi(url, null, params, null) ;
}
