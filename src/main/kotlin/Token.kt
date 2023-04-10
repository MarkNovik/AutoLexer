sealed interface Token

@JvmInline value class IntegerLiteral(val value: Int): Token {
    constructor(str: String) : this(str.toInt())
}
@JvmInline value class DoubleLiteral(val value: Double): Token {
    constructor(str: String) : this(str.toDouble())
}
@JvmInline value class FloatLiteral(val value: Float): Token {
    constructor(str: String) : this(str.toFloat())
}

@JvmInline value class Unparsed(val value: String): Token

class Parsed(
    val name: String,
    val value: Any
) : Token {
    override fun toString(): String = "$name($value)"
}