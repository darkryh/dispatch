package com.ead.dispatch.sample.domain.agents.internal.organization_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun organizationAgentPrompt(request: OrganizationAIRequest): Prompt {
    return prompt("organization-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured organization draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Avoid duplicate organization names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) User prompt, 2) Story context, 3) Existing organizations list."; br()
                +"If inputs conflict, honor the prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the organization coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short, distinctive organization name."; br()
                +"summary: one sentence capturing purpose and vibe."; br()
                +"description: 1-3 sentences; what it is and why it matters."; br()
                +"purpose: clear mission or agenda."; br()
                +"structure: brief structure or hierarchy."; br()
                +"resources: 2-5 notable assets or capabilities."; br()
                +"publicImage: how it is perceived."; br()
                +"secrets: one key hidden agenda or secret."; br()
                +"rivals: 0-3 opposing groups or threats."; br()

                h2("Consistency Checks")
                +"Ensure structure and resources fit the organization's purpose."; br()
                +"Align public image with secrets when possible (tension is fine)."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == OrganizationAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the prompt and story context."; br()
                    +"Invent unexpected but coherent details to make the organization vivid."; br()
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

                if (request.existingOrganizationNames.isNotEmpty()) {
                    h3("Existing Organizations")
                    +request.existingOrganizationNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
