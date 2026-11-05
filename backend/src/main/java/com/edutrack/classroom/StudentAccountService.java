package com.edutrack.classroom;

import com.edutrack.identity.AccountStatus;
import com.edutrack.identity.AuthService;
import com.edutrack.identity.ParentLinkService;
import com.edutrack.identity.UserAccount;
import com.edutrack.identity.UserAccountRepository;
import com.edutrack.identity.UserRole;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAccountService {
    private final UserAccountRepository users;
    private final AuthService auth;
    private final ParentLinkService links;
    public StudentAccountService(UserAccountRepository users, AuthService auth, ParentLinkService links) {
        this.users=users;this.auth=auth;this.links=links;
    }

    @Transactional
    public UserAccount create(String email,String fullName,String phone,LocalDate dateOfBirth,String parentEmail,UUID adminId) {
        if(email==null || fullName==null || fullName.isBlank() || fullName.length()>120 ||
            (phone!=null && phone.length()>30)) throw bad("Thông tin học sinh không hợp lệ");
        String normalized;
        try { normalized=UserAccount.normalizeEmail(email); }
        catch(RuntimeException e) { throw bad("Email học sinh không hợp lệ"); }
        if(users.findByEmailIgnoreCase(normalized).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Email đã được sử dụng");
        var user=new UserAccount(normalized,fullName.trim(),UserRole.STUDENT,AccountStatus.PENDING_ACTIVATION,null);
        user.setProfile(fullName.trim(),phone,dateOfBirth,null);
        try { users.saveAndFlush(user); }
        catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Email đã được sử dụng"); }
        auth.sendActivation(user);
        if(parentEmail!=null && !parentEmail.isBlank()) links.invite(user.getId(),parentEmail,adminId);
        return user;
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
