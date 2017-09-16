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

import scopt.OptionParser

abstract class Parser(programName: String) extends OptionParser[OptionConf](programName){

  opt[String]("site").action( (x, c) =>
    c.copy(site = x) ).text("site name (required)").required()
  opt[String]("key").action( (x, c) =>
    c.copy(key = x) ).text("API key (required)").required()
  opt[String]("uri").action( (x, c) =>
    c.copy(uri = x) ).text("MongoDB URI (default: \"mongodb://localhost:27017\")")
  opt[String]("db").action( (x, c) =>
    c.copy(site = x) ).text("db name (default: \"chargeBee\")")
  help("help").text("prints this usage text")
}
