package com.Shubham.carDealership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RAGService {

    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${pinecone.api.key:}")
    private String pineconeApiKey;

    @Value("${pinecone.index.url:}")
    private String pineconeIndexUrl;

    @Autowired
    private CarRepository carRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean available = false;

    @PostConstruct
    public void init() {
        if (pineconeApiKey != null && !pineconeApiKey.isEmpty()
                && openAiKey != null && !openAiKey.isEmpty()) {
            available = true;
            System.out.println("✅ RAG Service initialized (Pinecone + OpenAI embeddings)");
            // Index all available cars on startup
            indexAllCars();
        } else {
            System.out.println("⚠️ RAG Service not configured — missing Pinecone or OpenAI key");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PUBLIC: Answer a question using RAG
    // ─────────────────────────────────────────────────────────────
    public String answer(String userQuestion) {
        if (!available) return null;

        try {
            System.out.println("🔍 RAG: searching for: " + userQuestion);

            // 1. Embed the user question
            List<Double> questionEmbedding = embed(userQuestion);
            if (questionEmbedding == null) return null;

            // 2. Query Pinecone for top 5 similar cars
            List<String> carIds = queryPinecone(questionEmbedding, 5);
            if (carIds.isEmpty()) {
                return "I couldn't find any cars matching your question in our inventory.";
            }

            // 3. Fetch those cars from PostgreSQL
            List<Car> relevantCars = carIds.stream()
                    .map(id -> {
                        try { return carRepository.findById(Long.parseLong(id)).orElse(null); }
                        catch (Exception e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .filter(car -> "AVAILABLE".equals(car.getStatus()))
                    .collect(Collectors.toList());

            if (relevantCars.isEmpty()) {
                return "I found some results but those cars are no longer available.";
            }

            // 4. Build context from real car data
            String context = buildContext(relevantCars);

            // 5. Ask GPT-4o-mini to answer based on context
            String answer = generateRAGAnswer(userQuestion, context);
            System.out.println("✅ RAG answer generated");
            return answer;

        } catch (Exception e) {
            System.err.println("❌ RAG error: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // INDEX: Embed and store all cars in Pinecone
    // ─────────────────────────────────────────────────────────────
    public void indexAllCars() {
        if (!available) return;

        try {
            List<Car> cars = carRepository.findAll();
            System.out.println("📦 Indexing " + cars.size() + " cars into Pinecone...");

            int indexed = 0;
            for (Car car : cars) {
                boolean success = indexCar(car);
                if (success) indexed++;
                // Small delay to avoid rate limits
                Thread.sleep(100);
            }

            System.out.println("✅ Indexed " + indexed + "/" + cars.size() + " cars into Pinecone");

        } catch (Exception e) {
            System.err.println("❌ Indexing error: " + e.getMessage());
        }
    }

    // Index a single car (call this when a new car is listed)
    public boolean indexCar(Car car) {
        if (!available) return false;

        try {
            String text = buildCarText(car);
            List<Double> embedding = embed(text);
            if (embedding == null) return false;

            return upsertToPinecone(
                    String.valueOf(car.getId()),
                    embedding,
                    Map.of(
                            "make",   car.getMake()   != null ? car.getMake()   : "",
                            "model",  car.getModel()  != null ? car.getModel()  : "",
                            "year",   String.valueOf(car.getYear()),
                            "price",  car.getPrice()  != null ? car.getPrice().toPlainString() : "0",
                            "status", car.getStatus() != null ? car.getStatus() : "AVAILABLE",
                            "source", car.getCarSource() != null ? car.getCarSource() : "MARKETPLACE"
                    )
            );

        } catch (Exception e) {
            System.err.println("❌ Failed to index car " + car.getId() + ": " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Build text representation of a car for embedding
    // ─────────────────────────────────────────────────────────────
    private String buildCarText(Car car) {
        return String.format(
                "%d %s %s. Price: $%s. Mileage: %d km. Fuel: %s. Transmission: %s. Body: %s. " +
                        "Source: %s. Status: %s. %s",
                car.getYear(),
                car.getMake(),
                car.getModel(),
                car.getPrice() != null ? car.getPrice().toPlainString() : "0",
                car.getMileage() != null ? car.getMileage() : 0,
                car.getFuel() != null ? car.getFuel() : "",
                car.getTransmission() != null ? car.getTransmission() : "",
                car.getBodyType() != null ? car.getBodyType() : "",
                car.getCarSource() != null ? car.getCarSource() : "",
                car.getStatus() != null ? car.getStatus() : "",
                car.getDescription() != null ? car.getDescription() : ""
        );
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Build context string from retrieved cars
    // ─────────────────────────────────────────────────────────────
    private String buildContext(List<Car> cars) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available cars in our inventory:\n\n");
        for (Car car : cars) {
            sb.append(String.format(
                    "- %d %s %s | $%s | %d km | %s | %s | %s | Source: %s\n",
                    car.getYear(), car.getMake(), car.getModel(),
                    car.getPrice() != null ? car.getPrice().toPlainString() : "N/A",
                    car.getMileage() != null ? car.getMileage() : 0,
                    car.getFuel() != null ? car.getFuel() : "N/A",
                    car.getTransmission() != null ? car.getTransmission() : "N/A",
                    car.getBodyType() != null ? car.getBodyType() : "N/A",
                    car.getCarSource() != null ? car.getCarSource() : "N/A"
            ));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Generate answer using GPT-4o-mini with RAG context
    // ─────────────────────────────────────────────────────────────
    private String generateRAGAnswer(String question, String context) {
        try {
            String systemPrompt =
                    "You are a helpful car dealership assistant for Shubham's Car Dealership in Auckland, NZ. " +
                            "Answer the user's question based ONLY on the car inventory context provided. " +
                            "Be concise (under 120 words). If the context doesn't contain relevant info, say so honestly. " +
                            "Always mention specific car details (make, model, year, price) from the context.";

            String userPrompt = "INVENTORY CONTEXT:\n" + context + "\n\nUSER QUESTION: " + question;

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 250);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userPrompt)
            ));

            String json = objectMapper.writeValueAsString(body);

            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + openAiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Map<String, Object> result = objectMapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return ((String) message.get("content")).trim();
            }

        } catch (Exception e) {
            System.err.println("❌ RAG answer generation failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Generate embedding via OpenAI
    // ─────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Double> embed(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "text-embedding-3-small");
            body.put("input", text);

            String json = objectMapper.writeValueAsString(body);

            URL url = new URL("https://api.openai.com/v1/embeddings");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + openAiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Map<String, Object> result = objectMapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                return embedding;
            }

        } catch (Exception e) {
            System.err.println("❌ Embedding failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Query Pinecone for similar vectors
    // ─────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<String> queryPinecone(List<Double> vector, int topK) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("topK", topK);
            body.put("includeMetadata", true);

            String json = objectMapper.writeValueAsString(body);

            URL url = new URL(pineconeIndexUrl + "/query");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Api-Key", pineconeApiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Map<String, Object> result = objectMapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");

                return matches.stream()
                        .map(m -> (String) m.get("id"))
                        .collect(Collectors.toList());
            }

        } catch (Exception e) {
            System.err.println("❌ Pinecone query failed: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE: Upsert a vector into Pinecone
    // ─────────────────────────────────────────────────────────────
    private boolean upsertToPinecone(String id, List<Double> vector, Map<String, String> metadata) {
        try {
            Map<String, Object> vectorObj = new HashMap<>();
            vectorObj.put("id", id);
            vectorObj.put("values", vector);
            vectorObj.put("metadata", metadata);

            Map<String, Object> body = new HashMap<>();
            body.put("vectors", List.of(vectorObj));

            String json = objectMapper.writeValueAsString(body);

            URL url = new URL(pineconeIndexUrl + "/vectors/upsert");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Api-Key", pineconeApiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            return conn.getResponseCode() == 200;

        } catch (Exception e) {
            System.err.println("❌ Pinecone upsert failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}