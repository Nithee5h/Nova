package com.example.nova

import java.math.BigDecimal
import java.math.BigInteger

// If any nullable BigDecimal/BigInteger is negated in your code:
operator fun BigDecimal?.unaryMinus(): BigDecimal? = this?.negate()
operator fun BigInteger?.unaryMinus(): BigInteger? = this?.negate()
