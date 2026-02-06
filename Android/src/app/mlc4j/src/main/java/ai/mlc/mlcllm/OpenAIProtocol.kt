package ai.mlc.mlcllm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object OpenAIProtocol {

    @Serializable
    data class TopLogprobs(
        val token: String,
        val logprob: Float,
        val bytes: List<Int>? = null
    )

    @Serializable
    data class LogprobsContent(
        val token: String,
        val logprob: Float,
        val bytes: List<Int>? = null,
        val top_logprobs: List<TopLogprobs> = emptyList()
    )

    @Serializable
    data class Logprobs(
        val content: List<LogprobsContent>? = null
    )

    @Serializable
    data class ChatFunctionCall(
        val name: String,
        val arguments: String? = null
    )

    @Serializable
    data class ChatToolCall(
        val id: String = "",
        val type: String = "function",
        val function: ChatFunctionCall
    )

    @Serializable
    data class ChatCompletionMessage(
        val role: ChatCompletionRole = ChatCompletionRole.user,
        val content: String? = null,
        val name: String? = null,
        val tool_calls: List<ChatToolCall>? = null,
        val tool_call_id: String? = null
    )

    @Serializable
    enum class ChatCompletionRole {
        @SerialName("system")
        system,
        @SerialName("user")
        user,
        @SerialName("assistant")
        assistant,
        @SerialName("tool")
        tool
    }

    @Serializable
    data class ChatCompletionMessageContent(
        val asText: String? = null
    ) {
        fun asText(): String = asText ?: ""
    }

    @Serializable
    data class StreamOptions(
        val include_usage: Boolean = false
    )

    @Serializable
    data class ResponseFormat(
        val type: String = "text",
        val schema: JsonElement? = null
    )

    @Serializable
    data class ChatFunction(
        val description: String? = null,
        val name: String,
        val parameters: JsonElement? = null
    )

    @Serializable
    data class ChatTool(
        val type: String = "function",
        val function: ChatFunction
    )

    @Serializable
    data class ChatCompletionRequest(
        val messages: List<ChatCompletionMessage>,
        val model: String? = null,
        val frequency_penalty: Float? = null,
        val presence_penalty: Float? = null,
        val logprobs: Boolean = false,
        val top_logprobs: Int = 0,
        val logit_bias: Map<Int, Float>? = null,
        val max_tokens: Int? = null,
        val n: Int = 1,
        val seed: Int? = null,
        val stop: List<String>? = null,
        val stream: Boolean = false,
        val stream_options: StreamOptions? = null,
        val temperature: Float? = null,
        val top_p: Float? = null,
        val tools: List<ChatTool>? = null,
        val user: String? = null,
        val response_format: ResponseFormat? = null
    )

    @Serializable
    data class UsageExtra(
        val prefill_tokens_per_s: Float? = null,
        val decode_tokens_per_s: Float? = null
    )

    @Serializable
    data class Usage(
        val prompt_tokens: Int = 0,
        val completion_tokens: Int = 0,
        val total_tokens: Int = 0,
        val extra: UsageExtra? = null
    )

    @Serializable
    data class ChatCompletionStreamResponseChoiceDelta(
        val content: ChatCompletionMessageContent? = null,
        val role: ChatCompletionRole? = null,
        val tool_calls: List<ChatToolCall>? = null
    )

    @Serializable
    data class ChatCompletionStreamResponseChoice(
        val index: Int = 0,
        val finish_reason: String? = null,
        val delta: ChatCompletionStreamResponseChoiceDelta = ChatCompletionStreamResponseChoiceDelta(),
        val logprobs: Logprobs? = null
    )

    @Serializable
    data class ChatCompletionStreamResponse(
        val id: String,
        val choices: List<ChatCompletionStreamResponseChoice> = emptyList(),
        val created: Long? = null,
        val model: String? = null,
        val system_fingerprint: String? = null,
        val `object`: String = "chat.completion.chunk",
        val usage: Usage? = null
    )
}
