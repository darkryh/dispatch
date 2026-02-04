package com.ead.dispatch.sample.domain.agents.character_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown

fun characterAgentPrompt(request: CharacterAIRequest): Prompt {
    return prompt("character-agent") {
        system {
            markdown {
                h2("Role")
                +"You generate a structured character draft for a story in the schema provided."; br()
                +"Return only the structured data required by the schema."; br()
                +"Do not include narrative prose outside the structured fields."; br()
                +"Follow the user's constraints exactly and prioritize them over all other input."; br()
                +"Avoid duplicate character names when possible."; br()
                +"If a field is unknown or not implied, leave it null or empty."; br()

                h2("Input Priority")
                +"1) Constraints, 2) User prompt, 3) Story context, 4) Existing characters list."; br()
                +"If inputs conflict, honor constraints and prompt, then adjust the rest to fit."; br()
                +"Use story context to keep the character coherent with genre, setting, and tone."; br()

                h2("Field Guidance")
                +"name: short, distinct, and appropriate to the setting."; br()
                +"summary: one sentence capturing the core identity and role."; br()
                +"description: 1-3 sentences; vivid but concise."; br()
                +"roles: 1-3 roles max, story-facing (e.g., mentor, rival)."; br()
                +"goal: what the character wants now."; br()
                +"motivation: why they want it (values, needs, stakes)."; br()
                +"flaw: meaningful weakness or limitation."; br()
                +"internalConflict: a tension that drives behavior."; br()
                +"temperament: steady disposition (e.g., pragmatic, volatile)."; br()
                +"age: a number, range, or descriptor (e.g., late 30s)."; br()
                +"pronouns: short form (e.g., she/her)."; br()
                +"occupation: role in society or work."; br()
                +"backstory: 1-3 sentences; keep it relevant to the present."; br()
                +"voice: how they speak (cadence, diction, attitude)."; br()
                +"traits: 3-6 short traits (single ideas)."; br()
                +"quirks: 3-6 small habits or tells (single ideas)."; br()
                +"physical: keep each subfield short and concrete."; br()

                h2("Consistency Checks")
                +"Keep age, occupation, backstory, and voice consistent with each other."; br()
                +"Align temperament and goals with the story genre and stakes."; br()
                +"Avoid contradictory traits unless the conflict is explained."; br()

                h2("Draft Requirements")
                +"Provide a one-line summary in 'summary'."; br()
                +"Use 'missingFields' to note key info the user should clarify."; br()
                +"Use concise phrases; avoid long paragraphs."; br()

                h2("Output Rules")
                +"Lists must be arrays of strings (no commas inside items)."; br()
                +"Keep trait and quirk lists short (3-6 items)."; br()
                +"Keep roles list short (1-3 items)."; br()

                val isCreative = request.mode == CharacterAIMode.CREATIVE
                h2("Mode")
                if (isCreative) {
                    +"Creative mode: take full creative freedom within the constraints."; br()
                    +"Invent unexpected but coherent details to make the character vivid."; br()
                    +"Use story context as grounding and inspiration for choices."; br()
                    +"Be decisive and specific; avoid vague placeholders."; br()
                    +"If constraints exist, obey them even in creative mode."; br()
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

                if (request.existingCharacterNames.isNotEmpty()) {
                    h3("Existing Characters")
                    +request.existingCharacterNames.joinToString(", ")
                    br()
                }
            }
        }
    }
}
