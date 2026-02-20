package com.ead.dispatch.sample.domain.agents.internal.world_rule_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun worldRuleAgentPrompt(request: WorldRuleAIRequest): Prompt {
    return prompt("world-rule-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured world rule draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Avoid duplicate rule titles when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) User prompt, 2) Story context, 3) Existing rules list."; br()
                +"If inputs conflict, honor the prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the rule coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"title: short, distinct name for the rule."; br()
                +"summary: one sentence explaining the rule's core effect."; br()
                +"rule: the rule stated plainly."; br()
                +"scope: who or what the rule applies to."; br()
                +"implications: 2-5 consequences in daily life or conflict."; br()
                +"exceptions: 0-3 rare loopholes or exceptions."; br()
                +"storyImpact: how this rule shapes plot or tension."; br()

                h2("Consistency Checks")
                +"Keep implications consistent with the stated rule."; br()
                +"Avoid exceptions that contradict the core rule unless justified."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == WorldRuleAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the prompt and story context."; br()
                    +"Invent unexpected but coherent details to make the rule vivid."; br()
                    +"Use story context as grounding and inspiration for choices."; br()
                    +"Be decisive and specific; avoid vague placeholders."; br()
                    +"Only list missingFields when truly ambiguous or contradictory."; br()
                } else {
                    +"Normal mode: stay close to the user's prompt."; br()
                    +"Do not invent details beyond what is implied or required."; br()
                    +"List any missing info in missingFields."; br()
                }
            }
        }

        user {
            markdown {
                h3("User Prompt")
                +request.prompt
                br()

                val story = request.story
                if (story != null) {
                    h3("Story Context")
                    story.title?.let { +"Title: $it"; br() }
                    story.genre?.let { +"Genre: $it"; br() }
                    story.setting?.let { +"Setting: $it"; br() }
                    story.plotOutline?.let { +"Plot: $it"; br() }
                    story.styleNotes?.let { +"Style: $it"; br() }
                }

                if (request.existingRuleTitles.isNotEmpty()) {
                    h3("Existing Rules")
                    +request.existingRuleTitles.joinToString(", ")
                    br()
                }
            }
        }
    }
}
