<pre>
   ________                          ____
  / ____/ /_  ____  __________ ____ / __ )___  ___
 / /   / __ \/ __ `/ ___/ __ `/ _ \/ __  / _ \/ _ \
/ /___/ / / / /_/ / /  / /_/ /  __/ /_/ /  __/  __/
\____/_/ /_/\__,_/_/   \__, /\___/_____/\___/\___/
                      /____/

         __     __
     ___/ /__ _/ /____ _  ___ __ _____  ____
    / _  / _ `/ __/ _ `/ (_-</ // / _ \/ __/
    \_,_/\_,_/\__/\_,_/ /___/\_, /_//_/\__/
                            /___/
  
  ChargeBeeDataSync: A Scala tool for ChargeBee API
</pre>

#ChargeBeeDataSync

ChargeBeeDataSync is an open-source tool for synchronizing data from [ChargeBee API](https://apidocs.chargebee.com/docs/api) written in [Scala programming language](http://scala-lang.org).

#### Features:

It synchronizes all the available data from ChargeBee API according to their updated_at and resource_version attributes and stores them in a Mongo database.
Resources that do not contain the aforementioned attributes will be synchronized according to other appropriate timestamp attributes (except from coupon code resources, which are retrieved from scratch each time the client is executed).

(WARNING: Use the ScalaTest file only for a test site and not for a live one)

#### Requirements:

You ought to enable Order Management in your ChargeBee website for retrieving the orders, otherwise an error will be occurred (go Settings -> Site Info & Billing Rules).
You should have a MongoDB service running on your system.

###Building and Running
To build the ChargeBeeDataSync distribution, type the following command:
```
$ sbt dist
```
After a successful compilation, the distribution is located inside the ./target/universal/chargebeedatasync-*.zip archive.
You may run the client by extracting the archive and run the script "chargeBeeDataSync.sh" in "./chargebeedatasync-*/bin" folder.

####Usage: chargeBee data sync [options]

  --site <value>  ChargeBee site name (required)
  --key <value>   ChargeBee API key (required)
  --uri <value>   MongoDB URI (default: "mongodb://localhost:27017")
  --db <value>    db name (default: "chargeBee")
  --help          prints this usage text

## Contributions

Contributions are welcome.

## License

ChargeBeeDataSync comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it
under certain conditions; See the [GNU Lesser General Public License v3 for more details](http://www.gnu.org/licenses/lgpl-3.0.html).