data class MatchResult(val parsed: String, val left: String)

sealed interface Rule {
    fun match(source: String): MatchResult?
}

@JvmInline
value class SingleCharMatches(private val predicate: (Char) -> Boolean) : Rule {
    override fun match(source: String): MatchResult? = source
        .firstOrNull()
        ?.takeIf(predicate)
        ?.let { MatchResult(it.toString(), source.drop(1)) }
}

@JvmInline
value class Exact(private val char: Char) : Rule {
    override fun match(source: String): MatchResult? =
        if (source.firstOrNull() == char) MatchResult(source.take(1), source.drop(1))
        else null
}

@JvmInline
value class OneOf(private val chars: Iterable<Char>) : Rule {
    override fun match(source: String): MatchResult? =
        if (source.firstOrNull()?.let { it in chars } == true) MatchResult(source.take(1), source.drop(1))
        else null
}

@JvmInline
value class AnyOf(private val rules: List<Rule>) : Rule {
    override fun match(source: String): MatchResult? =
        rules.firstNotNullOfOrNull { it.match(source) }

}

@JvmInline
value class ZeroOrOne(private val rule: Rule) : Rule {
    override fun match(source: String): MatchResult = rule.match(source) ?: MatchResult("", source)
}

@JvmInline
value class ZeroOrMore(private val rule: Rule) : Rule {
    override fun match(source: String): MatchResult {
        var (acc, rest) = rule.match(source) ?: return MatchResult("", source)
        while (acc.isNotEmpty()) {
            val (match, r) = rule.match(rest) ?: return MatchResult(acc, rest)
            acc += match
            rest = r
        }
        return MatchResult(acc, "")
    }
}

@JvmInline
value class OneOrMore(private val rule: Rule) : Rule {
    override fun match(source: String): MatchResult? {
        var (acc, rest) = rule.match(source) ?: return null
        while (rest.isNotEmpty()) {
            val (match, r) = rule.match(rest) ?: return MatchResult(acc, rest)
            acc += match
            rest = r
        }
        return MatchResult(acc, "")
    }
}

class RuleSequence(private val first: Rule, private val second: Rule) : Rule {
    override fun match(source: String): MatchResult? =
        first.match(source)?.let { (match1, rest) ->
            second.match(rest)?.let { (match2, rest) ->
                MatchResult(match1 + match2, rest)
            }
        }
}