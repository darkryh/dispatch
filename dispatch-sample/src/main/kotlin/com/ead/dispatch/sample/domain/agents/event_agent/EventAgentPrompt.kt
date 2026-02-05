package com.ead.dispatch.sample.domain.agents.event_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun eventAgentPrompt(request: EventAIRequest): Prompt {
    return prompt("event-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured event draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Follow the user's constraints exactly and prioritize them over all other input."; br()
                +"Avoid duplicate event names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) Constraints, 2) User prompt, 3) Story context, 4) Existing events list."; br()
                +"If inputs conflict, honor constraints and prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the event coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short, event-like title."; br()
                +"summary: one sentence capturing what happened."; br()
                +"description: 1-3 sentences; focused on the key beats."; br()
                +"timeframe: when it occurs (date, era, relative time)."; br()
                +"location: where it happens (use location list if available)."; br()
                +"causes: 1-4 drivers leading to the event."; br()
                +"consequences: 2-5 outcomes or ripples."; br()
                +"participants: key people or groups involved (names or roles)."; br()
                +"status: planned, ongoing, or completed."; br()

                h2("Consistency Checks")
                +"Align consequences with the causes and participants."; br()
                +"Keep timing consistent with the story timeline."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == EventAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the constraints."; br()
                    +"Invent unexpected but coherent details to make the event vivid."; br()
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

                h3("Constraints")
                +if (request.constraints.isNullOrBlank()) "None" else request.constraints
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

                if (request.existingEventNames.isNotEmpty()) {
                    h3("Existing Events")
                    +request.existingEventNames.joinToString(", ")
                    br()
                }

                if (request.locationNames.isNotEmpty()) {
                    h3("Locations")
                    +request.locationNames.joinToString(", ")
                    br()
                }

                if (request.organizationNames.isNotEmpty()) {
                    h3("Organizations")
                    +request.organizationNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
