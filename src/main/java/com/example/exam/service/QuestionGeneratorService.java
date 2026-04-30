package com.example.exam.service;

import com.example.exam.model.Question;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionGeneratorService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final java.util.Random random = new java.util.Random();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    public List<Question> generateQuestions(String syllabusText, String topic, int easyCount, int mediumCount, int hardCount, boolean useAdvancedAI) {
        if (useAdvancedAI && apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE")) {
            try {
                return generateWithGemini(syllabusText, topic, easyCount, mediumCount, hardCount);
            } catch (Exception e) {
                System.err.println(">>> Advanced AI failed, falling back to heuristic: " + e.getMessage());
            }
        }
        
        // Combine topic and syllabus if both exist
        String combinedText = (topic != null ? topic + "\n" : "") + (syllabusText != null ? syllabusText : "");
        return generateHeuristically(combinedText, easyCount, mediumCount, hardCount);
    }

    private List<Question> generateHeuristically(String text, int easyCount, int mediumCount, int hardCount) {
        List<Question> questions = new ArrayList<>();
        int totalNeeded = easyCount + mediumCount + hardCount;
        
        boolean isJava = text != null && text.toLowerCase().contains("java");
        java.util.Map<String, List<String>> termToContexts = buildTermMap(text);
        
        int easyRemaining = easyCount;
        int mediumRemaining = mediumCount;
        int hardRemaining = hardCount;

        if (!termToContexts.isEmpty()) {
            List<String> terms = new ArrayList<>(termToContexts.keySet());
            Collections.shuffle(terms);
            int termIndex = 0;
            int attempts = 0;
            int maxAttempts = totalNeeded * 5;

            while (questions.size() < totalNeeded && attempts < maxAttempts) {
                attempts++;
                String term = terms.get(termIndex % terms.size());
                termIndex++;

                List<String> contexts = termToContexts.get(term);
                if (contexts == null || contexts.isEmpty()) continue;

                String context = contexts.get(random.nextInt(contexts.size()));
                String difficulty = getNextNeededDifficulty(easyRemaining, mediumRemaining, hardRemaining);
                if (difficulty == null) break;

                Question q = createContextualQuestion(term, context, difficulty, termToContexts);
                if (q != null && !isDuplicate(q, questions)) {
                    questions.add(q);
                    if (difficulty.equals("Easy")) easyRemaining--;
                    else if (difficulty.equals("Medium")) mediumRemaining--;
                    else hardRemaining--;
                }
            }
        }

        // Fill remaining with varied generic questions
        while (questions.size() < totalNeeded) {
            String difficulty = getNextNeededDifficulty(easyRemaining, mediumRemaining, hardRemaining);
            if (difficulty == null) break;
            
            questions.add(createVariedGenericQuestion(difficulty, isJava));
            if (difficulty.equals("Easy")) easyRemaining--;
            else if (difficulty.equals("Medium")) mediumRemaining--;
            else hardRemaining--;
        }

        return questions;
    }

    private List<Question> generateWithGemini(String syllabus, String topic, int easy, int medium, int hard) throws Exception {
        String prompt = String.format(
            "Generate %d total multiple-choice questions for an online exam.\n" +
            "Topic/Focus: %s\n" +
            "Context/Syllabus: %s\n\n" +
            "Difficulty Distribution:\n" +
            "- %d Easy (1 mark each)\n" +
            "- %d Medium (2 marks each)\n" +
            "- %d Hard (5 marks each)\n\n" +
            "Return the questions in a JSON array format exactly like this:\n" +
            "[{\"text\": \"...\", \"option1\": \"...\", \"option2\": \"...\", \"option3\": \"...\", \"option4\": \"...\", \"correctAnswer\": 1, \"difficulty\": \"Easy\", \"marks\": 1}, ...]\n" +
            "Ensure 'correctAnswer' is an integer (1-4). Do not include any markdown or text other than the JSON array.",
            (easy + medium + hard), topic != null ? topic : "General Knowledge", syllabus != null ? syllabus : "", easy, medium, hard
        );

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL + apiKey, entity, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            String textResponse = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
            // Basic cleanup in case Gemini includes markdown triple backticks
            textResponse = textResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            
            Question[] generated = objectMapper.readValue(textResponse, Question[].class);
            return Arrays.asList(generated);
        } else {
            throw new RuntimeException("Gemini API error: " + response.getStatusCode());
        }
    }

    private String getNextNeededDifficulty(int easy, int medium, int hard) {
        if (easy > 0) return "Easy";
        if (medium > 0) return "Medium";
        if (hard > 0) return "Hard";
        return null;
    }

    private Question createVariedGenericQuestion(String difficulty, boolean isJava) {
        List<Map<String, Object>> javaPool = new ArrayList<>();
        List<Map<String, Object>> generalPool = new ArrayList<>();
        
        // --- High-Quality Java Pool (User Provided) ---
        javaPool.add(Map.of("text", "Which keyword is used to inherit a class in Java?", 
                 "o1", "implement", "o2", "extends", "o3", "inherits", "o4", "super", "correct", 2));
        javaPool.add(Map.of("text", "Which method is the entry point of a Java program?", 
                 "o1", "start()", "o2", "run()", "o3", "main()", "o4", "init()", "correct", 3));
        javaPool.add(Map.of("text", "Which of these is not a primitive data type?", 
                 "o1", "int", "o2", "float", "o3", "String", "o4", "char", "correct", 3));
        javaPool.add(Map.of("text", "What is the size of int in Java?", 
                 "o1", "2 bytes", "o2", "4 bytes", "o3", "8 bytes", "o4", "Depends on system", "correct", 2));
        javaPool.add(Map.of("text", "Which operator is used for comparison?", 
                 "o1", "=", "o2", "==", "o3", "!", "o4", "&&", "correct", 2));
        javaPool.add(Map.of("text", "Which collection allows duplicate elements?", 
                 "o1", "Set", "o2", "Map", "o3", "List", "o4", "None", "correct", 3));
        javaPool.add(Map.of("text", "Which keyword is used to define a constant?", 
                 "o1", "static", "o2", "final", "o3", "const", "o4", "immutable", "correct", 2));
        javaPool.add(Map.of("text", "What is JVM?", 
                 "o1", "Java Variable Machine", "o2", "Java Virtual Machine", "o3", "Java Verified Machine", "o4", "Java Vendor Machine", "correct", 2));
        javaPool.add(Map.of("text", "Which exception is unchecked?", 
                 "o1", "IOException", "o2", "SQLException", "o3", "NullPointerException", "o4", "InterruptedException", "correct", 3));
        javaPool.add(Map.of("text", "Which loop executes at least once?", 
                 "o1", "for", "o2", "while", "o3", "do-while", "o4", "none", "correct", 3));

        // --- General Pool ---
        generalPool.add(Map.of(
            "text", "In the context of robust software engineering, what is the primary purpose of 'Unit Testing'?",
            "o1", "To verify the behavior of individual components in isolation.",
            "o2", "To test the entire system's user interface for visual bugs.",
            "o3", "To measure the response time of the production database.",
            "o4", "To ensure that all third-party APIs are currently online.",
            "correct", 1
        ));
        
        generalPool.add(Map.of(
            "text", "Which of the following best describes 'High Availability' in a distributed system?",
            "o1", "The system's ability to process massive amounts of data in seconds.",
            "o2", "The system's ability to remain operational even if some components fail.",
            "o3", "The ability of the system to scale its storage capacity infinitely.",
            "o4", "The security level of the system against brute-force attacks.",
            "correct", 2
        ));

        // Fallback pick logic
        Map<String, Object> data;
        if (isJava && random.nextInt(10) < 8) {
            data = javaPool.get(random.nextInt(javaPool.size()));
        } else if (!isJava && random.nextInt(10) < 8) {
            data = generalPool.get(random.nextInt(generalPool.size()));
        } else {
            List<Map<String, Object>> combined = new ArrayList<>(javaPool);
            combined.addAll(generalPool);
            data = combined.get(random.nextInt(combined.size()));
        }

        Question q = new Question();
        q.setText((String) data.get("text"));
        q.setOption1((String) data.get("o1"));
        q.setOption2((String) data.get("o2"));
        q.setOption3((String) data.get("o3"));
        q.setOption4((String) data.get("o4"));
        q.setCorrectAnswer((Integer) data.get("correct"));
        q.setDifficulty(difficulty);
        q.setMarks(setMarksByDifficulty(difficulty));
        return q;
    }

    private java.util.Map<String, List<String>> buildTermMap(String text) {
        java.util.Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) return map;
        
        String[] sentences = text.split("(?<=[.!?])\\s+(?=[A-Z])|\n+");

        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.length() < 15) continue;

            Pattern p = Pattern.compile("^([A-Z][a-zA-Z\\s]{2,30}?)(?=\\s+(is|are|provides|means|contains|includes|uses|refers|describes|aims|seeks|acts))", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            if (m.find()) {
                String term = m.group(1).trim();
                map.computeIfAbsent(term, k -> new ArrayList<>()).add(s);
            } else {
                Pattern p2 = Pattern.compile("^([A-Z][a-zA-Z\\s]{2,25}):\\s*(.*)");
                Matcher m2 = p2.matcher(s);
                if (m2.find()) {
                    String term = m2.group(1).trim();
                    map.computeIfAbsent(term, k -> new ArrayList<>()).add(s);
                }
            }
        }
        return map;
    }

    private Question createContextualQuestion(String term, String context, String difficulty, java.util.Map<String, List<String>> map) {
        String detail = context;
        Pattern p = Pattern.compile("^(.*?)\\s+(is|are|provides|means|contains|includes|uses|refers|describes|aims|seeks|acts|serves)\\s+(.*)$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(context);
        
        String action = "is";
        if (m.find()) {
            action = m.group(2).trim();
            detail = m.group(3).trim();
        }

        List<String> templates = List.of(
            "Which of the following describes the role of '[term]'?",
            "Identify a significant aspect of '[term]' according to the provided material:",
            "Regarding '[term]', what is stated as its primary function or characteristic?",
            "[term] [action] which of the following?",
            "How is '[term]' defined or characterized in this context?"
        );

        Question q = new Question();
        String stem = templates.get(random.nextInt(templates.size()));
        q.setText(stem.replace("[term]", term).replace("[action]", action));

        List<String> options = new ArrayList<>();
        String truncDetail = truncate(detail, 120);
        options.add(truncDetail);

        List<String> otherTerms = new ArrayList<>(map.keySet());
        otherTerms.remove(term);
        Collections.shuffle(otherTerms);

        for (String otherTerm : otherTerms) {
            if (options.size() >= 4) break;
            List<String> otherContexts = map.get(otherTerm);
            String otherContext = otherContexts.get(random.nextInt(otherContexts.size()));
            Matcher m2 = p.matcher(otherContext);
            if (m2.find()) {
                String truncatedOther = truncate(m2.group(3).trim(), 120);
                if (!options.contains(truncatedOther)) options.add(truncatedOther);
            }
        }

        while (options.size() < 4) {
            options.add("It provides an abstract interface for the " + term + " integration.");
            if (options.size() < 4) options.add("Implementation is typically handled by the underlying system kernel.");
        }

        return finalizeQuestion(q, options, truncDetail, difficulty);
    }

    private Question finalizeQuestion(Question q, List<String> options, String correctAnswer, String difficulty) {
        Collections.shuffle(options);
        q.setOption1(options.get(0));
        q.setOption2(options.get(1));
        q.setOption3(options.get(2));
        q.setOption4(options.get(3));

        for (int i = 0; i < 4; i++) {
            if (options.get(i).equals(correctAnswer)) {
                q.setCorrectAnswer(i + 1);
                break;
            }
        }
        q.setMarks(setMarksByDifficulty(difficulty));
        q.setDifficulty(difficulty);
        return q;
    }

    private boolean isDuplicate(Question newQ, List<Question> existing) {
        return existing.stream().anyMatch(q -> q.getText().equalsIgnoreCase(newQ.getText()));
    }

    private String truncate(String text, int length) {
        if (text.length() <= length) return text;
        String t = text.substring(0, length - 3);
        int lastSpace = t.lastIndexOf(" ");
        if (lastSpace > length / 2) t = t.substring(0, lastSpace);
        return t + "...";
    }

    private int setMarksByDifficulty(String difficulty) {
        return switch (difficulty) {
            case "Easy" -> 1;
            case "Medium" -> 2;
            case "Hard" -> 5;
            default -> 2;
        };
    }
}
