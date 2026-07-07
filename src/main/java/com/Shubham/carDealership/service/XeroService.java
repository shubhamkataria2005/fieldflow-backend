package com.Shubham.carDealership.service;

import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.model.FsmInvoice;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.fsm.repository.FsmInvoiceRepository;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class XeroService {

    private static final Logger log = LoggerFactory.getLogger(XeroService.class);

    private static final String AUTH_URL  = "https://login.xero.com/identity/connect/authorize";
    private static final String TOKEN_URL = "https://identity.xero.com/connect/token";
    private static final String CONN_URL  = "https://api.xero.com/connections";
    private static final String API_BASE  = "https://api.xero.com/api.xro/2.0";

    @Value("${xero.client-id:}")     private String clientId;
    @Value("${xero.client-secret:}") private String clientSecret;
    @Value("${xero.redirect-uri:}")  private String redirectUri;

    @Autowired private UserRepository         userRepo;
    @Autowired private FsmCustomerRepository  customerRepo;
    @Autowired private FsmInvoiceRepository   invoiceRepo;

    private final WebClient    webClient = WebClient.builder().build();
    private final ObjectMapper mapper    = new ObjectMapper();

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public String buildAuthUrl(Long userId) {
        String scope = "openid profile email accounting.invoices accounting.contacts offline_access";
        return AUTH_URL
                + "?response_type=code"
                + "&client_id="    + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope="        + URLEncoder.encode(scope, StandardCharsets.UTF_8)
                + "&state="        + userId;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCode(String code) {
        String raw = webClient.post().uri(TOKEN_URL)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", code)
                        .with("redirect_uri", redirectUri))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try { return mapper.readValue(raw, Map.class); }
        catch (Exception e) { throw new RuntimeException("Xero code exchange failed", e); }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshTokens(String refreshToken) {
        String raw = webClient.post().uri(TOKEN_URL)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try { return mapper.readValue(raw, Map.class); }
        catch (Exception e) { throw new RuntimeException("Xero token refresh failed", e); }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getTenantInfo(String accessToken) {
        String raw = webClient.get().uri(CONN_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            List<Map<String, Object>> conns = mapper.readValue(raw, List.class);
            if (conns == null || conns.isEmpty())
                return Map.of("tenantId", "", "orgName", "Xero Organisation");
            Map<String, Object> first = conns.get(0);
            return Map.of(
                    "tenantId", String.valueOf(first.getOrDefault("tenantId", "")),
                    "orgName",  String.valueOf(first.getOrDefault("tenantName", "Xero Organisation"))
            );
        } catch (Exception e) { throw new RuntimeException("Failed to get Xero tenant info", e); }
    }

    // Rotates the refresh token, saves the new one to the user record, returns fresh access token
    private String getAccessToken(User user) {
        Map<String, Object> tokens = refreshTokens(user.getXeroRefreshToken());
        user.setXeroRefreshToken(String.valueOf(tokens.get("refresh_token")));
        userRepo.save(user);
        return String.valueOf(tokens.get("access_token"));
    }

    @SuppressWarnings("unchecked")
    public void syncCustomer(FsmCustomer customer, User owner) {
        if (!isConfigured() || owner.getXeroRefreshToken() == null) return;
        try {
            String accessToken = getAccessToken(owner);
            String tenantId    = owner.getXeroTenantId();

            Map<String, Object> contact = new LinkedHashMap<>();
            if (customer.getXeroContactId() != null)
                contact.put("ContactID", customer.getXeroContactId());
            contact.put("Name", customer.getName());
            if (customer.getEmail() != null && !customer.getEmail().isBlank())
                contact.put("EmailAddress", customer.getEmail());
            if (customer.getPhone() != null && !customer.getPhone().isBlank())
                contact.put("Phones", List.of(Map.of("PhoneType", "MOBILE", "PhoneNumber", customer.getPhone())));
            if (customer.getAddress() != null && !customer.getAddress().isBlank())
                contact.put("Addresses", List.of(Map.of("AddressType", "STREET", "AddressLine1", customer.getAddress())));

            String payload = mapper.writeValueAsString(Map.of("Contacts", List.of(contact)));

            String resp = webClient.post().uri(API_BASE + "/Contacts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("Xero-tenant-id", tenantId)
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> result = mapper.readValue(resp, Map.class);
            List<Map<String, Object>> contacts = (List<Map<String, Object>>) result.get("Contacts");
            if (contacts != null && !contacts.isEmpty()) {
                customer.setXeroContactId(String.valueOf(contacts.get(0).get("ContactID")));
                customerRepo.save(customer);
            }
        } catch (Exception e) {
            String body = (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre)
                    ? wcre.getResponseBodyAsString() : "";
            log.warn("Xero: customer sync failed id={}: {} | body: {}", customer.getId(), e.getMessage(), body);
        }
    }

    @SuppressWarnings("unchecked")
    public void syncInvoice(FsmInvoice invoice, User owner) {
        if (!isConfigured() || owner.getXeroRefreshToken() == null) return;
        try {
            // Ensure customer has a Xero contact first
            FsmCustomer customer = invoice.getCustomer();
            if (customer != null && customer.getXeroContactId() == null) {
                syncCustomer(customer, owner);
                // owner.xeroRefreshToken is now updated in-memory by syncCustomer → getAccessToken
            }

            String accessToken = getAccessToken(owner);
            String tenantId    = owner.getXeroTenantId();

            java.math.BigDecimal subtotal = invoice.getAmount() != null
                    ? invoice.getAmount() : java.math.BigDecimal.ZERO;
            // Send the total amount (incl. GST if applicable) using NOTAX so Xero
            // doesn't need GST tax rates configured in the org.
            java.math.BigDecimal unitAmount = invoice.isGstEnabled()
                    ? subtotal.multiply(new java.math.BigDecimal("1.15"))
                              .setScale(2, java.math.RoundingMode.HALF_UP)
                    : subtotal;

            String jobDesc = (invoice.getJob() != null && invoice.getJob().getJobType() != null)
                    ? invoice.getJob().getJobType() : "Service";
            String lineDesc = invoice.isGstEnabled() ? jobDesc + " (GST incl.)" : jobDesc;

            Map<String, Object> lineItem = new LinkedHashMap<>();
            lineItem.put("Description", lineDesc);
            lineItem.put("Quantity",    1.0);
            lineItem.put("UnitAmount",  unitAmount.doubleValue());
            lineItem.put("AccountCode", "200");

            Map<String, Object> xeroInv = new LinkedHashMap<>();
            if (invoice.getXeroInvoiceId() != null) xeroInv.put("InvoiceID", invoice.getXeroInvoiceId());
            xeroInv.put("Type",            "ACCREC");
            xeroInv.put("Status",          "DRAFT");
            xeroInv.put("LineAmountTypes", "NOTAX");
            xeroInv.put("Reference",       "FF-" + String.format("%04d", invoice.getId()));
            xeroInv.put("LineItems",       List.of(lineItem));

            if (customer != null) {
                Map<String, Object> contactRef = new LinkedHashMap<>();
                if (customer.getXeroContactId() != null) contactRef.put("ContactID", customer.getXeroContactId());
                else contactRef.put("Name", customer.getName());
                xeroInv.put("Contact", contactRef);
            }

            String payload = mapper.writeValueAsString(Map.of("Invoices", List.of(xeroInv)));

            String resp = webClient.post().uri(API_BASE + "/Invoices")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("Xero-tenant-id", tenantId)
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> result = mapper.readValue(resp, Map.class);
            List<Map<String, Object>> invoices = (List<Map<String, Object>>) result.get("Invoices");
            if (invoices != null && !invoices.isEmpty()) {
                invoice.setXeroInvoiceId(String.valueOf(invoices.get(0).get("InvoiceID")));
                invoiceRepo.save(invoice);
            }
        } catch (Exception e) {
            String body = (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre)
                    ? wcre.getResponseBodyAsString() : "";
            log.warn("Xero: invoice sync failed id={}: {} | body: {}", invoice.getId(), e.getMessage(), body);
        }
    }

    private String basicAuth() {
        return Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }
}
