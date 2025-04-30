# HAR 파일 Parsor (Har to Jmx)

# 서비스 제공 기능
1. 웹 브라우저의 개발자 도구(F12)에서 화면을 Record하여 저장한 .har 파일을 자동으로 JMeter 파일(jmx)로 변환해 준다..
2. JWT Token Key Name을 입력하면 자동으로 로그인 url에서 JWT 값을 저장한다.
3. JWT가 존재할 경우 이후 모든 url header에 Authorization : Bearer + jwtToken 을 추가해 준다.
4. JSON 응답의 검증을 자동으로 수행한다. (http_code == 200 등)
5. 특정 keyword를 찾아 JSON Extractor를 추가하고 변수로 대체해 준다. (ex : 384 -> ${projectUid})
6. "로그인" url은 Once Only Controller & TestFragment로 처리하여 효율을 극대화 한다.
<br>

# 메인 화면

http://localhost:81

![Image](https://github-production-user-asset-6210df.s3.amazonaws.com/20077391/438502634-09a1582b-d5c2-4875-b7e7-d87959cee354.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20250429%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20250429T005441Z&X-Amz-Expires=300&X-Amz-Signature=b4f2c6c5581dc8ed218cc982c39fad102a805eec5f5156ee55f8338a890c43fe&X-Amz-SignedHeaders=host)

<br>[입력항목 설명]<br>

● 프로젝트명 : 프로젝트명칭 & 공통 Domain:포트<br>
● HAR 파일 선택 : JMX로 변환할 HAR 파일 선택<br>
● HTTPSampler 제외 확장자 : JMeter의 HTTP SAMPLER PROXY 제외할 대상<br>
● Seq No & 화면명 : 시퀀스 번호 & har 파일의 화면명<br>
● JWT Token Key Name : 로그인후 응답 JSON에서 JWT의 Key 이름<br>
● JSON 응답 검증 코드 : Response json 데이터에서 성공 여부를 검증할 코드(ex : biz_code == 0)<br>
● 응답검증 제외 URL : 응답 검증 적용을 배제할 url<br>
● 생성 JMX File : 생성될 JMX 파일 
<br><br>

# 실행 화면
![실행화면](https://github-production-user-asset-6210df.s3.amazonaws.com/20077391/438502528-570503b0-e2f3-4807-810c-47c7db743829.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20250429%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20250429T005551Z&X-Amz-Expires=300&X-Amz-Signature=270e833ae9aa655f5e3cf6e286e981075b273b21767fe4d5376efebcde283573&X-Amz-SignedHeaders=host)

# 생성된 JMX
![생성된 JMX](https://github-production-user-asset-6210df.s3.amazonaws.com/20077391/438501921-9745a3a6-c583-4723-97fe-a6f9f8ebc33c.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20250429%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20250429T004313Z&X-Amz-Expires=300&X-Amz-Signature=f275e77738b1b205ba399fab4bdff051728b7fc7f2fc76d8b3dc352aa8792233&X-Amz-SignedHeaders=host)
