package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.compat.LegacySecretCrypto;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter.EncryptedSecret;
import com.jobtracker.migrationpoc.config.AppEnvironment;
import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import org.jsoup.Jsoup;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
public class MailInboxService {
    private final Environment environment;
    private final LegacySecretCrypto crypto;
    private final LegacySecretCryptoWriter cryptoWriter;

    public MailInboxService(Environment environment, LegacySecretCrypto crypto, LegacySecretCryptoWriter cryptoWriter) {
        this.environment=environment; this.crypto=crypto; this.cryptoWriter=cryptoWriter;
    }

    public InboxView inbox(String userEmail)throws Exception{
        try(Connection c=open()){long userId=userId(c,userEmail);List<AccountView> accounts=new ArrayList<>();List<MailView> messages=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT id,email,provider,last_synced_at,last_error FROM mail_accounts WHERE user_id=? ORDER BY created_at")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())accounts.add(new AccountView(r.getLong(1),r.getString(2),r.getString(3),instant(r.getTimestamp(4)),r.getString(5)));}}
            try(PreparedStatement s=c.prepareStatement("SELECT m.id,m.sender,m.subject,m.body,m.received_at,a.email FROM collected_mails m JOIN mail_accounts a ON a.id=m.account_id WHERE m.user_id=? AND m.processed_at IS NULL ORDER BY m.received_at DESC NULLS LAST,m.id DESC LIMIT 100")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())messages.add(new MailView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),instant(r.getTimestamp(5)),r.getString(6)));}}
            return new InboxView(accounts,messages);
        }
    }

    public AccountView saveAccount(String userEmail,String email,String provider,String password)throws Exception{
        String cleanEmail=required(email,"邮箱地址",254).toLowerCase(Locale.ROOT),cleanProvider=provider==null?"":provider.trim().toLowerCase(Locale.ROOT),cleanPassword=required(password,"邮箱授权码",512);
        if(!List.of("qq","163").contains(cleanProvider))throw new ValidationException("目前仅支持 QQ 邮箱和网易邮箱");
        requireEncryption();testConnection(cleanEmail,cleanProvider,cleanPassword);EncryptedSecret secret=cryptoWriter.encrypt(encryptionKey(),cleanPassword);
        try(Connection c=open()){long userId=userId(c,userEmail);try(PreparedStatement s=c.prepareStatement("INSERT INTO mail_accounts(user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid,last_error) VALUES(?,?,?,?,?,?,0,'') ON CONFLICT(user_id,email) DO UPDATE SET provider=EXCLUDED.provider,encrypted_password=EXCLUDED.encrypted_password,encryption_iv=EXCLUDED.encryption_iv,auth_tag=EXCLUDED.auth_tag,last_error='',last_uid=0")){s.setLong(1,userId);s.setString(2,cleanEmail);s.setString(3,cleanProvider);s.setBytes(4,secret.encrypted());s.setBytes(5,secret.iv());s.setBytes(6,secret.authTag());s.executeUpdate();}}
        sync(userEmail);return inbox(userEmail).accounts().stream().filter(a->a.email().equals(cleanEmail)).findFirst().orElseThrow();
    }
    public void removeAccount(String email,long id)throws Exception{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM mail_accounts WHERE id=? AND user_id=?")){s.setLong(1,id);s.setLong(2,userId(c,email));s.executeUpdate();}}
    public InboxView sync(String email)throws Exception{try(Connection c=open()){long userId=userId(c,email);for(AccountRow account:accounts(c,userId)){try{syncAccount(c,account);}catch(Exception ignored){}}}return inbox(email);}
    public void process(String email,long id)throws Exception{updateMessage(email,id,false);}
    public void delete(String email,long id)throws Exception{updateMessage(email,id,true);}

    @Scheduled(fixedDelayString="${MAIL_SYNC_INTERVAL_MS:300000}",initialDelayString="${MAIL_SYNC_INITIAL_DELAY_MS:60000}")
    public void syncAll(){
        if(encryptionKey().length()<32)return;
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT id,user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid FROM mail_accounts ORDER BY id");ResultSet r=s.executeQuery()){while(r.next()){try{syncAccount(c,row(r));}catch(Exception ignored){}}}catch(Exception ignored){}
    }

    private void updateMessage(String email,long id,boolean delete)throws Exception{try(Connection c=open()){String sql=delete?"DELETE FROM collected_mails WHERE id=? AND user_id=?":"UPDATE collected_mails SET processed_at=NOW() WHERE id=? AND user_id=?";try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);s.setLong(2,userId(c,email));s.executeUpdate();}}}
    private void syncAccount(Connection c,AccountRow account)throws Exception{
        String password=crypto.decrypt(encryptionKey(),account.encrypted(),account.iv(),account.tag());Properties p=mailProperties();long newest=account.lastUid();
        try(Store store=Session.getInstance(p).getStore("imaps")){store.connect(host(account.provider()),993,account.email(),password);Folder folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);
            try{UIDFolder uidFolder=(UIDFolder)folder;long latestUid=folder.getMessageCount()>0?uidFolder.getUID(folder.getMessage(folder.getMessageCount())):1,start=account.lastUid()>0?account.lastUid()+1:Math.max(1,latestUid-29);Message[] mails=uidFolder.getMessagesByUID(start,UIDFolder.LASTUID);
                for(Message mail:mails){long uid=uidFolder.getUID(mail);if(uid<=0)continue;newest=Math.max(newest,uid);String body=extract(mail).trim();if(body.isBlank())continue;
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO collected_mails(user_id,account_id,message_uid,sender,subject,body,received_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(account_id,message_uid) DO NOTHING")){s.setLong(1,account.userId());s.setLong(2,account.id());s.setLong(3,uid);s.setString(4,addresses(mail.getFrom()));s.setString(5,decode(mail.getSubject()));s.setString(6,limit(body,100000));java.util.Date date=mail.getReceivedDate()!=null?mail.getReceivedDate():mail.getSentDate();s.setTimestamp(7,date==null?null:Timestamp.from(date.toInstant()));s.executeUpdate();}
                }
            }finally{folder.close(false);}
            try(PreparedStatement s=c.prepareStatement("UPDATE mail_accounts SET last_uid=?,last_synced_at=NOW(),last_error='' WHERE id=?")){s.setLong(1,newest);s.setLong(2,account.id());s.executeUpdate();}
        }catch(Exception e){try(PreparedStatement s=c.prepareStatement("UPDATE mail_accounts SET last_error='连接失败，请检查 IMAP 是否开启及授权码是否有效' WHERE id=?")){s.setLong(1,account.id());s.executeUpdate();}throw e;}
    }
    private String extract(Part part)throws Exception{
        if(part.isMimeType("text/html")){var document=Jsoup.parse(String.valueOf(part.getContent()));StringBuilder out=new StringBuilder(document.text());document.select("a[href]").forEach(link->{String href=link.attr("href");if(!href.isBlank())out.append("\n").append(link.text().isBlank()?"链接":link.text()).append("：").append(href);});return out.toString();}
        if(part.isMimeType("text/plain"))return String.valueOf(part.getContent());
        Object content=part.getContent();if(content instanceof Multipart multipart){String html="",plain="";for(int i=0;i<multipart.getCount();i++){BodyPart child=multipart.getBodyPart(i);String value=extract(child);if(child.isMimeType("text/html"))html=value;else if(child.isMimeType("text/plain")&&!value.isBlank())plain=value;else if(plain.isBlank()&&!value.isBlank())plain=value;}return html.isBlank()?plain:html;}return "";
    }
    private void testConnection(String email,String provider,String password){try(Store store=Session.getInstance(mailProperties()).getStore("imaps")){store.connect(host(provider),993,email,password);}catch(Exception e){throw new ValidationException("邮箱连接失败，请确认已开启 IMAP 并使用授权码");}}
    private Properties mailProperties(){Properties p=new Properties();p.put("mail.imaps.ssl.enable","true");p.put("mail.imaps.connectiontimeout","10000");p.put("mail.imaps.timeout","20000");return p;}
    private List<AccountRow> accounts(Connection c,long userId)throws Exception{List<AccountRow> list=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT id,user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid FROM mail_accounts WHERE user_id=?")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())list.add(row(r));}}return list;}
    private AccountRow row(ResultSet r)throws Exception{return new AccountRow(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBytes(5),r.getBytes(6),r.getBytes(7),r.getLong(8));}
    private Connection open()throws Exception{LegacyDatabaseUrl config=LegacyDatabaseUrl.parse(AppEnvironment.databaseUrl(environment));Properties p=new Properties();if(config.username()!=null)p.setProperty("user",config.username());if(config.password()!=null)p.setProperty("password",config.password());p.setProperty("ApplicationName","job-tracker-mail-inbox");return PooledConnections.open(config,p);}
    private long userId(Connection c,String email)throws Exception{try(PreparedStatement s=c.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL")){s.setString(1,email.trim().toLowerCase(Locale.ROOT));try(ResultSet r=s.executeQuery()){if(r.next())return r.getLong(1);}}throw new ValidationException("账号不可用");}
    private String encryptionKey(){String value=AppEnvironment.encryptionKey(environment);return value==null?"":value;}private void requireEncryption(){if(encryptionKey().length()<32)throw new ValidationException("服务器尚未配置安全密钥");}
    private String host(String provider){return "qq".equals(provider)?"imap.qq.com":"imap.163.com";}private String required(String value,String name,int max){String clean=value==null?"":value.trim();if(clean.isBlank())throw new ValidationException("请填写"+name);if(clean.length()>max)throw new ValidationException(name+"内容过长");return clean;}
    private String decode(String value){try{return value==null?"(无主题)":MimeUtility.decodeText(value);}catch(Exception e){return value;}}private String addresses(Address[] values){if(values==null)return "";StringBuilder out=new StringBuilder();for(Address value:values){if(!out.isEmpty())out.append(", ");out.append(decode(value.toString()));}return out.toString();}
    private String limit(String value,int max){return value.length()<=max?value:value.substring(0,max);}private String instant(Timestamp value){return value==null?"":value.toInstant().toString();}
    public record InboxView(List<AccountView> accounts,List<MailView> messages){}public record AccountView(long id,String email,String provider,String lastSyncedAt,String lastError){}public record MailView(long id,String sender,String subject,String body,String receivedAt,String accountEmail){}
    private record AccountRow(long id,long userId,String email,String provider,byte[] encrypted,byte[] iv,byte[] tag,long lastUid){}public static class ValidationException extends RuntimeException{public ValidationException(String message){super(message);}}
}
