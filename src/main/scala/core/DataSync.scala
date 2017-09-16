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

import java.sql.Timestamp
import java.util.logging.{Level, Logger}

import com.chargebee.Environment
import com.chargebee.models._
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._

import scala.collection.JavaConverters._
import MongoDBImplicits._
import me.tongfei.progressbar.{ProgressBar, ProgressBarStyle}

import scala.annotation.tailrec

object DataSync extends Parser("chargeBee data sync") with App{

  println(logo)
  Logger.getLogger("org.mongodb.driver").setLevel(Level.SEVERE)

  parse(args, OptionConf()) match {
    case Some(conf) =>

      implicit val mg: MongoClient = initialization(conf) //initialize api and mongo db parameters
      implicit val db: MongoDatabase = mg.getDatabase(conf.db)  //get mongo database
      val (from, to) = retrieveState  //retrieve last state
      val pb = new ProgressBar("Please Wait", 100, 200, System.out, ProgressBarStyle.UNICODE_BLOCK)
      println(s"""Sync data from: "${conf.site}""")

      //sync resources*/
      pb.start()
      syncSubscription(from, to, null)
      pb.stepTo(18)
      syncCustomer(from, to, null)
      pb.stepTo(58)
      syncInvoice(from, to, null)
      pb.stepTo(78)
      syncCreditNote(from, to, null)
      pb.stepTo(80)
      syncTransaction(from, to, null)
      pb.stepTo(82)
      syncHostedPage(from, to, null)
      pb.stepTo(84)
      syncPlan(from, to, null)
      pb.stepTo(86)
      syncAddon(from, to, null)
      pb.stepTo(88)
      syncCoupon(from, to, null)
      pb.stepTo(90)
      syncEvent(from, to, null)
      pb.stepTo(94)
      syncComment(from, to, null)
      pb.stepTo(96)
      syncSiteMigrationDetail(from, to, null)
      pb.stepTo(98)

      //get additional resources
      retrieveCouponCode(null)
      pb.stepTo(100)
      pb.stop()
      println("Data has been synchronized.\nExiting...")
      mg.close()

    case None => //exits
  }

  /**
    * Syncs site migration detail resources
    * @param db Mongo database
    * @param offset request offset
    */
  @tailrec
  private[core] def syncSiteMigrationDetail(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("SiteMigrationDetail")
    val results = SiteMigrationDetail.list().limit(100).offset(offset).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala.filter{entry =>
      val migratedAt = entry.siteMigrationDetail().migratedAt()
      migratedAt.after(new Timestamp(from)) && migratedAt.before(new Timestamp(to))
    }

    iterator.foreach{entry =>
      println(entry.siteMigrationDetail().migratedAt().getTime)
      val entity = entry.siteMigrationDetail()
      val json = entity.toJson
      collection.insertOne(Document(json)).results()
    }

    if(nextOffset != null) syncSiteMigrationDetail(from, to, nextOffset)
  }

  /**
    * Syncs comment resources from a timestamp to another on
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncComment(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Comment")
    val results = Comment.list().limit(100).offset(offset).createdAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.comment()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
      }

    }

    if(nextOffset != null) syncComment(from, to, nextOffset)
  }

  /**
    * Syncs events resources from a timestamp to another on
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncEvent(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Event")
    val results = Event.list().limit(100).offset(offset).occurredAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.event()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
      }

    }

    if(nextOffset != null) syncEvent(from, to, nextOffset)
  }

  /**
    * Retrieves coupon code resources
    * @param db Mongo database
    * @param offset request offset
    */
  @tailrec
  private[core] def retrieveCouponCode(offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("CouponCode")
    val results = CouponCode.list().limit(100).offset(offset).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.couponCode()
      val json = entity.toJson.replace("\"code\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.code()), Document(json)).results()
      }
    }

