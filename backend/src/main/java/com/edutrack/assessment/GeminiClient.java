package com.edutrack.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Stateless Gemini Interactions API adapter. Student work is never retained as a server-side interaction. */
@Component
public class GeminiClient {
    public record Media(byte[] bytes,String mimeType) {}
    private static final Map<String,Object> EVALUATION_SCHEMA=Map.of(
        "type","object",
        "properties",Map.of(
            "suggestedScore",Map.of("type","number"),
            "maxScore",Map.of("type","number"),
            "evaluation",Map.of("type","string"),
            "strengths",Map.of("type","array","items",Map.of("type","string")),
            "weaknesses",Map.of("type","array","items",Map.of("type","string")),
            "mistakes",Map.of("type","array","items",Map.of("type","string")),
            "improvementSuggestions",Map.of("type","array","items",Map.of("type","string")),
            "confidence",Map.of("type","number"),
            "gradingReason",Map.of("type","string")),
        "required",List.of("suggestedScore","maxScore","evaluation","strengths","weaknesses",
            "mistakes","improvementSuggestions","confidence","gradingReason"));
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String key,model;
    private final int timeoutSeconds;
    public GeminiClient(ObjectMapper json,@Value("${GEMINI_API_KEY:}") String key,@Value("${GEMINI_MODEL:}") String model,
                        @Value("${GEMINI_TIMEOUT_SECONDS:60}") int timeoutSeconds) {
        this.json=json;this.key=key;this.model=model;this.timeoutSeconds=Math.max(5,Math.min(timeoutSeconds,120));
    }
    public String model() { return model; }
    public boolean configured() { return !key.isBlank() && !model.isBlank(); }
    public String generateText(String prompt) {return request(prompt);}
    public String analyzeImage(String prompt,String base64,String mimeType) {
        if(!List.of("image/jpeg","image/png","image/webp").contains(mimeType) || base64==null || base64.length()>12_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ảnh không hợp lệ hoặc quá lớn");
        return request(List.of(Map.of("type","text","text",prompt),Map.of("type","image","data",base64,"mime_type",mimeType)));
    }
    public String generateStructured(String prompt,List<Media> media) {
        if(media.size()>5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quá nhiều tệp bài làm");
        List<Map<String,Object>> input=new ArrayList<>();
        input.add(Map.of("type","text","text",prompt));
        for(Media item:media) {
            if(!List.of("image/jpeg","image/png","image/webp","application/pdf").contains(item.mimeType()) ||
                item.bytes()==null || item.bytes().length<1 || item.bytes().length>10_485_760)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tệp bài làm không hợp lệ");
            input.add(Map.of("type",item.mimeType().equals("application/pdf")?"document":"image",
                "data",Base64.getEncoder().encodeToString(item.bytes()),"mime_type",item.mimeType()));
        }
        return request(input,true);
    }
    private String request(Object input) {
        return request(input,false);
    }
    private String request(Object input,boolean structured) {
        if(key.isBlank() || model.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình Gemini");
        Map<String,Object> payload=structured ? Map.of("model",model,"input",input,"store",false,
            "response_format",Map.of("type","text","mime_type","application/json","schema",EVALUATION_SCHEMA)) :
            Map.of("model",model,"input",input,"store",false);
        try {
            byte[] body=json.writeValueAsBytes(payload);
            var request=HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta2/interactions"))
                .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type","application/json")
                .header("x-goog-api-key",key).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response=null;
            for(int attempt=0;attempt<2;attempt++) {
                try { response=http.send(request,HttpResponse.BodyHandlers.ofString()); }
                catch(java.net.http.HttpTimeoutException e) {
                    if(attempt==1) throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"Gemini quá thời gian",e);
                    continue;
                }
                catch(java.io.IOException e) {
                    if(attempt==1) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Không kết nối được Gemini",e);
                    continue;
                }
                if(response.statusCode()==429 || response.statusCode()>=500) {
                    if(attempt==0) continue;
                }
                break;
            }
            if(response==null || response.statusCode()/100!=2)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Gemini chưa trả về kết quả hợp lệ");
            JsonNode root=json.readTree(response.body());
            StringBuilder output=new StringBuilder();
            for(JsonNode step:root.path("steps")) if("model_output".equals(step.path("type").asText()))
                for(JsonNode content:step.path("content")) if("text".equals(content.path("type").asText())) output.append(content.path("text").asText());
            if(output.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Gemini không trả về nội dung");
            return output.toString();
        } catch(ResponseStatusException e) {throw e;}
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Yêu cầu Gemini bị gián đoạn",e);}
        catch(Exception e) {throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Không kết nối được Gemini",e);}
    }
}
