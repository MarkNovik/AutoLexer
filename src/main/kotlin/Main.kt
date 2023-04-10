const val TO_PARSE = """12_73_"""

fun main() {
    val lang = rules {
        val decDigit = oneOf('0'..'9')
        val decDigitNoZero = oneOf('1'..'9')
        val decDigitOrSeparator = decDigit or exact('_')
        val decDigits = anyOf(
            decDigit + zeroOrMore(decDigitOrSeparator) + decDigit,
            decDigit
        )
        val doubleExponent = oneOf("Ee") + optional(oneOf("+-")) + decDigits
        val integerLiteral = anyOf(
            decDigitNoZero + oneOrMore(decDigitOrSeparator) + decDigit,
            decDigit
        )
        val doubleLiteral =
            (zeroOrMore(decDigit) + exact('.') + decDigit + optional(doubleExponent))
        val floatLiteral = anyOf(
            doubleLiteral + oneOf("Ff"),
            decDigits + oneOf("Ff")
        )

        val test = true

        if (test) {
            decDigit defines "Digit"
            decDigitNoZero + oneOrMore(decDigitOrSeparator) + decDigit defines "Integer Number"
        } else {
            integerLiteral defines ::IntegerLiteral
            doubleLiteral defines ::DoubleLiteral
            floatLiteral defines ::FloatLiteral
        }
    }
    val email = rules {
        fun withAfterDotRepetitions(rule: Rule): Rule = rule + zeroOrMore(exact('.') + rule)
        val letter = oneOf('a'..'z')
        val digit = oneOf('0'..'9')
        val acceptableLocalChar = anyOf(letter, digit, oneOf("!#$%'*+/=?^_`{|}~-"))
        val acceptableDomainChar = letter or digit
        val localPart = withAfterDotRepetitions(oneOrMore(acceptableLocalChar))
        val domain = withAfterDotRepetitions(oneOrMore(acceptableDomainChar))
        val email = localPart + exact('@') + domain
    }
    println(lang.parse(TO_PARSE))
}


class RuleBuilder {
    val rules: ArrayDeque<Pair<Rule, (String) -> Token>> = ArrayDeque()

    fun exact(char: Char): Rule = Exact(char)
    fun oneOf(chars: Iterable<Char>): Rule = OneOf(chars)
    fun oneOf(chars: CharSequence): Rule = OneOf(chars.asIterable())
    fun anyOf(vararg rules: Rule): Rule = AnyOf(rules.toList())
    fun optional(rule: Rule): Rule = ZeroOrOne(rule)
    fun zeroOrMore(rule: Rule): Rule = ZeroOrMore(rule)
    fun oneOrMore(rule: Rule): Rule = OneOrMore(rule)

    infix fun Rule.or(rule: Rule): Rule = anyOf(this, rule)
    operator fun Rule.plus(rule: Rule): Rule = RuleSequence(this, rule)

    infix fun Rule.defines(transform: (String) -> Token): Rule = also { rule -> rules.addFirst(rule to transform) }
    infix fun Rule.defines(name: String): Rule = also { rule -> rules.addFirst(rule to { Parsed(name, it) }) }

    fun build(): Grammar = Grammar(rules.asSequence())
}

class Grammar(val rules: Sequence<Pair<Rule, (String) -> Token>>) {
    fun parse(source: String): List<Token> {
        var rest = source
        val tokens = mutableListOf<Token>()
        while (rest.isNotEmpty()) {
            val (token, r) = rules
                .firstNotNullOfOrNull { (rule, wrap) ->
                    rule.match(rest)
                        ?.let { (match, rest) -> wrap(match) to rest }
                } ?: run {
                tokens += Unparsed(rest)
                return tokens
            }

            tokens += token
            rest = r
        }
        return tokens
    }
}


fun rules(block: RuleBuilder.() -> Unit): Grammar {
    val builder = RuleBuilder()
    builder.apply(block)
    return builder.build()
}