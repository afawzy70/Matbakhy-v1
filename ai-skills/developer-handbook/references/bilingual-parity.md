# Bilingual mirror (Stage E)

The source language is authoritative. Create, for every source page `name.md`, exactly one mirror `name<suffix>.md` in the same directory (default suffix `-ar` for Egyptian Arabic). The mirror is a complete semantic and structural copy: every section, paragraph, table row, list item, checklist item, warning, note, example, command, diagram, link and acceptance criterion. No summaries, no placeholders such as "same as English", no links that replace content.

## Preserve exactly
Code fences and contents, commands, configuration, environment variable names, endpoint paths, HTTP methods and status codes, file and directory paths, class/method/package names, database identifiers, branch names, workflow names, versions, config keys, identifiers (ticket, rule and test ids), URLs, evidence labels, status labels, arrows (direction matters), numbers (Western digits). Never fix the source silently; record suspected source errors separately.

## Language style (Egyptian Arabic)
Follow [egyptian-arabic-style-guide.md](egyptian-arabic-style-guide.md) (register, English-versus-Arabic rules, preferred wording, concept distinctions, typography) and check against [egyptian-arabic-anti-patterns.md](egyptian-arabic-anti-patterns.md). Never translate word for word when the result is unnatural, misleading or wrong; translate the meaning in context. Keep one glossary page per project for its own business terms (they do not belong in the skill). Watch polysemous words: *revision* is not *review*; *audit* is not *review*; *dismissal* is not *rejection*; *stale* is not *expired*; *typed* is not *typo*; *commit* is not *commitment*; *seeded* is not *planted*. For synthetic test data write "بيانات تجريبية غير حقيقية (Synthetic Data)" so it is never read as real customer data.

Every mirror then goes through the editorial QA stage in [quality-gates.md](quality-gates.md); structural parity alone never shows that the Arabic is natural.

## Structure and navigation
- Same heading levels and order, same paragraph count, same list and table shapes.
- A language-switch line directly under the metadata of both pages, linking to the other language; the two entry points (index pages) must be visible from each other.
- Internal links in the mirror point to the mirror pages; anchors point to the equivalent heading (the validator's `--fix-anchors` converts English slugs to the mirror's slugs).
- Mermaid: keep ids, arrows, syntax; translate human labels only, quoted.

## Maintenance policy
English and mirror change together in the same pull request; a renamed or removed source page needs the mirror renamed or removed; exceptions need a written reason and a reviewer note; a reviewed-pair record plus a same-PR rule in CI keep the pair honest. Do not generate machine translations inside CI.
