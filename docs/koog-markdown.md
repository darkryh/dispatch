KOOG Markdown DSL
=================

Overview
--------
KOOG provides a Markdown DSL that lets you build structured prompt text inside
system/user/assistant blocks. The DSL lives in the `ai.koog.prompt.markdown`
package and is designed to be used alongside the `prompt` DSL.

This project currently uses KOOG `0.6.0`.

Imports
-------
Use these imports in Kotlin:

```kotlin
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
```

Basic usage
-----------
You can use `markdown { ... }` inside `system { ... }`, `user { ... }`,
or `assistant { ... }` blocks.

```kotlin
val prompt = prompt("assistant") {
    system {
        markdown {
            +"I want to create a new post on Instagram."
            br()
            +"Can you write something creative under my instagram post?"
            br()
            h2("Requirements")
            bulleted {
                item("Be funny and creative")
                item("No explicit content or harassment")
                item("Keep it short")
            }
        }
    }
}
```

Core builders
-------------
The markdown DSL exposes a `MarkdownContentBuilder` with these functions:

- `header(level, text)`
- `h1` ... `h6`
- `bold(text)`
- `italic(text)`
- `strikethrough(text)`
- `code(text)` for inline code
- `codeblock(code, language)` for fenced blocks
- `link(text, url)`
- `image(alt, url)`
- `horizontalRule()`
- `blockquote(text)`
- `line { ... }` for a single-line builder
- `table(headers, rows, alignments)` with `TableAlignment.LEFT/CENTER/RIGHT`
- `bulleted { ... }` / `numbered { ... }` for lists

Text helpers
------------
`MarkdownContentBuilder` extends KOOG's text builder and supports:

- `+"text"` or `text("text")`
- `br()` or `newline()` for line breaks
- `textWithNewLine("text")`

Line builder
------------
`line { ... }` gives a `LineContext` with chainable helpers:

- `text("...")`, `space()`
- `bold("...")`, `italic("...")`, `strikethrough("...")`, `code("...")`
- `link("label", "url")`, `image("alt", "url")`

Lists
-----
`bulleted` and `numbered` provide `item` overloads:

```kotlin
bulleted {
    item("Plain item")
    item {
        bold("Bold item")
        space()
        text("with extra text")
    }
}
```

Tables
------
Tables are built with headers, rows, and optional alignments:

```kotlin
table(
    headers = listOf("Field", "Value"),
    rows = listOf(listOf("POV", "First person")),
    alignments = listOf(TableAlignment.LEFT, TableAlignment.RIGHT)
)
```

Notes
-----
- Use `content_ref` + `content_range` in the DB to point AI edits to specific
  Markdown sections.
- Keep the Markdown DSL inside prompt builders for consistent formatting.
