package com.ai.opt.sso.handler;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.annotation.Resource;
import javax.security.auth.login.LoginException;
import javax.validation.constraints.NotNull;

import org.apache.commons.beanutils.BeanUtils;
import org.jasig.cas.Message;
import org.jasig.cas.authentication.BasicCredentialMetaData;
import org.jasig.cas.authentication.Credential;
import org.jasig.cas.authentication.HandlerResult;
import org.jasig.cas.authentication.PreventedException;
import org.jasig.cas.authentication.handler.NoOpPrincipalNameTransformer;
import org.jasig.cas.authentication.handler.PasswordEncoder;
import org.jasig.cas.authentication.handler.PlainTextPasswordEncoder;
import org.jasig.cas.authentication.handler.PrincipalNameTransformer;
import org.jasig.cas.authentication.handler.support.AbstractPreAndPostProcessingAuthenticationHandler;
import org.jasig.cas.authentication.principal.SimplePrincipal;
import org.jasig.cas.authentication.support.PasswordPolicyConfiguration;
import org.springframework.util.StringUtils;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.RPCSystemException;
import com.ai.opt.sdk.components.mcs.MCSClientFactory;
import com.ai.opt.sso.exception.PasswordErrorException;
import com.ai.opt.sso.exception.PasswordIsNullException;
import com.ai.opt.sso.principal.BssCredentials;
import com.ai.opt.sso.service.LoadAccountService;
import com.ai.opt.sso.util.RegexUtils;
import com.ai.opt.uac.web.constants.Constants;
import com.ai.opt.uac.web.constants.Constants.LoginConstant;
import com.ai.slp.user.api.login.param.LoginRequest;
import com.ai.slp.user.api.login.param.LoginResponse;

public final class BssCredentialsAuthencationHandler
        extends AbstractPreAndPostProcessingAuthenticationHandler {

    @Resource
    private LoadAccountService loadAccountService;

    @NotNull
    private PasswordEncoder passwordEncoder;

    @NotNull
    private PrincipalNameTransformer principalNameTransformer;

    private PasswordPolicyConfiguration passwordPolicyConfiguration;

    public BssCredentialsAuthencationHandler() {
        this.passwordEncoder = new PlainTextPasswordEncoder();
        this.principalNameTransformer = new NoOpPrincipalNameTransformer();
    }

    @Override
    public boolean supports(Credential credentials) {
        return credentials != null
                && (BssCredentials.class.isAssignableFrom(credentials.getClass()));
    }

    @Override
    protected HandlerResult doAuthentication(final Credential credentials)
            throws GeneralSecurityException, PreventedException {
        logger.debug("开始认证用户凭证credentials");
        if (credentials == null) {
            logger.info("用户凭证credentials为空");
            throw new LoginException("Credentials is null");
        }
        
        BssCredentials bssCredentials = (BssCredentials) credentials;
        final String username = bssCredentials.getUsername();
        final String pwdFromPage = bssCredentials.getPassword();
        final String captchaCode = bssCredentials.getCaptchaCode();

        // 用户名非空校验
        if (!StringUtils.hasText(username)) {
            logger.error("请输入用户名/手机号码/邮箱地址");
            throw new BusinessException("ERROR_CODE1","请输入用户名/手机号码/邮箱地址");
        }
        // 密码非空校验
        if (!StringUtils.hasText(pwdFromPage)) {
            logger.error("密码为空！");
            throw new PasswordIsNullException();
        }
        if (!StringUtils.hasText(captchaCode)) {
            logger.error("验证码为空！");
            throw new PasswordIsNullException();
        }
        
        //校验验证码
        //String service_url = MCSClientFactory.getCacheClient(LoginConstant.CACHE_NAMESPACE).get(Constants.URLConstant.INDEX_URL_KEY);
        LoginRequest request = new LoginRequest();
        LoginResponse response = null;
            request.setTenantId(bssCredentials.getTenantId());
            request.setUserType(bssCredentials.getUserType());
            if (RegexUtils.checkIsPhone(bssCredentials.getUsername())) {
                request.setUserMp(bssCredentials.getUsername());
            } else if (RegexUtils.checkIsEmail(bssCredentials.getUsername())) {
                request.setUserEmail(bssCredentials.getUsername());
            } else {
                request.setUserLoginName(bssCredentials.getUsername());
            }
            try{
            response = loadAccountService.login(request);
            }catch (RPCSystemException e) {
                e.printStackTrace();
            }
            String dbPwd = response.getUserLoginPwd();
            logger.info("【dbPwd】=" + dbPwd);
            //String encryDbPwd = Md5Encoder.encodePassword(SSOConstants.AIOPT_SALT_KEY + dbPwd);
            //logger.info("【encryDbPwd】=" + encryDbPwd);
            logger.info("【pwdFromPage】=" + pwdFromPage);
            if (!pwdFromPage.equals(dbPwd)) {
                // 密码不对
                logger.error("密码错误！");
                throw new PasswordErrorException();
            }
            /*
             * if(!SSOConstants.ACCOUNT_ACITVE_STATE.equals(user.getState())){ //密码不对 throw new
             * CredentialException("账号状态异常"); } Date currentDate=new Date(); Date
             * acitveDate=user.getActiveTime(); Date inactiveDate=user.getInactiveTime();
             * if(acitveDate!=null&&currentDate.before(acitveDate)){ throw new
             * CredentialException("账号未生效"); }
             * if(inactiveDate!=null&&inactiveDate.before(currentDate)){ throw new
             * CredentialException("账号已失效"); }
             */

            try {
                BeanUtils.copyProperties(bssCredentials, response);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            logger.info("用户 [" + username + "] 认证成功。");
            return creatHandlerResult(bssCredentials, new SimplePrincipal(response.getUserId()), null);
        } 

    private HandlerResult creatHandlerResult(BssCredentials bssCredentials,
            SimplePrincipal simplePrincipal, List<Message> warnings) {
        return new HandlerResult(this, new BasicCredentialMetaData(bssCredentials), simplePrincipal,
                warnings);
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public PrincipalNameTransformer getPrincipalNameTransformer() {
        return principalNameTransformer;
    }

    public void setPrincipalNameTransformer(PrincipalNameTransformer principalNameTransformer) {
        this.principalNameTransformer = principalNameTransformer;
    }

    public PasswordPolicyConfiguration getPasswordPolicyConfiguration() {
        return passwordPolicyConfiguration;
    }

    public void setPasswordPolicyConfiguration(
            PasswordPolicyConfiguration passwordPolicyConfiguration) {
        this.passwordPolicyConfiguration = passwordPolicyConfiguration;
    }

}
