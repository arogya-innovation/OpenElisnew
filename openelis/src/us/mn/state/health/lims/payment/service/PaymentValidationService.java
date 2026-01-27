package us.mn.state.health.lims.payment.service;

import org.json.JSONObject;
import us.mn.state.health.lims.common.util.ConfigurationProperties;
import us.mn.state.health.lims.common.util.ConfigurationProperties.Property;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service to validate payment status for lab orders via Odoo API
 * Java 7 compatible implementation using URLConnection
 */
public class PaymentValidationService {
    
    // Default values if not configured in database
    private static final String DEFAULT_ODOO_API_URL = "http://localhost:8069/lab/payment/status";
    private static final String DEFAULT_TIMEOUT = "10";
    private static final String DEFAULT_ENABLED = "false";
    
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
     * Inner class to hold payment status information
     */
    public static class PaymentStatus {
        private String status;
        private boolean allowSample;
        private String message;
        
        public PaymentStatus(String status, boolean allowSample, String message) {
            this.status = status;
            this.allowSample = allowSample;
            this.message = message;
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
    }
    
    /**
     * Check if payment validation is enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }
    
    /**
     * Validates payment status for a lab order
     * @param orderUuid The UUID of the lab order from Bahmni
     * @return PaymentStatus object with validation result
     */
    public PaymentStatus validatePayment(String orderUuid) {
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
            
            // Prepare request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("order_uuid", orderUuid);
            String jsonInputString = requestBody.toString();
            
            // Send request
            outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonInputString);
            outputStream.flush();
            
            // Get response code
            int responseCode = connection.getResponseCode();
            
            // Read response
            reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuffer response = new StringBuffer();
            
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            
            // Parse JSON response
            JSONObject result = new JSONObject(response.toString());
            
            // Log the response for debugging
            System.out.println("Payment validation response for order " + orderUuid + ": " + result.toString());
            
            return new PaymentStatus(
                result.optString("status", "error"),
                result.optBoolean("allow_sample", false),
                result.optString("message", "Unable to verify payment status")
            );
            
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Payment validation timeout for order " + orderUuid);
            e.printStackTrace();
            return new PaymentStatus("error", false, 
                "Payment verification timeout. Please try again or contact billing.");
                
        } catch (java.io.IOException e) {
            System.err.println("Payment validation IO error for order " + orderUuid + ": " + e.getMessage());
            e.printStackTrace();
            return new PaymentStatus("error", false, 
                "Unable to connect to payment service. Please contact billing.");
                
        } catch (Exception e) {
            System.err.println("Payment validation error for order " + orderUuid + ": " + e.getMessage());
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
            URL url = new URL(odooApiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode > 0;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }
}