package com.edutrack.classroom;

import com.edutrack.identity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class SchoolController {
    public record StudentInput(@NotBlank @Email String email, @NotBlank @Size(max=120) String fullName,
                               @Size(max=30) String phone, LocalDate dateOfBirth) {}
    public record StudentView(UUID id, String email, String fullName, String phone,
                              LocalDate dateOfBirth, AccountStatus status) {
        static StudentView of(UserAccount u) {
            return new StudentView(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getDateOfBirth(), u.getStatus());
        }
    }
    public record ClassroomInput(@NotBlank @Size(max=40) String code, @NotBlank @Size(max=180) String name,
                                 String description, @NotBlank @Size(max=120) String subject,
                                 @Size(max=40) String gradeLevel, @NotBlank @Size(max=20) String academicYear,
                                 LocalDate startsOn, LocalDate endsOn) {}
    public record ClassroomView(UUID id, String code, String name, String description, String subject,
                                String gradeLevel, String academicYear, LocalDate startsOn, LocalDate endsOn,
                                String status, long studentCount) {}

    private final UserAccountRepository users;
    private final AuthService auth;
    private final JdbcTemplate db;

    public SchoolController(UserAccountRepository users, AuthService auth, JdbcTemplate db) {
        this.users = users; this.auth = auth; this.db = db;
    }

    @PostMapping("/admin/students") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public StudentView createStudent(@Valid @RequestBody StudentInput input) {
        String email = UserAccount.normalizeEmail(input.email());
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        var user = new UserAccount(email, input.fullName(), UserRole.STUDENT, AccountStatus.PENDING_ACTIVATION, null);
        user.setProfile(input.fullName(), input.phone(), input.dateOfBirth(), null);
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng"); }
        auth.sendActivation(user);
        return StudentView.of(user);
    }

    @GetMapping("/admin/students") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<StudentView> students() {
        return users.findByRoleOrderByCreatedAtDesc(UserRole.STUDENT).stream().map(StudentView::of).toList();
    }

    @PostMapping("/admin/students/{id}/resend-activation") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public void resendActivation(@PathVariable UUID id) {
        var user = student(id);
        if (user.getStatus() != AccountStatus.PENDING_ACTIVATION) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản không chờ kích hoạt");
        auth.sendActivation(user);
    }

    @PatchMapping("/admin/students/{id}/status") @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public StudentView setStudentStatus(@PathVariable UUID id, @RequestParam AccountStatus status) {
        if (status != AccountStatus.LOCKED && status != AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ khóa hoặc mở tài khoản");
        var user = student(id);
        if (status == AccountStatus.ACTIVE && user.getPasswordHash() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Học sinh chưa kích hoạt tài khoản");
        user.setStatus(status);
        return StudentView.of(user);
    }

    @PostMapping("/admin/classrooms") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public ClassroomView createClassroom(@Valid @RequestBody ClassroomInput input) {
        dates(input);
        UUID id = UUID.randomUUID();
        try {
            db.update("INSERT INTO classrooms(id,code,name,description,subject,grade_level,academic_year,starts_on,ends_on,status) VALUES (?,?,?,?,?,?,?,?,?,'DRAFT')",
                id, input.code().trim(), input.name().trim(), input.description() == null ? "" : input.description().trim(),
                input.subject().trim(), input.gradeLevel(), input.academicYear().trim(), input.startsOn(), input.endsOn());
        } catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã được sử dụng"); }
        return classroom(id);
    }

    @GetMapping("/admin/classrooms") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<ClassroomView> classrooms() {
        return db.query(CLASS_SELECT + " WHERE c.deleted_at IS NULL ORDER BY c.created_at DESC", this::mapClassroom);
    }

    @GetMapping("/admin/classrooms/{id}") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public ClassroomView classroom(@PathVariable UUID id) {
        return db.query(CLASS_SELECT + " WHERE c.id=? AND c.deleted_at IS NULL", this::mapClassroom, id).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
    }

    @PutMapping("/admin/classrooms/{id}") @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public ClassroomView updateClassroom(@PathVariable UUID id, @Valid @RequestBody ClassroomInput input) {
        dates(input); classroom(id);
        try {
            db.update("UPDATE classrooms SET code=?,name=?,description=?,subject=?,grade_level=?,academic_year=?,starts_on=?,ends_on=?,updated_at=now(),version=version+1 WHERE id=?",
                input.code().trim(), input.name().trim(), input.description() == null ? "" : input.description().trim(),
                input.subject().trim(), input.gradeLevel(), input.academicYear().trim(), input.startsOn(), input.endsOn(), id);
        } catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã được sử dụng"); }
        return classroom(id);
    }

    @PatchMapping("/admin/classrooms/{id}/status") @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public ClassroomView status(@PathVariable UUID id, @RequestParam String value) {
        if (!List.of("DRAFT", "ACTIVE", "COMPLETED", "ARCHIVED").contains(value))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        classroom(id);
        db.update("UPDATE classrooms SET status=?,updated_at=now(),version=version+1 WHERE id=?", value, id);
        return classroom(id);
    }

    @DeleteMapping("/admin/classrooms/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public void deleteClassroom(@PathVariable UUID id) {
        classroom(id);
        db.update("UPDATE classrooms SET deleted_at=now(),updated_at=now() WHERE id=?", id);
        db.update("UPDATE student_enrollments SET left_at=now() WHERE classroom_id=? AND left_at IS NULL", id);
    }

    @PostMapping("/admin/classrooms/{classId}/students/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public void enroll(@PathVariable UUID classId, @PathVariable UUID studentId, JwtAuthenticationToken principal) {
        classroom(classId); student(studentId);
        if (db.queryForObject("SELECT count(*) FROM student_enrollments WHERE classroom_id=? AND student_id=? AND left_at IS NULL",
            Long.class, classId, studentId) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Học sinh đã thuộc lớp");
        db.update("INSERT INTO student_enrollments(id,classroom_id,student_id,enrolled_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), classId, studentId, UUID.fromString(principal.getName()));
    }

    @DeleteMapping("/admin/classrooms/{classId}/students/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN_TEACHER')") @Transactional
    public void leave(@PathVariable UUID classId, @PathVariable UUID studentId) {
        if (db.update("UPDATE student_enrollments SET left_at=now() WHERE classroom_id=? AND student_id=? AND left_at IS NULL",
            classId, studentId) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Học sinh không thuộc lớp");
    }

    @GetMapping("/admin/classrooms/{id}/students") @PreAuthorize("hasRole('ADMIN_TEACHER')")
    public List<StudentView> classStudents(@PathVariable UUID id) {
        classroom(id);
        return db.query("SELECT u.id,u.email,u.full_name,u.phone,u.date_of_birth,u.status FROM users u JOIN student_enrollments e ON e.student_id=u.id WHERE e.classroom_id=? AND e.left_at IS NULL ORDER BY u.full_name",
            (rs, row) -> new StudentView(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"),
                rs.getString("phone"), rs.getObject("date_of_birth", LocalDate.class), AccountStatus.valueOf(rs.getString("status"))), id);
    }

    @GetMapping("/student/classrooms") @PreAuthorize("hasRole('STUDENT')")
    public List<ClassroomView> myClassrooms(JwtAuthenticationToken principal) {
        return db.query(CLASS_SELECT + " JOIN student_enrollments mine ON mine.classroom_id=c.id AND mine.left_at IS NULL WHERE mine.student_id=? AND c.deleted_at IS NULL ORDER BY c.created_at DESC",
            this::mapClassroom, UUID.fromString(principal.getName()));
    }

    private UserAccount student(UUID id) {
        return users.findById(id).filter(u -> u.getRole() == UserRole.STUDENT)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh"));
    }
    private void dates(ClassroomInput input) {
        if (input.startsOn() != null && input.endsOn() != null && input.endsOn().isBefore(input.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc trước ngày bắt đầu");
    }
    private static final String CLASS_SELECT = "SELECT c.id,c.code,c.name,c.description,c.subject,c.grade_level,c.academic_year,c.starts_on,c.ends_on,c.status,(SELECT count(*) FROM student_enrollments e WHERE e.classroom_id=c.id AND e.left_at IS NULL) AS student_count FROM classrooms c";
    private ClassroomView mapClassroom(ResultSet rs, int row) throws SQLException {
        return new ClassroomView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
            rs.getString("description"), rs.getString("subject"), rs.getString("grade_level"), rs.getString("academic_year"),
            rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class), rs.getString("status"), rs.getLong("student_count"));
    }
}
