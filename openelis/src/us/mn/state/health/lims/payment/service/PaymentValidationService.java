package us.mn.state.health.lims.payment.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
 * Java 7 compatible implementation
 */
public class PaymentValidationService {

    private static final Log LOG = LogFactory.getLog(PaymentValidationService.class);

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
        SiteInformation apiUrlInfo =
                siteInfoDAO.getSiteInformationByName("paymentValidationApiUrl");

        this.odooApiUrl =
                (apiUrlInfo != null && apiUrlInfo.getValue() != null)
                        ? apiUrlInfo.getValue()
                        : "http://localhost:8069/lab/payment/status";

        SiteInformation timeoutInfo =
                siteInfoDAO.getSiteInformationByName("paymentValidationTimeout");

        this.timeoutSeconds =
                (timeoutInfo != null && timeoutInfo.getValue() != null)
                        ? Integer.parseInt(timeoutInfo.getValue())
                        : 10;

        LOG.info("PaymentValidationService configured | apiUrl=" + odooApiUrl +
                 " | timeoutSeconds=" + timeoutSeconds);
    }

    /**
     * Holder for payment validation result
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
     * Validate payment status for a lab order
     */
    public PaymentStatus validatePayment(String orderUuid) {

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        BufferedReader reader = null;

        try {
            LOG.info("Validating payment | orderUuid=" + orderUuid);

            URL url = new URL(odooApiUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            JSONObject requestBody = new JSONObject();
            requestBody.put("order_uuid", orderUuid);

            outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(requestBody.toString());
            outputStream.flush();

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOG.warn("Payment API returned non-200 response | orderUuid=" +
                         orderUuid + " | httpCode=" + responseCode);

                return new PaymentStatus(
                        "error",
                        false,
                        "Payment verification failed (HTTP " + responseCode + ")");
            }

            reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));

            String line;
            StringBuffer response = new StringBuffer();

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JSONObject result = new JSONObject(response.toString());

            LOG.debug("Payment API response | orderUuid=" + orderUuid +
                      " | response=" + result.toString());

            return new PaymentStatus(
                    result.optString("status", "error"),
                    result.optBoolean("allow_sample", false),
                    result.optString("message", "Unable to verify payment status"));

        } catch (java.net.SocketTimeoutException e) {
            LOG.error("Payment validation TIMEOUT | orderUuid=" + orderUuid, e);

            return new PaymentStatus(
                    "error",
                    false,
                    "Payment verification timeout. Please contact billing.");

        } catch (java.io.IOException e) {
            LOG.error("Payment validation IO ERROR | orderUuid=" + orderUuid, e);

            return new PaymentStatus(
                    "error",
                    false,
                    "Unable to connect to payment service. Please contact billing.");

        } catch (Exception e) {
            LOG.error("Payment validation UNEXPECTED ERROR | orderUuid=" + orderUuid, e);

            return new PaymentStatus(
                    "error",
                    false,
                    "Unable to verify payment. Please contact billing.");

        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                LOG.warn("Error closing output stream", e);
            }

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                LOG.warn("Error closing response reader", e);
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Test API connectivity
     */
    public boolean testConnection() {
        HttpURLConnection connection = null;

        try {
            LOG.info("Testing payment API connectivity | url=" + odooApiUrl);

            URL url = new URL(odooApiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            LOG.info("Payment API connectivity OK | httpCode=" + responseCode);

            return responseCode > 0;

        } catch (Exception e) {
            LOG.error("Payment API connectivity FAILED", e);
            return false;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
