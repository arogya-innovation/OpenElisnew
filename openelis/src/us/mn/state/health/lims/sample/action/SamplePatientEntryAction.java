package us.mn.state.health.lims.sample.action;

import org.apache.commons.beanutils.PropertyUtils;
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
 * The SampleEntryAction class represents the initial Action for the SampleEntry
 * form of the application
 */
public class SamplePatientEntryAction extends BaseSampleEntryAction {

    private SampleSourceDAO sampleSourceDAO;
    private ProviderDAO providerDAO;
    private PaymentValidationService paymentValidationService;

    public SamplePatientEntryAction() {
        this.sampleSourceDAO = new SampleSourceDAOImpl();
        this.providerDAO = new ProviderDAOImpl();
        this.paymentValidationService = new PaymentValidationService();
    }

    protected ActionForward performAction(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        String[] fieldsetOrder = {"order", "samples", "patient"};
        String forward = "success";

        request.getSession().setAttribute(IActionConstants.SAVE_DISABLED, IActionConstants.TRUE);

        BaseActionForm dynaForm = (BaseActionForm) form;

        // Initialize the form.
        dynaForm.initialize(mapping);

        // Set received date and entered date to today's date
        String dateAsText = DateUtil.getCurrentDateAsText();
        PropertyUtils.setProperty(form, "currentDate", dateAsText);

        boolean needRequesterList = FormFields.getInstance().useField(FormFields.Field.RequesterSiteList);
        boolean needSampleInitialConditionList = FormFields.getInstance().useField(FormFields.Field.InitialSampleCondition);
        boolean needPaymentOptions = ConfigurationProperties.getInstance().isPropertyValueEqual(Property.trackPatientPayment, "true");

        SiteInformationDAO siteInfo = new SiteInformationDAOImpl();
        SiteInformation sampleEntryFieldsetOrder = siteInfo.getSiteInformationByName("SampleEntryFieldsetOrder");
        if (sampleEntryFieldsetOrder != null &&
                sampleEntryFieldsetOrder.getValue() != null &&
                !sampleEntryFieldsetOrder.getValue().isEmpty()) {
            fieldsetOrder = sampleEntryFieldsetOrder.getValue().split("\\|");
        }

        // ====== PAYMENT VALIDATION LOGIC ======
        // Check if payment validation is enabled
        SiteInformation paymentValidationEnabledInfo = siteInfo.getSiteInformationByName("enablePaymentValidation");
        boolean paymentValidationEnabled = false;
        
        if (paymentValidationEnabledInfo != null && paymentValidationEnabledInfo.getValue() != null) {
            paymentValidationEnabled = "true".equalsIgnoreCase(paymentValidationEnabledInfo.getValue());
        }

        // Check if there's an order UUID in the request (coming from Bahmni)
        String orderUuid = request.getParameter("orderUuid");

        // Alternative: Check if it's stored in session
        if (orderUuid == null || orderUuid.isEmpty()) {
            orderUuid = (String) request.getSession().getAttribute("currentOrderUuid");
        }
        
        // Alternative: Check from lab number or accession number
        if (orderUuid == null || orderUuid.isEmpty()) {
            orderUuid = request.getParameter("labNo");
        }

        // If payment validation is enabled and we have an order UUID
        if (paymentValidationEnabled && orderUuid != null && !orderUuid.isEmpty()) {
            try {
                PaymentStatus paymentStatus = paymentValidationService.validatePayment(orderUuid);

                if (!paymentStatus.isAllowSample()) {
                    // Payment not verified - set attributes to show warning
                    request.setAttribute("paymentBlocked", "true");
                    request.setAttribute("paymentStatus", paymentStatus.getStatus());
                    request.setAttribute("paymentMessage", paymentStatus.getMessage());
                    request.setAttribute("orderUuid", orderUuid);

                    // Also set in form for JSP access
                    PropertyUtils.setProperty(dynaForm, "paymentBlocked", "true");
                    PropertyUtils.setProperty(dynaForm, "paymentStatus", paymentStatus.getStatus());
                    PropertyUtils.setProperty(dynaForm, "paymentMessage", paymentStatus.getMessage());

                    // Log the blocked attempt
                    System.out.println("Sample collection blocked for order: " + orderUuid +
                            " - Reason: " + paymentStatus.getMessage());
                } else {
                    // Payment verified - allow sample collection
                    request.setAttribute("paymentVerified", "true");
                    PropertyUtils.setProperty(dynaForm, "paymentVerified", "true");

                    System.out.println("Payment verified for order: " + orderUuid);
                }
            } catch (Exception e) {
                // Log the error
                System.err.println("Error validating payment for order: " + orderUuid);
                e.printStackTrace();

                // Decide behavior on error - currently blocking for safety
                request.setAttribute("paymentBlocked", "true");
                request.setAttribute("paymentStatus", "error");
                request.setAttribute("paymentMessage", "Unable to verify payment status. Please contact billing.");
                PropertyUtils.setProperty(dynaForm, "paymentBlocked", "true");
            }
        }
        // ====== END PAYMENT VALIDATION LOGIC ======

        PropertyUtils.setProperty(dynaForm, "receivedDateForDisplay", dateAsText);
        PropertyUtils.setProperty(dynaForm, "requestDate", dateAsText);
        PropertyUtils.setProperty(dynaForm, "collapsePatientInfo", Boolean.TRUE);
        PropertyUtils.setProperty(dynaForm, "patientProperties", new PatientManagmentInfo());
        PropertyUtils.setProperty(dynaForm, "sampleTypes", DisplayListService.getList(ListType.SAMPLE_TYPE));
        PropertyUtils.setProperty(dynaForm, "orderTypes", DisplayListService.getList(ListType.SAMPLE_PATIENT_PRIMARY_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "followupPeriodOrderTypes", DisplayListService.getList(ListType.SAMPLE_PATIENT_FOLLOW_UP_PERIOD_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "initialPeriodOrderTypes", DisplayListService.getList(ListType.SAMPLE_PATIENT_INITIAL_PERIOD_ORDER_TYPE));
        PropertyUtils.setProperty(dynaForm, "testSectionList", DisplayListService.getList(ListType.TEST_SECTION));
        PropertyUtils.setProperty(dynaForm, "labNo", "");
        PropertyUtils.setProperty(dynaForm, "sampleEntryFieldsetOrder", Arrays.asList(fieldsetOrder));
        PropertyUtils.setProperty(dynaForm, "sampleSourceList", sampleSourceDAO.getAllActive());
        PropertyUtils.setProperty(dynaForm, "providerList", providerDAO.getAllActiveProviders());

        addProjectList(dynaForm);

        if (needRequesterList) {
            PropertyUtils.setProperty(dynaForm, "referringSiteList", DisplayListService.getFreshList(ListType.SAMPLE_PATIENT_REFERRING_CLINIC));
        }

        if (needSampleInitialConditionList) {
            PropertyUtils.setProperty(dynaForm, "initialSampleConditionList", DisplayListService.getList(ListType.INITIAL_SAMPLE_CONDITION));
        }

        if (needPaymentOptions) {
            setDictionaryList(dynaForm, "paymentOptions", "PP", true);
        }

        return mapping.findForward(forward);
    }
}