package com.ead.dispatch.sample.domain.agents.culture_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun cultureAgentPrompt(request: CultureAIRequest): Prompt {
    return prompt("culture-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured culture draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Avoid duplicate culture names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) User prompt, 2) Story context, 3) Existing cultures list."; br()
                +"If inputs conflict, honor the prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the culture coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short, distinct, and culture-appropriate."; br()
                +"summary: one sentence capturing the culture's essence."; br()
                +"description: 1-3 sentences on identity and worldview."; br()
                +"values: 3-6 guiding values or priorities."; br()
                +"rituals: 2-4 signature practices."; br()
                +"taboos: 1-3 strong prohibitions."; br()
                +"symbols: 1-3 common symbols."; br()
                +"socialStructure: concise description of hierarchy or roles."; br()
                +"storyRole: how this culture impacts the story."; br()

                h2("Consistency Checks")
                +"Ensure rituals and taboos align with the values."; br()
                +"Keep social structure consistent with the setting and genre."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                val isCreative = request.mode == CultureAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the prompt and story context."; br()
                    +"Invent unexpected but coherent details to make the culture vivid."; br()
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

                if (request.existingCultureNames.isNotEmpty()) {
                    h3("Existing Cultures")
                    +request.existingCultureNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
