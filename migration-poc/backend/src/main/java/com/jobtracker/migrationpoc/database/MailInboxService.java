package com.jobtracker.migrationpoc.database;

import com.jobtracker.migrationpoc.compat.LegacySecretCrypto;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter;
import com.jobtracker.migrationpoc.compat.LegacySecretCryptoWriter.EncryptedSecret;
import com.jobtracker.migrationpoc.config.AppEnvironment;
import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.jsoup.Jsoup;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MailInboxService {
    private final Environment environment;
    private final LegacySecretCrypto crypto;
    private final LegacySecretCryptoWriter cryptoWriter;
    private final Set<Long> syncingAccounts=ConcurrentHashMap.newKeySet();

    public MailInboxService(Environment environment, LegacySecretCrypto crypto, LegacySecretCryptoWriter cryptoWriter) {
        this.environment=environment; this.crypto=crypto; this.cryptoWriter=cryptoWriter;
    }

    public InboxView inbox(String userEmail)throws Exception{
        try(Connection c=open()){c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);long userId=userId(c,userEmail);List<AccountView> accounts=new ArrayList<>();List<MailView> messages=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT id,email,provider,last_synced_at,last_error FROM mail_accounts WHERE user_id=? ORDER BY created_at")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())accounts.add(new AccountView(r.getLong(1),r.getString(2),r.getString(3),instant(r.getTimestamp(4)),r.getString(5)));}}
            try(PreparedStatement s=c.prepareStatement("SELECT m.id,m.sender,m.subject,m.body,m.received_at,a.email FROM collected_mails m JOIN mail_accounts a ON a.id=m.account_id WHERE m.user_id=? AND m.processed_at IS NULL ORDER BY m.received_at DESC NULLS LAST,m.id DESC LIMIT 100")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())messages.add(new MailView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),instant(r.getTimestamp(5)),r.getString(6)));}}
            InboxView view=new InboxView(accounts,messages,pendingCount(c,userId));c.commit();return view;
        }
    }

    public AccountView saveAccount(String userEmail,String email,String provider,String password)throws Exception{
        String cleanEmail=required(email,"邮箱地址",254).toLowerCase(Locale.ROOT),cleanProvider=provider==null?"":provider.trim().toLowerCase(Locale.ROOT),cleanPassword=required(password,"邮箱授权码",512);
        if(!List.of("qq","163").contains(cleanProvider))throw new ValidationException("目前仅支持 QQ 邮箱和网易邮箱");
        requireEncryption();Instant collectAfter=Instant.now();long latestUid=testConnection(cleanEmail,cleanProvider,cleanPassword);EncryptedSecret secret=cryptoWriter.encrypt(encryptionKey(),cleanPassword);
        try(Connection c=open()){long userId=userId(c,userEmail);try(PreparedStatement s=c.prepareStatement("INSERT INTO mail_accounts(user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid,last_error,last_synced_at,initialized,collect_after) VALUES(?,?,?,?,?,?,?,'',NOW(),TRUE,?) ON CONFLICT(user_id,email) DO UPDATE SET provider=EXCLUDED.provider,encrypted_password=EXCLUDED.encrypted_password,encryption_iv=EXCLUDED.encryption_iv,auth_tag=EXCLUDED.auth_tag,last_error='',last_uid=EXCLUDED.last_uid,last_synced_at=NOW(),initialized=TRUE,collect_after=EXCLUDED.collect_after")){s.setLong(1,userId);s.setString(2,cleanEmail);s.setString(3,cleanProvider);s.setBytes(4,secret.encrypted());s.setBytes(5,secret.iv());s.setBytes(6,secret.authTag());s.setLong(7,latestUid);s.setTimestamp(8,Timestamp.from(collectAfter));s.executeUpdate();}}
        sync(userEmail);return inbox(userEmail).accounts().stream().filter(a->a.email().equals(cleanEmail)).findFirst().orElseThrow();
    }
    public void removeAccount(String email,long id)throws Exception{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM mail_accounts WHERE id=? AND user_id=?")){s.setLong(1,id);s.setLong(2,userId(c,email));s.executeUpdate();}}
    public InboxView sync(String email)throws Exception{
        for(AccountRow account:accounts(email)){try{syncAccount(account);}catch(Exception ignored){}}
        return inbox(email);
    }
    public void process(String email,long id)throws Exception{updateMessage(email,id,false);}
    public int processAll(String email)throws Exception{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE collected_mails SET processed_at=NOW() WHERE user_id=? AND processed_at IS NULL")){s.setLong(1,userId(c,email));return s.executeUpdate();}}
    public void delete(String email,long id)throws Exception{updateMessage(email,id,true);}

    @Scheduled(fixedDelayString="${MAIL_SYNC_INTERVAL_MS:15000}",initialDelayString="${MAIL_SYNC_INITIAL_DELAY_MS:15000}")
    public void syncAll(){
        if(encryptionKey().length()<32)return;
        try{for(AccountRow account:accounts()){try{syncAccount(account);}catch(Exception ignored){}}}catch(Exception ignored){}
    }

    private void updateMessage(String email,long id,boolean delete)throws Exception{try(Connection c=open()){String sql=delete?"DELETE FROM collected_mails WHERE id=? AND user_id=?":"UPDATE collected_mails SET processed_at=NOW() WHERE id=? AND user_id=?";try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);s.setLong(2,userId(c,email));s.executeUpdate();}}}
    private void syncAccount(AccountRow account)throws Exception{
        if(!syncingAccounts.add(account.id()))return;
        try{
            SyncResult result=fetch(account);
            persist(account,result);
        }catch(Exception e){persistError(account.id(),connectionFailure(account.provider(),e));throw e;
        }finally{syncingAccounts.remove(account.id());}
    }
    private SyncResult fetch(AccountRow account)throws Exception{
        String password=crypto.decrypt(encryptionKey(),account.encrypted(),account.iv(),account.tag());long newest=account.lastUid();List<FetchedMail> fetched=new ArrayList<>();
        try(Store store=connect(account.email(),account.provider(),password)){Folder folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);
            try{UIDFolder uidFolder=(UIDFolder)folder;long latestUid=folder.getMessageCount()>0?uidFolder.getUID(folder.getMessage(folder.getMessageCount())):0;
                if(!account.initialized())return new SyncResult(latestUid,List.of());
                long start=account.lastUid()+1;Message[] mails=uidFolder.getMessagesByUID(start,UIDFolder.LASTUID);
                for(Message mail:mails){long uid=uidFolder.getUID(mail);if(uid<=0)continue;newest=Math.max(newest,uid);java.util.Date date=mail.getReceivedDate()!=null?mail.getReceivedDate():mail.getSentDate();if(date==null||!date.toInstant().isAfter(account.collectAfter()))continue;String body=extract(mail).trim();if(body.isBlank())continue;
                    fetched.add(new FetchedMail(uid,addresses(mail.getFrom()),decode(mail.getSubject()),limit(body,100000),date.toInstant()));
                }
            }finally{folder.close(false);}
        }
        return new SyncResult(newest,List.copyOf(fetched));
    }
    private void persist(AccountRow account,SyncResult result)throws Exception{
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                for(FetchedMail mail:result.mails())try(PreparedStatement s=c.prepareStatement("INSERT INTO collected_mails(user_id,account_id,message_uid,sender,subject,body,received_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(account_id,message_uid) DO NOTHING")){s.setLong(1,account.userId());s.setLong(2,account.id());s.setLong(3,mail.uid());s.setString(4,mail.sender());s.setString(5,mail.subject());s.setString(6,mail.body());s.setTimestamp(7,Timestamp.from(mail.receivedAt()));s.executeUpdate();}
                try(PreparedStatement s=c.prepareStatement("UPDATE mail_accounts SET last_uid=?,last_synced_at=NOW(),last_error='',initialized=TRUE WHERE id=?")){s.setLong(1,result.newestUid());s.setLong(2,account.id());s.executeUpdate();}
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    private void persistError(long accountId,String message){
        try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE mail_accounts SET last_error=? WHERE id=?")){s.setString(1,message);s.setLong(2,accountId);s.executeUpdate();}catch(Exception ignored){}
    }
    private String extract(Part part)throws Exception{
        if(part.isMimeType("text/html")){var document=Jsoup.parse(String.valueOf(part.getContent()));StringBuilder out=new StringBuilder(document.text());document.select("a[href]").forEach(link->{String href=link.attr("href");if(!href.isBlank())out.append("\n").append(link.text().isBlank()?"链接":link.text()).append("：").append(href);});return out.toString();}
        if(part.isMimeType("text/plain"))return String.valueOf(part.getContent());
        Object content=part.getContent();if(content instanceof Multipart multipart){String html="",plain="";for(int i=0;i<multipart.getCount();i++){BodyPart child=multipart.getBodyPart(i);String value=extract(child);if(child.isMimeType("text/html"))html=value;else if(child.isMimeType("text/plain")&&!value.isBlank())plain=value;else if(plain.isBlank()&&!value.isBlank())plain=value;}return html.isBlank()?plain:html;}return "";
    }
    private long testConnection(String email,String provider,String password){try(Store store=connect(email,provider,password)){Folder folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);try{return folder.getMessageCount()>0?((UIDFolder)folder).getUID(folder.getMessage(folder.getMessageCount())):0;}finally{folder.close(false);}}catch(Exception e){throw new ValidationException(connectionFailure(provider,e));}}
    private Store connect(String email,String provider,String password)throws MessagingException{
        Store store=Session.getInstance(mailProperties()).getStore("imaps");
        try{
            store.connect(host(provider),993,email,password);
            if("163".equals(provider)&&store instanceof IMAPStore imapStore){
                Map<String,String> clientId=new LinkedHashMap<>();
                clientId.put("name","job-tracker");clientId.put("version","1.0");clientId.put("vendor","job-tracker");clientId.put("support-url","https://github.com/dfaskl/job-tracker-desktop");
                imapStore.id(clientId);
            }
            return store;
        }catch(MessagingException|RuntimeException e){try{store.close();}catch(Exception ignored){}throw e;}
    }
    private String connectionFailure(String provider,Throwable error){
        String service="163".equals(provider)?"网易邮箱":"QQ 邮箱";
        String detail=exceptionText(error).toLowerCase(Locale.ROOT);
        if(detail.contains("unsafe login")||detail.contains("login is not safe")||detail.contains("web login required")||detail.contains("please log in via your web browser"))return service+"拒绝了第三方登录：请先登录网页版邮箱完成安全验证，再重新生成客户端授权码";
        if(error instanceof AuthenticationFailedException||detail.contains("authentication")||detail.contains("authenticate")||detail.contains("login failed")||detail.contains("invalid password"))return service+"认证失败：请使用新生成的客户端授权码，不要填写邮箱登录密码";
        if(hasCause(error,SocketTimeoutException.class)||detail.contains("timed out")||detail.contains("timeout"))return "连接 "+host(provider)+":993 超时，请检查部署平台是否允许访问外部 IMAP 端口";
        if(hasCause(error,ConnectException.class)||detail.contains("connection refused")||detail.contains("couldn't connect")||detail.contains("could not connect")||detail.contains("unknown host"))return "无法连接 "+host(provider)+":993，请检查部署平台网络和邮箱 IMAP 服务状态";
        if(detail.contains("not enabled")||detail.contains("imap service")||detail.contains("select unsafe")||detail.contains("command is not valid"))return service+"拒绝访问收件箱：请确认 IMAP 已开启，并在网页版邮箱完成客户端授权设置";
        return service+"连接失败（"+safeDetail(error)+"），请重新生成客户端授权码后再试";
    }
    private String exceptionText(Throwable error){StringBuilder out=new StringBuilder();for(Throwable current=error;current!=null;current=current.getCause()){if(current.getMessage()!=null)out.append(' ').append(current.getMessage());}return out.toString();}
    private boolean hasCause(Throwable error,Class<? extends Throwable> type){for(Throwable current=error;current!=null;current=current.getCause())if(type.isInstance(current))return true;return false;}
    private String safeDetail(Throwable error){String detail=exceptionText(error).replaceAll("[\\r\\n]+"," ").trim();return detail.isBlank()?error.getClass().getSimpleName():limit(detail,160);}
    private Properties mailProperties(){Properties p=new Properties();p.put("mail.imaps.ssl.enable","true");p.put("mail.imaps.ssl.checkserveridentity","true");p.put("mail.imaps.connectiontimeout","10000");p.put("mail.imaps.timeout","20000");p.put("mail.imaps.writetimeout","20000");return p;}
    private List<AccountRow> accounts(String email)throws Exception{try(Connection c=open()){return accounts(c,userId(c,email));}}
    private List<AccountRow> accounts()throws Exception{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT id,user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid,initialized,collect_after FROM mail_accounts ORDER BY id");ResultSet r=s.executeQuery()){List<AccountRow> list=new ArrayList<>();while(r.next())list.add(row(r));return list;}}
    private List<AccountRow> accounts(Connection c,long userId)throws Exception{List<AccountRow> list=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT id,user_id,email,provider,encrypted_password,encryption_iv,auth_tag,last_uid,initialized,collect_after FROM mail_accounts WHERE user_id=?")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){while(r.next())list.add(row(r));}}return list;}
    private long pendingCount(Connection c,long userId)throws Exception{try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM collected_mails WHERE user_id=? AND processed_at IS NULL")){s.setLong(1,userId);try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):0;}}}
    private AccountRow row(ResultSet r)throws Exception{return new AccountRow(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBytes(5),r.getBytes(6),r.getBytes(7),r.getLong(8),r.getBoolean(9),r.getTimestamp(10).toInstant());}
    private Connection open()throws Exception{LegacyDatabaseUrl config=LegacyDatabaseUrl.parse(AppEnvironment.databaseUrl(environment));Properties p=new Properties();if(config.username()!=null)p.setProperty("user",config.username());if(config.password()!=null)p.setProperty("password",config.password());p.setProperty("ApplicationName","job-tracker-mail-inbox");return PooledConnections.open(config,p);}
    private long userId(Connection c,String email)throws Exception{try(PreparedStatement s=c.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND disabled_at IS NULL")){s.setString(1,email.trim().toLowerCase(Locale.ROOT));try(ResultSet r=s.executeQuery()){if(r.next())return r.getLong(1);}}throw new ValidationException("账号不可用");}
    private String encryptionKey(){String value=AppEnvironment.encryptionKey(environment);return value==null?"":value;}private void requireEncryption(){if(encryptionKey().length()<32)throw new ValidationException("服务器尚未配置安全密钥");}
    private String host(String provider){return "qq".equals(provider)?"imap.qq.com":"imap.163.com";}private String required(String value,String name,int max){String clean=value==null?"":value.trim();if(clean.isBlank())throw new ValidationException("请填写"+name);if(clean.length()>max)throw new ValidationException(name+"内容过长");return clean;}
    private String decode(String value){try{return value==null?"(无主题)":MimeUtility.decodeText(value);}catch(Exception e){return value;}}private String addresses(Address[] values){if(values==null)return "";StringBuilder out=new StringBuilder();for(Address value:values){if(!out.isEmpty())out.append(", ");out.append(decode(value.toString()));}return out.toString();}
    private String limit(String value,int max){return value.length()<=max?value:value.substring(0,max);}private String instant(Timestamp value){return value==null?"":value.toInstant().toString();}
    public record InboxView(List<AccountView> accounts,List<MailView> messages,long pendingCount){}public record AccountView(long id,String email,String provider,String lastSyncedAt,String lastError){}public record MailView(long id,String sender,String subject,String body,String receivedAt,String accountEmail){}
    private record AccountRow(long id,long userId,String email,String provider,byte[] encrypted,byte[] iv,byte[] tag,long lastUid,boolean initialized,Instant collectAfter){}
    private record FetchedMail(long uid,String sender,String subject,String body,Instant receivedAt){}
    private record SyncResult(long newestUid,List<FetchedMail> mails){}
    public static class ValidationException extends RuntimeException{public ValidationException(String message){super(message);}}
}
