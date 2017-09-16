/*
 *    ________                          ____
 *   / ____/ /_  ____  __________ ____ / __ )___  ___
 *  / /   / __ \/ __ `/ ___/ __ `/ _ \/ __  / _ \/ _ \
 * / /___/ / / / /_/ / /  / /_/ /  __/ /_/ /  __/  __/
 * \____/_/ /_/\__,_/_/   \__, /\___/_____/\___/\___/
 *                       /____/
 *
 *        __     __
 *    ___/ /__ _/ /____ _  ___ __ _____  ____
 *   / _  / _ `/ __/ _ `/ (_-</ // / _ \/ __/
 *   \_,_/\_,_/\__/\_,_/ /___/\_, /_//_/\__/
 *                           /___/
 *
 * Copyright (c) Alexandros Mavrommatis.
 *
 * This file is part of ChargeBeeDataSync.
 *
 * ChargeBeeDataSync is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * ChargeBeeDataSync is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ChargeBeeDataSync. If not, see <http://www.gnu.org/licenses/>.
 */
package core

import java.util.UUID
import com.chargebee.models._
import com.chargebee.models.enums.EntityType
import core.DataSync._
import core.MongoDBImplicits._
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.scalatest.FunSuite

class ChargeBeeSuite(conf: OptionConf) extends FunSuite {

  implicit val mg: MongoClient = initialization(conf)
  implicit val db: MongoDatabase = mg.getDatabase(conf.db)

  val uuidSubscription: String = UUID.randomUUID().toString
  val uuidCustomer: String = UUID.randomUUID().toString
  var uuidInvoice: String = _
  var uuidCreditNote: String = _
  var uuidOrder: String = _
  val uuidPlan: String = UUID.randomUUID().toString
  val uuidAddon: String = UUID.randomUUID().toString
  val uuidCoupon: String = UUID.randomUUID().toString
  val uuidCouponCode: String = UUID.randomUUID().toString

  test("check if subscription sync works after insert") {
    val plans = Plan.list().request().iterator()
    if(plans.hasNext) {
      Subscription.create().planId(plans.next().plan().id()).id(uuidSubscription).request()
      Thread.sleep(5000)
      val (from, to) = retrieveState()
      syncSubscription(from, to, null)
    }
    val subscription = db.getCollection("Subscription").find(equal("_id", uuidSubscription)).headResult()
    assert(subscription != null)
  }

