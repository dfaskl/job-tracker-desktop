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
            "SELECT id,email,password_salt,password_hash,disabled_at IS NOT NULL AS disabled FROM users WHERE "+field+"=?")){
            statement.setObject(1,value);try(ResultSet r=statement.executeQuery()){return r.next()?Optional.of(new LegacyUser(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5))):Optional.empty();}
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
        String requiredCode=environment.getProperty("REGISTRATION_CODE","");
        if(!requiredCode.isBlank()&&!requiredCode.equals(code))throw new AccountForbiddenException("邀请码不正确");
        if(!registrationOpen())throw new AccountForbiddenException("当前未开放注册");
        PasswordRecord record=passwords.create(password);
        try(Connection c=open()){
            c.setAutoCommit(false);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO users(email,password_salt,password_hash) VALUES(?,?,?) RETURNING id,email",Statement.RETURN_GENERATED_KEYS)){
                s.setString(1,clean);s.setString(2,record.salt());s.setString(3,record.hash());
                try(ResultSet r=s.executeQuery()){r.next();long id=r.getLong(1);String saved=r.getString(2);
                    try(PreparedStatement d=c.prepareStatement("INSERT INTO user_data(user_id,data) VALUES(?,?::jsonb)")){d.setLong(1,id);d.setString(2,"{\"applications\":[],\"events\":[],\"settings\":{}}");d.executeUpdate();}
                    c.commit();return new LegacyUser(id,saved,record.salt(),record.hash(),false);
                }
            }catch(SQLException e){c.rollback();if("23505".equals(e.getSQLState()))throw new AccountConflictException("该邮箱已注册");throw e;}catch(Exception e){c.rollback();throw e;}
        }
    }
    private Connection open()throws Exception{
        if(!enabled())throw new AccountDisabledException(sandbox.status().message());
        LegacyDatabaseUrl config=LegacyDatabaseUrl.parse(environment.getProperty("POC_WRITE_DATABASE_URL"));
        Properties p=new Properties();if(config.username()!=null)p.setProperty("user",config.username());if(config.password()!=null)p.setProperty("password",config.password());
        p.setProperty("ApplicationName","job-tracker-migration-poc-accounts");return PooledConnections.open(config,p);
    }
    private String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    public static class AccountValidationException extends RuntimeException{public AccountValidationException(String m){super(m);}}
    public static class AccountForbiddenException extends RuntimeException{public AccountForbiddenException(String m){super(m);}}
    public static class AccountConflictException extends RuntimeException{public AccountConflictException(String m){super(m);}}
    public static class AccountDisabledException extends RuntimeException{public AccountDisabledException(String m){super(m);}}
}

