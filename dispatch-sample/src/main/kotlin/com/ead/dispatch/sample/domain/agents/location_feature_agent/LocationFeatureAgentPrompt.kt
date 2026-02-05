package com.ead.dispatch.sample.domain.agents.location_feature_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun locationFeatureAgentPrompt(request: LocationFeatureAIRequest): Prompt {
    return prompt("location-feature-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured location feature draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Follow the user's constraints exactly and prioritize them over all other input."; br()
                +"Avoid duplicate feature names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) Constraints, 2) User prompt, 3) Story context, 4) Existing features list."; br()
                +"If inputs conflict, honor constraints and prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the feature coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short feature name."; br()
                +"summary: one sentence capturing what it is."; br()
                +"description: 1-3 sentences; concrete details."; br()
                +"featureType: landmark, hazard, resource, etc."; br()
                +"function: how it is used or why it matters."; br()
                +"risks: 1-3 risks or complications."; br()
                +"relatedLocation: where it belongs (use location list if available)."; br()

                h2("Consistency Checks")
                +"Ensure the featureType fits the description."; br()
                +"Align risks with the function and setting."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == LocationFeatureAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the constraints."; br()
                    +"Invent unexpected but coherent details to make the feature vivid."; br()
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

                if (request.existingFeatureNames.isNotEmpty()) {
                    h3("Existing Features")
                    +request.existingFeatureNames.joinToString(", ")
                    br()
                }

                if (request.locationNames.isNotEmpty()) {
                    h3("Locations")
                    +request.locationNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
