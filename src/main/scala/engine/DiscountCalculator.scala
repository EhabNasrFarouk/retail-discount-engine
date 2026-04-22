package engine


object DiscountCalculator {

  def computeDiscount(tx: Transaction): Double = {
    val qualifiedDiscounts: List[Double] =
      DiscountRules.allRules
        .map(rule => rule(tx))
        .flatten
        .sortWith(_ > _)
        .take(2)

    qualifiedDiscounts match {
      case Nil  => 0.0
      case ds   => ds.sum / ds.length
    }
  }

  def process(tx: Transaction): ProcessedTransaction = {
    val discount   = computeDiscount(tx)
    val finalPrice = tx.quantity * tx.unitPrice * (1.0 - discount / 100.0)
    ProcessedTransaction(tx, discount, finalPrice)
  }
}
