package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import com.jobtracker.migrationpoc.database.MailInboxService;
import com.jobtracker.migrationpoc.database.MailInboxService.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/poc/mail-inbox")
public class PocMailInboxController {
    private final PocAuthController auth;private final MailInboxService inbox;
    public PocMailInboxController(PocAuthController auth,MailInboxService inbox){this.auth=auth;this.inbox=inbox;}
    @GetMapping public ResponseEntity<?> view(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token){return withUser(token,u->inbox.inbox(u.email()));}
    @PostMapping("/accounts") public ResponseEntity<?> save(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token,@RequestBody AccountRequest body,HttpServletRequest request){if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");return withUser(token,u->inbox.saveAccount(u.email(),body.email(),body.provider(),body.password()));}
    @DeleteMapping("/accounts/{id}") public ResponseEntity<?> remove(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token,@PathVariable long id,HttpServletRequest request){if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");return withUser(token,u->{inbox.removeAccount(u.email(),id);return Map.of("ok",true);});}
    @PostMapping("/sync") public ResponseEntity<?> sync(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token,HttpServletRequest request){if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");return withUser(token,u->inbox.sync(u.email()));}
    @PatchMapping("/messages/{id}/processed") public ResponseEntity<?> processed(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token,@PathVariable long id,HttpServletRequest request){if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");return withUser(token,u->{inbox.process(u.email(),id);return Map.of("ok",true);});}
    @DeleteMapping("/messages/{id}") public ResponseEntity<?> delete(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false)String token,@PathVariable long id,HttpServletRequest request){if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");return withUser(token,u->{inbox.delete(u.email(),id);return Map.of("ok",true);});}
    private ResponseEntity<?> withUser(String token,Work work){try{Optional<LegacyUser> user=auth.authenticatedUser(token);if(user.isEmpty())return error(HttpStatus.UNAUTHORIZED,"请先登录");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.run(user.get()));}catch(ValidationException e){return error(HttpStatus.BAD_REQUEST,e.getMessage());}catch(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"邮件服务暂时不可用");}}
    private boolean sameOrigin(HttpServletRequest request){String origin=request.getHeader("Origin");if(origin==null||origin.isBlank())return true;try{return request.getServerName().equalsIgnoreCase(URI.create(origin).getHost());}catch(Exception e){return false;}}
    private ResponseEntity<Map<String,String>> error(HttpStatus status,String message){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message",message));}
    private interface Work{Object run(LegacyUser user)throws Exception;}public record AccountRequest(String email,String provider,String password){}
}
