package engine

import java.time.temporal.ChronoUnit

object DiscountRules {

  val expiryRule: Transaction => Option[Double] = tx => {
    val txDate      = tx.timestamp.toLocalDate
    val daysLeft    = ChronoUnit.DAYS.between(txDate, tx.expiryDate)
    if (daysLeft > 0 && daysLeft < 30)
      Some((30 - daysLeft).toDouble)
    else
      None
  }

  val cheeseWineRule: Transaction => Option[Double] = tx => {
    val name = tx.productName.toLowerCase
    if      (name.contains("cheese")) Some(10.0)
    else if (name.contains("wine"))   Some(5.0)
    else                              None
  }

  val specialDayRule: Transaction => Option[Double] = tx =>
    if (tx.timestamp.getMonthValue == 3 && tx.timestamp.getDayOfMonth == 23)
      Some(50.0)
    else
      None

  val bulkQuantityRule: Transaction => Option[Double] = tx =>
    tx.quantity match {
      case q if q >= 6  && q <= 9  => Some(5.0)
      case q if q >= 10 && q <= 14 => Some(7.0)
      case q if q >= 15            => Some(10.0)
      case _                       => None
    }

  val appChannelRule: Transaction => Option[Double] = tx =>
    if (tx.channel.equalsIgnoreCase("App")) {
      val nearestMultipleOf5 = math.ceil(tx.quantity / 5.0).toInt * 5
      Some(nearestMultipleOf5.toDouble)
    } else
      None

  val visaRule: Transaction => Option[Double] = tx =>
    if (tx.paymentMethod.equalsIgnoreCase("Visa")) Some(5.0) else None

  val allRules: List[Transaction => Option[Double]] = List(
    expiryRule,
    cheeseWineRule,
    specialDayRule,
    bulkQuantityRule,
    appChannelRule,
    visaRule
  )
}
