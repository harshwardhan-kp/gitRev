import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Asks OpenAI to pick a category for each file based on its name.
 * If there is no API key (or the request fails) it falls back to simple extension rules.
 */
public class AiCategorizer {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int BATCH_SIZE = 100;     // file names sent per request
    private static final int DEEP_BATCH_SIZE = 10; // files per request when contents are included

    private final String apiKey;
    private final String model;
    private final boolean deep;
    private final HttpClient http = HttpClient.newHttpClient();

    /** @param deep also send a sample of each file's contents (text, images, PDFs) */
    public AiCategorizer(String apiKey, String model, boolean deep) {
        this.apiKey = apiKey;
        this.model = model;
        this.deep = deep;
    }

    /** Returns a map of file -> category name. */
    public Map<FileInfo, String> categorize(List<FileInfo> files) {
        Map<FileInfo, String> result = new HashMap<>();
        int size = deep ? DEEP_BATCH_SIZE : BATCH_SIZE;
        for (int start = 0; start < files.size(); start += size) {
            List<FileInfo> batch = files.subList(start, Math.min(start + size, files.size()));
            if (deep) System.out.printf("  reading files %d-%d of %d...%n", start + 1, start + batch.size(), files.size());
            result.putAll(categorizeBatch(batch));
        }
        return result;
    }

    private Map<FileInfo, String> categorizeBatch(List<FileInfo> batch) {
        Map<FileInfo, String> result = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String content = deep ? buildDeepContent(batch) : "\"" + Json.escape(buildPrompt(batch)) + "\"";
                parseAnswer(askOpenAi(content), batch, result);
            } catch (Exception e) {
                if (deep) {
                    System.out.println("  Reading contents failed (" + e.getMessage() + "), retrying with names only.");
                    try {
                        parseAnswer(askOpenAi("\"" + Json.escape(buildPrompt(batch)) + "\""), batch, result);
                    } catch (Exception ignored) {
                        // fall through to extension rules below
                    }
                } else {
                    System.out.println("  AI request failed (" + e.getMessage() + "), using extension rules instead.");
                }
            }
        }
        // Anything the AI didn't answer for gets a rule-based category.
        for (FileInfo file : batch) {
            result.putIfAbsent(file, FallbackRules.categoryFor(file.extension()));
        }
        return result;
    }

    private String buildPrompt(List<FileInfo> batch) {
        StringBuilder prompt = new StringBuilder(instructions()).append("\n\nFiles:\n");
        for (int i = 0; i < batch.size(); i++) {
            prompt.append(i + 1).append(". ").append(batch.get(i).name()).append('\n');
        }
        return prompt.toString();
    }

    /** A JSON array of message parts: each file's number and name, followed by its contents. */
    private String buildDeepContent(List<FileInfo> batch) {
        List<String> parts = new ArrayList<>();
        parts.add(textPart(instructions() + " Look at each file's contents when shown - they matter more than the name."));
        for (int i = 0; i < batch.size(); i++) {
            parts.add(textPart("File " + (i + 1) + ": " + batch.get(i).name()));
            parts.addAll(ContentReader.partsFor(batch.get(i)));
        }
        return "[" + String.join(",", parts) + "]";
    }

    private static String textPart(String text) {
        return "{\"type\":\"text\",\"text\":\"" + Json.escape(text) + "\"}";
    }

    private String instructions() {
        return new StringBuilder().append("You organize a messy folder. Put each file into a short, human-friendly category ")
              .append("such as Academics, Documents, Images, Videos, Music, Installers & Apps, Code, ")
              .append("Archives, Finance, Work, Personal. Invent a new category if clearly better. ")
              .append("Reuse the same category names across files. ")
              .append("Reply ONLY with lines in the format: number|Category").toString();
    }

    /** Reads lines like "3|Academics" and matches them back to files. */
    private void parseAnswer(String answer, List<FileInfo> batch, Map<FileInfo, String> result) {
        for (String line : answer.split("\n")) {
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) continue;
            try {
                int index = Integer.parseInt(parts[0].replaceAll("[^0-9]", "")) - 1;
                String category = cleanCategory(parts[1]);
                if (index >= 0 && index < batch.size() && !category.isEmpty()) {
                    result.put(batch.get(index), category);
                }
            } catch (NumberFormatException ignored) {
                // not a valid line, skip it
            }
        }
    }

    /** Removes characters that are not allowed in folder names. */
    private String cleanCategory(String raw) {
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        // "." or ".." would move files into (or above) the root folder.
        return cleaned.matches("\\.*") ? "" : cleaned;
    }

    /** @param content the message content as JSON: a quoted string or an array of parts */
    private String askOpenAi(String content) throws Exception {
        String body = "{\"model\":\"" + model + "\",\"temperature\":0,"
                + "\"messages\":[{\"role\":\"user\",\"content\":" + content + "}]}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        return Json.readStringField(response.body(), "content");
    }
}
