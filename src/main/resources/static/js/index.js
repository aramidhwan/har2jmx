document.addEventListener("DOMContentLoaded", () => {
    const prjNm = document.getElementById("prjNm");
    const jmxFileNm = document.getElementById("jmxFileNm");
    const DTCName = document.getElementById("DTCName");

    // 초기 설정
    updateJmxFileName();

    // 실시간 반영
    prjNm.addEventListener("input", updateJmxFileName);
    DTCName.addEventListener("input", updateJmxFileName);
});

function updateJmxFileName() {
    const prjNmValue = prjNm.value.trim();
    const DTCNameValue = DTCName.value.trim();

    if (prjNmValue || DTCNameValue) {
        jmxFileNm.value = `D:/apache-jmeter-5.6.3/bin/scripts/[Har2Jmx]${prjNmValue}-${DTCNameValue}.jmx`;
    } else {
        jmxFileNm.value = "";
    }
}
