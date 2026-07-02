package com.Shubham.carDealership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.FsmTechnician;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG service for FSM entities (jobs, customers, technicians).
 * Uses the same Pinecone index as the car dealership RAG, but in namespace "fsm".
 * Metadata filter on businessOwnerId ensures multi-tenant isolation.
 */
@Service
public class FsmRAGService {

    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${pinecone.api.key:}")
    private String pineconeApiKey;

    @Value("${pinecone.index.url:}")
    private String pineconeIndexUrl;

    @Autowired private FsmJobRepository jobRepo;
    @Autowired private FsmTechnicianRepository techRepo;
    @Autowired private FsmCustomerRepository customerRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isAvailable() {
        return pineconeApiKey != null && !pineconeApiKey.isEmpty()
            && openAiKey != null && !openAiKey.isEmpty()
            && pineconeIndexUrl != null && !pineconeIndexUrl.isEmpty();
    }

    // ── Public: search for relevant FSM context ──────────────────────────
    public String searchRelevant(String query, Long businessOwnerId, int topK) {
        if (!isAvailable()) return null;
        try {
            List<Double> queryVec = embed(query);
            if (queryVec == null) return null;

            List<Map<String, Object>> matches = queryPinecone(queryVec, topK, businessOwnerId);
            if (matches.isEmpty()) return null;

            StringBuilder ctx = new StringBuilder();
            ctx.append("RELEVANT HISTORY (from vector search):\n");
            for (Map<String, Object> m : matches) {
                Map<String, Object> meta = (Map<String, Object>) m.get("metadata");
                if (meta == null) continue;
                String type = (String) meta.getOrDefault("type", "");
                String text = (String) meta.getOrDefault("text", "");
                if (!text.isEmpty()) ctx.append("- [").append(type).append("] ").append(text).append("\n");
            }
            return ctx.toString();
        } catch (Exception e) {
            System.err.println("FSM RAG search error: " + e.getMessage());
            return null;
        }
    }

    // ── Public: index a job into Pinecone ───────────────────────────────
    public void indexJob(FsmJob job) {
        if (!isAvailable()) return;
        try {
            String text = buildJobText(job);
            List<Double> vec = embed(text);
            if (vec == null) return;
            Map<String, String> meta = new HashMap<>();
            meta.put("type", "job");
            meta.put("id", String.valueOf(job.getId()));
            meta.put("businessOwnerId", String.valueOf(job.getBusinessOwnerId()));
            meta.put("text", text.length() > 500 ? text.substring(0, 500) : text);
            upsert("job-" + job.getId(), vec, meta, job.getBusinessOwnerId());
        } catch (Exception e) {
            System.err.println("FSM RAG index job error: " + e.getMessage());
        }
    }

    // ── Public: index a customer ─────────────────────────────────────────
    public void indexCustomer(FsmCustomer c) {
        if (!isAvailable()) return;
        try {
            String text = buildCustomerText(c);
            List<Double> vec = embed(text);
            if (vec == null) return;
            Map<String, String> meta = new HashMap<>();
            meta.put("type", "customer");
            meta.put("id", String.valueOf(c.getId()));
            meta.put("businessOwnerId", String.valueOf(c.getBusinessOwnerId()));
            meta.put("text", text);
            upsert("cust-" + c.getId(), vec, meta, c.getBusinessOwnerId());
        } catch (Exception e) {
            System.err.println("FSM RAG index customer error: " + e.getMessage());
        }
    }

    // ── Public: index a technician ───────────────────────────────────────
    public void indexTechnician(FsmTechnician t) {
        if (!isAvailable()) return;
        try {
            String text = buildTechText(t);
            List<Double> vec = embed(text);
            if (vec == null) return;
            Map<String, String> meta = new HashMap<>();
            meta.put("type", "technician");
            meta.put("id", String.valueOf(t.getId()));
            meta.put("businessOwnerId", String.valueOf(t.getBusinessOwnerId()));
            meta.put("text", text);
            upsert("tech-" + t.getId(), vec, meta, t.getBusinessOwnerId());
        } catch (Exception e) {
            System.err.println("FSM RAG index technician error: " + e.getMessage());
        }
    }

