package com.ead.dispatch.sample.domain.agents.location_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun locationAgentPrompt(request: LocationAIRequest): Prompt {
    return prompt("location-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured location draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Avoid duplicate location names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) User prompt, 2) Story context, 3) Existing locations list."; br()
                +"If inputs conflict, honor the prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the location coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short, distinct, and appropriate to the setting."; br()
                +"summary: one sentence capturing function and vibe."; br()
                +"description: 1-3 sentences; vivid but concise."; br()
                +"tags: 3-6 short tags; use setting or function language."; br()
                +"atmosphere: sensory tone in a short phrase."; br()
                +"function: how the location is used or why it matters."; br()
                +"access: who can enter and how."; br()
                +"risks: 1-4 hazards or complications."; br()
                +"storyUse: how the location matters to plot or character."; br()

                h2("Consistency Checks")
                +"Keep atmosphere and function aligned with the story's tone."; br()
                +"Align risks with the location's purpose and setting."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == LocationAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the prompt and story context."; br()
                    +"Invent unexpected but coherent details to make the location vivid."; br()
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

                if (request.existingLocationNames.isNotEmpty()) {
                    h3("Existing Locations")
                    +request.existingLocationNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
