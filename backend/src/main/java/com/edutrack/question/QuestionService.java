package com.edutrack.question;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {
    public enum Type { SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, ESSAY, MATCHING, ORDERING, FILL_IN_THE_BLANK }
    public enum Difficulty { EASY, MEDIUM, HARD }
    public record Input(Type type, String subject, String gradeLevel, String topic, String chapter, String lesson,
                        Difficulty difficulty, String body, JsonNode content, JsonNode answerKey,
                        String explanation, String rubric, BigDecimal defaultPoints) {}
    public record View(UUID id, Type type, String subject, String gradeLevel, String topic, String chapter,
                       String lesson, Difficulty difficulty, String body, JsonNode content, JsonNode answerKey,
                       String explanation, String rubric, BigDecimal defaultPoints, long version) {}
    public record Page(List<View> items, long total, int page, int size) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public QuestionService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }

    @Transactional
    public View create(Input input, UUID actorId) {
        validate(input);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO questions(id,type,subject,grade_level,topic,chapter,lesson,difficulty,body,content,answer_key,explanation,rubric,default_points,created_by) VALUES (?,?,?,?,?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?,?,?,?)",
            id,input.type().name(),trim(input.subject()),trimOrNull(input.gradeLevel()),trim(input.topic()),trimOrNull(input.chapter()),
            trimOrNull(input.lesson()),input.difficulty().name(),trim(input.body()),input.content().toString(),input.answerKey().toString(),
            trimOrNull(input.explanation()),trimOrNull(input.rubric()),input.defaultPoints(),actorId);
        return get(id);
    }

    @Transactional(readOnly=true)
    public View get(UUID id) {
        return db.query(SELECT + " WHERE id=? AND deleted_at IS NULL",this::map,id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy câu hỏi"));
    }

    @Transactional(readOnly=true)
    public Page list(String subject, String topic, Type type, Difficulty difficulty, int page, int size) {
        if(page<0 || size<1 || size>100) throw bad("Phân trang không hợp lệ");
        var where=new StringBuilder(" WHERE deleted_at IS NULL");
        var args=new ArrayList<Object>();
        if(subject!=null && !subject.isBlank()) { where.append(" AND lower(subject)=lower(?)");args.add(subject.trim()); }
        if(topic!=null && !topic.isBlank()) { where.append(" AND lower(topic)=lower(?)");args.add(topic.trim()); }
        if(type!=null) { where.append(" AND type=?");args.add(type.name()); }
        if(difficulty!=null) { where.append(" AND difficulty=?");args.add(difficulty.name()); }
        Long total=db.queryForObject("SELECT count(*) FROM questions"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        return new Page(db.query(SELECT+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::map,args.toArray()),
            total==null?0:total,page,size);
    }

    @Transactional
    public View update(UUID id, Input input, long expectedVersion) {
        validate(input);
        int changed=db.update("UPDATE questions SET type=?,subject=?,grade_level=?,topic=?,chapter=?,lesson=?,difficulty=?,body=?,content=cast(? as jsonb),answer_key=cast(? as jsonb),explanation=?,rubric=?,default_points=?,updated_at=now(),version=version+1 WHERE id=? AND version=? AND deleted_at IS NULL",
            input.type().name(),trim(input.subject()),trimOrNull(input.gradeLevel()),trim(input.topic()),trimOrNull(input.chapter()),
            trimOrNull(input.lesson()),input.difficulty().name(),trim(input.body()),input.content().toString(),input.answerKey().toString(),
            trimOrNull(input.explanation()),trimOrNull(input.rubric()),input.defaultPoints(),id,expectedVersion);
        if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Câu hỏi đã thay đổi hoặc không tồn tại");
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long expectedVersion) {
        if(db.update("UPDATE questions SET deleted_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=? AND deleted_at IS NULL",
            id,expectedVersion)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Câu hỏi đã thay đổi hoặc không tồn tại");
    }

    void validate(Input q) {
        if(q==null || q.type()==null || q.difficulty()==null || blank(q.subject()) || blank(q.topic()) || blank(q.body()) ||
            q.subject().length()>120 || q.topic().length()>160 || q.body().length()>20000 ||
            q.defaultPoints()==null || q.defaultPoints().compareTo(BigDecimal.ZERO)<=0 || q.defaultPoints().compareTo(new BigDecimal("100"))>0 ||
            q.content()==null || !q.content().isObject() || q.answerKey()==null || !q.answerKey().isObject()) throw bad("Dữ liệu câu hỏi không hợp lệ");
        JsonNode a=q.answerKey(),c=q.content();
        JsonNode policy=a.path("gradingPolicy");
        if(!policy.isMissingNode()) {
            String mode=policy.path("mode").asText("EXACT");
            if(!policy.isObject() || (!"EXACT".equals(mode) && !"PARTIAL".equals(mode)) ||
                (policy.has("caseSensitive") && !policy.path("caseSensitive").isBoolean()) ||
                (policy.has("normalizeWhitespace") && !policy.path("normalizeWhitespace").isBoolean()) ||
                ("PARTIAL".equals(mode) && q.type()!=Type.MULTIPLE_CHOICE && q.type()!=Type.MATCHING &&
                    q.type()!=Type.ORDERING && q.type()!=Type.FILL_IN_THE_BLANK)) throw bad("Quy tắc chấm không hợp lệ");
        }
        switch(q.type()) {
            case SINGLE_CHOICE -> {
                Set<String> ids=ids(c.path("options"),2,20);
                if(!ids.contains(a.path("correctId").asText())) throw bad("Đáp án không thuộc lựa chọn");
            }
            case MULTIPLE_CHOICE -> {
                Set<String> ids=ids(c.path("options"),2,20);
                Set<String> correct=textArray(a.path("correctIds"),1,ids.size());
                if(!ids.containsAll(correct)) throw bad("Đáp án không thuộc lựa chọn");
            }
            case TRUE_FALSE -> { if(!a.path("correct").isBoolean()) throw bad("Thiếu đáp án đúng/sai"); }
            case SHORT_ANSWER -> {
                textArray(a.path("acceptedAnswers"),1,30);
                if(a.has("caseSensitive") && !a.path("caseSensitive").isBoolean()) throw bad("Cấu hình chữ hoa/thường không hợp lệ");
            }
            case ESSAY -> {
                if(blank(q.rubric()) || blank(a.path("referenceAnswer").asText())) throw bad("Tự luận cần rubric và đáp án tham khảo");
            }
            case MATCHING -> {
                Set<String> left=ids(c.path("left"),2,30),right=ids(c.path("right"),2,30);
                JsonNode pairs=a.path("pairs");
                if(!pairs.isObject() || pairs.size()!=left.size()) throw bad("Thiếu cặp ghép đúng");
                for(String id:left) if(!right.contains(pairs.path(id).asText())) throw bad("Cặp ghép không hợp lệ");
            }
            case ORDERING -> {
                Set<String> ids=ids(c.path("items"),2,30);
                if(!textArray(a.path("order"),ids.size(),ids.size()).equals(ids)) throw bad("Thứ tự đáp án không hợp lệ");
            }
            case FILL_IN_THE_BLANK -> {
                Set<String> ids=ids(c.path("blanks"),1,30);
                JsonNode answers=a.path("answers");
                if(!answers.isObject() || answers.size()!=ids.size()) throw bad("Thiếu đáp án ô trống");
                for(String id:ids) textArray(answers.path(id),1,30);
            }
        }
    }
    private Set<String> ids(JsonNode nodes,int min,int max) {
        if(!nodes.isArray() || nodes.size()<min || nodes.size()>max) throw bad("Số phần tử câu hỏi không hợp lệ");
        Set<String> ids=new HashSet<>();
        for(JsonNode n:nodes) {
            String id=n.path("id").asText(),text=n.path("text").asText();
            if(blank(id)||blank(text)||id.length()>80||text.length()>2000||!ids.add(id)) throw bad("Phần tử câu hỏi không hợp lệ");
        }
        return ids;
    }
    private Set<String> textArray(JsonNode nodes,int min,int max) {
        if(!nodes.isArray() || nodes.size()<min || nodes.size()>max) throw bad("Danh sách đáp án không hợp lệ");
        Set<String> values=new HashSet<>();
        for(JsonNode n:nodes) {
            if(!n.isTextual() || blank(n.asText()) || n.asText().length()>2000 || !values.add(n.asText()))
                throw bad("Đáp án trùng hoặc không hợp lệ");
        }
        return values;
    }
    private View map(ResultSet rs,int row) throws SQLException {
        try {
            return new View(rs.getObject("id",UUID.class),Type.valueOf(rs.getString("type")),rs.getString("subject"),
                rs.getString("grade_level"),rs.getString("topic"),rs.getString("chapter"),rs.getString("lesson"),
                Difficulty.valueOf(rs.getString("difficulty")),rs.getString("body"),json.readTree(rs.getString("content")),
                json.readTree(rs.getString("answer_key")),rs.getString("explanation"),rs.getString("rubric"),
                rs.getBigDecimal("default_points"),rs.getLong("version"));
        } catch(JsonProcessingException e) { throw new SQLException("Invalid stored question JSON",e); }
    }
    private static final String SELECT="SELECT id,type,subject,grade_level,topic,chapter,lesson,difficulty,body,content,answer_key,explanation,rubric,default_points,version FROM questions";
    private static String trim(String s) { return s.trim(); }
    private static String trimOrNull(String s) { return s==null || s.isBlank()?null:s.trim(); }
    private static boolean blank(String s) { return s==null || s.isBlank(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
