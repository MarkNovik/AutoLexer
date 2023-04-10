import kotlin.reflect.KProperty

const val TO_PARSE = """1273f"""


fun main() {
    val json = rules {
        val jsonNull = exact("null")

        val jsonBool = (exact("true") or exact("false"))

        val decDigit = oneOf('0'..'9')
        val decDigits = oneOrMore(decDigit)
        val doubleExponent = oneOf("Ee") + optional(oneOf("+-")) + decDigits
        val jsonNumber = (zeroOrMore(decDigit) + exact('.') + decDigit + optional(doubleExponent))

        val stringLiteral = oneOrMore(singleCharMatches { it != '"' })
        val jsonString = exact('"') + stringLiteral + exact('"')

        val ws = zeroOrMore(singleCharMatches(Char::isWhitespace))
        val trim = { rule: Rule -> ws + rule + ws }

        val jsonArray = every(
            trim(exact('[')),
            zeroOrMore(rule("jsonValue") + trim(exact(','))),
            optional(rule("jsonValue")),
            trim(exact(']'))
        )

        val objectEntry = jsonString + trim(exact(':')) + rule("jsonValue")
        val jsonObject = every(
            trim(exact('{')),
            zeroOrMore(objectEntry + trim(exact(','))),
            optional(objectEntry),
            trim(exact('}')),
        )

        val jsonValue by anyOf(
            jsonNull defines "JsonNull",
            jsonBool defines "JsonBool",
            jsonNumber defines "JsonNumber",
            jsonString defines "JsonString",
            jsonArray defines "JsonArray",
            jsonObject defines "JsonObject",
        ) //defines "JsonValue"
    }

    println(
        json.parse(
            """
        null
    """.trimIndent()
        )
    )

    val numeralLiterals = rules {
        val decDigit = oneOf('0'..'9')
        val decDigitNoZero = oneOf('1'..'9')
        val decDigitOrSeparator = decDigit or exact('_')
        val decDigits = anyOf(
            decDigit + zeroOrMore(decDigitOrSeparator) + decDigit,
            decDigit
        )
        val doubleExponent = oneOf("Ee") + optional(oneOf("+-")) + decDigits
        val integerLiteral = anyOf(
            decDigitNoZero + zeroOrMore(decDigitOrSeparator) + decDigit,
            decDigit
        )
        val doubleLiteral = (zeroOrMore(decDigit) + exact('.') + decDigit + optional(doubleExponent))
        val floatLiteral = anyOf(
            doubleLiteral + oneOf("Ff"),
            decDigits + oneOf("Ff")
        )

        integerLiteral defines ::IntegerLiteral
        doubleLiteral defines ::DoubleLiteral
        floatLiteral defines ::FloatLiteral
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
    println(numeralLiterals.parse(TO_PARSE))
}


class ParserBuilder {
    val recursion = mutableMapOf<String, Rule>()
    private val defined: ArrayDeque<Pair<Rule, (String) -> Token>> = ArrayDeque()

    fun exact(char: Char): Rule = ExactChar(char)
    fun exact(str: String): Rule = ExactString(str)
    fun singleCharMatches(predicate: (Char) -> Boolean): Rule = SingleCharMatches(predicate)
    fun oneOf(chars: Iterable<Char>): Rule = OneOf(chars)
    fun oneOf(chars: CharSequence): Rule = OneOf(chars.asIterable())
    fun anyOf(vararg rules: Rule): Rule = AnyOf(rules.asSequence())
    fun every(vararg rules: Rule): Rule = rules.reduce { r1, r2 -> r1 + r2 }
    fun nonempty(rule: Rule): Rule = NonEmpty(rule)
    fun optional(rule: Rule): Rule = ZeroOrOne(rule)
    fun zeroOrMore(rule: Rule): Rule = ZeroOrMore(rule)
    fun oneOrMore(rule: Rule): Rule = NonEmpty(ZeroOrMore(rule))
    fun rule(name: String): Rule = ContextDefined(recursion, name)

    infix fun Rule.or(rule: Rule): Rule = anyOf(this, rule)
    operator fun Rule.plus(rule: Rule): Rule = RuleSequence(this, rule)

    infix fun Rule.defines(transform: (String) -> Token): Rule = also { rule -> defined.addFirst(rule to transform) }
    infix fun Rule.defines(name: String): Rule = also { rule -> defined.addFirst(rule to { Parsed(name, it) }) }

    operator fun Rule.getValue(nothing: Nothing?, property: KProperty<*>): Rule = this
    operator fun Rule.provideDelegate(nothing: Nothing?, property: KProperty<*>): Rule =
        also { rule -> recursion[property.name] = rule }

    fun build(): Parser = Parser(defined.asSequence())
}

class Parser(val rules: Sequence<Pair<Rule, (String) -> Token>>) {
    fun parse(source: String): List<Token> {
        var rest = source
        val tokens = mutableListOf<Token>()
        while (rest.isNotEmpty()) {
            val (token, r) = rules
                .flatMap { (rule, wrap) ->
                    rule.match(rest).map { (parsed, rest) ->
                        wrap(parsed) to rest
                    }
                }
                .distinct()
                .minByOrNull { it.second.length } ?: return tokens + Unparsed(rest)
            tokens += token
            rest = r
        }
        return tokens
    }
}


fun rules(block: ParserBuilder.() -> Unit): Parser {
    val builder = ParserBuilder()
    builder.apply(block)
    return builder.build()
}