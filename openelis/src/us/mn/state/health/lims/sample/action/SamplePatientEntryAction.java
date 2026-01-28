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
     * Get patient UUID from patient identifier (national ID, hospital ID, etc.)
     * Checks both patient table and patient_identity table
     */
    private String getPatientUuidFromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            // First try patient_identity table (for ABC200000 style identifiers)
            String sql = "SELECT p.uuid " +
                        "FROM clinlims.patient p " +
                        "INNER JOIN clinlims.patient_identity pi ON pi.patient_id = p.id " +
                        "WHERE pi.identity_data = ? " +
                        "LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, identifier);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from patient_identity: " + patientUuid);
                return patientUuid;
            }
            
            // Close first query resources
            if (rs != null) rs.close();
            if (ps != null) ps.close();
            
            // If not found, try patient table columns
            sql = "SELECT uuid FROM clinlims.patient " +
                 "WHERE national_id = ? " +
                 "OR external_id = ? " +
                 "OR chart_number = ? " +
                 "LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, identifier);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from patient table identifier: " + patientUuid);
                return patientUuid;
            }
        } catch (Exception e) {
            System.err.println("Error querying patient UUID by identifier: " + e.getMessage());
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
     * Get patient UUID from person table using person identifiers
     */
    private String getPatientUuidFromPersonIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            // Look in person table first, then join to patient
            String sql = "SELECT p.uuid FROM clinlims.patient p " +
                        "INNER JOIN clinlims.person per ON per.id = p.person_id " +
                        "WHERE per.national_id = ? " +
                        "OR per.first_name || ' ' || per.last_name = ? " +
                        "LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                System.out.println("Found patient UUID from person identifier: " + patientUuid);
                return patientUuid;
            }
        } catch (Exception e) {
            System.err.println("Error querying patient UUID by person identifier: " + e.getMessage());
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
     * Get patient UUID from patient table by ID with retry logic
     */
    private String getPatientUuidFromPatientIdWithRetry(String patientId) {
        if (patientId == null || patientId.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            // Try direct lookup first
            String sql = "SELECT uuid FROM clinlims.patient WHERE id = ?";
            
            ps = conn.prepareStatement(sql);
            
            try {
                ps.setInt(1, Integer.parseInt(patientId));
            } catch (NumberFormatException e) {
                System.err.println("Invalid patient ID format: " + patientId);
                return null;
            }
            
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String patientUuid = rs.getString("uuid");
                if (patientUuid != null && !patientUuid.trim().isEmpty()) {
                    System.out.println("Found patient UUID from patient ID: " + patientUuid);
                    return patientUuid;
                } else {
                    System.out.println("Patient UUID is null or empty for patient ID: " + patientId);
                }
            } else {
                System.out.println("No patient found with ID: " + patientId);
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
     * Enhanced with multiple fallback options
     */
    private String extractPatientUuid(HttpServletRequest request, BaseActionForm dynaForm) {
        String patientUuid = null;
        
        System.out.println("=== Starting Patient UUID Extraction ===");
        
        // 1. Try request parameter (direct from URL)
        patientUuid = request.getParameter("patientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("✓ Patient UUID from request parameter: " + patientUuid);
            return patientUuid;
        }
        
        // 2. Try session attribute
        patientUuid = (String) request.getSession().getAttribute("currentPatientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("✓ Patient UUID from session: " + patientUuid);
            return patientUuid;
        }
        
        // 3. Try to get from form patientPK field directly
        try {
            Object patientPKObj = PropertyUtils.getProperty(dynaForm, "patientPK");
            if (patientPKObj != null) {
                String patientPK = patientPKObj.toString();
                if (!patientPK.isEmpty()) {
                    System.out.println("Found patientPK from form: " + patientPK);
                    
                    // First try as patient ID
                    patientUuid = getPatientUuidFromPatientIdWithRetry(patientPK);
                    if (patientUuid != null) {
                        System.out.println("✓ Patient UUID from patientPK (as ID): " + patientUuid);
                        return patientUuid;
                    }
                    
                    // If not found as ID, try as identifier (could be ABC200000 format)
                    patientUuid = getPatientUuidFromIdentifier(patientPK);
                    if (patientUuid != null) {
                        System.out.println("✓ Patient UUID from patientPK (as identifier): " + patientUuid);
                        return patientUuid;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not get patientPK from form: " + e.getMessage());
        }
        
        // 3a. Try to get patient identifier fields from form
        String[] identifierFieldNames = {
            "patientIdentifier",
            "subjectNumber",
            "ST",
            "nationalId"
        };
        
        for (String fieldName : identifierFieldNames) {
            try {
                Object fieldValue = PropertyUtils.getProperty(dynaForm, fieldName);
                if (fieldValue != null) {
                    String fieldStr = fieldValue.toString();
                    if (!fieldStr.isEmpty()) {
                        System.out.println("Found " + fieldName + " from form: " + fieldStr);
                        patientUuid = getPatientUuidFromIdentifier(fieldStr);
                        if (patientUuid != null) {
                            System.out.println("✓ Patient UUID from " + fieldName + ": " + patientUuid);
                            return patientUuid;
                        }
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist, continue
            }
        }
        
        // 3b. Try to get from PatientManagmentInfo object
        try {
            Object patientPropsObj = PropertyUtils.getProperty(dynaForm, "patientProperties");
            if (patientPropsObj != null) {
                System.out.println("Found PatientManagmentInfo object");
                
                // List of possible properties to check
                String[] patientInfoFields = {
                    "patientPK",
                    "subjectNumber",
                    "STnumber",
                    "nationalId"
                };
                
                for (String propName : patientInfoFields) {
                    try {
                        Object propValue = PropertyUtils.getProperty(patientPropsObj, propName);
                        if (propValue != null) {
                            String propStr = propValue.toString();
                            if (!propStr.isEmpty()) {
                                System.out.println("Found PatientManagmentInfo." + propName + ": " + propStr);
                                
                                // Try as patient ID first
                                patientUuid = getPatientUuidFromPatientIdWithRetry(propStr);
                                if (patientUuid != null) {
                                    System.out.println("✓ Patient UUID from PatientManagmentInfo." + propName + " (as ID): " + patientUuid);
                                    return patientUuid;
                                }
                                
                                // Try as identifier
                                patientUuid = getPatientUuidFromIdentifier(propStr);
                                if (patientUuid != null) {
                                    System.out.println("✓ Patient UUID from PatientManagmentInfo." + propName + " (as identifier): " + patientUuid);
                                    return patientUuid;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Property doesn't exist
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not access PatientManagmentInfo: " + e.getMessage());
        }
        
        // 4. Try to get from PatientManagmentInfo object in form
        try {
            Object patientPropsObj = PropertyUtils.getProperty(dynaForm, "patientProperties");
            if (patientPropsObj != null) {
                System.out.println("Found PatientManagmentInfo object in form");
                
                // Try multiple possible property names in PatientManagmentInfo
                String[] possibleProps = {
                    "patientPK",
                    "personPK", 
                    "STnumber",
                    "nationalId",
                    "guid",
                    "externalId"
                };
                
                for (String propName : possibleProps) {
                    try {
                        Object propValue = PropertyUtils.getProperty(patientPropsObj, propName);
                        if (propValue != null) {
                            String propStr = propValue.toString();
                            if (!propStr.isEmpty()) {
                                System.out.println("Found PatientManagmentInfo." + propName + ": " + propStr);
                                
                                // Try as patient ID first
                                patientUuid = getPatientUuidFromPatientIdWithRetry(propStr);
                                if (patientUuid != null) {
                                    System.out.println("✓ Patient UUID from PatientManagmentInfo." + propName + ": " + patientUuid);
                                    return patientUuid;
                                }
                                
                                // Try as identifier if it looks like an identifier
                                if (propName.toLowerCase().contains("national") || 
                                    propName.toLowerCase().contains("external") ||
                                    propName.toLowerCase().contains("guid")) {
                                    patientUuid = getPatientUuidFromIdentifier(propStr);
                                    if (patientUuid != null) {
                                        System.out.println("✓ Patient UUID from identifier " + propName + ": " + patientUuid);
                                        return patientUuid;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Property doesn't exist, continue
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not access PatientManagmentInfo: " + e.getMessage());
        }
        
        // 5. Try various other form field names
        String[] possiblePatientFields = {
            "patientId", 
            "patient_id", 
            "patientID",
            "patient.id",
            "patientProperties.patientPK"
        };
        
        for (String fieldName : possiblePatientFields) {
            try {
                Object fieldValue = PropertyUtils.getProperty(dynaForm, fieldName);
                if (fieldValue != null) {
                    String fieldStr = fieldValue.toString();
                    if (!fieldStr.isEmpty()) {
                        System.out.println("Found patient field '" + fieldName + "': " + fieldStr);
                        patientUuid = getPatientUuidFromPatientIdWithRetry(fieldStr);
                        if (patientUuid != null) {
                            System.out.println("✓ Patient UUID from " + fieldName + ": " + patientUuid);
                            return patientUuid;
                        }
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist, try next one
            }
        }
        
        // 6. Try from session attributes (various possible names)
        String[] sessionAttributes = {
            "patientId",
            "patientPK",
            "patient_id",
            "personId",
            "person_id",
            "patientIdentifier",
            "nationalId",
            "patientNationalId"
        };
        
        for (String attrName : sessionAttributes) {
            Object attrValue = request.getSession().getAttribute(attrName);
            if (attrValue != null) {
                String attrStr = attrValue.toString();
                if (!attrStr.isEmpty()) {
                    System.out.println("Found session attribute '" + attrName + "': " + attrStr);
                    
                    // If it's an identifier field, try identifier lookup first
                    if (attrName.toLowerCase().contains("identifier") || 
                        attrName.toLowerCase().contains("national")) {
                        patientUuid = getPatientUuidFromIdentifier(attrStr);
                        if (patientUuid != null) {
                            System.out.println("✓ Patient UUID from identifier " + attrName + ": " + patientUuid);
                            return patientUuid;
                        }
                    }
                    
                    // If it's personId, use different lookup
                    if (attrName.toLowerCase().contains("person")) {
                        patientUuid = getPatientUuidFromPersonIdentifier(attrStr);
                    } else {
                        patientUuid = getPatientUuidFromPatientIdWithRetry(attrStr);
                    }
                    
                    if (patientUuid != null) {
                        System.out.println("✓ Patient UUID from session " + attrName + ": " + patientUuid);
                        return patientUuid;
                    }
                }
            }
        }
        
        // 7. Try from form fields that might contain identifiers
        String[] identifierFields = {
            "nationalId",
            "patientIdentifier", 
            "externalId",
            "chartNumber",
            "nationalID",
            "patient.nationalId"
        };
        
        for (String fieldName : identifierFields) {
            try {
                Object fieldValue = PropertyUtils.getProperty(dynaForm, fieldName);
                if (fieldValue != null) {
                    String fieldStr = fieldValue.toString();
                    if (!fieldStr.isEmpty()) {
                        System.out.println("Found identifier field '" + fieldName + "': " + fieldStr);
                        patientUuid = getPatientUuidFromIdentifier(fieldStr);
                        if (patientUuid != null) {
                            System.out.println("✓ Patient UUID from identifier field " + fieldName + ": " + patientUuid);
                            return patientUuid;
                        }
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist, try next one
            }
        }
        
        // 8. Try to get from sample UUID in form
        try {
            String sampleUuid = (String) PropertyUtils.getProperty(dynaForm, "uuid");
            System.out.println("Sample UUID from form: " + sampleUuid);
            
            if (sampleUuid != null && !sampleUuid.isEmpty()) {
                patientUuid = getPatientUuidFromSampleUuid(sampleUuid);
                if (patientUuid != null && !patientUuid.isEmpty()) {
                    System.out.println("✓ Patient UUID from sample UUID: " + patientUuid);
                    return patientUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID from sample UUID: " + e.getMessage());
        }
        
        // 9. Try to get from accession number
        try {
            String accessionNumber = (String) PropertyUtils.getProperty(dynaForm, "labNo");
            System.out.println("Accession number from form: " + accessionNumber);
            
            if (accessionNumber != null && !accessionNumber.isEmpty()) {
                patientUuid = getPatientUuidFromAccessionNumber(accessionNumber);
                if (patientUuid != null && !patientUuid.isEmpty()) {
                    System.out.println("✓ Patient UUID from accession number: " + patientUuid);
                    return patientUuid;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID from accession number: " + e.getMessage());
        }
        
        // 10. Try request attribute (might be set by previous action)
        patientUuid = (String) request.getAttribute("patientUuid");
        if (patientUuid != null && !patientUuid.isEmpty()) {
            System.out.println("✓ Patient UUID from request attribute: " + patientUuid);
            return patientUuid;
        }
        
        // 11. Last resort - check if there's a patient in context
        Object patientInContext = request.getAttribute("patient");
        if (patientInContext != null) {
            System.out.println("Found patient object in request attributes");
            // Try to get UUID from patient object via reflection
            try {
                Object uuidObj = PropertyUtils.getProperty(patientInContext, "uuid");
                if (uuidObj != null) {
                    patientUuid = uuidObj.toString();
                    if (!patientUuid.isEmpty()) {
                        System.out.println("✓ Patient UUID from patient object: " + patientUuid);
                        return patientUuid;
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not extract UUID from patient object: " + e.getMessage());
            }
        }
        
        System.out.println("✗ Could not extract patient UUID from any source");
        System.out.println("=== End Patient UUID Extraction ===");
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
        
        // DEBUGGING: Enable this temporarily to see all available form fields
        // Uncomment the next line to debug what fields are available
        // us.mn.state.health.lims.common.util.FormDebugger.dumpAll(request, dynaForm);
        
        // Debug specific patient fields
        try {
            System.out.println("DEBUG: Checking specific patient fields...");
            Object patientPKObj = PropertyUtils.getProperty(dynaForm, "patientPK");
            System.out.println("  patientPK = " + patientPKObj);
            
            Object patientPropsObj = PropertyUtils.getProperty(dynaForm, "patientProperties");
            System.out.println("  patientProperties = " + patientPropsObj);
            
            if (patientPropsObj != null) {
                System.out.println("  patientProperties class: " + patientPropsObj.getClass().getName());
                // Try to inspect PatientManagmentInfo object
                try {
                    java.beans.PropertyDescriptor[] props = PropertyUtils.getPropertyDescriptors(patientPropsObj);
                    System.out.println("  PatientManagmentInfo available properties:");
                    for (java.beans.PropertyDescriptor prop : props) {
                        if (prop.getReadMethod() != null && 
                            (prop.getName().toLowerCase().contains("patient") || 
                             prop.getName().toLowerCase().contains("id") ||
                             prop.getName().toLowerCase().contains("uuid") ||
                             prop.getName().toLowerCase().contains("person"))) {
                            try {
                                Object val = PropertyUtils.getProperty(patientPropsObj, prop.getName());
                                System.out.println("    " + prop.getName() + " = " + val);
                            } catch (Exception e) {
                                System.out.println("    " + prop.getName() + " = <error: " + e.getMessage() + ">");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("  Could not inspect PatientManagmentInfo: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("  Error checking patient fields: " + e.getMessage());
        }
        
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