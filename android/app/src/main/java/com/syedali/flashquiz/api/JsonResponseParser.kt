package com.syedali.flashquiz.api

import com.google.gson.JsonParser
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

object JsonResponseParser {

    fun parseFlashcards(content: String, tags: List<String>): List<Flashcard> {
        val json = cleanJson(content)
        val parsed = tryParse(json)
        val array = extractArray(parsed, listOf("flashcards", "cards", "data", "items", "questions"))

        return array.map { element ->
            val obj = element.asJsonObject
            Flashcard(
                deckId = 0,
                type = obj.get("type")?.asString ?: "Basic",
                front = obj.get("front")?.asString ?: "",
                back = obj.get("back")?.asString ?: "",
                text = obj.get("text")?.asString ?: "",
                tags = tags + (obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()),
                source = obj.get("source")?.asString ?: ""
            )
        }
    }

    fun parseMCQs(content: String, tags: List<String>): List<MCQ> {
        val json = cleanJson(content)
        val parsed = tryParse(json)
        val array = extractArray(parsed, listOf("mcqs", "questions", "data", "items"))

        return array.map { element ->
            val obj = element.asJsonObject
            val distractorExplanations = mutableMapOf<String, String>()
            obj.getAsJsonObject("distractor_explanations")?.let { de ->
                de.entrySet().forEach { (k, v) -> distractorExplanations[k] = v.asString }
            }

            MCQ(
                deckId = 0,
                question = obj.get("question").asString,
                options = obj.getAsJsonArray("options").map { it.asString },
                correctIndex = obj.get("correct_index").asInt,
                explanation = obj.get("explanation")?.asString ?: "",
                distractorExplanations = distractorExplanations,
                tags = tags + (obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList())
            )
        }
    }

    private fun cleanJson(content: String): String {
        var json = content.trim()

        // Remove markdown code blocks
        if (json.startsWith("```")) {
            json = json.removePrefix("```").removePrefix("json").removeSuffix("```").trim()
        }

        // Find the first [ or { and last ] or }
        val firstBracket = json.indexOfFirst { it == '[' || it == '{' }
        val lastBracket = json.indexOfLast { it == ']' || it == '}' }

        if (firstBracket >= 0 && lastBracket > firstBracket) {
            json = json.substring(firstBracket, lastBracket + 1)
        }

        return json
    }

    private fun tryParse(json: String): com.google.gson.JsonElement {
        // Try direct parse
        try {
            return JsonParser.parseString(json)
        } catch (e: Exception) {
            // Continue to fix attempts
        }

        // Try fixing truncated JSON
        val fixed = fixTruncatedJson(json)
        try {
            return JsonParser.parseString(fixed)
        } catch (e: Exception) {
            // Continue
        }

        // Try extracting just the array portion
        val arrayMatch = "\\[\\s*\\{.*?\\}\\s*\\]".toRegex(RegexOption.DOT_MATCHES_ALL).find(json)
        if (arrayMatch != null) {
            try {
                return JsonParser.parseString(arrayMatch.value)
            } catch (e: Exception) {
                // Continue
            }
        }

        // Last resort: try to parse each object individually
        val objects = mutableListOf<com.google.gson.JsonElement>()
        val objPattern = "\\{[^{}]*\\}".toRegex()
        objPattern.findAll(json).forEach { match ->
            try {
                objects.add(JsonParser.parseString(match.value))
            } catch (e: Exception) {
                // Skip invalid objects
            }
        }

        if (objects.isNotEmpty()) {
            val array = com.google.gson.JsonArray()
            objects.forEach { array.add(it) }
            return array
        }

        throw Exception("Could not parse JSON response")
    }

    private fun fixTruncatedJson(json: String): String {
        var fixed = json.trim()

        // Remove trailing commas
        fixed = fixed.replace(Regex(",\\s*([}\\]])"), "$1")

        // Count brackets
        val openBrackets = fixed.count { it == '[' }
        val closeBrackets = fixed.count { it == ']' }
        val openBraces = fixed.count { it == '{' }
        val closeBraces = fixed.count { it == '}' }

        // If inside a string at the end, close it
        val lastChar = fixed.lastOrNull()
        if (lastChar == '\\' || (lastChar != '"' && lastChar != '}' && lastChar != ']' && lastChar != ',')) {
            // Check if we're inside a string
            val quoteCount = fixed.count { it == '"' }
            if (quoteCount % 2 != 0) {
                fixed += "\""
            }
        }

        // Remove trailing comma before closing
        if (fixed.endsWith(",")) {
            fixed = fixed.dropLast(1)
        }

        // Close unclosed objects
        repeat(openBraces - closeBraces) { fixed += "}" }

        // Close unclosed arrays
        repeat(openBrackets - closeBrackets) { fixed += "]" }

        return fixed
    }

    private fun extractArray(parsed: com.google.gson.JsonElement, keys: List<String>): com.google.gson.JsonArray {
        return when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject -> {
                val obj = parsed.asJsonObject
                keys.firstOrNull { obj.has(it) }?.let { obj.getAsJsonArray(it) }
                    ?: throw Exception("Could not find card data in response")
            }
            else -> throw Exception("Invalid response format")
        }
    }
}
