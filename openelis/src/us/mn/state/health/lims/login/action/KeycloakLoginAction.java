package us.mn.state.health.lims.login.action;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import us.mn.state.health.lims.common.log.LogEvent;
import us.mn.state.health.lims.common.util.SystemConfiguration;
import us.mn.state.health.lims.common.util.validator.ActionError;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.UUID;

public class KeycloakLoginAction extends LoginBaseAction {

    protected ActionForward performAction(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                          HttpServletResponse response) throws Exception {
        if (alreadyLoggedIn(request)) {
            return mapping.findForward("dashboard");
        }

        if (!SystemConfiguration.getInstance().isKeycloakSSOEnabled()) {
            return mapping.findForward(FWD_FAIL);
        }

        String authorizationEndpoint = SystemConfiguration.getInstance().getKeycloakAuthorizationEndpoint();
        String clientId = SystemConfiguration.getInstance().getKeycloakClientId();
        String redirectUri = SystemConfiguration.getInstance().getKeycloakRedirectUri();
        String scopes = SystemConfiguration.getInstance().getKeycloakScopes();

        if (GenericValidator.isBlankOrNull(authorizationEndpoint) || GenericValidator.isBlankOrNull(clientId)
                || GenericValidator.isBlankOrNull(redirectUri)) {
            ActionMessages errors = new ActionMessages();
            errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionError("login.error.sso.configuration", null, null));
            saveErrors(request, errors);
            return mapping.findForward(FWD_FAIL);
        }

        String state = UUID.randomUUID().toString();
        request.getSession().setAttribute(KEYCLOAK_OAUTH_STATE, state);

        StringBuilder target = new StringBuilder(authorizationEndpoint);
        target.append("?response_type=code");
        target.append("&client_id=").append(urlEncode(clientId));
        target.append("&redirect_uri=").append(urlEncode(redirectUri));
        target.append("&scope=").append(urlEncode(scopes));
        target.append("&state=").append(urlEncode(state));

        LogEvent.logInfo("KeycloakLoginAction", "performAction", "Redirecting to Keycloak authorization endpoint");
        response.sendRedirect(target.toString());
        return null;
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    protected String getPageTitleKey() {
        return "login.title";
    }

    protected String getPageSubtitleKey() {
        return "login.subTitle";
    }
}
