package com.edutrack.classroom;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentImportService {
    public record RowError(int row, String message) { }
    public record ImportResult(int created, int failed, List<RowError> errors) { }
    private final StudentAccountService accounts;
    public StudentImportService(StudentAccountService accounts) { this.accounts=accounts; }

    public ImportResult importFile(MultipartFile file, UUID adminId) {
        if(file==null || file.isEmpty() || file.getSize()>2_000_000) throw bad("File rỗng hoặc quá 2 MB");
        try {
            byte[] start=file.getBytes();
            boolean xlsx=start.length>=4 && start[0]=='P' && start[1]=='K' && start[2]==3 && start[3]==4;
            boolean xls=start.length>=8 && (start[0]&255)==208 && (start[1]&255)==207 && (start[2]&255)==17 && (start[3]&255)==224;
            return xlsx||xls ? importRows(parseExcel(file),adminId) : importCsv(file,adminId);
        } catch(java.io.IOException e) {throw bad("Không đọc được file import");}
    }

    public ImportResult importCsv(MultipartFile file, UUID adminId) {
        if(file==null || file.isEmpty() || file.getSize()>2_000_000) throw bad("File CSV rỗng hoặc quá 2 MB");
        try {
            String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
            return importRows(parseCsv(content),adminId);
        } catch(CharacterCodingException e) {throw bad("CSV phải được lưu dưới dạng UTF-8");}
        catch(java.io.IOException e) {throw bad("Không đọc được file CSV");}
    }

    private List<List<String>> parseExcel(MultipartFile file) {
        try(var stream=file.getInputStream();var workbook=WorkbookFactory.create(stream)) {
            if(workbook.getNumberOfSheets()<1) throw bad("Excel không có bảng dữ liệu");
            var sheet=workbook.getSheetAt(0);
            if(sheet.getLastRowNum()>500) throw bad("Mỗi lần chỉ import tối đa 500 học sinh");
            var header=sheet.getRow(0);
            if(header==null || header.getLastCellNum()<2 || header.getLastCellNum()>20) throw bad("Tiêu đề Excel không hợp lệ");
            int columns=header.getLastCellNum();
            var formatter=new DataFormatter(Locale.ROOT);
            List<List<String>> rows=new ArrayList<>();
            for(int i=0;i<=sheet.getLastRowNum();i++) {
                var row=sheet.getRow(i);List<String> values=new ArrayList<>();
                for(int j=0;j<columns;j++) values.add(readCell(row==null?null:row.getCell(j),formatter));
                rows.add(values);
            }
            return rows;
        } catch(ResponseStatusException e) {throw e;}
        catch(Exception e) {throw bad("File Excel không hợp lệ hoặc không đọc được");}
    }

    private static String readCell(Cell cell,DataFormatter formatter) {
        if(cell==null || cell.getCellType()==CellType.BLANK) return "";
        if(cell.getCellType()==CellType.FORMULA) throw bad("Không chấp nhận công thức trong file Excel");
        if(cell.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        return formatter.formatCellValue(cell).strip();
    }

    private ImportResult importRows(List<List<String>> rows,UUID adminId) {
            if(rows.isEmpty()) throw bad("File import thiếu dòng tiêu đề");
            Map<String,Integer> columns=new HashMap<>();
            List<String> header=rows.getFirst();
            for(int i=0;i<header.size();i++) {
                String name=header.get(i).strip().replace("\uFEFF","").toLowerCase(Locale.ROOT);
                if(columns.put(name,i)!=null) throw bad("Tiêu đề CSV bị trùng");
            }
            if(!columns.containsKey("email") || !columns.containsKey("fullname"))
                throw bad("File import cần cột email và fullName");
            if(rows.size()-1>500) throw bad("Mỗi lần chỉ import tối đa 500 học sinh");
            int created=0;List<RowError> errors=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=1;i<rows.size();i++) {
                List<String> row=rows.get(i);
                if(row.stream().allMatch(String::isBlank)) continue;
                if(row.size()!=header.size()) {errors.add(new RowError(i+1,"Số cột không khớp tiêu đề"));continue;}
                String email=value(row,columns,"email").strip().toLowerCase(Locale.ROOT);
                String name=value(row,columns,"fullname").strip();
                String phone=optional(row,columns,"phone");
                String birthday=optional(row,columns,"dateofbirth");
                String parent=optional(row,columns,"parentemail");
                if(!validEmail(email) || name.isBlank() || name.length()>120 ||
                    (parent!=null && !validEmail(parent)) || (phone!=null && phone.length()>30)) {
                    errors.add(new RowError(i+1,"Email hoặc thông tin học sinh không hợp lệ"));continue;
                }
                if(!seen.add(email)) {errors.add(new RowError(i+1,"Email trùng trong file"));continue;}
                LocalDate date=null;
                try {if(birthday!=null) date=LocalDate.parse(birthday);}
                catch(DateTimeParseException e) {errors.add(new RowError(i+1,"Ngày sinh cần định dạng YYYY-MM-DD"));continue;}
                try {accounts.create(email,name,phone,date,parent,adminId);created++;}
                catch(ResponseStatusException e) {errors.add(new RowError(i+1,e.getReason()==null?"Không tạo được tài khoản":e.getReason()));}
            }
            return new ImportResult(created,errors.size(),errors);
    }

    static List<List<String>> parseCsv(String content) {
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<content.length();i++) {
            char c=content.charAt(i);
            if(quoted) {
                if(c=='"') {
                    if(i+1<content.length() && content.charAt(i+1)=='"') {field.append('"');i++;}
                    else {quoted=false;closed=true;}
                } else field.append(c);
            } else if(c=='"') {
                if(field.length()!=0 || closed) throw bad("Dấu nháy CSV không hợp lệ");
                quoted=true;
            } else if(c==',') {
                row.add(field.toString());field.setLength(0);closed=false;
            } else if(c=='\n' || c=='\r') {
                if(c=='\r' && i+1<content.length() && content.charAt(i+1)=='\n') i++;
                row.add(field.toString());rows.add(row);row=new ArrayList<>();field.setLength(0);closed=false;
            } else {
                if(closed && !Character.isWhitespace(c)) throw bad("Ký tự sau dấu nháy CSV không hợp lệ");
                if(!closed) field.append(c);
            }
        }
        if(quoted) throw bad("Dấu nháy CSV chưa đóng");
        if(field.length()>0 || !row.isEmpty() || closed) {row.add(field.toString());rows.add(row);}
        return rows;
    }
    private static String value(List<String> row,Map<String,Integer> columns,String key) {return row.get(columns.get(key));}
    private static String optional(List<String> row,Map<String,Integer> columns,String key) {
        Integer i=columns.get(key);if(i==null)return null;String value=row.get(i).strip();return value.isBlank()?null:value;
    }
    private static boolean validEmail(String value) {
        return value!=null && value.length()<=254 && value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
