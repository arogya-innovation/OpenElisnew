package us.mn.state.health.lims.payment.service;

import org.json.JSONArray;
import org.json.JSONObject;
import us.mn.state.health.lims.common.util.ConfigurationProperties;
import us.mn.state.health.lims.common.util.ConfigurationProperties.Property;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to validate payment status for lab orders via Odoo JSON-RPC API
 * Java 7 compatible implementation using URLConnection
 * Updated to work with patient-based payment validation
 */
public class PaymentValidationService {
    
    // Default values if not configured in database
    private static final String DEFAULT_ODOO_API_URL = "http://odoo:8069/lab/payment/status";
    private static final String DEFAULT_TIMEOUT = "10";
    private static final String DEFAULT_ENABLED = "true";
    
    private String odooApiUrl;
    private int timeoutSeconds;
    private boolean enabled;
    
    public PaymentValidationService() {
        loadConfiguration();
    }
    
    /**
     * Load configuration from ConfigurationProperties (which reads from database)
     */
    private void loadConfiguration() {
        ConfigurationProperties config = ConfigurationProperties.getInstance();
        
        // Load enabled flag
        String enabledValue = config.getPropertyValue(Property.enablePaymentValidation);
        this.enabled = "true".equalsIgnoreCase(enabledValue != null ? enabledValue : DEFAULT_ENABLED);
        
        // Load API URL
        String apiUrl = config.getPropertyValue(Property.paymentValidationApiUrl);
        this.odooApiUrl = (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : DEFAULT_ODOO_API_URL;
        
        // Load timeout
        String timeout = config.getPropertyValue(Property.paymentValidationTimeout);
        this.timeoutSeconds = 10; // default
        if (timeout != null && !timeout.isEmpty()) {
            try {
                this.timeoutSeconds = Integer.parseInt(timeout);
            } catch (NumberFormatException e) {
                System.err.println("Invalid timeout value, using default: " + e.getMessage());
            }
        }
        
        System.out.println("Payment Validation Service initialized:");
        System.out.println("  - Enabled: " + this.enabled);
        System.out.println("  - API URL: " + this.odooApiUrl);
        System.out.println("  - Timeout: " + this.timeoutSeconds + " seconds");
    }
    
    /**
     * Inner class to hold invoice information
     */
    public static class InvoiceInfo {
        private String invoiceNumber;
        private String invoiceDate;
        private double totalAmount;
        private double amountDue;
        private String paymentState;
        private String shopName;
        
        public InvoiceInfo(String invoiceNumber, String invoiceDate, double totalAmount, 
                          double amountDue, String paymentState, String shopName) {
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
            this.totalAmount = totalAmount;
            this.amountDue = amountDue;
            this.paymentState = paymentState;
            this.shopName = shopName;
        }
        
        public String getInvoiceNumber() { return invoiceNumber; }
        public String getInvoiceDate() { return invoiceDate; }
        public double getTotalAmount() { return totalAmount; }
        public double getAmountDue() { return amountDue; }
        public String getPaymentState() { return paymentState; }
        public String getShopName() { return shopName; }
    }
    
    /**
     * Inner class to hold payment status information
     */
    public static class PaymentStatus {
        private String status;
        private boolean allowSample;
        private String message;
        private String patientUuid;
        private Integer patientId;
        private String patientName;
        private String patientRef;
        private Integer invoiceCount;
        private List<InvoiceInfo> invoices;
        private Double totalDueAmount;
        
        public PaymentStatus(String status, boolean allowSample, String message) {
            this.status = status;
            this.allowSample = allowSample;
            this.message = message;
            this.invoices = new ArrayList<InvoiceInfo>();
        }
        
        public boolean isAllowSample() { 
            return allowSample; 
        }
        
        public String getStatus() { 
            return status; 
        }
        
        public String getMessage() { 
            return message; 
        }
        
        public String getPatientUuid() { return patientUuid; }
        public Integer getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getPatientRef() { return patientRef; }
        public Integer getInvoiceCount() { return invoiceCount; }
        public List<InvoiceInfo> getInvoices() { return invoices; }
        public Double getTotalDueAmount() { return totalDueAmount; }
        
        public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }
        public void setPatientId(Integer patientId) { this.patientId = patientId; }
        public void setPatientName(String patientName) { this.patientName = patientName; }
        public void setPatientRef(String patientRef) { this.patientRef = patientRef; }
        public void setInvoiceCount(Integer invoiceCount) { this.invoiceCount = invoiceCount; }
        public void setInvoices(List<InvoiceInfo> invoices) { this.invoices = invoices; }
        public void setTotalDueAmount(Double totalDueAmount) { this.totalDueAmount = totalDueAmount; }
    }
    
