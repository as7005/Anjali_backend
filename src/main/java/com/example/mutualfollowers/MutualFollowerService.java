package com.example.mutualfollowers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class MutualFollowerService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public void process() throws Exception {
        String regNo = "REG12347";  // Odd → Question 1 (but we use actual data to determine)
        String name = "John Doe";
        String email = "john@example.com";

        // Step 1: Generate webhook and data
        String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
        Map<String, String> requestBody = Map.of("name", name, "regNo", regNo, "email", email);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(generateUrl, request, String.class);
        JsonNode root = mapper.readTree(response.getBody());

        String webhookUrl = root.get("webhook").asText().trim();
        String accessToken = root.get("accessToken").asText().trim();
        JsonNode data = root.get("data");

        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put(" regNo ", regNo);

        // Check if it's Question 2: Nth-Level Followers
        if (data.has("users") && data.get("users").has("n")) {
            System.out.println("Processing as Question 2: Nth-Level Followers");

            int n = data.get("users").get("n").asInt();
            int findId = data.get("users").get("findId").asInt();
            JsonNode users = data.get("users").get("users");

            Map<Integer, Set<Integer>> graph = new HashMap<>();
            for (JsonNode user : users) {
                int id = user.get("id").asInt();
                Set<Integer> follows = new HashSet<>();
                JsonNode followsNode = user.get("follows");
                if (followsNode != null && followsNode.isArray()) {
                    for (JsonNode f : followsNode) {
                        follows.add(f.asInt());
                    }
                }
                graph.put(id, follows);
            }

            Set<Integer> visited = new HashSet<>();
            Queue<Integer> queue = new LinkedList<>();
            queue.offer(findId);
            visited.add(findId);

            int level = 0;
            while (!queue.isEmpty() && level < n) {
                int size = queue.size();
                for (int i = 0; i < size; i++) {
                    int curr = queue.poll();
                    for (int neighbor : graph.getOrDefault(curr, new HashSet<>())) {
                        if (!visited.contains(neighbor)) {
                            queue.offer(neighbor);
                            visited.add(neighbor);
                        }
                    }
                }
                level++;
            }

            List<Integer> result = new ArrayList<>(queue);
            resultPayload.put(" outcome ", result);

        } else {
            throw new RuntimeException("Unrecognized data format or not Question 2");
        }

        // Step 3: Send final POST to webhook with retry
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        postHeaders.set("Authorization", accessToken);

        String jsonPayload = mapper.writeValueAsString(resultPayload);
        System.out.println("Sending payload: " + jsonPayload);

        HttpEntity<String> postRequest = new HttpEntity<>(jsonPayload, postHeaders);

        boolean sent = false;
        for (int i = 0; i < 4 && !sent; i++) {
            try {
                ResponseEntity<String> postResponse = restTemplate.postForEntity(webhookUrl, postRequest, String.class);
                System.out.println("Webhook response: " + postResponse.getBody());
                sent = true;
            } catch (Exception ex) {
                System.out.println("Attempt " + (i + 1) + " failed: " + ex.getMessage());
                Thread.sleep(1000);
            }
        }

        if (!sent) {
            System.err.println("Failed to send after 4 attempts.");
        }
    }
}
