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
import us.mn.state.health.lims.hibernate.HibernateUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    
    /**
     * Get order UUID from external_reference table using sample UUID,
     * or from client_reference field in sample table
     */
    private String getOrderUuidFromSampleUuid(String sampleUuid) {
        if (sampleUuid == null || sampleUuid.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            // First try: Query external_reference table
            String sql = "SELECT external_id FROM clinlims.external_reference " +
                        "WHERE item_id = ? ORDER BY id DESC LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, sampleUuid);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String orderUuid = rs.getString("external_id");
                System.out.println("Found order UUID from external_reference: " + orderUuid);
                if (orderUuid != null && !orderUuid.isEmpty()) {
                    return orderUuid;
                }
            }
            
            // Second try: Get from client_reference in sample table
            if (rs != null) rs.close();
            if (ps != null) ps.close();
            
            sql = "SELECT client_reference FROM clinlims.sample WHERE uuid = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, sampleUuid);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String orderUuid = rs.getString("client_reference");
                System.out.println("Found order UUID from sample.client_reference: " + orderUuid);
                if (orderUuid != null && !orderUuid.isEmpty()) {
                    return orderUuid;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error querying for order UUID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                // Don't close connection - managed by Hibernate
            } catch (Exception e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Get order UUID from accession number
     */
    private String getOrderUuidFromAccessionNumber(String accessionNumber) {
        if (accessionNumber == null || accessionNumber.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            String sql = "SELECT client_reference FROM clinlims.sample " +
                        "WHERE accession_number = ? ORDER BY id DESC LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, accessionNumber);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String orderUuid = rs.getString("client_reference");
                System.out.println("Found order UUID from accession number: " + orderUuid);
                return orderUuid;
            }
        } catch (Exception e) {
            System.err.println("Error querying by accession number: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (Exception e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Extract order UUID from various possible sources in the request
     */
    private String extractOrderUuid(HttpServletRequest request, BaseActionForm dynaForm) {
        String orderUuid = null;
        
        // 1. Try request parameter (direct from URL)
        orderUuid = request.getParameter("orderUuid");
        if (orderUuid != null && !orderUuid.isEmpty()) {
            System.out.println("Order UUID from parameter: " + orderUuid);
            return orderUuid;
        }
        
        // 2. Try session
        orderUuid = (String) request.getSession().getAttribute("currentOrderUuid");
        if (orderUuid != null && !orderUuid.isEmpty()) {
            System.out.println("Order UUID from session: " + orderUuid);
            return orderUuid;
        }
        
        // 3. Try to get from sample UUID in form
        try {
            String sampleUuid = (String) PropertyUtils.getProperty(dynaForm, "uuid");
            System.out.println("Sample UUID from form: " + sampleUuid);
            
            if (sampleUuid != null && !sampleUuid.isEmpty()) {
                orderUuid = getOrderUuidFromSampleUuid(sampleUuid);
                if (orderUuid != null && !orderUuid.isEmpty()) {
                    return orderUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting sample UUID from form: " + e.getMessage());
        }
        
        // 4. Try to get from accession number
        try {
            String accessionNumber = (String) PropertyUtils.getProperty(dynaForm, "labNo");
            System.out.println("Accession number from form: " + accessionNumber);
            
            if (accessionNumber != null && !accessionNumber.isEmpty()) {
                orderUuid = getOrderUuidFromAccessionNumber(accessionNumber);
                if (orderUuid != null && !orderUuid.isEmpty()) {
                    return orderUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting accession number from form: " + e.getMessage());
        }
        
        // 5. Try request attribute (might be set by previous action)
        orderUuid = (String) request.getAttribute("orderUuid");
        if (orderUuid != null && !orderUuid.isEmpty()) {
            System.out.println("Order UUID from request attribute: " + orderUuid);
            return orderUuid;
        }
        
        System.out.println("Could not extract order UUID from any source");
        return null;
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
        System.out.println("===== PAYMENT VALIDATION CHECK =====");
        
        // Check if payment validation is enabled
        boolean paymentValidationEnabled = paymentValidationService.isEnabled();
        System.out.println("Payment validation enabled: " + paymentValidationEnabled);
        
        // Extract order UUID from all possible sources
        String orderUuid = extractOrderUuid(request, dynaForm);
        System.out.println("Final orderUuid: " + orderUuid);

        // If payment validation is enabled and we have an order UUID
        if (paymentValidationEnabled && orderUuid != null && !orderUuid.isEmpty()) {
            try {
                System.out.println("===== CALLING PAYMENT VALIDATION API =====");
                PaymentStatus paymentStatus = paymentValidationService.validatePayment(orderUuid);
                
                System.out.println("Payment status: " + paymentStatus.getStatus());
                System.out.println("Allow sample: " + paymentStatus.isAllowSample());
                System.out.println("Message: " + paymentStatus.getMessage());

                if (!paymentStatus.isAllowSample()) {
                    // Payment not verified - set attributes to show warning
                    request.setAttribute("paymentBlocked", "true");
                    request.setAttribute("paymentStatus", paymentStatus.getStatus());
                    request.setAttribute("paymentMessage", paymentStatus.getMessage());
                    request.setAttribute("orderUuid", orderUuid);

                    System.out.println("===== SAMPLE COLLECTION BLOCKED =====");
                    System.out.println("Reason: " + paymentStatus.getMessage());
                } else {
                    // Payment verified - allow sample collection
                    request.setAttribute("paymentVerified", "true");
                    System.out.println("===== PAYMENT VERIFIED - ALLOWING SAMPLE COLLECTION =====");
                }
            } catch (Exception e) {
                System.err.println("===== ERROR IN PAYMENT VALIDATION =====");
                e.printStackTrace();

                // Block on error for safety
                request.setAttribute("paymentBlocked", "true");
                request.setAttribute("paymentStatus", "error");
                request.setAttribute("paymentMessage", "Unable to verify payment status. Please contact billing.");
            }
        } else if (paymentValidationEnabled && (orderUuid == null || orderUuid.isEmpty())) {
            System.out.println("===== WARNING: Payment validation enabled but no order UUID found =====");
            // Optionally block if no UUID can be found
            // Uncomment the following lines to block when UUID is missing:
            // request.setAttribute("paymentBlocked", "true");
            // request.setAttribute("paymentStatus", "unknown");
            // request.setAttribute("paymentMessage", "Unable to identify order for payment verification.");
        }
        System.out.println("===== END PAYMENT VALIDATION CHECK =====");
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