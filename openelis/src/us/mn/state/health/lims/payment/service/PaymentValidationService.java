package us.mn.state.health.lims.payment.service;

import org.json.JSONObject;
import us.mn.state.health.lims.siteinformation.dao.SiteInformationDAO;
import us.mn.state.health.lims.siteinformation.daoimpl.SiteInformationDAOImpl;
import us.mn.state.health.lims.siteinformation.valueholder.SiteInformation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PaymentValidationService {
    
    private String odooApiUrl;
    private int timeoutSeconds;
    private SiteInformationDAO siteInfoDAO;
    
    public PaymentValidationService() {
        this.siteInfoDAO = new SiteInformationDAOImpl();
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        // Load API URL from configuration
        SiteInformation apiUrlInfo = siteInfoDAO.getSiteInformationByName("paymentValidationApiUrl");
        this.odooApiUrl = (apiUrlInfo != null && apiUrlInfo.getValue() != null) 
            ? apiUrlInfo.getValue() 
            : "http://localhost:8069/lab/payment/status"; // Default fallback
        
        // Load timeout from configuration
        SiteInformation timeoutInfo = siteInfoDAO.getSiteInformationByName("paymentValidationTimeout");
        this.timeoutSeconds = (timeoutInfo != null && timeoutInfo.getValue() != null) 
            ? Integer.parseInt(timeoutInfo.getValue()) 
            : 10; // Default 10 seconds
    }
    
    public static class PaymentStatus {
        private String status;
        private boolean allowSample;
        private String message;
        
        public PaymentStatus(String status, boolean allowSample, String message) {
            this.status = status;
            this.allowSample = allowSample;
            this.message = message;
        }
        
        public boolean isAllowSample() { return allowSample; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
    
    public PaymentStatus validatePayment(String orderUuid) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
            
            // Prepare request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("order_uuid", orderUuid);
            
            // Build HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(odooApiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
            
            // Send request
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            // Parse response
            JSONObject result = new JSONObject(response.body());
            
            return new PaymentStatus(
                result.optString("status", "error"),
                result.optBoolean("allow_sample", false),
                result.optString("message", "Unable to verify payment status")
            );
            
        } catch (Exception e) {
            // Log error
            System.err.println("Payment validation error for order " + orderUuid + ": " + e.getMessage());
            e.printStackTrace();
            
            // Block sample collection on error (safer approach)
            return new PaymentStatus("error", false, 
                "Unable to verify payment. Please contact billing department.");
        }
    }
}