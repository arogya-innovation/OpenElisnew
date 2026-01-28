package us.mn.state.health.lims.sample.action;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import us.mn.state.health.lims.common.action.IActionConstants;
import us.mn.state.health.lims.common.formfields.FormFields;
import us.mn.state.health.lims.common.services.DisplayListService;
import us.mn.state.health.lims.common.services.DisplayListService.ListType;
import us.mn.state.health.lims.common.util.ConfigurationProperties;
import us.mn.state.health.lims.common.util.ConfigurationProperties.Property;
import us.mn.state.health.lims.common.util.DateUtil;
import us.mn.state.health.lims.hibernate.HibernateUtil;
import us.mn.state.health.lims.patient.action.bean.PatientManagmentInfo;
import us.mn.state.health.lims.payment.service.PaymentValidationService;
import us.mn.state.health.lims.payment.service.PaymentValidationService.PaymentStatus;
import us.mn.state.health.lims.provider.dao.ProviderDAO;
import us.mn.state.health.lims.provider.daoimpl.ProviderDAOImpl;
import us.mn.state.health.lims.samplesource.dao.SampleSourceDAO;
import us.mn.state.health.lims.samplesource.daoimpl.SampleSourceDAOImpl;
import us.mn.state.health.lims.siteinformation.dao.SiteInformationDAO;
import us.mn.state.health.lims.siteinformation.daoimpl.SiteInformationDAOImpl;
import us.mn.state.health.lims.siteinformation.valueholder.SiteInformation;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

public class SamplePatientEntryAction extends BaseSampleEntryAction {

    private final SampleSourceDAO sampleSourceDAO = new SampleSourceDAOImpl();
    private final ProviderDAO providerDAO = new ProviderDAOImpl();
    private final PaymentValidationService paymentValidationService =
            new PaymentValidationService();

    @Override
    protected ActionForward performAction(
            ActionMapping mapping,
            ActionForm form,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {

        String[] fieldsetOrder = {"order", "samples", "patient"};
        String forward = "success";

        request.getSession().setAttribute(
                IActionConstants.SAVE_DISABLED,
                IActionConstants.TRUE
        );

        BaseActionForm dynaForm = (BaseActionForm) form;
        dynaForm.initialize(mapping);

        String dateAsText = DateUtil.getCurrentDateAsText();

        // ==================================================
        // UI REQUIRED INITIALIZATION (DO NOT REMOVE)
        // ==================================================
        PropertyUtils.setProperty(dynaForm, "currentDate", dateAsText);
        PropertyUtils.setProperty(dynaForm, "receivedDateForDisplay", dateAsText);
        PropertyUtils.setProperty(dynaForm, "requestDate", dateAsText);
        PropertyUtils.setProperty(dynaForm, "collapsePatientInfo", Boolean.TRUE);
        PropertyUtils.setProperty(dynaForm, "patientProperties", new PatientManagmentInfo());
        PropertyUtils.setProperty(dynaForm, "labNo", "");

        // ==================================================
        // Load configurable fieldset order
        // ==================================================
        SiteInformationDAO siteInfoDAO = new SiteInformationDAOImpl();
        SiteInformation orderInfo =
                siteInfoDAO.getSiteInformationByName("SampleEntryFieldsetOrder");

        if (orderInfo != null && orderInfo.getValue() != null &&
                !orderInfo.getValue().isEmpty()) {
            fieldsetOrder = orderInfo.getValue().split("\\|");
        }

        PropertyUtils.setProperty(
                dynaForm,
                "sampleEntryFieldsetOrder",
                Arrays.asList(fieldsetOrder)
        );

        // ==================================================
        // Load UI dropdowns (CRITICAL)
        // ==================================================
        PropertyUtils.setProperty(dynaForm,
                "sampleTypes",
                DisplayListService.getList(ListType.SAMPLE_TYPE));

        PropertyUtils.setProperty(dynaForm,
                "orderTypes",
                DisplayListService.getList(
                        ListType.SAMPLE_PATIENT_PRIMARY_ORDER_TYPE));

        PropertyUtils.setProperty(dynaForm,
                "followupPeriodOrderTypes",
                DisplayListService.getList(
                        ListType.SAMPLE_PATIENT_FOLLOW_UP_PERIOD_ORDER_TYPE));

        PropertyUtils.setProperty(dynaForm,
                "initialPeriodOrderTypes",
                DisplayListService.getList(
                        ListType.SAMPLE_PATIENT_INITIAL_PERIOD_ORDER_TYPE));

        PropertyUtils.setProperty(dynaForm,
                "testSectionList",
                DisplayListService.getList(ListType.TEST_SECTION));

        PropertyUtils.setProperty(dynaForm,
                "sampleSourceList",
                sampleSourceDAO.getAllActive());

        PropertyUtils.setProperty(dynaForm,
                "providerList",
                providerDAO.getAllActiveProviders());

        // ==================================================
        // Payment options (config-driven)
        // ==================================================
        boolean needPaymentOptions =
                ConfigurationProperties.getInstance()
                        .isPropertyValueEqual(Property.trackPatientPayment, "true");

        if (needPaymentOptions) {
            setDictionaryList(dynaForm, "paymentOptions", "PP", true);
        }

        // ==================================================
        // Extract patientId → resolve UUID
        // ==================================================
        Integer patientId = extractPatientId(dynaForm);
        String patientUuid = getPatientUuid(patientId);

        request.setAttribute("patientId", patientId);
        request.setAttribute("patientUuid", patientUuid);

        // ==================================================
        // Payment validation (UUID-based)
        // ==================================================
        if (paymentValidationService.isEnabled() && patientUuid != null) {

            PaymentStatus status =
                    paymentValidationService.validatePayment(patientUuid);

            if (!status.isAllowSample()) {
                request.setAttribute("paymentBlocked", "true");
                request.setAttribute("paymentStatus", status.getStatus());
                request.setAttribute("paymentMessage", status.getMessage());
            } else {
                request.setAttribute("paymentVerified", "true");
            }
        }

        return mapping.findForward(forward);
    }

    // ==================================================
    // Helpers
    // ==================================================

    private Integer extractPatientId(BaseActionForm form) {
        try {
            Object pk = PropertyUtils.getProperty(form, "patientPK");
            if (pk != null) {
                return Integer.valueOf(pk.toString());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getPatientUuid(Integer patientId) {
        if (patientId == null) return null;

        String sql = "SELECT uuid FROM clinlims.patient WHERE id = ?";

        try (Connection conn = HibernateUtil.getSession().connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("uuid");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
