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
import us.mn.state.health.lims.payment.service.PaymentValidationService.InvoiceInfo;
import us.mn.state.health.lims.hibernate.HibernateUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

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
     * Get patient UUID from sample UUID
     */
    private String getPatientUuidFromSampleUuid(String sampleUuid) {
        if (sampleUuid == null || sampleUuid.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            // Query to get patient UUID from sample
            String sql = "SELECT p.uuid FROM clinlims.patient p " +
                        "INNER JOIN clinlims.sample_human sh ON sh.patient_id = p.id " +
                        "INNER JOIN clinlims.sample s ON s.id = sh.samp_id " +
                        "WHERE s.uuid = ? LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, sampleUuid);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from sample UUID: " + patientUuid);
                return patientUuid;
            }
            
        } catch (Exception e) {
            System.err.println("Error querying for patient UUID from sample: " + e.getMessage());
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
     * Get patient UUID from accession number
     */
    private String getPatientUuidFromAccessionNumber(String accessionNumber) {
        if (accessionNumber == null || accessionNumber.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            String sql = "SELECT p.uuid FROM clinlims.patient p " +
                        "INNER JOIN clinlims.sample_human sh ON sh.patient_id = p.id " +
                        "INNER JOIN clinlims.sample s ON s.id = sh.samp_id " +
                        "WHERE s.accession_number = ? " +
                        "ORDER BY s.id DESC LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, accessionNumber);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from accession number: " + patientUuid);
                return patientUuid;
            }
        } catch (Exception e) {
            System.err.println("Error querying patient UUID by accession number: " + e.getMessage());
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
     * Get patient UUID from patient ID in form
     */
    private String getPatientUuidFromPatientId(String patientId) {
        if (patientId == null || patientId.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            String sql = "SELECT uuid FROM clinlims.patient WHERE id = ?";
            
            ps = conn.prepareStatement(sql);
            ps.setInt(1, Integer.parseInt(patientId));
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from patient ID: " + patientUuid);
                return patientUuid;
            }
        } catch (Exception e) {
            System.err.println("Error querying patient UUID by patient ID: " + e.getMessage());
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
     * Extract patient UUID from various possible sources in the request
     */
    private String extractPatientUuid(HttpServletRequest request, BaseActionForm dynaForm) {
        String patientUuid = null;
        
        // 1. Try request parameter (direct from URL)
        patientUuid = request.getParameter("patientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("Patient UUID from parameter: " + patientUuid);
            return patientUuid;
        }
        
        // 2. Try session
        patientUuid = (String) request.getSession().getAttribute("currentPatientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("Patient UUID from session: " + patientUuid);
            return patientUuid;
        }
        
        // 3. Try to get from PatientManagmentInfo in form
        try {
            PatientManagmentInfo patientInfo = (PatientManagmentInfo) PropertyUtils.getProperty(dynaForm, "patientProperties");
            if (patientInfo != null) {
                // Try to get UUID from patient info
                String uuid = patientInfo.getPatientUUID();
                if (uuid != null && !uuid.isEmpty()) {
                    System.out.println("Patient UUID from PatientManagmentInfo: " + uuid);
                    return uuid;
                }
                
                // Try to get patient ID and lookup UUID
                String patientId = patientInfo.getPatientID();
                if (patientId != null && !patientId.isEmpty()) {
                    patientUuid = getPatientUuidFromPatientId(patientId);
                    if (patientUuid != null) {
                        return patientUuid;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID from PatientManagmentInfo: " + e.getMessage());
        }
        
        // 4. Try to get from sample UUID in form
        try {
            String sampleUuid = (String) PropertyUtils.getProperty(dynaForm, "uuid");
            System.out.println("Sample UUID from form: " + sampleUuid);
            
            if (sampleUuid != null && !sampleUuid.isEmpty()) {
                patientUuid = getPatientUuidFromSampleUuid(sampleUuid);
                if (patientUuid != null && !patientUuid.isEmpty()) {
                    return patientUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID from sample UUID: " + e.getMessage());
        }
        
        // 5. Try to get from accession number
        try {
            String accessionNumber = (String) PropertyUtils.getProperty(dynaForm, "labNo");
            System.out.println("Accession number from form: " + accessionNumber);
            
            if (accessionNumber != null && !accessionNumber.isEmpty()) {
                patientUuid = getPatientUuidFromAccessionNumber(accessionNumber);
                if (patientUuid != null && !patientUuid.isEmpty()) {
                    return patientUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID from accession number: " + e.getMessage());
        }
        
        // 6. Try request attribute (might be set by previous action)
        patientUuid = (String) request.getAttribute("patientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("Patient UUID from request attribute: " + patientUuid);
            return patientUuid;
        }
        
        System.out.println("Could not extract patient UUID from any source");
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
        
        // Extract patient UUID from all possible sources
        String patientUuid = extractPatientUuid(request, dynaForm);
        System.out.println("Final patientUuid: " + patientUuid);

        // If payment validation is enabled and we have a patient UUID
        if (paymentValidationEnabled && patientUuid != null && !patientUuid.isEmpty()) {
            try {
                System.out.println("===== CALLING PAYMENT VALIDATION API =====");
                PaymentStatus paymentStatus = paymentValidationService.validatePayment(patientUuid);
                
                System.out.println("Payment status: " + paymentStatus.getStatus());
                System.out.println("Allow sample: " + paymentStatus.isAllowSample());
                System.out.println("Message: " + paymentStatus.getMessage());

                if (!paymentStatus.isAllowSample()) {
                    // Payment not verified - set attributes to show warning
                    request.setAttribute("paymentBlocked", "true");
                    request.setAttribute("paymentStatus", paymentStatus.getStatus());
                    request.setAttribute("paymentMessage", paymentStatus.getMessage());
                    request.setAttribute("patientUuid", patientUuid);
                    
                    // Add detailed patient and invoice information
                    if (paymentStatus.getPatientName() != null) {
                        request.setAttribute("paymentPatientName", paymentStatus.getPatientName());
                    }
                    if (paymentStatus.getPatientRef() != null) {
                        request.setAttribute("paymentPatientRef", paymentStatus.getPatientRef());
                    }
                    if (paymentStatus.getTotalDueAmount() != null) {
                        request.setAttribute("paymentTotalDue", paymentStatus.getTotalDueAmount());
                    }
                    if (paymentStatus.getInvoiceCount() != null) {
                        request.setAttribute("paymentInvoiceCount", paymentStatus.getInvoiceCount());
                    }
                    
                    // Add invoice details if available
                    List<InvoiceInfo> invoices = paymentStatus.getInvoices();
                    if (invoices != null && !invoices.isEmpty()) {
                        request.setAttribute("paymentInvoices", invoices);
                        
                        // Log invoice details
                        System.out.println("Unpaid invoices:");
                        for (InvoiceInfo invoice : invoices) {
                            System.out.println("  - " + invoice.getInvoiceNumber() + 
                                             ": Due=" + invoice.getAmountDue() + 
                                             ", Total=" + invoice.getTotalAmount() +
                                             ", Date=" + invoice.getInvoiceDate());
                        }
                    }

                    System.out.println("===== SAMPLE COLLECTION BLOCKED =====");
                    System.out.println("Reason: " + paymentStatus.getMessage());
                    System.out.println("Patient: " + paymentStatus.getPatientName());
                    System.out.println("Total Due: " + paymentStatus.getTotalDueAmount());
                } else {
                    // Payment verified - allow sample collection
                    request.setAttribute("paymentVerified", "true");
                    request.setAttribute("paymentPatientName", paymentStatus.getPatientName());
                    
                    System.out.println("===== PAYMENT VERIFIED - ALLOWING SAMPLE COLLECTION =====");
                    System.out.println("Patient: " + paymentStatus.getPatientName());
                }
            } catch (Exception e) {
                System.err.println("===== ERROR IN PAYMENT VALIDATION =====");
                e.printStackTrace();

                // Block on error for safety
                request.setAttribute("paymentBlocked", "true");
                request.setAttribute("paymentStatus", "error");
                request.setAttribute("paymentMessage", "Unable to verify payment status. Please contact billing.");
            }
        } else if (paymentValidationEnabled && (patientUuid == null || patientUuid.isEmpty())) {
            System.out.println("===== WARNING: Payment validation enabled but no patient UUID found =====");
            // Optionally block if no UUID can be found
            // Uncomment the following lines to block when UUID is missing:
            // request.setAttribute("paymentBlocked", "true");
            // request.setAttribute("paymentStatus", "unknown");
            // request.setAttribute("paymentMessage", "Unable to identify patient for payment verification.");
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