    // ── Text builders ────────────────────────────────────────────────────
    private String buildJobText(FsmJob j) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("Job #").append(j.getId()).append(": ").append(j.getJobType());
        sb.append(", status=").append(j.getStatus());
        sb.append(", priority=").append(j.getPriority());
        if (j.getAddress() != null) sb.append(", address=").append(j.getAddress());
        if (j.getTechnician() != null) sb.append(", tech=").append(j.getTechnician().getName());
        if (j.getCustomer()   != null) sb.append(", customer=").append(j.getCustomer().getName());
        if (j.getScheduledAt() != null) sb.append(", scheduled=").append(j.getScheduledAt().format(fmt));
        if (j.getAmount() != null && j.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
            sb.append(", amount=$").append(j.getAmount().toPlainString());
        if (j.getDescription() != null) sb.append(". ").append(j.getDescription());
        if (j.getNotes() != null) sb.append(" Notes: ").append(j.getNotes());
        return sb.toString();
    }

    private String buildCustomerText(FsmCustomer c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer: ").append(c.getName());
        if (c.getPhone() != null)   sb.append(", phone=").append(c.getPhone());
        if (c.getEmail() != null)   sb.append(", email=").append(c.getEmail());
        if (c.getAddress() != null) sb.append(", address=").append(c.getAddress());
        if (c.getNotes() != null)   sb.append(". Notes: ").append(c.getNotes());
        return sb.toString();
    }

    private String buildTechText(FsmTechnician t) {
        StringBuilder sb = new StringBuilder();
        sb.append("Technician: ").append(t.getName());
        sb.append(", status=").append(t.getStatus());
        if (t.getSkills() != null)  sb.append(", skills=").append(t.getSkills());
        if (t.getPhone() != null)   sb.append(", phone=").append(t.getPhone());
        if (t.getEmail() != null)   sb.append(", email=").append(t.getEmail());
        return sb.toString();
    }

    // ── Pinecone: query with metadata filter ─────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryPinecone(List<Double> vector, int topK, Long businessOwnerId) {
        try {
            Map<String, Object> filter = Map.of(
                "businessOwnerId", Map.of("$eq", String.valueOf(businessOwnerId))
            );
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("topK", topK);
            body.put("includeMetadata", true);
            body.put("filter", filter);
            body.put("namespace", "fsm");

            String json   = objectMapper.writeValueAsString(body);
            URL url       = new URL(pineconeIndexUrl + "/query");
            HttpURLConnection conn = openConn(url, "POST");
            conn.setRequestProperty("Api-Key", pineconeApiKey);
            try (OutputStream os = conn.getOutputStream()) { os.write(json.getBytes("utf-8")); }

            if (conn.getResponseCode() == 200) {
                String resp = readBody(conn);
                Map<String, Object> result  = objectMapper.readValue(resp, Map.class);
                return (List<Map<String, Object>>) result.getOrDefault("matches", new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("FSM Pinecone query error: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // ── Pinecone: upsert with namespace ──────────────────────────────────
    private void upsert(String id, List<Double> vector, Map<String, String> metadata, Long businessOwnerId) {
        try {
            Map<String, Object> vecObj = new HashMap<>();
            vecObj.put("id", id);
            vecObj.put("values", vector);
            vecObj.put("metadata", metadata);

            Map<String, Object> body = new HashMap<>();
            body.put("vectors", List.of(vecObj));
            body.put("namespace", "fsm");

            String json   = objectMapper.writeValueAsString(body);
            URL url       = new URL(pineconeIndexUrl + "/vectors/upsert");
            HttpURLConnection conn = openConn(url, "POST");
            conn.setRequestProperty("Api-Key", pineconeApiKey);
            try (OutputStream os = conn.getOutputStream()) { os.write(json.getBytes("utf-8")); }
            conn.getResponseCode(); // fire
        } catch (Exception e) {
            System.err.println("FSM Pinecone upsert error: " + e.getMessage());
        }
    }

    // ── OpenAI embeddings ────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Double> embed(String text) {
        try {
            Map<String, Object> body = Map.of("model", "text-embedding-3-small", "input", text);
            String json   = objectMapper.writeValueAsString(body);
            URL url       = new URL("https://api.openai.com/v1/embeddings");
            HttpURLConnection conn = openConn(url, "POST");
            conn.setRequestProperty("Authorization", "Bearer " + openAiKey);
            try (OutputStream os = conn.getOutputStream()) { os.write(json.getBytes("utf-8")); }

            if (conn.getResponseCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(readBody(conn), Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                return (List<Double>) data.get(0).get("embedding");
            }
        } catch (Exception e) {
            System.err.println("FSM embed error: " + e.getMessage());
        }
        return null;
    }

    private HttpURLConnection openConn(URL url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
