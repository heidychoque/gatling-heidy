package restfulapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class RestfulApiTest extends Simulation {
    String baseUrl = System.getProperty("baseUrl", "https://api.restful-api.dev/objects");

    // Define the data
    FeederBuilder.FileBased<Object> feeder = jsonFile("data/restful.json").circular();

    // Define the base URL and headers
    private HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // Define the scenario
    ScenarioBuilder scn = scenario("Restful API Test")
            .feed(feeder)
            .exec(http("Post object")
                    .post("")
                    .body(StringBody(session -> {
                        // Extract the JSON object from the feeder
                        String jsonString = session.getString("name");
                        Map<String, Object> dataMap = session.getMap("data");
                        Map<String, Object> jsonObject = new HashMap<>();
                        jsonObject.put("name", jsonString);
                        jsonObject.put("data", dataMap);

                        // Convert the JSON object to a string
                        try {
                            return new ObjectMapper().writeValueAsString(jsonObject);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .asJson()
                    .check(status().is(200))
                    .check(bodyString().saveAs("RESPONSE_BODY"))
                    .check(jmesPath("id").saveAs("ID_RESPONSE"))
            )
            .exec(session -> {
                String idResponse = session.getString("ID_RESPONSE");
                System.out.println("ID response: " + idResponse);
                return session;
            })
            .exec(http("Put object")
                    .put(session -> "/" + session.getString("ID_RESPONSE"))
                    .body(StringBody(session -> {
                        // Create JSON body for PUT request using captured ID
                        Map<String, Object> putJsonObject = new HashMap<>();
                        putJsonObject.put("name", "Updated Name");
                        putJsonObject.put("data", new HashMap<String, Object>() {{
                            put("year", 2020);
                            put("price", 1949.99);
                            put("CPU model", "Intel Core i10");
                            put("Hard disk size", "2 TB");
                        }});

                        // Convert the JSON object to a string
                        try {
                            return new ObjectMapper().writeValueAsString(putJsonObject);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .asJson()
                    .check(status().is(200)) // Adjust status code as necessary
                    .check(bodyString().saveAs("PUT_RESPONSE_BODY"))
            )
            .exec(session -> {
                String putResponseBody = session.getString("PUT_RESPONSE_BODY");
                System.out.println("PUT response body: " + putResponseBody);
                return session;
            })
            .exec(http("Get object")
                    .get(session -> "/" + session.getString("ID_RESPONSE"))
                    .check(status().is(200))
                    .check(bodyString().saveAs("GET_RESPONSE_BODY"))
            )
            .exec(session -> {
                String putName = session.getString("PUT_RESPONSE_BODY").split("\"name\":\"")[1].split("\"")[0];
                String putData = session.getString("PUT_RESPONSE_BODY").split("\"data\":")[1].split("}")[0] + "}";
                String getName = session.getString("GET_RESPONSE_BODY").split("\"name\":\"")[1].split("\"")[0];
                String getData = session.getString("GET_RESPONSE_BODY").split("\"data\":")[1].split("}")[0] + "}";

                System.out.println("PUT name: " + putName);
                System.out.println("GET name: " + getName);
                System.out.println("PUT data: " + putData);
                System.out.println("GET data: " + getData);

                assert putName.equals(getName) : "Name mismatch";
                assert putData.equals(getData) : "Data mismatch";

                return session;
            });

    {
        setUp(
                scn.injectClosed(
                        constantConcurrentUsers(10).during(Duration.ofSeconds(5))
                )
        ).protocols(httpProtocol);
    }
}