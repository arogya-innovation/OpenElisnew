package us.mn.state.health.lims.payment.service;

import org.json.JSONObject;
import us.mn.state.health.lims.siteinformation.dao.SiteInformationDAO;
import us.mn.state.health.lims.siteinformation.daoimpl.SiteInformationDAOImpl;
import us.mn.state.health.lims.siteinformation.valueholder.SiteInformation;

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
    
    private String odooApiUrl;
    private int timeoutSeconds;
    private SiteInformationDAO siteInfoDAO;
    
    public PaymentValidationService() {
        this.siteInfoDAO = new SiteInformationDAOImpl();
        loadConfiguration();
    }
    
    /**
     * Load configuration from database
     */
    private void loadConfiguration() {
        // Load API URL from configuration
        SiteInformation apiUrlInfo = siteInfoDAO.getSiteInformationByName("paymentValidationApiUrl");
        this.odooApiUrl = (apiUrlInfo != null && apiUrlInfo.getValue() != null) 
            ? apiUrlInfo.getValue() 
            : "http://localhost:8069/lab/payment/status";
        
        // Load timeout from configuration
        SiteInformation timeoutInfo = siteInfoDAO.getSiteInformationByName("paymentValidationTimeout");
        this.timeoutSeconds = (timeoutInfo != null && timeoutInfo.getValue() != null) 
            ? Integer.parseInt(timeoutInfo.getValue()) 
            : 10;
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