    if(nextOffset != null) retrieveCouponCode(nextOffset)
  }

  /**
    * Syncs coupon resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncCoupon(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Coupon")
    val results = Coupon.list().limit(100).offset(offset).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.coupon()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncCoupon(from, to, nextOffset)
  }

  /**
    * Syncs addon resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncAddon(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Addon")
    val results = Addon.list().limit(100).offset(offset).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.addon()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncAddon(from, to, nextOffset)
  }

  /**
    * Syncs plan resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncPlan(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Plan")
    val results = Plan.list().limit(100).offset(offset).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.plan()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncPlan(from, to, nextOffset)
  }

  /**
    * Syncs hosted page resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncHostedPage(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("HostedPage")
    val results = HostedPage.list().limit(100).offset(offset).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.hostedPage()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncHostedPage(from, to, nextOffset)
  }

  /**
    * Syncs transaction resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncTransaction(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Transaction")
    val results = Transaction.list().limit(100).offset(offset).includeDeleted(true).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.transaction()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncTransaction(from, to, nextOffset)
  }

  /**
    * Syncs credit note resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncCreditNote(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("CreditNote")
    val results = CreditNote.list().limit(100).offset(offset).includeDeleted(true).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.creditNote()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }
    }

    if(nextOffset != null) syncCreditNote(from, to, nextOffset)
  }

  /**
    * Syncs order for an invoice
    * @param db Mongo database
    * @param invoiceId invoice id
    * @param offset request offset
    */
  @inline
  @tailrec
  private[core] def syncOrder(invoiceId: String, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Order")
    val results = Order.list().limit(100).offset(offset).invoiceId().is(invoiceId).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.order()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
      }
    }

    if(nextOffset != null) syncOrder(invoiceId, nextOffset)
  }

  /**
    * Syncs invoice resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncInvoice(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Invoice")
    val results = Invoice.list().limit(100).offset(offset).includeDeleted(true).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.invoice()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }

      //in case there is an order for an invoice
      syncOrder(entity.id(), null)
    }

    if(nextOffset != null) syncInvoice(from, to, nextOffset)
  }

  /**
    * Sync card for a customer
    * @param db Mongo database
    * @param entity card resource
    */
  @inline
  private[core] def syncCard(entity: Card)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Card")
    if(entity != null) {
      val json = entity.toJson.replace("\"customer_id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException =>
          collection.replaceOne(equal("_id", entity.customerId()), Document(json)).results()
      }
    }
  }

  /**
    * Syncs payment sources for a customer
    * @param db Mongo database
    * @param customerId customer id
    * @param offset request offset
    */
  @inline
  @tailrec
  private[core] def syncPaymentSource(customerId: String, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("PaymentSource")
    val results = PaymentSource.list().limit(100).offset(offset).customerId.is(customerId).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.paymentSource()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
      }
    }

    if(nextOffset != null) syncPaymentSource(customerId, nextOffset)
  }

  /**
    * Syncs customer, card, payment sources resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncCustomer(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Customer")
    val results = Customer.list().limit(100).offset(offset).includeDeleted(true).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.customer()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }

      //in case there is a card for a customer
      syncCard(entry.card())

      //in case there are payment sources for a customer
      syncPaymentSource(entity.id(), null)

      //in case there are unbilled charges for a customer
      syncUnbilledCharge(entity.id(), null, subscription = false)
    }

    if(nextOffset != null) syncCustomer(from, to, nextOffset)
  }

  /**
    * Syncs unbilled charge for a subscription/customer
    * @param db Mongo database
    * @param id customer/subscription id
    * @param offset request offset
    * @param subscription if enabled the id corresponds to a subscription
    */
  @inline
  @tailrec
  private[core] def syncUnbilledCharge(id: String, offset: String, subscription: Boolean)(implicit db: MongoDatabase) {

    val collection = db.getCollection("UnbilledCharge")
    val results = if (subscription) {
      UnbilledCharge.list().limit(100).offset(offset).includeDeleted(true).subscriptionId().is(id)
        .request()
    } else {
      UnbilledCharge.list().limit(100).offset(offset).includeDeleted(true).customerId().is(id)
        .request()
    }
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.unbilledCharge()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
      }
    }

    if(nextOffset != null) syncUnbilledCharge(id, nextOffset, subscription)
  }

  /**
    * Syncs subscription resources from a timestamp to another one
    * @param from starting timestamp
    * @param to ending timestamp
    * @param offset request offset
    * @param db mongo database
    */
  @tailrec
  private[core] def syncSubscription(from: Long, to: Long, offset: String)(implicit db: MongoDatabase) {

    val collection = db.getCollection("Subscription")
    val results = Subscription.list().limit(100).offset(offset).includeDeleted(true).updatedAt()
      .between(new Timestamp(from), new Timestamp(to)).request()
    val nextOffset = results.nextOffset()
    val iterator = results.iterator().asScala

    iterator.foreach{entry =>
      val entity = entry.subscription()
      val json = entity.toJson.replace("\"id\"", "\"_id\"")
      try {
        collection.insertOne(Document(json)).results()
      }
      catch {
        case _: MongoWriteException=>
          val prevResNum = collection.find(equal("_id", entity.id())).headResult().getLong("resource_version")
          if(prevResNum < entity.resourceVersion()) {
            collection.replaceOne(equal("_id", entity.id()), Document(json)).results()
          }
      }

      //in case there are unbilling charges for a subscription
      syncUnbilledCharge(entity.id(), null, subscription = true)
    }

    if(nextOffset != null) syncSubscription(from, to, nextOffset)
  }

  /**
    * Retrieves the last timestamp that the system has synced
    * @param mg MongoClient
    * @param db mongo database
    * @return the last and the new sync timestamps
    */
  private[core] def retrieveState()(implicit mg: MongoClient, db: MongoDatabase) = {

    val state = db.getCollection("State")
    val to = System.currentTimeMillis()
    if(db.listCollectionNames().results().contains("State")) {
      val fromEntry = state.findOneAndReplace(equal("_id", 1), Document("_id" -> 1, "updatedAt" -> to)).headResult()
      fromEntry.get("updatedAt") match {
        case Some(from) => (from.asInt64().getValue, to)
        case None => (0L, to)
      }
    }
    else {
      state.insertOne(Document("_id" -> 1, "updatedAt" -> to))
        .results()
      (0L, to)
    }
  }

  private[core] def initialization(conf: OptionConf) = {
    Environment.configure(conf.site, conf.key)
    MongoClient(conf.uri)
  }

}