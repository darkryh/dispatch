package com.ead.dispatch.sample.domain.agents.chat_agent.planner

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.planner.AIAgentPlanner
import ai.koog.agents.planner.llm.PlanStep
import ai.koog.agents.planner.llm.SimplePlan
import ai.koog.agents.planner.llm.SimplePlanAssessment
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import kotlin.reflect.typeOf

/**
 */
class ChatModePlanner : AIAgentPlanner<String, SimplePlan>(
    stateType = typeOf<String>(),
) {
    override suspend fun buildPlan(
        context: AIAgentFunctionalContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlan {
        val planAssessment = assessPlan(context, state, plan)
        if (planAssessment is SimplePlanAssessment.Continue) {
            return planAssessment.currentPlan
        }

        val shouldReplan = planAssessment is SimplePlanAssessment.Replan

        val newPlan = context.llm.writeSession {
            replaceHistoryWithTLDR(
                strategy = RetrieveFactsFromHistory(
                    Concept(
                        keyword = "achievements",
                        description = "What important milestones have been achieved",
                        factType = FactType.MULTIPLE
                    ),
                    Concept(
                        keyword = "struggles",
                        description = "What major problems have been encountered throughout the course of the task, and how they have been resolved",
                        factType = FactType.MULTIPLE
                    )
                )
            )

            rewritePrompt { oldPrompt ->
                prompt("planner") {
                    system {
                        markdown {
                            h1("Main Goal -- Create a Plan")
                            textWithNewLine("You are a chat-mode planning agent for a writing tool.")
                            textWithNewLine("Focus on story setup data (story metadata, characters, locations, arcs, facts).")
                            textWithNewLine("Do not draft story prose or create chapters/volumes in this mode.")
                            textWithNewLine("The user chooses the starting focus; address their request first.")
                            textWithNewLine("Only suggest next steps after the user's focus is handled.")
                            textWithNewLine("Return json with fields 'goal' and 'steps'. Only output json.")
                            codeblock(
                                """
                                {
                                  "goal": "Short goal statement.",
                                  "steps": [
                                    { "description": "Step 1: address the user's request", "isCompleted": false },
                                    { "description": "Step 2: ask only for missing details required to complete it", "isCompleted": false },
                                    { "description": "Optional: suggest what to do next after the request", "isCompleted": false }
                                  ]
                                }
                                """.trimIndent(),
                                "json"
                            )

                            if (shouldReplan) {
                                h1("Previous Plan (failed)")

                                textWithNewLine("Previously it was attempted to solve the problem with another plan, but it has failed")
                                textWithNewLine("Below you'll see the previous plan with the reason for replan")

                                h2("Previous Plan Overview")

                                textWithNewLine("Previously, the following plan has been tried:")

                                h3("Previous Plan Goal")
                                textWithNewLine("The goal of the previous plan was:")
                                textWithNewLine(planAssessment.currentPlan.goal)

                                h3("Previous Plan Steps")
                                textWithNewLine("The previous plan consisted of the following consecutive steps:")

                                bulleted {
                                    planAssessment.currentPlan.steps.forEach {
                                        if (it.isCompleted) {
                                            item("[COMPLETED!] ${it.description}")
                                        } else {
                                            item(it.description)
                                        }
                                    }
                                }

                                h2("Reason(s) to Replan")

                                textWithNewLine("The previous plan needs to be revised for the following reason")

                                blockquote(planAssessment.reason)
                            }

                            h1("What to do next?")

                            textWithNewLine("You need to create a new plan with steps that will solve the user's problem:")

                            blockquote(state)

                            if (shouldReplan) {
                                bold("Note: Below you'll see some observations from the ")
                            }
                        }
                    }

                    if (shouldReplan) {
                        oldPrompt.messages.filter { it !is Message.System }.forEach {
                            message(it)
                        }
                    }
                }
            }

            val structuredPlanResult = requestLLMStructured(
                serializer = SimplePlan.serializer(),
                examples = listOf(
                    SimplePlan(
                        goal = "The main goal to be achieved by the system",
                        steps = mutableListOf(
                            PlanStep("First step description", isCompleted = true),
                            PlanStep("Second step description", isCompleted = true),
                            PlanStep("Some other action", isCompleted = false),
                            PlanStep("Action to be performed on the step 4", isCompleted = false),
                            PlanStep("Next high-level goal (5)", isCompleted = false),
                        )
                    )
                )
            ).getOrThrow()

            structuredPlanResult.data
        }

        context.llm.writeSession {
            rewritePrompt { oldPrompt ->
                prompt("agent") {
                    system {
                        markdown {
                            h1("Plan")

                            textWithNewLine("You must follow the following plan to solve the problem:")

                            h2("Main Goal:")

                            textWithNewLine(newPlan.goal)

                            h2("Plan Steps:")

                            numbered {
                                newPlan.steps.forEach {
                                    if (it.isCompleted) {
                                        item("[COMPLETED!] ${it.description}")
                                    } else {
                                        item(it.description)
                                    }
                                }
                            }
                        }
                    }

                    oldPrompt.messages.filter { it !is Message.System }.forEach {
                        message(it)
                    }
                }
            }
        }

        // Create a SimplePlan with the generated steps
        return SimplePlan(goal = newPlan.goal, steps = newPlan.steps.toMutableList())
    }

    protected suspend fun assessPlan(
        context: AIAgentFunctionalContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlanAssessment<SimplePlan> {
        // Simple implementation always continues with the current plan
        return if (plan == null) SimplePlanAssessment.NoPlan() else SimplePlanAssessment.Continue(plan)
    }

    override suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: String,
        plan: SimplePlan
    ): String {
        val currentStep = plan.steps.firstOrNull { !it.isCompleted } ?: return "All steps of the plan are completed!"

        // Execute the step using LLM
        val result = context.llm.writeSession {
            appendPrompt {
                system("You are executing a step in a plan. The goal is: ${plan.goal}")
                user("Execute the following step: ${currentStep.description}")
                user("Current state: $state")
            }

            requestLLMWithoutTools()
        }

        // Mark the step as completed
        val stepIndex = plan.steps.indexOf(currentStep)
        plan.steps[stepIndex] = currentStep.copy(isCompleted = true)

        return result.content
    }

    override suspend fun isPlanCompleted(context: AIAgentFunctionalContext, state: String, plan: SimplePlan): Boolean =
        plan.steps.all { it.isCompleted }
}
