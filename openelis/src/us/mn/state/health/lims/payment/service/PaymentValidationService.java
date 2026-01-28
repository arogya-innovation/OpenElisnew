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
 * Payment validation service
 * Supports validation using patient_id OR patient_uuid
 * Java 7 compatible
 */
public class PaymentValidationService {

    private static final String DEFAULT_ODOO_API_URL = "http://odoo:8069/lab/payment/status";
    private static final int DEFAULT_TIMEOUT = 10;

    private String odooApiUrl;
    private int timeoutSeconds;
    private boolean enabled;

    public PaymentValidationService() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        ConfigurationProperties config = ConfigurationProperties.getInstance();

        String enabledValue = config.getPropertyValue(Property.enablePaymentValidation);
        this.enabled = !"false".equalsIgnoreCase(enabledValue);

        String apiUrl = config.getPropertyValue(Property.paymentValidationApiUrl);
        this.odooApiUrl = (apiUrl != null && !apiUrl.isEmpty())
                ? apiUrl
                : DEFAULT_ODOO_API_URL;

        try {
            this.timeoutSeconds = Integer.parseInt(
                    config.getPropertyValue(Property.paymentValidationTimeout));
        } catch (Exception e) {
            this.timeoutSeconds = DEFAULT_TIMEOUT;
        }
    }

    /* ==========================
       PUBLIC ENTRY POINTS
       ========================== */

    public PaymentStatus validatePaymentByPatientId(Integer patientId) {
        if (patientId == null) {
            return new PaymentStatus("error", false, "patient_id is required");
        }
        return validatePaymentInternal(patientId, null);
    }

    public PaymentStatus validatePaymentByPatientUuid(String patientUuid) {
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            return new PaymentStatus("error", false, "patient_uuid is required");
        }
        return validatePaymentInternal(null, patientUuid);
    }

    /* ==========================
       CORE IMPLEMENTATION
       ========================== */

    private PaymentStatus validatePaymentInternal(
            Integer patientId,
            String patientUuid
    ) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(odooApiUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setDoOutput(true);

            /* ---- JSON-RPC payload ---- */
            JSONObject params = new JSONObject();

            if (patientId != null) {
                params.put("patient_id", patientId);
            } else {
                params.put("patient_uuid", patientUuid);
            }

            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "call");
            payload.put("params", params);
            payload.put("id", 1);

            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(payload.toString());
            out.flush();
            out.close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8")
            );

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return parseResponse(response.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return new PaymentStatus(
                    "error",
                    false,
                    "Unable to verify payment status"
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /* ==========================
       RESPONSE PARSING
       ========================== */

    private PaymentStatus parseResponse(String responseString) throws Exception {

        JSONObject json = new JSONObject(responseString);

        if (json.has("error")) {
            return new PaymentStatus(
                    "error",
                    false,
                    json.getJSONObject("error").optString("message")
            );
        }

        JSONObject result = json.getJSONObject("result");

        PaymentStatus status = new PaymentStatus(
                result.optString("status"),
                result.optBoolean("allow_sample"),
                result.optString("message")
        );

        status.patientId = result.optInt("patient_id", 0);
        status.patientUuid = result.optString("patient_uuid", null);
        status.patientName = result.optString("patient_name", null);
        status.patientRef = result.optString("patient_ref", null);
        status.invoiceCount = result.optInt("invoice_count", 0);
        status.totalDueAmount = result.optDouble("total_due_amount", 0.0);

        if (result.has("invoices")) {
            JSONArray invoices = result.getJSONArray("invoices");
            for (int i = 0; i < invoices.length(); i++) {
                JSONObject inv = invoices.getJSONObject(i);
                status.invoices.add(new InvoiceInfo(
                        inv.optString("invoice_number"),
                        inv.optString("invoice_date"),
                        inv.optDouble("total_amount"),
                        inv.optDouble("amount_due"),
                        inv.optString("payment_state"),
                        inv.optString("shop_name")
                ));
            }
        }

        return status;
    }

    /* ==========================
       DTO CLASSES
       ========================== */

    public static class PaymentStatus {
        private String status;
        private boolean allowSample;
        private String message;

        private Integer patientId;
        private String patientUuid;
        private String patientName;
        private String patientRef;

        private Integer invoiceCount;
        private Double totalDueAmount;
        private List<InvoiceInfo> invoices = new ArrayList<InvoiceInfo>();

        public PaymentStatus(String status, boolean allowSample, String message) {
            this.status = status;
            this.allowSample = allowSample;
            this.message = message;
        }

        public boolean isAllowSample() {
            return allowSample;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class InvoiceInfo {
        private String invoiceNumber;
        private String invoiceDate;
        private double totalAmount;
        private double amountDue;
        private String paymentState;
        private String shopName;

        public InvoiceInfo(String invoiceNumber, String invoiceDate,
                           double totalAmount, double amountDue,
                           String paymentState, String shopName) {
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
            this.totalAmount = totalAmount;
            this.amountDue = amountDue;
            this.paymentState = paymentState;
            this.shopName = shopName;
        }
    }
}