    /**
     * Check if payment validation is enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }
    
    /**
     * Validates payment status for a patient
     * @param patientUuid The UUID of the patient from Bahmni
     * @return PaymentStatus object with validation result
     */
    public PaymentStatus validatePayment(String patientUuid) {
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        BufferedReader reader = null;
        
        try {
            // Create URL and open connection
            URL url = new URL(odooApiUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            
            // Prepare JSON-RPC 2.0 request body
            JSONObject params = new JSONObject();
            params.put("patient_uuid", patientUuid);  // Changed from order_uuid to patient_uuid
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("method", "call");
            requestBody.put("params", params);
            requestBody.put("id", 1);
            
            String jsonInputString = requestBody.toString();
            
            System.out.println("Sending payment validation request: " + jsonInputString);
            
            // Send request
            outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonInputString);
            outputStream.flush();
            
            // Get response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response code: " + responseCode);
            
            // Read response
            reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuffer response = new StringBuffer();
            
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            
            String responseString = response.toString();
            System.out.println("Payment validation response: " + responseString);
            
            // Parse JSON-RPC response
            JSONObject jsonResponse = new JSONObject(responseString);
            
            // Check if there's an error in the JSON-RPC response
            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown error");
                System.err.println("JSON-RPC error: " + errorMessage);
                return new PaymentStatus("error", false, errorMessage);
            }
            
            // Extract the result object
            JSONObject result = jsonResponse.getJSONObject("result");
            
            // Create PaymentStatus object with basic info
            PaymentStatus paymentStatus = new PaymentStatus(
                result.optString("status", "error"),
                result.optBoolean("allow_sample", false),
                result.optString("message", "Unable to verify payment status")
            );
            
            // Set additional patient information
            paymentStatus.setPatientUuid(result.optString("patient_uuid", null));
            
            if (result.has("patient_id") && !result.isNull("patient_id")) {
                paymentStatus.setPatientId(result.getInt("patient_id"));
            }
            
            paymentStatus.setPatientName(result.optString("patient_name", null));
            paymentStatus.setPatientRef(result.optString("patient_ref", null));
            
            // Set invoice information if present
            if (result.has("invoice_count") && !result.isNull("invoice_count")) {
                paymentStatus.setInvoiceCount(result.getInt("invoice_count"));
            }
            
            if (result.has("total_due_amount") && !result.isNull("total_due_amount")) {
                paymentStatus.setTotalDueAmount(result.getDouble("total_due_amount"));
            }
            
            // Parse invoices array if present
            if (result.has("invoices") && !result.isNull("invoices")) {
                JSONArray invoicesArray = result.getJSONArray("invoices");
                List<InvoiceInfo> invoicesList = new ArrayList<InvoiceInfo>();
                
                for (int i = 0; i < invoicesArray.length(); i++) {
                    JSONObject invoiceObj = invoicesArray.getJSONObject(i);
                    
                    InvoiceInfo invoice = new InvoiceInfo(
                        invoiceObj.optString("invoice_number", null),
                        invoiceObj.optString("invoice_date", null),
                        invoiceObj.optDouble("total_amount", 0.0),
                        invoiceObj.optDouble("amount_due", 0.0),
                        invoiceObj.optString("payment_state", null),
                        invoiceObj.optString("shop_name", null)
                    );
                    
                    invoicesList.add(invoice);
                }
                
                paymentStatus.setInvoices(invoicesList);
            }
            
            return paymentStatus;
            
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Payment validation timeout for patient " + patientUuid);
            e.printStackTrace();
            return new PaymentStatus("error", false, 
                "Payment verification timeout. Please try again or contact billing.");
                
        } catch (java.io.IOException e) {
            System.err.println("Payment validation IO error for patient " + patientUuid + ": " + e.getMessage());
            e.printStackTrace();
            return new PaymentStatus("error", false, 
                "Unable to connect to payment service. Please contact billing.");
                
        } catch (Exception e) {
            System.err.println("Payment validation error for patient " + patientUuid + ": " + e.getMessage());
            e.printStackTrace();
            return new PaymentStatus("error", false, 
                "Unable to verify payment. Please contact billing department.");
                
        } finally {
            // Close resources properly
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing output stream: " + e.getMessage());
            }
            
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing reader: " + e.getMessage());
            }
            
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Test method to verify API connectivity
     * @return true if API is reachable, false otherwise
     */
    public boolean testConnection() {
        try {
            // Test with a dummy UUID
            PaymentStatus status = validatePayment("98d62923-6b7d-4182-9672-963b5cff769d");
            return status != null;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }
}