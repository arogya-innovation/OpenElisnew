package us.mn.state.health.lims.login.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.commons.validator.GenericValidator;
import us.mn.state.health.lims.common.log.LogEvent;
import us.mn.state.health.lims.common.util.SystemConfiguration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;

public class LogoutPageAction extends LoginBaseAction {

	protected ActionForward performAction(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
        LogEvent.logWarn("LogoutPageAction", "performAction", request.getSession().getId());
        String idToken = (String) request.getSession().getAttribute(KEYCLOAK_ID_TOKEN);

        if (alreadyLoggedIn(request)) {
            request.getSession().invalidate();
            request.getSession(true);
        }

        if (SystemConfiguration.getInstance().isKeycloakSSOEnabled()) {
            String logoutEndpoint = SystemConfiguration.getInstance().getKeycloakLogoutEndpoint();
            String postLogoutRedirectUri = SystemConfiguration.getInstance().getKeycloakPostLogoutRedirectUri();
            String clientId = SystemConfiguration.getInstance().getKeycloakClientId();

            if (!GenericValidator.isBlankOrNull(logoutEndpoint)) {
                StringBuilder redirect = new StringBuilder(logoutEndpoint);
                boolean hasQuery = logoutEndpoint.contains("?");

                if (!GenericValidator.isBlankOrNull(idToken)) {
                    redirect.append(hasQuery ? "&" : "?")
                            .append("id_token_hint=")
                            .append(URLEncoder.encode(idToken, "UTF-8"));
                    hasQuery = true;
                }

                if (!GenericValidator.isBlankOrNull(postLogoutRedirectUri)) {
                    redirect.append(hasQuery ? "&" : "?")
                            .append("post_logout_redirect_uri=")
                            .append(URLEncoder.encode(postLogoutRedirectUri, "UTF-8"));
                    hasQuery = true;
                }

                if (!GenericValidator.isBlankOrNull(clientId)) {
                    redirect.append(hasQuery ? "&" : "?")
                            .append("client_id=")
                            .append(URLEncoder.encode(clientId, "UTF-8"));
                }

                response.sendRedirect(redirect.toString());
                return null;
            }
        }

        return mapping.findForward("success");
	}

	protected String getPageTitleKey() {
		return "login.title";
	}

	protected String getPageSubtitleKey() {
		return "login.subTitle";
	}
}
