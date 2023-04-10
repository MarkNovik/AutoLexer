data class MatchResult(val parsed: String, val rest: String)

sealed interface Rule {
    fun match(source: String): Sequence<MatchResult>
}

private fun Sequence<MatchResult>.normalized() = distinct().sortedByDescending { it.parsed.length }

@JvmInline
value class SingleCharMatches(private val predicate: (Char) -> Boolean) : Rule {
    override fun match(source: String): Sequence<MatchResult> = sequence {
        source
            .firstOrNull()
            ?.takeIf(predicate)
            ?.let { yield(MatchResult(it.toString(), source.drop(1))) }
    }
}

@JvmInline
value class ExactChar(private val char: Char) : Rule {
    override fun match(source: String): Sequence<MatchResult> = sequence {
        if (source.firstOrNull() == char)
            yield(MatchResult(source.take(1), source.drop(1)))
    }
}

@JvmInline
value class ExactString(private val value: String) : Rule {
    override fun match(source: String): Sequence<MatchResult> = sequence {
        if (source.startsWith(value)) yield(MatchResult(value, source.removePrefix(value)))
    }
}

@JvmInline
value class OneOf(private val chars: Iterable<Char>) : Rule {
    override fun match(source: String): Sequence<MatchResult> = sequence {
        if (source.firstOrNull()?.let { it in chars } == true)
            yield(MatchResult(source.take(1), source.drop(1)))
    }
}

@JvmInline
value class AnyOf(private val rules: Sequence<Rule>) : Rule {
    override fun match(source: String): Sequence<MatchResult> =
        rules
            .flatMap { it.match(source) }
            .normalized()
}

@JvmInline
value class ZeroOrOne(private val rule: Rule) : Rule {
    override fun match(source: String): Sequence<MatchResult> =
        sequence {
            yieldAll(rule.match(source).normalized())
            yield(MatchResult("", source))
        }
}

@JvmInline
value class ZeroOrMore(private val rule: Rule) : Rule {
    override fun match(source: String): Sequence<MatchResult> = sequence {
        yieldAll(
            rule.match(source)
                .flatMap { (p1, rest) ->
                    match(rest)
                        .map { it.copy(parsed = p1 + it.parsed) }
                        .normalized()
                }.normalized()
        )
        yield(MatchResult("", source))
    }
}

@JvmInline
value class OneOrMore(private val rule: Rule) : Rule {
    override fun match(source: String): Sequence<MatchResult> =
        rule.match(source)
            .flatMap { (p1, rest) ->
                this@OneOrMore.match(rest)
                    .map { it.copy(parsed = p1 + it.parsed) }
                    .normalized()
            }.normalized()
}

@JvmInline
value class NonEmpty(private val rule: Rule) : Rule {
    override fun match(source: String): Sequence<MatchResult> =
        rule.match(source).filter { it.parsed.isNotEmpty() }
}

class ContextDefined(val context: MutableMap<String, Rule>, val name: String): Rule {
    override fun match(source: String): Sequence<MatchResult> =
        context[name]?.match(source) ?: error("Context didn't have rule $name")
}

class RuleSequence(private val first: Rule, private val second: Rule) : Rule {
    override fun match(source: String): Sequence<MatchResult> = first.match(source)
        .flatMap { (p, rest) ->
            second.match(rest)
                .map { it.copy(parsed = p + it.parsed) }
                .normalized()
        }.normalized()
}