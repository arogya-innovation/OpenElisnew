package us.mn.state.health.lims.sample.action;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import us.mn.state.health.lims.common.action.BaseActionForm;
import us.mn.state.health.lims.common.action.IActionConstants;
import us.mn.state.health.lims.common.formfields.FormFields;
import us.mn.state.health.lims.common.services.DisplayListService;
import us.mn.state.health.lims.common.services.DisplayListService.ListType;
import us.mn.state.health.lims.common.util.ConfigurationProperties;
import us.mn.state.health.lims.common.util.ConfigurationProperties.Property;
import us.mn.state.health.lims.common.util.DateUtil;
import us.mn.state.health.lims.patient.action.bean.PatientManagmentInfo;
import us.mn.state.health.lims.provider.dao.ProviderDAO;
import us.mn.state.health.lims.provider.daoimpl.ProviderDAOImpl;
import us.mn.state.health.lims.samplesource.dao.SampleSourceDAO;
import us.mn.state.health.lims.samplesource.daoimpl.SampleSourceDAOImpl;
import us.mn.state.health.lims.siteinformation.dao.SiteInformationDAO;
import us.mn.state.health.lims.siteinformation.daoimpl.SiteInformationDAOImpl;
import us.mn.state.health.lims.siteinformation.valueholder.SiteInformation;
import us.mn.state.health.lims.payment.service.PaymentValidationService;
import us.mn.state.health.lims.payment.service.PaymentValidationService.PaymentStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * SamplePatientEntryAction
 * Handles patient sample entry with payment validation (paywall)
 */
public class SamplePatientEntryAction extends BaseSampleEntryAction {

    private static final Log LOG = LogFactory.getLog(SamplePatientEntryAction.class);

    private SampleSourceDAO sampleSourceDAO;
    private ProviderDAO providerDAO;
    private PaymentValidationService paymentValidationService;

    public SamplePatientEntryAction() {
        this.sampleSourceDAO = new SampleSourceDAOImpl();
        this.providerDAO = new ProviderDAOImpl();
        this.paymentValidationService = new PaymentValidationService();
    }

