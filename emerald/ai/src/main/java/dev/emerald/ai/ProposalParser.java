package dev.emerald.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Strict JSON -> {@link AiProposal}. Rejects non-objects, missing fields, wrong types and any field
 * outside the schema (so "grantItems", "state":"TESTED_TRUE" or "command" never get through).
 */
public final class ProposalParser {
    private static final Set<String> FIELDS = Set.of("action", "statement", "conceptIds", "expectedObservation");

    private ProposalParser() {
    }

    public static AiProposal parse(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json.strip());
        } catch (JsonParseException e) {
            throw new ProposalRejected("malformed JSON");
        }
        if (!root.isJsonObject()) {
            throw new ProposalRejected("response is not a JSON object");
        }
        JsonObject o = root.getAsJsonObject();
        for (String key : o.keySet()) {
            if (!FIELDS.contains(key)) {
                throw new ProposalRejected("unexpected field '" + key + "'");
            }
        }
        return new AiProposal(string(o, "action"), string(o, "statement"), strings(o, "conceptIds"),
                string(o, "expectedObservation"));
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new ProposalRejected("field '" + key + "' missing or not a string");
        }
        return e.getAsString();
    }

    private static List<String> strings(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new ProposalRejected("field '" + key + "' missing or not an array");
        }
        JsonArray a = e.getAsJsonArray();
        List<String> out = new ArrayList<>();
        for (JsonElement item : a) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new ProposalRejected("field '" + key + "' contains a non-string");
            }
            out.add(item.getAsString());
        }
        return out;
    }
}
