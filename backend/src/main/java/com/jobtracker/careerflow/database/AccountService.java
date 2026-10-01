package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier.PasswordRecord;
import com.jobtracker.careerflow.database.LegacyReadService.LegacyUser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AccountService {
    private final Environment environment;
    private final ApplicationService sandbox;
    private final LegacyPasswordVerifier passwords;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountService(Environment environment, ApplicationService sandbox, LegacyPasswordVerifier passwords) {
        this.environment=environment;this.sandbox=sandbox;this.passwords=passwords;
    }
    public boolean enabled(){return sandbox.status().enabled();}
    public Optional<LegacyUser> findByEmail(String email)throws Exception{return find("email",normalize(email));}
    public Optional<LegacyUser> findById(long id)throws Exception{return find("id",id);}
    private Optional<LegacyUser> find(String field,Object value)throws Exception{
        try(Connection connection=open();PreparedStatement statement=connection.prepareStatement(
            "SELECT u.id,u.email,u.password_salt,u.password_hash,u.disabled_at IS NOT NULL AS disabled,u.display_name,d.data::text AS user_data FROM users u LEFT JOIN user_data d ON d.user_id=u.id WHERE u."+field+"=?")){
            statement.setObject(1,value);try(ResultSet r=statement.executeQuery()){return r.next()?Optional.of(new LegacyUser(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getString(6),profileAvatar(r.getString(7)))):Optional.empty();}
        }
    }
    public boolean registrationOpen()throws Exception{
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT value::text FROM system_settings WHERE key='registration_open'");ResultSet r=s.executeQuery()){
            if(r.next())return Boolean.parseBoolean(r.getString(1));
        }
        return !"false".equalsIgnoreCase(environment.getProperty("ALLOW_REGISTRATION","true"));
    }
    public LegacyUser register(String email,String password,String code)throws Exception{
        String clean=normalize(email);
        if(!clean.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new AccountValidationException("请输入有效邮箱");
        if(password==null||password.length()<10||password.length()>128)throw new AccountValidationException("密码长度需为 10–128 位");
        if(!registrationOpen())throw new AccountForbiddenException("当前未开放注册");
        if(!registrationCodeMatches(code))throw new AccountForbiddenException("邀请码不正确");
        PasswordRecord record=passwords.create(password);
        try(Connection c=open()){
            c.setAutoCommit(false);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO users(email,password_salt,password_hash,display_name) VALUES(?,?,?,?) RETURNING id,email,display_name")){
                s.setString(1,clean);s.setString(2,record.salt());s.setString(3,record.hash());s.setString(4,defaultDisplayName(clean));
                long id;String saved;String displayName;
                try(ResultSet r=s.executeQuery()){r.next();id=r.getLong(1);saved=r.getString(2);displayName=r.getString(3);}
                try(PreparedStatement d=c.prepareStatement("INSERT INTO user_data(user_id,data) VALUES(?,?::jsonb)")){d.setLong(1,id);d.setString(2,"{\"applications\":[],\"events\":[],\"settings\":{}}");d.executeUpdate();}
                c.commit();return new LegacyUser(id,saved,record.salt(),record.hash(),false,displayName,"");
            }catch(SQLException e){c.rollback();if(uniqueViolation(e))throw new AccountConflictException("该邮箱已注册");throw e;}catch(Exception e){c.rollback();throw e;}
        }
    }
    public LegacyUser updateDisplayName(String email,String displayName)throws Exception{
        String clean=displayName==null?"":displayName.trim().replaceAll("\\s+"," ");
        if(clean.isBlank()||clean.length()>32)throw new AccountValidationException("昵称长度需为 1–32 个字符");
        try(Connection c=open();PreparedStatement s=c.prepareStatement(
            "UPDATE users SET display_name=? WHERE lower(email)=? AND disabled_at IS NULL RETURNING id,email,password_salt,password_hash,display_name")){
            s.setString(1,clean);s.setString(2,normalize(email));
            try(ResultSet r=s.executeQuery()){
                if(!r.next())throw new AccountForbiddenException("当前账户不可用");
                return new LegacyUser(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),false,r.getString(5));
            }
        }
    }
    public LegacyUser updateProfile(String email,String displayName,String avatar)throws Exception{
        String clean=displayName==null?"":displayName.trim().replaceAll("\\s+"," ");
        if(clean.isBlank()||clean.length()>32)throw new AccountValidationException("昵称长度需为 1–32 个字符");
        if(avatar!=null)validateAvatar(avatar);
        String normalized=normalize(email);
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                long userId;
                String document;
                try(PreparedStatement query=c.prepareStatement("SELECT u.id,d.data::text FROM users u LEFT JOIN user_data d ON d.user_id=u.id WHERE lower(u.email)=? AND u.disabled_at IS NULL")){
                    query.setString(1,normalized);
                    try(ResultSet r=query.executeQuery()){
                        if(!r.next())throw new AccountForbiddenException("当前账户不可用");
                        userId=r.getLong(1);document=r.getString(2);
                    }
                }
                try(PreparedStatement update=c.prepareStatement("UPDATE users SET display_name=? WHERE id=?")){
                    update.setString(1,clean);update.setLong(2,userId);update.executeUpdate();
                }
                if(avatar!=null){
                    ObjectNode root=document==null||document.isBlank()?objectMapper.createObjectNode():(ObjectNode)objectMapper.readTree(document);
                    ObjectNode settings;
                    if(root.path("settings") instanceof ObjectNode existing){
                        settings=existing;
                    }else{
                        settings=root.putObject("settings");
                    }
                    if(avatar.isBlank())settings.remove("profileAvatar");else settings.put("profileAvatar",avatar);
                    try(PreparedStatement update=c.prepareStatement("UPDATE user_data SET data=?::jsonb,updated_at=NOW() WHERE user_id=?")){
                        update.setString(1,objectMapper.writeValueAsString(root));update.setLong(2,userId);update.executeUpdate();
                    }
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
        return findByEmail(normalized).orElseThrow(()->new AccountForbiddenException("当前账户不可用"));
    }
    public void changePassword(String email,String currentPassword,String newPassword)throws Exception{
        if(newPassword==null||newPassword.length()<10||newPassword.length()>128)throw new AccountValidationException("新密码长度需为 10–128 位");
        if(newPassword.equals(currentPassword))throw new AccountValidationException("新密码不能与当前密码相同");
        Optional<LegacyUser> found=findByEmail(email);
        if(found.isEmpty()||found.get().disabled()||!passwords.verify(currentPassword,found.get().passwordSalt(),found.get().passwordHash()))
            throw new AccountForbiddenException("当前密码不正确");
        PasswordRecord record=passwords.create(newPassword);
        try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE users SET password_salt=?,password_hash=? WHERE id=?")){
            s.setString(1,record.salt());s.setString(2,record.hash());s.setLong(3,found.get().id());s.executeUpdate();
        }
    }
    private boolean registrationCodeMatches(String code)throws Exception{
        try(Connection c=open();PreparedStatement s=c.prepareStatement(
            "SELECT value #>> '{}' FROM system_settings WHERE key='registration_code_hash'"
        );ResultSet r=s.executeQuery()){
            if(r.next()){
                String stored=r.getString(1);
                if(stored==null||stored.isBlank())return true;
                String[] parts=stored.split(":",2);
                return parts.length==2&&passwords.verify(code,parts[0],parts[1]);
            }
        }
        String requiredCode=environment.getProperty("REGISTRATION_CODE","");
        return requiredCode.isBlank()||requiredCode.equals(code);
    }
    private Connection open()throws Exception{
        if(!enabled())throw new AccountDisabledException(sandbox.status().message());
        LegacyDatabaseUrl config=LegacyDatabaseUrl.parse(com.jobtracker.careerflow.config.AppEnvironment.databaseUrl(environment));
        Properties p=new Properties();if(config.username()!=null)p.setProperty("user",config.username());if(config.password()!=null)p.setProperty("password",config.password());
        p.setProperty("ApplicationName","careerflow-accounts");return PooledConnections.open(config,p);
    }
    private String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private boolean uniqueViolation(SQLException exception){String message=exception.getMessage();return "23505".equals(exception.getSQLState())||exception.getErrorCode()==19||(message!=null&&message.toLowerCase(Locale.ROOT).contains("unique constraint"));}
    private String defaultDisplayName(String email){int separator=email.indexOf('@');return separator>0?email.substring(0,separator):email;}
    private String profileAvatar(String json){try{String value=objectMapper.readTree(json==null?"{}":json).path("settings").path("profileAvatar").asText("");return value.startsWith("data:image/")?value:"";}catch(Exception ignored){return "";}}
    private void validateAvatar(String avatar){
        if(avatar.isBlank())return;
        if(avatar.length()>380000||!avatar.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$"))throw new AccountValidationException("头像格式无效，请使用 PNG、JPEG 或 WebP 图片");
        try{if(Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',')+1)).length>280000)throw new AccountValidationException("头像文件过大");}
        catch(IllegalArgumentException e){throw new AccountValidationException("头像内容无效");}
    }
    public static class AccountValidationException extends RuntimeException{public AccountValidationException(String m){super(m);}}
    public static class AccountForbiddenException extends RuntimeException{public AccountForbiddenException(String m){super(m);}}
    public static class AccountConflictException extends RuntimeException{public AccountConflictException(String m){super(m);}}
    public static class AccountDisabledException extends RuntimeException{public AccountDisabledException(String m){super(m);}}
}

