package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier;
import com.jobtracker.migrationpoc.compat.LegacyPasswordVerifier.PasswordRecord;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

@Component
public class AccountSandboxService {
    private final Environment environment;
    private final ApplicationSandboxService sandbox;
    private final LegacyPasswordVerifier passwords;

    public AccountSandboxService(Environment environment, ApplicationSandboxService sandbox, LegacyPasswordVerifier passwords) {
        this.environment=environment;this.sandbox=sandbox;this.passwords=passwords;
    }
    public boolean enabled(){return sandbox.status().enabled();}
    public Optional<LegacyUser> findByEmail(String email)throws Exception{return find("email",normalize(email));}
    public Optional<LegacyUser> findById(long id)throws Exception{return find("id",id);}
    private Optional<LegacyUser> find(String field,Object value)throws Exception{
        try(Connection connection=open();PreparedStatement statement=connection.prepareStatement(
            "SELECT id,email,password_salt,password_hash,disabled_at IS NOT NULL AS disabled,display_name FROM users WHERE "+field+"=?")){
            statement.setObject(1,value);try(ResultSet r=statement.executeQuery()){return r.next()?Optional.of(new LegacyUser(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getString(6))):Optional.empty();}
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
                try(ResultSet r=s.executeQuery()){r.next();long id=r.getLong(1);String saved=r.getString(2);String displayName=r.getString(3);
                    try(PreparedStatement d=c.prepareStatement("INSERT INTO user_data(user_id,data) VALUES(?,?::jsonb)")){d.setLong(1,id);d.setString(2,"{\"applications\":[],\"events\":[],\"settings\":{}}");d.executeUpdate();}
                    c.commit();return new LegacyUser(id,saved,record.salt(),record.hash(),false,displayName);
                }
            }catch(SQLException e){c.rollback();if("23505".equals(e.getSQLState()))throw new AccountConflictException("该邮箱已注册");throw e;}catch(Exception e){c.rollback();throw e;}
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
        LegacyDatabaseUrl config=LegacyDatabaseUrl.parse(com.jobtracker.migrationpoc.config.AppEnvironment.databaseUrl(environment));
        Properties p=new Properties();if(config.username()!=null)p.setProperty("user",config.username());if(config.password()!=null)p.setProperty("password",config.password());
        p.setProperty("ApplicationName","job-tracker-migration-poc-accounts");return PooledConnections.open(config,p);
    }
    private String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private String defaultDisplayName(String email){int separator=email.indexOf('@');return separator>0?email.substring(0,separator):email;}
    public static class AccountValidationException extends RuntimeException{public AccountValidationException(String m){super(m);}}
    public static class AccountForbiddenException extends RuntimeException{public AccountForbiddenException(String m){super(m);}}
    public static class AccountConflictException extends RuntimeException{public AccountConflictException(String m){super(m);}}
    public static class AccountDisabledException extends RuntimeException{public AccountDisabledException(String m){super(m);}}
}