  test("check if subscription sync works after update") {
    val prevUpdatedAt = db.getCollection("Subscription").find(equal("_id", uuidSubscription)).headResult()
      .getInteger("updated_at")
    Subscription.update(uuidSubscription).billingCycles(10).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncSubscription(from, to, null)
    val subscription = db.getCollection("Subscription").find(equal("_id", uuidSubscription)).headResult()
    assert(subscription.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if customer sync works after insert") {
    Customer.create().id(uuidCustomer).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCustomer(from, to, null)
    val customer = db.getCollection("Customer").find(equal("_id", uuidCustomer)).headResult()
    assert(customer != null)
  }

  test("check if customer sync works after update") {
    val prevUpdatedAt = db.getCollection("Customer").find(equal("_id", uuidCustomer)).headResult()
      .getInteger("updated_at")
    Customer.update(uuidCustomer).firstName("first_name").request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCustomer(from, to, null)
    val customer = db.getCollection("Customer").find(equal("_id", uuidCustomer)).headResult()
    assert(customer.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if card and payment source sync works after update") {
    Card.updateCardForCustomer(uuidCustomer).number("5105105105105100").expiryMonth(10).expiryYear(2019)
      .billingZip("11111").request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCustomer(from, to, null)
    val card = db.getCollection("Card").find(equal("_id", uuidCustomer)).headResult()
    val paymentSource = db.getCollection("PaymentSource").find(equal("customer_id", uuidCustomer)).headResult()
    assert(card.getString("billing_zip").equals("11111"))
    assert(Document(paymentSource.get("card").get.toString).getString("billing_zip").equals("11111"))
  }

  test("check if invoice sync works after insert") {
    Invoice.create().customerId(uuidCustomer).chargeAmount(1, 500).chargeDescription(1, "empty").request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncInvoice(from, to, null)
    val invoice = db.getCollection("Invoice").find(and(gt("updated_at", from / 1000), lt("updated_at", to / 1000)))
      .headResult()
    uuidInvoice = invoice.getString("_id")
    assert(invoice != null)
  }

  test("check if credit note sync works after insert") {
    CreditNote.create().referenceInvoiceId(uuidInvoice).total(50)
      .reasonCode(CreditNote.ReasonCode.PRODUCT_UNSATISFACTORY).`type`(CreditNote.Type.REFUNDABLE).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCreditNote(from, to, null)
    val creditNote = db.getCollection("CreditNote")
      .find(and(gt("updated_at", from / 1000), lt("updated_at", to / 1000))).headResult()
    uuidCreditNote = creditNote.getString("_id")
    assert(creditNote != null)
  }

  test("check if credit note sync works after delete") {
    val prevUpdatedAt = db.getCollection("CreditNote").find(equal("_id", uuidCreditNote)).headResult()
      .getInteger("updated_at")
    CreditNote.delete(uuidCreditNote).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCreditNote(from, to, null)
    val creditNote = db.getCollection("CreditNote").find(equal("_id", uuidCreditNote)).headResult()
    assert(creditNote.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if order sync works after insert") {
    Order.create().invoiceId(uuidInvoice).status(Order.Status.NEW).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncInvoice(from, to, null)
    val order = db.getCollection("Order").find(equal("invoice_id", uuidInvoice)).headResult()
    uuidOrder = order.getString("_id")
    assert(order != null)
  }

  test("check if order sync works after update") {
    Order.update(uuidOrder).status(Order.Status.CANCELLED).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncInvoice(from, to, null)
    val order = db.getCollection("Order").find(equal("_id", uuidOrder)).headResult()
    assert(order.getString("status").equals("cancelled"))
  }

  test("check if hosted page sync works after insert") {
    HostedPage.checkoutExisting().subscriptionId(uuidSubscription).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncHostedPage(from, to, null)
    val hostedPage = db.getCollection("HostedPage")
      .find(and(gt("updated_at", from / 1000), lt("updated_at", to / 1000))).headResult()
    assert(hostedPage != null)
  }

  test("check if plan sync works after insert") {
    Plan.create().id(uuidPlan).name(uuidPlan).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncPlan(from, to, null)
    val plan = db.getCollection("Plan").find(equal("_id", uuidPlan)).headResult()
    assert(plan != null)
  }

  test("check if plan sync works after update") {
    val prevUpdatedAt = db.getCollection("Plan").find(equal("_id", uuidPlan)).headResult()
      .getInteger("updated_at")
    Plan.update(uuidPlan).billingCycles(10).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncPlan(from, to, null)
    val plan = db.getCollection("Plan").find(equal("_id", uuidPlan)).headResult()
    assert(plan.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if addon sync works after insert") {
    Addon.create().id(uuidAddon).name(uuidAddon).chargeType(Addon.ChargeType.RECURRING).`type`(Addon.Type.ON_OFF)
      .period(5).periodUnit(Addon.PeriodUnit.MONTH).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncAddon(from, to, null)
    val addon = db.getCollection("Addon").find(equal("_id", uuidAddon)).headResult()
    assert(addon != null)
  }

  test("check if addon sync works after update") {
    val prevUpdatedAt = db.getCollection("Addon").find(equal("_id", uuidAddon)).headResult()
      .getInteger("updated_at")
    Addon.update(uuidAddon).periodUnit(Addon.PeriodUnit.WEEK).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncAddon(from, to, null)
    val addon = db.getCollection("Addon").find(equal("_id", uuidAddon)).headResult()
    assert(addon.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if coupon sync works after insert") {
    Coupon.create().id(uuidCoupon).name(uuidCoupon).discountType(Coupon.DiscountType.PERCENTAGE)
      .applyOn(Coupon.ApplyOn.EACH_SPECIFIED_ITEM).durationType(Coupon.DurationType.FOREVER)
      .status(Coupon.Status.ARCHIVED).planConstraint(Coupon.PlanConstraint.ALL)
      .addonConstraint(Coupon.AddonConstraint.ALL).discountPercentage(30.0).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCoupon(from, to, null)
    val coupon = db.getCollection("Coupon").find(equal("_id", uuidCoupon)).headResult()
    assert(coupon != null)
  }

  test("check if coupon sync works after update") {
    val prevUpdatedAt = db.getCollection("Coupon").find(equal("_id", uuidCoupon)).headResult()
      .getInteger("updated_at")
    Coupon.unarchive(uuidCoupon).request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncCoupon(from, to, null)
    val coupon = db.getCollection("Coupon").find(equal("_id", uuidCoupon)).headResult()
    assert(coupon.getInteger("updated_at") > prevUpdatedAt)
  }

  test("check if coupon code sync works after insert") {
    CouponCode.create().code(uuidCouponCode).couponSetName(uuidCouponCode).couponId(uuidCoupon).request()
    Thread.sleep(5000)
    retrieveCouponCode(null)
    val couponCode = db.getCollection("CouponCode").find(equal("_id", uuidCouponCode)).headResult()
    assert(couponCode != null)
  }

  test("check if coupon code sync works after update") {
    CouponCode.archive(uuidCouponCode).request()
    Thread.sleep(5000)
    retrieveCouponCode(null)
    val couponCode = db.getCollection("CouponCode").find(equal("_id", uuidCouponCode)).headResult()
    assert(couponCode.getString("status").equals("archived"))
  }

  test("check if comment sync works after insert") {
    Comment.create().entityType(EntityType.SUBSCRIPTION).entityId(uuidSubscription).notes("test comment").request()
    Thread.sleep(5000)
    val (from, to) = retrieveState()
    syncComment(from, to, null)
    val comment = db.getCollection("Comment").find(equal("entity_id", uuidSubscription)).headResult()
    assert(comment != null)
  }

}

object ChargeBeeSuite extends Parser("chargeBee data sync test") with App {

  parse(args, OptionConf()) match {
    case Some(conf) => new ChargeBeeSuite(conf).execute()
    case None => //exits
  }
}