    @Override
    protected ActionForward performAction(ActionMapping mapping, ActionForm form,
                                          HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        String[] fieldsetOrder = {"order", "samples", "patient"};
        String forward = "success";

        request.getSession().setAttribute(IActionConstants.SAVE_DISABLED, IActionConstants.TRUE);

        BaseActionForm dynaForm = (BaseActionForm) form;
        dynaForm.initialize(mapping);

        String dateAsText = DateUtil.getCurrentDateAsText();
        PropertyUtils.setProperty(dynaForm, "currentDate", dateAsText);

        boolean needRequesterList =
                FormFields.getInstance().useField(FormFields.Field.RequesterSiteList);

        boolean needSampleInitialConditionList =
                FormFields.getInstance().useField(FormFields.Field.InitialSampleCondition);

        boolean needPaymentOptions =
                ConfigurationProperties.getInstance()
                        .isPropertyValueEqual(Property.trackPatientPayment, "true");

        SiteInformationDAO siteInfoDAO = new SiteInformationDAOImpl();

        SiteInformation fieldsetOrderInfo =
                siteInfoDAO.getSiteInformationByName("SampleEntryFieldsetOrder");

        if (fieldsetOrderInfo != null &&
                fieldsetOrderInfo.getValue() != null &&
                !fieldsetOrderInfo.getValue().isEmpty()) {

            fieldsetOrder = fieldsetOrderInfo.getValue().split("\\|");
        }

        /* ================= PAYMENT VALIDATION (PAYWALL) ================= */

        SiteInformation paymentValidationEnabledInfo =
                siteInfoDAO.getSiteInformationByName("enablePaymentValidation");

        boolean paymentValidationEnabled =
                paymentValidationEnabledInfo != null &&
                "true".equalsIgnoreCase(paymentValidationEnabledInfo.getValue());

        String orderUuid = request.getParameter("orderUuid");

        if (orderUuid == null || orderUuid.isEmpty()) {
            orderUuid = (String) request.getSession().getAttribute("currentOrderUuid");
        }

        if (orderUuid == null || orderUuid.isEmpty()) {
            orderUuid = request.getParameter("labNo");
        }

        if (paymentValidationEnabled && orderUuid != null && !orderUuid.isEmpty()) {
            try {
                LOG.info("Starting payment validation | orderUuid=" + orderUuid);

                PaymentStatus paymentStatus =
                        paymentValidationService.validatePayment(orderUuid);

                if (!paymentStatus.isAllowSample()) {

                    request.setAttribute("paymentBlocked", "true");
                    request.setAttribute("paymentStatus", paymentStatus.getStatus());
                    request.setAttribute("paymentMessage", paymentStatus.getMessage());
                    request.setAttribute("orderUuid", orderUuid);

                    PropertyUtils.setProperty(dynaForm, "paymentBlocked", "true");
                    PropertyUtils.setProperty(dynaForm, "paymentStatus", paymentStatus.getStatus());
                    PropertyUtils.setProperty(dynaForm, "paymentMessage", paymentStatus.getMessage());

                    LOG.warn("Sample collection BLOCKED | orderUuid=" + orderUuid +
                             " | status=" + paymentStatus.getStatus() +
                             " | message=" + paymentStatus.getMessage());

                } else {
                    request.setAttribute("paymentVerified", "true");
                    PropertyUtils.setProperty(dynaForm, "paymentVerified", "true");

                    LOG.info("Payment VERIFIED | orderUuid=" + orderUuid);
                }

            } catch (Exception e) {
                LOG.error("Payment validation FAILED | orderUuid=" + orderUuid, e);

                request.setAttribute("paymentBlocked", "true");
                request.setAttribute("paymentStatus", "error");
                request.setAttribute("paymentMessage",
                        "Unable to verify payment status. Please contact billing.");

                PropertyUtils.setProperty(dynaForm, "paymentBlocked", "true");
            }
        }

        /* ================= END PAYMENT VALIDATION ================= */

        PropertyUtils.setProperty(dynaForm, "receivedDateForDisplay", dateAsText);
        PropertyUtils.setProperty(dynaForm, "requestDate", dateAsText);
        PropertyUtils.setProperty(dynaForm, "collapsePatientInfo", Boolean.TRUE);
        PropertyUtils.setProperty(dynaForm, "patientProperties", new PatientManagmentInfo());
        PropertyUtils.setProperty(dynaForm, "sampleTypes",
                DisplayListService.getList(ListType.SAMPLE_TYPE));
        PropertyUtils.setProperty(dynaForm, "orderTypes",
                DisplayListService.getList(ListType.SAMPLE_PATIENT_PRIMARY_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "followupPeriodOrderTypes",
                DisplayListService.getList(ListType.SAMPLE_PATIENT_FOLLOW_UP_PERIOD_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "initialPeriodOrderTypes",
                DisplayListService.getList(ListType.SAMPLE_PATIENT_INITIAL_PERIOD_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "testSectionList",
                DisplayListService.getList(ListType.TEST_SECTION));
        PropertyUtils.setProperty(dynaForm, "labNo", "");
        PropertyUtils.setProperty(dynaForm, "sampleEntryFieldsetOrder",
                Arrays.asList(fieldsetOrder));
        PropertyUtils.setProperty(dynaForm, "sampleSourceList",
                sampleSourceDAO.getAllActive());
        PropertyUtils.setProperty(dynaForm, "providerList",
                providerDAO.getAllActiveProviders());

        addProjectList(dynaForm);

        if (needRequesterList) {
            PropertyUtils.setProperty(dynaForm, "referringSiteList",
                    DisplayListService.getFreshList(
                            ListType.SAMPLE_PATIENT_REFERRING_CLINIC));
        }

        if (needSampleInitialConditionList) {
            PropertyUtils.setProperty(dynaForm, "initialSampleConditionList",
                    DisplayListService.getList(ListType.INITIAL_SAMPLE_CONDITION));
        }

        if (needPaymentOptions) {
            setDictionaryList(dynaForm, "paymentOptions", "PP", true);
        }

        return mapping.findForward(forward);
    }
}
