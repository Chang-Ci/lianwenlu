package com.lianwenqi.app.ai

import com.lianwenqi.app.data.entity.ApiType
import com.lianwenqi.app.data.entity.ChatRole
import com.lianwenqi.app.data.entity.Provider
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 对话消息（发送给模型的统一结构） */
data class ChatTurn(val role: ChatRole, val content: String)

data class AiRequest(
    val turns: List<ChatTurn>,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String = ""
)

data class AiResult(
    val success: Boolean,
    val content: String = "",
    val error: String? = null,
    /** 深度思考内容（reasoning_content / responses 的 reasoning 项），为空表示模型没有思考过程 */
    val reasoning: String = "",
    /** 本次消耗的输入 token（usage.prompt_tokens） */
    val promptTokens: Int = 0,
    /** 本次消耗的输出 token（usage.completion_tokens） */
    val completionTokens: Int = 0,
    /** 本次输入中命中缓存的 token 数（OpenAI cached_tokens / Anthropic cache_read_input_tokens） */
    val cachedTokens: Int = 0
) {
    companion object {
        fun ok(
            text: String,
            reasoning: String = "",
            promptTokens: Int = 0,
            completionTokens: Int = 0,
            cachedTokens: Int = 0
        ) = AiResult(
            true, text,
            reasoning = reasoning,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            cachedTokens = cachedTokens
        )

        fun fail(message: String) = AiResult(false, error = message)
    }
}

// ---------- OpenAI 兼容协议 DTO ----------

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null
)

/** 流式请求时要求服务端在最后一个分片中返回 usage（必须真正发送，故强制编码默认值） */
@Serializable
data class StreamOptions(@EncodeDefault @SerialName("include_usage") val includeUsage: Boolean = true)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    /** 其中命中缓存的前缀 token 数 */
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
)

/** OpenAI 命中缓存的 token 数（usage.prompt_tokens_details.cached_tokens） */
@Serializable
data class PromptTokensDetails(@SerialName("cached_tokens") val cachedTokens: Int = 0)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null
) {
    @Serializable
    data class Choice(
        val message: ChatMessageDto? = null,
        val text: String? = null,
        @SerialName("finish_reason") val finishReason: String? = null
    )
}

/** 流式分片（SSE data: 行） */
@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null
) {
    @Serializable
    data class ChunkChoice(
        val delta: Delta? = null,
        @SerialName("finish_reason") val finishReason: String? = null
    )

    @Serializable
    data class Delta(
        val content: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null
    )
}

@Serializable
data class ApiError(val message: String? = null, val type: String? = null, val code: String? = null)

/** Responses API（OpenAI /v1/responses） */
@Serializable
data class ResponsesRequest(
    val model: String,
    val input: List<ChatMessageDto>,
    val temperature: Float? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    val instructions: String? = null,
    val stream: Boolean = false,
    /**
     * GPT 的缓存路由键：带上同一个名字，服务端会把请求尽量分派到存有你前缀缓存的那台机器，
     * 从而提高缓存命中率（名字相同的请求每分钟不要超过 15 次）。
     */
    @SerialName("prompt_cache_key") val promptCacheKey: String? = null
)

@Serializable
data class ResponsesResponse(
    @SerialName("output_text") val outputText: String? = null,
    val output: List<OutputItem> = emptyList(),
    val usage: ResponsesUsage? = null,
    val error: ApiError? = null
) {
    @Serializable
    data class OutputItem(val type: String = "", val role: String = "", val content: List<ContentPart> = emptyList())
    @Serializable
    data class ContentPart(val type: String = "", val text: String? = null)
}

@Serializable
data class ResponsesUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("input_tokens_details") val inputTokensDetails: PromptTokensDetails? = null
)

/** Responses API 流式事件（SSE data: 行） */
@Serializable
data class ResponsesStreamEvent(
    val type: String = "",
    val delta: String? = null,
    val response: ResponsesResponse? = null,
    val error: ApiError? = null
)

@Serializable
data class ModelListResponse(
    val data: List<ModelItem> = emptyList(),
    val error: ApiError? = null
) {
    @Serializable
    data class ModelItem(val id: String = "")
}

/** 供应商 + 模型的可发送配置 */
data class ModelEndpoint(
    val provider: Provider,
    val modelName: String,
    val apiType: ApiType = provider.apiType,
    val temperature: Float = 0.8f,
    /** <= 0 表示不限输出（请求时不发送 max_tokens / max_output_tokens） */
    val maxTokens: Int = 2048,
    /** 携带的历史消息条数上限 */
    val maxContext: Int = 20,
    val systemPrompt: String = ""
)

// ---------- Anthropic Messages 协议 DTO（/v1/messages） ----------

/** 缓存断点标记：标记到哪一块，服务端就把「从开头到该块」的前缀缓存下来 */
@Serializable
data class AnthropicCacheControl(@EncodeDefault val type: String = "ephemeral")

@Serializable
data class AnthropicBlock(
    /** type 为协议必填字段，强制编码（json 配置了 encodeDefaults=false） */
    @EncodeDefault val type: String = "text",
    val text: String? = null,
    /** thinking 块的正文（仅解析响应时出现） */
    val thinking: String? = null,
    @SerialName("cache_control") val cacheControl: AnthropicCacheControl? = null
)

@Serializable
data class AnthropicMessage(val role: String, val content: List<AnthropicBlock>)

@Serializable
data class AnthropicRequest(
    val model: String,
    /** Anthropic 要求必填 */
    @SerialName("max_tokens") val maxTokens: Int,
    /** 系统提示词（预设提示词 + 大纲/资料），断点打在最后一块上即「缓存点1」 */
    val system: List<AnthropicBlock>? = null,
    val messages: List<AnthropicMessage>,
    val temperature: Float? = null,
    val stream: Boolean = false
)

@Serializable
data class AnthropicUsage(
    /** 未命中缓存的输入 token */
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    /** 本次从缓存读取（命中）的 token 数，即缓存命中率分子 */
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0,
    /** 本次写入缓存的 token 数（首次会以 1.25 倍计价） */
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int = 0
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
    val error: ApiError? = null
)

/** Anthropic 流式事件（SSE data: 行，事件类型在 JSON 的 type 字段里） */
@Serializable
data class AnthropicStreamEvent(
    val type: String = "",
    val delta: AnthropicDelta? = null,
    val message: AnthropicResponse? = null,
    val usage: AnthropicUsage? = null,
    val error: ApiError? = null
)

@Serializable
data class AnthropicDelta(
    val type: String = "",
    val text: String? = null,
    val thinking: String? = null,
    /** message_delta 里携带的结束原因，max_tokens 表示被输出上限截断 */
    @SerialName("stop_reason") val stopReason: String? = null
)