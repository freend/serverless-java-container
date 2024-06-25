# aws lambda spring boot container service
아마존 웹 서비스 중 lambda 에서 spring boot 를 docker container 로 만드는 예제 입니다.  
maven 과 gradle 에서 둘다 빌드되게 되어 있으며 Dockerfile 도 maven 용과 gradle 용으로 별도의 두개의 파일로 만들었습니다.
# spec
- Java 17
- Gradle 8.6
- maven 3.9.5
# maven
## Setting
빌드시 의존성 파일을 같이 컴파일 하기 위해서는 assembly-zip 을 사용하며 pom.xml 에 profile 추가,  
src/assembly/bin.xml 이라는 파일을 추가해 줘야 합니다.
### pom.xml
```xml
        <profile>
            <id>assembly-zip</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <build>
                <plugins>
                    <!-- don't build a jar, we'll use the classes dir -->
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-jar-plugin</artifactId>
                        <version>3.2.0</version>
                        <executions>
                            <execution>
                                <id>default-jar</id>
                                <phase>none</phase>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-install-plugin</artifactId>
                        <version>3.1.2</version>
                        <configuration>
                            <skip>true</skip>
                        </configuration>
                    </plugin>
                    <!-- select and copy only runtime dependencies to a temporary lib folder -->
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-dependency-plugin</artifactId>
                        <version>3.6.1</version>
                        <executions>
                            <execution>
                                <id>copy-dependencies</id>
                                <phase>package</phase>
                                <goals>
                                    <goal>copy-dependencies</goal>
                                </goals>
                                <configuration>
                                    <outputDirectory>${project.build.directory}${file.separator}lib</outputDirectory>
                                    <includeScope>runtime</includeScope>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-assembly-plugin</artifactId>
                        <version>3.7.1</version>
                        <executions>
                            <execution>
                                <id>zip-assembly</id>
                                <phase>package</phase>
                                <goals>
                                    <goal>single</goal>
                                </goals>
                                <configuration>
                                    <finalName>${project.artifactId}-${project.version}</finalName>
                                    <descriptors>
                                        <descriptor>src${file.separator}assembly${file.separator}bin.xml</descriptor>
                                    </descriptors>
                                    <attach>false</attach>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
```
### assembly.bin.xml
```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.0.0 http://maven.apache.org/xsd/assembly-2.0.0.xsd">
    <id>lambda-package</id>
    <formats>
        <format>zip</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <fileSets>
        <!-- copy runtime dependencies with some exclusions -->
        <fileSet>
            <directory>${project.build.directory}${file.separator}lib</directory>
            <outputDirectory>lib</outputDirectory>
            <excludes>
                <exclude>tomcat-embed*</exclude>
            </excludes>
        </fileSet>
        <!-- copy all classes -->
        <fileSet>
            <directory>${project.build.directory}${file.separator}classes</directory>
            <includes>
                <include>**</include>
            </includes>
            <outputDirectory>${file.separator}</outputDirectory>
        </fileSet>
    </fileSets>
</assembly>
```
## Build
```shell
mvn package
```
위 명령으로 빌드를 하면 다음과 같은 tree 구조가 됩니다.  
```
target
├── archive-tmp
├── classes
├── generated-sources
├── generated-test-sources
├── lib
├── maven-status
├── serverless-sample-1.0-SNAPSHOT-lambda-package.zip
└── test-classes
```
만약 Docker 대신 jar 나 zip 을 사용하고 싶으면 위의 zip 파일을 업데이트 하셔서 사용하셔도 됩니다.  
여기서 docker build 에 필요한 폴더는 classes, lib 입니다.   
그럼 Dockerfile-maven 을 보도록 하겠습니다.  
```dockerfile
FROM public.ecr.aws/lambda/java:17

# Copy function code and runtime dependencies from Maven layout
COPY target/classes ${LAMBDA_TASK_ROOT}
COPY target/lib/* ${LAMBDA_TASK_ROOT}/lib/

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "com.example.StreamLambdaHandler::handleRequest" ]
```
docker file 의 내용은 amazon 에서 제공하는 java 이미지를 이용해서 빌드된 곳의 폴더를 복사한 후  
`handleRequest`를 연결하면 됩니다.
# Gradle
## Setting
gradle 에서는 assembly 와 같은 기능을 task 로 추가해서 사용할 수 있습니다.  
build.gradle 에 다음 내용을 추가해 줍니다.
```groovy
// add dependencies into lib
tasks.register('copyDependencies', Copy) {
    into layout.buildDirectory.dir("dependencies").get().asFile
    from configurations.runtimeClasspath
}
```
또한 gradle 8 버전에서는 빌드시 자바 버전을 21로 빌드하므로 버전을 맞춰줘야 합니다.
```groovy
java.sourceCompatibility = '17'
```
## Build
```shell
gradle build copyDependencies
```
위의 명령은 gradle 을 빌드하면서 build.gradle 에 있는 copyDependencies 를 실행하라는 명령입니다.  
그러면 아래와 같은 tree 구조가 됩니다.
```
build
├── classes
├── dependencies
├── generated
├── libs
└── tmp
```
여기서 docker build 에 필요한 폴더는 classes, dependencies 입니다.   
그럼 Dockerfile-gradle 을 보도록 하겠습니다.  
```dockerfile
FROM public.ecr.aws/lambda/java:17

# Copy function code and runtime dependencies from Gradle layout
COPY build/classes/java/main ${LAMBDA_TASK_ROOT}
COPY build/dependencies/* ${LAMBDA_TASK_ROOT}/lib/

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "com.example.StreamLambdaHandler::handleRequest" ]
```
maven 의 build 명령과 크게 다른 내용이 없으므로 설명은 생략하겠습니다.
# Docker
## Before
베이스 이미지로 `public.ecr.aws/lambda/java:17` 를 필요로 합니다.  
해당 이미지를 가져오기 위해서는 아래 명령으로 aws의 public ecr에 접속해야 합니다.
```shell
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
```
저 명령이 실행되지 않으면 이미지가 없다는 에러 메세지를 보실 수 있습니다.
## Build
우선 도커로 빌드되는 파일을 편의상 Dockerfile 이라고 통일하겠습니다.  
lambda 함수의 아키텍처가 x86_64 혹은 arm64 인지에 따라 2가지 빌드 방법이 존재합니다.
```shell
# x86_64
docker build --platform linux/amd64 -t lambda/lambda-docker-java .
# arm 64
docker build --platform linux/arm64 -t lambda/lambda-docker-java .
```
잘 보면 거의 동일한 명령이라 햇갈릴 수 있으며 lambda function 의 아키텍처와 Dockerfile 의 platform 이 다르면 오류가 발생하니 주의하시기 바랍니다.
## Run
이렇게 docker image 로 만들면 local 에서 docker 를 실행해서 작동 유무를 확인 할 수 있습니다.  
실행 명령을 확인해 보도록 하겠습니다.  
여기서도 빌드할 때의 platform 과 run 할 때의 platform 을 맞춰 주시기 바랍니다.
```shell
docker run --platform linux/amd64 -p 9000:8080 --rm lambda/lambda-docker-java:latest
```
여기서는 도커를 종료하면 자동으로 컨테이너를 삭제하는 명령이 추가되었으며 포트번호는 이미 지정되어 있습니다.  
이제 포스트 맨을 실행한 후 아래 내용을 맞춰서 입력해 주시기 바랍니다.
```shell
# method
POST
# url
localhost:9000/2015-03-31/functions/function/invocations
```
그리고 Body 의 raw 에 JSON 으로 아래의 내용을 복사해 줍니다.  

