package com.jobtracker.migrationpoc.web;

import com.jobtracker.migrationpoc.database.ApplicationSandboxService;
import com.jobtracker.migrationpoc.database.BackupSandboxService;
import com.jobtracker.migrationpoc.database.LegacyReadService;
import com.jobtracker.migrationpoc.database.LegacyReadService.LegacyUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/poc")
public class PocCompanyLinksController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocCompanyLinksController.class);
    private final PocAuthController auth;
    private final LegacyReadService legacy;
    private final ApplicationSandboxService sandbox;
    private final BackupSandboxService backup;
    public PocCompanyLinksController(PocAuthController auth, LegacyReadService legacy, ApplicationSandboxService sandbox, BackupSandboxService backup) { this.auth=auth;this.legacy=legacy;this.sandbox=sandbox;this.backup=backup; }

    @GetMapping("/company-links")
    public ResponseEntity<?> links(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false) String token) {
        try { Optional<LegacyUser> user=auth.authenticatedUser(token); if(user.isEmpty()) return error(HttpStatus.UNAUTHORIZED,"请先登录");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sandbox.status().enabled()?backup.companyLinks(user.get().email()):legacy.findCompanyLinks(user.get().id()));
        } catch(Exception e){LOGGER.warn("company links read failed",e);return error(HttpStatus.SERVICE_UNAVAILABLE,"数据库暂时不可用");}
    }
    @PostMapping("/company-links")
    public ResponseEntity<?> save(@CookieValue(value=PocAuthController.COOKIE_NAME,required=false) String token,@RequestBody LinksRequest body,HttpServletRequest request){
        if(!sameOrigin(request))return error(HttpStatus.FORBIDDEN,"请求来源无效");
        try{Optional<LegacyUser> user=auth.authenticatedUser(token);if(user.isEmpty())return error(HttpStatus.UNAUTHORIZED,"请先登录");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(backup.saveCompanyLinks(user.get().email(),body==null?null:body.items()));}
        catch(IllegalArgumentException e){return error(HttpStatus.BAD_REQUEST,e.getMessage());}catch(Exception e){LOGGER.warn("company links save failed",e);return error(HttpStatus.SERVICE_UNAVAILABLE,"测试数据库暂时不可用");}
    }
    private ResponseEntity<Map<String,String>> error(HttpStatus status,String message){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message",message));}
    private boolean sameOrigin(HttpServletRequest request){String origin=request.getHeader("Origin");if(origin==null||origin.isBlank())return true;try{URI value=URI.create(origin);String forwarded=request.getHeader("X-Forwarded-Proto");String scheme=forwarded==null||forwarded.isBlank()?request.getScheme():forwarded.split(",",2)[0].trim();String host=request.getHeader("Host");return scheme.equalsIgnoreCase(value.getScheme())&&host!=null&&host.equalsIgnoreCase(value.getRawAuthority());}catch(IllegalArgumentException e){return false;}}
    public record LinksRequest(JsonNode items){}
}