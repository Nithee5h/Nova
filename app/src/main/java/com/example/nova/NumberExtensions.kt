package com.example.nova

import java.math.BigDecimal
import java.math.BigInteger

operator fun BigDecimal?.unaryMinus(): BigDecimal? = this?.negate()
operator fun BigInteger?.unaryMinus(): BigInteger? = this?.negate()