```json
{
  "body": "eyJ0ZXN0IjoiYm9keSJ9",
  "resource": "/{proxy+}",
  "path": "/",
  "httpMethod": "POST",
  "isBase64Encoded": true,
  "queryStringParameters": {
    "foo": "bar"
  },
  "multiValueQueryStringParameters": {
    "foo": [
      "bar"
    ]
  },
  "pathParameters": {
    "proxy": "/path/to/resource"
  },
  "stageVariables": {
    "baz": "qux"
  },
  "headers": {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Encoding": "gzip, deflate, sdch",
    "Accept-Language": "en-US,en;q=0.8",
    "Cache-Control": "max-age=0",
    "CloudFront-Forwarded-Proto": "https",
    "CloudFront-Is-Desktop-Viewer": "true",
    "CloudFront-Is-Mobile-Viewer": "false",
    "CloudFront-Is-SmartTV-Viewer": "false",
    "CloudFront-Is-Tablet-Viewer": "false",
    "CloudFront-Viewer-Country": "US",
    "Host": "1234567890.execute-api.us-east-1.amazonaws.com",
    "Upgrade-Insecure-Requests": "1",
    "User-Agent": "Custom User Agent String",
    "Via": "1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)",
    "X-Amz-Cf-Id": "cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==",
    "X-Forwarded-For": "127.0.0.1, 127.0.0.2",
    "X-Forwarded-Port": "443",
    "X-Forwarded-Proto": "https"
  },
  "multiValueHeaders": {
    "Accept": [
      "appliation/json,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    ],
    "Accept-Encoding": [
      "gzip, deflate, sdch"
    ],
    "Accept-Language": [
      "en-US,en;q=0.8"
    ],
    "Cache-Control": [
      "max-age=0"
    ],
    "CloudFront-Forwarded-Proto": [
      "https"
    ],
    "CloudFront-Is-Desktop-Viewer": [
      "true"
    ],
    "CloudFront-Is-Mobile-Viewer": [
      "false"
    ],
    "CloudFront-Is-SmartTV-Viewer": [
      "false"
    ],
    "CloudFront-Is-Tablet-Viewer": [
      "false"
    ],
    "CloudFront-Viewer-Country": [
      "US"
    ],
    "Host": [
      "0123456789.execute-api.us-east-1.amazonaws.com"
    ],
    "Upgrade-Insecure-Requests": [
      "1"
    ],
    "User-Agent": [
      "Custom User Agent String"
    ],
    "Via": [
      "1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)"
    ],
    "X-Amz-Cf-Id": [
      "cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA=="
    ],
    "X-Forwarded-For": [
      "127.0.0.1, 127.0.0.2"
    ],
    "X-Forwarded-Port": [
      "443"
    ],
    "X-Forwarded-Proto": [
      "https"
    ]
  },
  "requestContext": {
    "accountId": "123456789012",
    "resourceId": "123456",
    "stage": "prod",
    "requestId": "c6af9ac6-7b61-11e6-9a41-93e8deadbeef",
    "requestTime": "09/Apr/2015:12:34:56 +0000",
    "requestTimeEpoch": 1428582896000,
    "identity": {
      "cognitoIdentityPoolId": null,
      "accountId": null,
      "cognitoIdentityId": null,
      "caller": null,
      "accessKey": null,
      "sourceIp": "127.0.0.1",
      "cognitoAuthenticationType": null,
      "cognitoAuthenticationProvider": null,
      "userArn": null,
      "userAgent": "Custom User Agent String",
      "user": null
    },
    "path": "/prod/path/to/resource",
    "resourcePath": "/{proxy+}",
    "httpMethod": "POST",
    "apiId": "1234567890",
    "protocol": "HTTP/1.1"
  }
}
```
아래 내용중에서 우리가 변경할 부분은 맨 위의 `httpMethod`와 `path`입니다. 이 두개를 spring boot application 의  
api 에 맞춰 주시면 됩니다. 그리고 실행하면 그 밑에 화면과 같은 결과가 나옵니다.
```text
{"statusCode":200,"multiValueHeaders":{"Content-Length":["9"],"Content-Type":["appliation/json; charset=UTF-8"]},"body":"Post post","isBase64Encoded":false}
```
# AWS gateway에서 함수 호출하기

문서 준비중 입니다.

# URL 연결하기

문서 준비중 입니다.

# 기타

## Gradle에서 빌드 후 zip file로 사용하기

maven에서는 `mvn package` 만 실행해서 zip file이 만들어지면 그것을 lambda 함수에 upload해서 사용할 수 있다고 했습니다. 그런데 gradle은 현재까지 gradle 명령으로 zip을 만들어서 lambda 함수에 정상작동하는걸 만들지 못했습니다.

그래서 다음과 같이 일부 수동작업을 거치면 됩니다.

```bash
# 이 명령으로 의존성 파일 jar와 컴파일된 classes를 만들어 줍니다.
gradle build copyDependencies
# 그후 build/dependencies 폴더를 build/classes/java/main 위치로 이동하며 폴더이름을 변경해 줍니다.
mv build/dependencies build/classes/java/main/lib 
# classes/java/main에서 zip file로 압축을 해주면 됩니다.
cd build/classes/java/main
zip sample.zip -r *
```