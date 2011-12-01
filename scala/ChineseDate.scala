import java.util.{Date, Locale}
import java.text.DateFormat
import java.text.DateFormat._

object ChineseDate{
    def main(args: Array[String]){
        val now = new Date
        val df = getDateInstance()
        println (df format now)
    }
}
// vim: set ts=4 sw=4 et:
