package us.mn.state.health.lims.login.action;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.json.JSONObject;
import us.mn.state.health.lims.common.action.IActionConstants;
import us.mn.state.health.lims.common.log.LogEvent;
import us.mn.state.health.lims.common.util.SystemConfiguration;
import us.mn.state.health.lims.common.util.validator.ActionError;
import us.mn.state.health.lims.login.dao.LoginDAO;
import us.mn.state.health.lims.login.dao.UserModuleDAO;
import us.mn.state.health.lims.login.daoimpl.LoginDAOImpl;
import us.mn.state.health.lims.login.daoimpl.UserModuleDAOImpl;
import us.mn.state.health.lims.login.valueholder.Login;
import us.mn.state.health.lims.login.valueholder.UserSessionData;
import us.mn.state.health.lims.systemuser.dao.SystemUserDAO;
import us.mn.state.health.lims.systemuser.daoimpl.SystemUserDAOImpl;
import us.mn.state.health.lims.systemuser.valueholder.SystemUser;
import us.mn.state.health.lims.systemusermodule.dao.PermissionAgentModuleDAO;
import us.mn.state.health.lims.systemusermodule.daoimpl.RoleModuleDAOImpl;
import us.mn.state.health.lims.systemusermodule.valueholder.RoleModule;
import us.mn.state.health.lims.userrole.dao.UserRoleDAO;
import us.mn.state.health.lims.userrole.daoimpl.UserRoleDAOImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;

public class KeycloakCallbackAction extends LoginBaseAction {

    protected ActionForward performAction(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                          HttpServletResponse response) throws Exception {
        if (alreadyLoggedIn(request)) {
            return mapping.findForward("success");
        }

        if (!SystemConfiguration.getInstance().isKeycloakSSOEnabled()) {
            return mapping.findForward(FWD_FAIL);
        }

        String state = request.getParameter("state");
        String code = request.getParameter("code");

        String expectedState = (String) request.getSession().getAttribute(KEYCLOAK_OAUTH_STATE);
        request.getSession().removeAttribute(KEYCLOAK_OAUTH_STATE);

        if (GenericValidator.isBlankOrNull(code) || GenericValidator.isBlankOrNull(state)
                || GenericValidator.isBlankOrNull(expectedState) || !expectedState.equals(state)) {
            addLoginError(request, "login.error.sso.state");
            return mapping.findForward(FWD_FAIL);
        }

        JSONObject tokenPayload = exchangeCodeForToken(code);
        if (tokenPayload == null) {
            addLoginError(request, "login.error.sso.token");
            return mapping.findForward(FWD_FAIL);
        }

        String loginName = resolveLoginName(tokenPayload);

        if (GenericValidator.isBlankOrNull(loginName)) {
            addLoginError(request, "login.error.sso.userinfo");
            return mapping.findForward(FWD_FAIL);
        }

        LoginDAO loginDAO = new LoginDAOImpl();
        Login loginInfo = loginDAO.getUserProfile(loginName);

        if (loginInfo == null) {
            addLoginError(request, "login.error.sso.user.not.mapped");
            return mapping.findForward(FWD_FAIL);
        }

        if (YES.equalsIgnoreCase(loginInfo.getAccountDisabled())) {
            addLoginError(request, "login.error.account.disable");
            return mapping.findForward(FWD_FAIL);
        }

        if (YES.equalsIgnoreCase(loginInfo.getAccountLocked())) {
            addLoginError(request, "login.error.account.lock");
            return mapping.findForward(FWD_FAIL);
        }

        if (loginInfo.getPasswordExpiredDayNo() <= 0) {
            addLoginError(request, "login.error.password.expired");
            return mapping.findForward(FWD_FAIL);
        }

        if (loginInfo.getSystemUserId() == 0) {
            ActionMessages errors = new ActionMessages();
            errors.add(ActionMessages.GLOBAL_MESSAGE,
                    new ActionError("login.error.system.user.id", loginInfo.getLoginName(), null));
            saveErrors(request, errors);
            return mapping.findForward(FWD_FAIL);
        }

        SystemUserDAO systemUserDAO = new SystemUserDAOImpl();
        SystemUser systemUser = new SystemUser();
        systemUser.setId(String.valueOf(loginInfo.getSystemUserId()));
        systemUserDAO.getData(systemUser);

        int timeOut = Integer.parseInt(loginInfo.getUserTimeOut());
        request.getSession().setMaxInactiveInterval(timeOut * 60);

        UserSessionData usd = new UserSessionData();
        usd.setSytemUserId(loginInfo.getSystemUserId());
        usd.setLoginName(loginInfo.getLoginName());
        usd.setElisUserName(systemUser.getNameForDisplay());
        usd.setUserTimeOut(timeOut * 60);
        request.getSession().setAttribute(USER_SESSION_DATA, usd);

        String idToken = tokenPayload.optString("id_token", null);
        if (!GenericValidator.isBlankOrNull(idToken)) {
            request.getSession().setAttribute(KEYCLOAK_ID_TOKEN, idToken);
        }

        if (SystemConfiguration.getInstance().getPermissionAgent().equals("ROLE")) {
            HashSet<String> permittedPages = getPermittedForms(usd.getSystemUserId());
            request.getSession().setAttribute(IActionConstants.PERMITTED_ACTIONS_MAP, permittedPages);
        }

        if (!YES.equalsIgnoreCase(loginInfo.getIsAdmin())) {
            UserModuleDAO userModuleDAO = new UserModuleDAOImpl();
            if (!userModuleDAO.isUserModuleFound(request)) {
                addLoginError(request, "login.error.no.module");
                return mapping.findForward(FWD_FAIL);
            }
        }

        return mapping.findForward("success");
    }

    @SuppressWarnings("unchecked")
    private HashSet<String> getPermittedForms(int systemUserId) {
        HashSet<String> permittedPages = new HashSet<String>();

        UserRoleDAO userRoleDAO = new UserRoleDAOImpl();
        List<String> roleIds = userRoleDAO.getRoleIdsForUser(Integer.toString(systemUserId));

        PermissionAgentModuleDAO roleModuleDAO = new RoleModuleDAOImpl();

        for (String roleId : roleIds) {
            List<RoleModule> roleModules = roleModuleDAO.getAllPermissionModulesByAgentId(Integer.parseInt(roleId));

            for (RoleModule roleModule : roleModules) {
                permittedPages.add(roleModule.getSystemModule().getSystemModuleName());
            }
        }

        return permittedPages;
    }

    private String resolveLoginName(JSONObject tokenPayload) {
        String accessToken = tokenPayload.optString("access_token", "");
        if (GenericValidator.isBlankOrNull(accessToken)) {
            return "";
        }

        JSONObject userInfo = fetchUserInfo(accessToken);

        if (userInfo == null) {
            return "";
        }

        String loginName = userInfo.optString("preferred_username", "");
        if (GenericValidator.isBlankOrNull(loginName)) {
            loginName = userInfo.optString("email", "");
        }

        if (GenericValidator.isBlankOrNull(loginName)) {
            loginName = userInfo.optString("sub", "");
        }

        return GenericValidator.isBlankOrNull(loginName) ? "" : loginName.trim();
    }

    private JSONObject fetchUserInfo(String accessToken) {
        HttpURLConnection connection = null;
        try {
            String userInfoEndpoint = SystemConfiguration.getInstance().getKeycloakUserInfoEndpoint();

            if (GenericValidator.isBlankOrNull(userInfoEndpoint)) {
                LogEvent.logError("KeycloakCallbackAction", "fetchUserInfo", "Missing Keycloak userinfo endpoint configuration");
                return null;
            }

            connection = (HttpURLConnection) new java.net.URL(userInfoEndpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();

            String payload = readStream(stream);

            if (statusCode < 200 || statusCode >= 300) {
                LogEvent.logError("KeycloakCallbackAction", "fetchUserInfo",
                        "Userinfo endpoint returned status " + statusCode + " payload: " + payload);
                return null;
            }

            return new JSONObject(payload);
        } catch (Exception e) {
            LogEvent.logError("KeycloakCallbackAction", "fetchUserInfo", e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject exchangeCodeForToken(String code) {
        HttpURLConnection connection = null;
        try {
            String tokenEndpoint = SystemConfiguration.getInstance().getKeycloakTokenEndpoint();
            String clientId = SystemConfiguration.getInstance().getKeycloakClientId();
            String clientSecret = SystemConfiguration.getInstance().getKeycloakClientSecret();
            String redirectUri = SystemConfiguration.getInstance().getKeycloakRedirectUri();

            if (GenericValidator.isBlankOrNull(tokenEndpoint) || GenericValidator.isBlankOrNull(clientId)
                    || GenericValidator.isBlankOrNull(redirectUri)) {
                LogEvent.logError("KeycloakCallbackAction", "exchangeCodeForToken",
                        "Missing Keycloak token endpoint/clientId/redirectUri configuration");
                return null;
            }

            StringBuilder body = new StringBuilder();
            body.append("grant_type=authorization_code");
            body.append("&code=").append(URLEncoder.encode(code, "UTF-8"));
            body.append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"));
            body.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"));

            if (!GenericValidator.isBlankOrNull(clientSecret)) {
                body.append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"));
            }

            byte[] bodyBytes = body.toString().getBytes("UTF-8");

            connection = (HttpURLConnection) new java.net.URL(tokenEndpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bodyBytes);
            outputStream.flush();
            outputStream.close();

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();

            String payload = readStream(stream);

            if (statusCode < 200 || statusCode >= 300) {
                LogEvent.logError("KeycloakCallbackAction", "exchangeCodeForToken",
                        "Token endpoint returned status " + statusCode + " payload: " + payload);
                return null;
            }

            return new JSONObject(payload);
        } catch (Exception e) {
            LogEvent.logError("KeycloakCallbackAction", "exchangeCodeForToken", e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        reader.close();
        return builder.toString();
    }

    private void addLoginError(HttpServletRequest request, String messageKey) {
        ActionMessages errors = new ActionMessages();
        errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionError(messageKey, null, null));
        saveErrors(request, errors);
    }

    protected String getPageTitleKey() {
        return "login.title";
    }

    protected String getPageSubtitleKey() {
        return "login.subTitle";
    }
}
