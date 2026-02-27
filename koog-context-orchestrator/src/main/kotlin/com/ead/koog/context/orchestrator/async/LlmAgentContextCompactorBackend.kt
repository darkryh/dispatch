package com.ead.koog.context.orchestrator.async

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * LLM-backed compactor that runs as an isolated private agent.
 *
 * Persistence remains in [ContextCompactionStore]; this backend only produces artifacts.
 */
class LlmAgentContextCompactorBackend(
    private val promptExecutor: PromptExecutor,
    private val llmModel: LLModel,
    private val temperature: Double = 0.2,
    private val maxInputMessages: Int = 100,
    private val maxCharsPerMessage: Int = 600,
    private val maxOutputChars: Int = 8_000,
) : ContextCompactorBackend {
    override suspend fun compact(job: ContextCompactionJob): ContextCompactionResult {
        if (job.promptMessages.isEmpty()) {
            return ContextCompactionResult(
                status = ContextCompactionJobStatus.STALE_SKIPPED,
                error = "No prompt messages available for compaction.",
            )
        }

        val constrainedMessages = job.promptMessages
            .takeLast(maxInputMessages)
            .map { msg -> msg.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(maxCharsPerMessage) }
            .filter { it.isNotBlank() }

        if (constrainedMessages.isEmpty()) {
            return ContextCompactionResult(
                status = ContextCompactionJobStatus.STALE_SKIPPED,
                error = "No meaningful prompt messages after normalization.",
            )
        }

        val agent = AIAgent<ContextCompactionJob, String>(
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<ContextCompactionJob, String>("context-compactor-private-agent") {
                val compactNode by node<ContextCompactionJob, String>("compact-context") { input ->
                    llm.writeSession {
                        rewritePrompt {
                            prompt("context-compactor-private-agent") {
                                system(
                                    """
                                    You are a context compactor.
                                    Produce a compact memory artifact preserving objective, constraints, open commitments, and critical references.
                                    Output only compacted text with no markdown fences and no extra commentary.
                                    Keep output concise and information-dense.
                                    """.trimIndent(),
                                )
                                user(
                                    buildUserPayload(input, constrainedMessages),
                                )
                            }
                        }
                        val response = requestLLMWithoutTools()
                        (response as? Message.Assistant)?.content.orEmpty()
                    }
                }

                edge(nodeStart forwardTo compactNode)
                edge(compactNode forwardTo nodeFinish)
            },
            responseProcessor = null,
            maxIterations = 4,
            temperature = temperature,
            id = "context-compactor-private-agent",
        )

        val compacted = runCatching { agent.run(job) }
            .getOrElse { throwable ->
                return ContextCompactionResult(
                    status = ContextCompactionJobStatus.FAILED,
                    error = throwable.message ?: throwable::class.simpleName,
                )
            }
            .trim()
            .take(maxOutputChars)

        if (compacted.isBlank()) {
            return ContextCompactionResult(
                status = ContextCompactionJobStatus.FAILED,
                error = "LLM produced empty compaction output.",
            )
        }

        return ContextCompactionResult(
            status = ContextCompactionJobStatus.SUCCEEDED,
            artifact = ContextCompactionArtifact(
                agentId = job.agentId,
                sourceVersion = job.sourceVersion,
                sourceFingerprint = job.sourceFingerprint,
                resultVersion = job.sourceVersion,
                mode = job.mode,
                text = compacted,
            ),
        )
    }

    private fun buildUserPayload(
        job: ContextCompactionJob,
        normalizedMessages: List<String>,
    ): String {
        val continuity = job.hints.continuityPacket
        val constraints = continuity?.constraints.orEmpty().take(12)
        val accepted = continuity?.acceptedDecisions.orEmpty().take(10)
        val pending = continuity?.pendingActions.orEmpty().take(10)
        val critical = continuity?.criticalReferences.orEmpty().take(10)
        val openQuestions = continuity?.openQuestions.orEmpty().take(10)

        return buildString {
            appendLine("mode=${job.mode.name}")
            appendLine("risk_zone=${job.riskZone.name}")
            appendLine("source_version=${job.sourceVersion}")
            continuity?.objective?.takeIf { it.isNotBlank() }?.let {
                appendLine("objective:")
                appendLine(it)
            }
            if (constraints.isNotEmpty()) {
                appendLine("constraints:")
                constraints.forEach { appendLine("- $it") }
            }
            if (accepted.isNotEmpty()) {
                appendLine("accepted_decisions:")
                accepted.forEach { appendLine("- $it") }
            }
            if (pending.isNotEmpty()) {
                appendLine("pending_actions:")
                pending.forEach { appendLine("- $it") }
            }
            if (critical.isNotEmpty()) {
                appendLine("critical_references:")
                critical.forEach { appendLine("- $it") }
            }
            if (openQuestions.isNotEmpty()) {
                appendLine("open_questions:")
                openQuestions.forEach { appendLine("- $it") }
            }
            appendLine("recent_messages:")
            normalizedMessages.forEach { appendLine("- $it") }
        }.trim()
    }
}
